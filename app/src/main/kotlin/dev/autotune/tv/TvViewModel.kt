package dev.autotune.tv

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.autotune.dsp.Layout
import dev.autotune.dsp.PlayMethod
import dev.autotune.dsp.Speaker
import dev.autotune.link.LINK_PORT
import dev.autotune.link.LinkMsg
import dev.autotune.link.LinkServer
import dev.autotune.link.Msg
import dev.autotune.link.NSD_TYPE
import dev.autotune.link.Pairing
import dev.autotune.link.ShownRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.Inet4Address

/** What the TV screen shows. */
data class TvUi(
    val code: String = "",
    val tvName: String = "",
    val address: String? = null,
    val probe: OutputProbe? = null,
    /** The paired phone, while it is connected. */
    val phone: String? = null,
    val playing: Speaker? = null,
    /** Seconds into the clip that is playing. */
    val playT: Double = 0.0,
    val layout: Layout = Layout.L51,
    val rows: List<ShownRow> = emptyList(),
    val headline: String? = null,
    val eqOn: Boolean = false,
    val error: String? = null,
)

/**
 * The TV side of a home-theatre test: waits for the phone on the Wi-Fi, plays the clip the phone asks for, shows what
 * the phone found, and holds the TV's own EQ. Only a phone that sent this TV's code is listened to.
 */
class TvViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    private val _ui = MutableStateFlow(TvUi())
    val ui: StateFlow<TvUi> = _ui

    // the shared TV-or-PC end of the link: it writes on its own thread, so sending from the main thread is safe
    private val server = LinkServer(
        code = { _ui.value.code },
        welcome = {
            val p = _ui.value.probe
            Msg.welcome(_ui.value.tvName, (p?.method ?: PlayMethod.STEREO).name, TvEq.on, p?.output ?: "TV")
        },
        onMessage = ::handle,
        onPhone = { name -> _ui.update { it.copy(phone = name, error = if (name != null) null else it.error) } },
        deniedText = "That code is not the one on the TV.",
    )
    private var playJob: Job? = null
    private var eachJob: Job? = null
    private var nsdListener: NsdManager.RegistrationListener? = null

    init {
        val prefs = ctx.getSharedPreferences("autotune_tv", Context.MODE_PRIVATE)
        val code = prefs.getString("code", null) ?: Pairing.newCode().also { prefs.edit().putString("code", it).apply() }
        val probe = runCatching { TvAudio.probe(ctx) }.getOrNull()
        val layout = probe?.method?.layouts?.first() ?: Layout.L20
        TvEq.restore(ctx)
        _ui.update { it.copy(code = code, tvName = deviceName(), address = ipAddress(), probe = probe, layout = layout, eqOn = TvEq.on) }
        startServer()
    }

    private fun deviceName(): String =
        runCatching { Settings.Global.getString(ctx.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: Build.MODEL

    private fun ipAddress(): String? = runCatching {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        cm.getLinkProperties(cm.activeNetwork)?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }.getOrNull()

    private fun startServer() {
        viewModelScope.launch(Dispatchers.IO) {
            val port = runCatching { server.start(LINK_PORT) }.getOrNull()
            if (port == null) { _ui.update { it.copy(error = "Could not open a connection for the phone.") }; return@launch }
            announce(port)
        }
    }

    private fun announce(port: Int) {
        val nsd = ctx.getSystemService(NsdManager::class.java) ?: return
        val info = NsdServiceInfo().apply { serviceName = "AutoTune on ${_ui.value.tvName}"; serviceType = NSD_TYPE; this.port = port }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) {}
            override fun onRegistrationFailed(i: NsdServiceInfo, code: Int) {}
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, code: Int) {}
        }
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l); nsdListener = l }
    }

    private fun send(m: LinkMsg) { server.send(m) }

    private fun handle(m: LinkMsg) {
        when (m.type) {
            Msg.PLAY -> {
                val id = m["id"]?.toIntOrNull() ?: return
                val sp = Speaker.of(m["speaker"]) ?: return send(Msg.failed(id, "Unknown speaker"))
                Layout.of(m["layout"])?.let { l -> _ui.update { it.copy(layout = l) } }
                play(sp) { err -> send(if (err == null) Msg.done(id) else Msg.failed(id, err)) }
                send(Msg.playing(id, sp))
            }
            Msg.SHOW -> _ui.update {
                it.copy(layout = Layout.of(m["layout"]) ?: it.layout, headline = m["headline"], rows = Msg.rows(m["rows"]))
            }
            Msg.EQ -> setEq(Msg.chains(m["chains"]))
            Msg.EQ_OFF -> setEq(emptyMap())
        }
    }

    private fun setEq(c: Map<dev.autotune.dsp.Speaker, dev.autotune.dsp.EqChain>) {
        val err = TvEq.set(ctx, c, _ui.value.probe?.method ?: PlayMethod.STEREO)
        _ui.update { it.copy(eqOn = TvEq.on && err == null, error = err) }
        send(Msg.eqState(TvEq.on && err == null, err))
    }

    /** Plays [sp]'s clip; [then] gets null when it played, or what went wrong. */
    private fun play(sp: Speaker, then: (String?) -> Unit = {}) {
        playJob?.cancel()
        val method = _ui.value.probe?.method ?: PlayMethod.STEREO
        playJob = viewModelScope.launch {
            _ui.update { it.copy(playing = sp, playT = 0.0) }
            // raw sound passes the TV's EQ; a test must read the speakers as they are
            TvEq.hold(true)
            val err = runCatching { TvAudio.play(ctx, method, sp) { t -> _ui.update { it.copy(playT = t) } } }.exceptionOrNull()
            TvEq.hold(false)
            _ui.update { it.copy(playing = null) }
            then(err?.let { it.message ?: "The TV could not play it." })
        }
    }

    /** Plays every speaker in turn from the TV's remote, to hear that each one is where it should be. */
    fun playEach() {
        if (eachJob?.isActive == true || _ui.value.playing != null) {
            eachJob?.cancel(); playJob?.cancel(); TvEq.hold(false); _ui.update { it.copy(playing = null) }
            return
        }
        eachJob = viewModelScope.launch {
            for (sp in _ui.value.layout.speakers) { play(sp); playJob?.join() }
        }
    }

    fun setLayout(l: Layout) = _ui.update { it.copy(layout = l) }

    fun eqOff() { viewModelScope.launch(Dispatchers.IO) { setEq(emptyMap()) } }

    override fun onCleared() {
        runCatching { nsdListener?.let { ctx.getSystemService(NsdManager::class.java)?.unregisterService(it) } }
        server.close()
        super.onCleared()
    }
}
