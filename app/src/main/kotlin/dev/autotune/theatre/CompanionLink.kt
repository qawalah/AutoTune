package dev.autotune.theatre

import android.content.Context
import android.os.Build
import dev.autotune.audio.AutoTuneWidgets
import dev.autotune.link.LinkMsg
import dev.autotune.link.Msg
import dev.autotune.reconnectDelayMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The phone's link to AutoTune on a TV or PC, for the whole app (2.25; owner: "the app disconnects quickly from the
 * companion, I want it persistent"). Before, the link lived and died with the home-theatre page's view model, sent
 * nothing while idle, and once the phone slept or the Wi-Fi blinked it stayed down until paired again.
 *
 * Now: one link outlives every screen; a keep-alive goes out every [PING_MS] (TCP keep-alive too), so a dead link is
 * noticed; the TV or PC, its address and its code are remembered, and a link that drops (not by Disconnect) is tried
 * again by itself ([reconnectDelayMs]); the page and the widget reconnect on demand ([ensure]).
 */
object CompanionLink {
    private const val PING_MS = 15_000L
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var link: LinkClient
        private set

    /** The paired TV's or PC's name and its welcome, the latest pairing first; null while none is paired. */
    private val _welcome = MutableStateFlow<Pair<String, LinkMsg>?>(null)
    val welcome: StateFlow<Pair<String, LinkMsg>?> = _welcome
    private val _reconnecting = MutableStateFlow(false)
    /** True while a dropped link is being tried again. */
    val reconnecting: StateFlow<Boolean> = _reconnecting

    /** False after Disconnect: a drop is then not tried again (the page and the widget still can). */
    @Volatile private var wanted = false
    private var pinger: Job? = null
    private var retry: Job? = null

    private val prefs get() = app.getSharedPreferences("autotune_prefs", Context.MODE_PRIVATE)

    @Synchronized
    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        link = LinkClient(app, scope)
        scope.launch {
            link.open.collect { open ->
                pinger?.cancel()
                if (open) pinger = scope.launch { while (true) { delay(PING_MS); link.send(Msg.ping()) } }
                else {
                    _welcome.value = null
                    if (wanted) retryLater()
                }
                AutoTuneWidgets.update(app)
            }
        }
        // the TV's EQ state, kept for the widget (it shows it with the app closed)
        scope.launch {
            link.incoming.collect { m ->
                if (m.type == Msg.EQ_STATE) { prefs.edit().putBoolean(PREF_EQ_ON, m["on"] == "1").apply(); AutoTuneWidgets.update(app) }
            }
        }
    }

    val open: Boolean get() = ::link.isInitialized && link.open.value

    /** The remembered TV or PC, or null before the first pairing. */
    fun rememberedName(ctx: Context): String? = ctx.getSharedPreferences("autotune_prefs", Context.MODE_PRIVATE).getString(PREF_NAME, null)

    private fun phoneName() = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    /** Pairs with a TV or PC found on the Wi-Fi; remembers it. */
    suspend fun connect(tv: FoundTv, code: String): LinkMsg = paired(link.connect(tv, code, phoneName()), tv.name, code)

    /** Pairs with a TV or PC by its address; remembers it. */
    suspend fun connect(host: String, port: Int, code: String, name: String): LinkMsg = paired(link.connect(host, port, code, phoneName()), name, code)

    private fun paired(w: LinkMsg, name: String, code: String): LinkMsg {
        val tvName = w["name"] ?: name
        prefs.edit().putString(PREF_NAME, tvName).putString(PREF_CODE, code).putString(PREF_HOST, link.lastHost).putInt(PREF_PORT, link.lastPort)
            .putBoolean(PREF_EQ_ON, w["eq"] == "1").apply()
        wanted = true
        retry?.cancel(); _reconnecting.value = false
        _welcome.value = tvName to w
        AutoTuneWidgets.update(app)
        return w
    }

    /**
     * Connected to the remembered TV or PC, connecting first when needed: at its last address, else by its name on the
     * Wi-Fi (its address may have changed). False when there is none or it does not answer.
     */
    suspend fun ensure(): Boolean {
        if (open) return true
        val name = prefs.getString(PREF_NAME, null) ?: return false
        val code = prefs.getString(PREF_CODE, null) ?: return false
        val host = prefs.getString(PREF_HOST, null)
        if (host != null && runCatching { connect(host, prefs.getInt(PREF_PORT, dev.autotune.link.LINK_PORT), code, name) }.isSuccess) return true
        // not there any more: look for it by name for a few seconds
        link.discover()
        val tv = withTimeoutOrNull(6_000) { link.found.first { l -> l.any { it.name == name } } }?.firstOrNull { it.name == name } ?: return false
        return runCatching { connect(tv, code) }.isSuccess
    }

    private fun retryLater() {
        if (retry?.isActive == true) return
        retry = scope.launch {
            _reconnecting.value = true
            val start = System.currentTimeMillis()
            var attempt = 0
            try {
                while (wanted && !open) {
                    val wait = reconnectDelayMs(attempt, System.currentTimeMillis() - start) ?: break
                    delay(wait)
                    if (ensure()) break
                    attempt++
                }
            } finally { _reconnecting.value = false }
        }
    }

    /** Disconnect: closes the link and stops trying again. The TV or PC stays remembered for the widget. */
    fun disconnect() {
        wanted = false
        retry?.cancel()
        _reconnecting.value = false
        if (::link.isInitialized) link.close()
        _welcome.value = null
        AutoTuneWidgets.update(app)
    }

    const val PREF_NAME = "companionName"
    const val PREF_CODE = "companionCode"
    const val PREF_HOST = "companionHost"
    const val PREF_PORT = "companionPort"
    /** The TV's or PC's EQ is on (its last report), the home theatre's EQ and speakers last sent, and the flavour. */
    const val PREF_EQ_ON = "companionEqOn"
    const val PREF_BASE = "companionBase"
    const val PREF_SPEAKERS = "companionSpeakers"
    const val PREF_FLAVOUR = "companionFlavour"
}
