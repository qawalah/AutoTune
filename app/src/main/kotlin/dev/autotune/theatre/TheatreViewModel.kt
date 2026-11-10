package dev.autotune.theatre

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.autotune.Diag
import dev.autotune.audio.MeasurementRunner
import dev.autotune.audio.UsbMic
import dev.autotune.dsp.Calibration
import dev.autotune.dsp.ChannelAnalysis
import dev.autotune.dsp.ChannelClip
import dev.autotune.dsp.EqChain
import dev.autotune.dsp.Layout
import dev.autotune.dsp.Marker
import dev.autotune.dsp.PlayMethod
import dev.autotune.dsp.Speaker
import dev.autotune.dsp.SpeakerReading
import dev.autotune.dsp.Target
import dev.autotune.dsp.TheatreJudge
import dev.autotune.dsp.TheatreReport
import dev.autotune.link.LinkMsg
import dev.autotune.link.Msg
import dev.autotune.link.ShownRow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

data class TheatreUi(
    val found: List<String> = emptyList(),
    val connecting: Boolean = false,
    /** The paired TV's name, while connected. */
    val tv: String? = null,
    val method: PlayMethod? = null,
    val output: String? = null,
    val tvEqOn: Boolean = false,
    val layout: Layout = Layout.L51,
    val playing: Speaker? = null,
    val busy: Boolean = false,
    val status: String? = null,
    val error: String? = null,
    val latest: TheatreSession? = null,
    val report: TheatreReport? = null,
    val hasStock: Boolean = false,
    /** The code last used, per TV name, so a TV is paired once. */
    val codes: Map<String, String> = emptyMap(),
    val needsMic: Boolean = false,
)

/**
 * A home-theatre test from the phone: the TV plays each speaker in turn, the phone listens, and the result (levels,
 * distances, EQ per speaker) is shown on both. The first full test is kept as the stock reference.
 */
class TheatreViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    private val fs = ChannelClip.FS
    private val dir get() = File(ctx.filesDir, "theatre")
    private val prefs = ctx.getSharedPreferences("autotune_prefs", Context.MODE_PRIVATE)
    val link = LinkClient(ctx, viewModelScope)
    private val _ui = MutableStateFlow(TheatreUi())
    val ui: StateFlow<TheatreUi> = _ui
    private var job: Job? = null
    private var nextId = 0
    private var stock: TheatreSession? = null
    private val target: Target get() = Target.HOME_THEATER

    init {
        stock = TheatreStore.load(dir, "stock")
        val latest = TheatreStore.load(dir, "latest")
        val codes = prefs.all.filterKeys { it.startsWith("tvCode_") }.mapNotNull { (k, v) -> (v as? String)?.let { k.removePrefix("tvCode_") to it } }.toMap()
        _ui.update { it.copy(latest = latest, layout = latest?.layout ?: Layout.L51, hasStock = stock != null, codes = codes) }
        latest?.let { judge(it) }
        viewModelScope.launch { link.found.collect { f -> _ui.update { it.copy(found = f.map { t -> t.name }) } } }
        viewModelScope.launch { link.open.collect { open -> if (!open) _ui.update { it.copy(tv = null, playing = null) } } }
        viewModelScope.launch { link.incoming.collect(::onMessage) }
    }

    fun start() { link.discover() }

    fun pause() { link.stopDiscovery() }

    private fun phoneName() = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    fun connect(index: Int, code: String) {
        val tv = link.found.value.getOrNull(index) ?: return
        doConnect(tv.name, code) { link.connect(tv, code, phoneName()) }
    }

    fun connectAddress(host: String, code: String) {
        doConnect(host, code) { link.connect(host.trim(), code = code, phone = phoneName()) }
    }

    private fun doConnect(name: String, code: String, how: suspend () -> LinkMsg) {
        if (_ui.value.connecting) return
        _ui.update { it.copy(connecting = true, error = null) }
        viewModelScope.launch {
            try {
                val w = how()
                val tvName = w["name"] ?: name
                prefs.edit().putString("tvCode_$tvName", code).putString("tvCode_$name", code).apply()
                val method = PlayMethod.of(w["method"]) ?: PlayMethod.STEREO
                _ui.update {
                    val layout = if (it.layout in method.layouts) it.layout else method.layouts.first()
                    it.copy(connecting = false, tv = tvName, method = method, output = w["out"], tvEqOn = w["eq"] == "1", layout = layout,
                        codes = it.codes + (tvName to code) + (name to code))
                }
                _ui.value.report?.let(::show)
                Diag.log(ctx, "theatre_connected", mapOf("method" to method.name, "output" to w["out"]))
            } catch (e: Exception) {
                _ui.update { it.copy(connecting = false, error = e.message ?: "Could not connect to the TV.") }
            }
        }
    }

    fun disconnect() { stop(); link.close() }

    fun setLayout(l: Layout) {
        if (_ui.value.busy) return
        _ui.update { it.copy(layout = l) }
        _ui.value.latest?.let { judge(it) }
    }

    fun micGranted(ok: Boolean) = _ui.update { it.copy(needsMic = !ok) }

    private val failures = HashMap<Int, CompletableDeferred<String>>()
    /** When the TV said the clip that is being recorded has ended (0 = not yet). */
    @Volatile private var doneAt = 0L

    private fun onMessage(m: LinkMsg) {
        when (m.type) {
            Msg.FAILED -> m["id"]?.toIntOrNull()?.let { failures[it]?.complete(m["reason"] ?: "The TV could not play it.") }
            Msg.DONE -> m["id"]?.toIntOrNull()?.let { if (it == nextId) doneAt = System.currentTimeMillis() }
            Msg.EQ_STATE -> _ui.update { it.copy(tvEqOn = m["on"] == "1", error = m["error"] ?: it.error) }
        }
    }

    /** The pinned key: tests what the last result asks for (one speaker, or all). */
    fun next() {
        val r = _ui.value.report
        if (r != null && r.move == dev.autotune.dsp.TheatreMove.USE_EQ) useEq()
        else test(theatreNextSpeakers(r, _ui.value.layout))
    }

    fun testAll() = test(_ui.value.layout.speakers)

    fun testOne(s: Speaker) = test(listOf(s))

    private fun test(speakers: List<Speaker>) {
        val u = _ui.value
        if (u.busy || u.tv == null) return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _ui.update { it.copy(needsMic = true) }
            return
        }
        val layout = u.layout
        val method = u.method ?: PlayMethod.STEREO
        _ui.update { it.copy(busy = true, error = null, status = null) }
        job = viewModelScope.launch {
            val cal = loadCalibration()
            val base = _ui.value.latest?.takeIf { it.layout == layout && speakers.size < layout.speakers.size }
            var session = base ?: TheatreSession(System.currentTimeMillis(), layout, method, u.tv, emptyList())
            try {
                for (s in speakers) {
                    val r = measure(s, layout, cal)
                    session = session.with(r, System.currentTimeMillis())
                    Diag.log(ctx, "theatre_speaker", mapOf("speaker" to s.code, "heard" to r.heard, "deltaMs" to r.deltaMs, "snr" to r.snrDb, "fatal" to r.fatal))
                }
            } finally {
                _ui.update { it.copy(busy = false, playing = null, status = null) }
            }
            if (session.readings.isNotEmpty()) {
                TheatreStore.save(dir, "latest", session)
                // the first full test of a layout is the stock reference every EQ is fitted from
                if (session.complete && (stock == null || stock?.layout != layout)) {
                    stock = session
                    TheatreStore.save(dir, "stock", session)
                }
                _ui.update { it.copy(latest = session, hasStock = stock != null) }
                judge(session)?.let(::show)
            }
        }
    }

    fun stop() {
        job?.cancel()
        _ui.update { it.copy(busy = false, playing = null, status = null) }
    }

    private fun loadCalibration(): Calibration? =
        runCatching { File(ctx.filesDir, "calibration.txt").takeIf { it.exists() }?.readText()?.let { Calibration.parse(it) } }.getOrNull()

    private suspend fun measure(s: Speaker, layout: Layout, cal: Calibration?): SpeakerReading {
        doneAt = 0L
        val id = ++nextId
        val failed = CompletableDeferred<String>()
        failures[id] = failed
        _ui.update { it.copy(playing = s, status = "${s.title.lowercase()} · waiting for the start beeps") }
        val after = (ChannelClip.afterMarkerSeconds * fs).toInt()
        var markerAt = -1
        val seconds = (ChannelClip.seconds + 9).toInt()
        try {
            val rec = MeasurementRunner.recordOnly(
                UsbMic.find(ctx), fs, seconds,
                stopWhen = { buf, got ->
                    if (markerAt < 0) markerAt = Marker.find(buf, fs, max(0, got - 5 * fs), got) ?: -1
                    // the clip ended a while ago and no beeps came: stop waiting
                    val gaveUp = markerAt < 0 && doneAt > 0 && System.currentTimeMillis() - doneAt > 2500
                    failed.isCompleted || gaveUp || (markerAt >= 0 && got >= markerAt + after)
                },
            ) { sec ->
                if (sec == 1) link.send(Msg.play(id, s, layout.name))
                if (markerAt >= 0) _ui.update { it.copy(status = "${s.title.lowercase()} · sweep") }
            }
            if (failed.isCompleted) return SpeakerReading(s, null, null, null, failed.getCompleted())
            _ui.update { it.copy(status = "${s.title.lowercase()} · reading") }
            return withContext(Dispatchers.Default) { ChannelAnalysis.read(rec, fs, s, cal, markerAt.takeIf { it >= 0 }) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return SpeakerReading(s, null, null, null, e.message ?: "The recording failed.")
        } finally {
            failures.remove(id)
        }
    }

    private fun judge(session: TheatreSession): TheatreReport? {
        val layout = _ui.value.layout
        if (session.layout != layout) { _ui.update { it.copy(report = null) }; return null }
        val r = TheatreJudge.judge(layout, session.readings, target, stock?.takeIf { it.layout == layout }?.readings)
        _ui.update { it.copy(report = r) }
        return r
    }

    private fun show(r: TheatreReport) {
        val rows = r.speakers.map { ShownRow(it.speaker, TheatreJudge.trimText(it.trimDb), TheatreJudge.distanceText(it.distanceM), it.heard) }
        link.send(Msg.show(r.layout.name, r.headline, rows))
    }

    /**
     * Sends each speaker's EQ to the TV. One preamp for all, so the levels just set stay level: the largest any
     * speaker needs.
     */
    fun useEq() {
        val r = _ui.value.report ?: return
        val withEq = r.speakers.filter { it.heard }
        val pre = withEq.minOfOrNull { EqChain.withSafePreamp(it.eq).preampDb } ?: 0.0
        val chains = r.layout.speakers.associateWith { s -> EqChain(withEq.firstOrNull { it.speaker == s }?.eq.orEmpty().sortedBy { it.freqHz }, pre) }
        link.send(Msg.eq(chains))
        Diag.log(ctx, "theatre_eq_sent", mapOf("speakers" to chains.size, "preampDb" to pre))
    }

    fun tvEqOff() = link.send(Msg.eqOff())

    /** Forgets the stock reference: the next full test becomes it. */
    fun startOver() {
        stock = null
        TheatreStore.delete(dir, "stock")
        _ui.update { it.copy(hasStock = false) }
        _ui.value.latest?.let { judge(it) }
    }

    override fun onCleared() {
        link.stopDiscovery()
        link.close()
        super.onCleared()
    }
}
