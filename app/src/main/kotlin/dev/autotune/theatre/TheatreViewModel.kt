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
import dev.autotune.audio.LiveCapture
import dev.autotune.dsp.EqReport
import dev.autotune.dsp.EqJudge
import dev.autotune.dsp.DistanceReport
import dev.autotune.dsp.DistanceJudge
import dev.autotune.dsp.PassKind
import dev.autotune.dsp.PassReading
import dev.autotune.dsp.PassClip
import dev.autotune.dsp.PassAnalysis
import dev.autotune.dsp.LevelNoise
import dev.autotune.dsp.LevelMeter
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

/** The level test: the speaker playing noise, front left's reading, the change to make so far, seconds held level. */
data class LevelState(val speaker: Speaker, val ref: Double?, val change: Double?, val steady: Int, val index: Int, val count: Int)

data class TheatreUi(
    val found: List<String> = emptyList(),
    val connecting: Boolean = false,
    /** The paired TV's or PC's name, while connected. */
    val tv: String? = null,
    /** What answered: [Msg.KIND_TV] or [Msg.KIND_PC] (AutoTune on Windows). */
    val kind: String = Msg.KIND_TV,
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
    /** It plays the three tests of 2.16 (levels, distances, EQ); older ones test speaker by speaker. */
    val threeTests: Boolean = false,
    /** The home theatres measured on this phone (id, name), and the one open. */
    val tests: List<Pair<String, String>> = emptyList(),
    val test: TheatreTest? = null,
    /** The last distances and EQ results of the open test. */
    val distances: DistanceReport? = null,
    val eq: EqReport? = null,
    /** How many seats the EQ test averages, and while it runs, the seat (1-based) and whether it waits for the next. */
    val seats: Int = 1,
    val seat: Int? = null,
    val waitingSeat: Boolean = false,
    /** The share of the EQ's shape the EQ check heard arrive (1 = all), null before one ran. */
    val eqReached: Double? = null,
    /** The test whose result shows first. */
    val focus: RunKind = RunKind.EQ,
    /** The EQ runs (by time) drawn together on the chart. */
    val compare: Set<Long> = emptySet(),
    /** A phone or tablet standing in for a bigger system; [real] is what it really is. */
    val pretend: Boolean = false,
    val real: String? = null,
    /** The level test, while it runs: the speaker playing noise, its change to make (+ = up), seconds held level. */
    val level: LevelState? = null,
    /** Every speaker is playing at once (the system test's last sweep). */
    val allPlaying: Boolean = false,
) {
    /** The word for what plays, in keys and messages: "TV" or "PC". */
    val device: String get() = if (kind == Msg.KIND_PC) "PC" else "TV"

    /** The changes the last level test of the open test ended with, per speaker. */
    val levelsDone: Map<Speaker, Double> get() = test?.last(RunKind.LEVELS)?.levels.orEmpty()
}

/**
 * A home-theatre test from the phone: the TV plays each speaker in turn, the phone listens, and the result (levels,
 * distances, EQ per speaker) is shown on both. The first full test is kept as the stock reference.
 */
/** Seconds of noise before the level is read: the home theatre switches over and the noise settles at the seat. */
private const val LEVEL_SETTLE_SECONDS = 3

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
        latest?.let(::judge)
        // the named tests: 2.13-2.15 results become the first one's runs
        runCatching { TheatreTests.migrate(ctx.filesDir) }
        val pick = prefs.getString("theatreTest", null)
        val all = TheatreTests.list(ctx.filesDir)
        val open = all.firstOrNull { it.id == pick } ?: all.firstOrNull()
        _ui.update { it.copy(tests = all.map { t -> t.id to t.name }, seats = prefs.getInt("theatreSeats", 1)) }
        open?.let { openTest(it) }
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
                    it.copy(connecting = false, tv = tvName, kind = w["kind"] ?: Msg.KIND_TV, method = method, output = w["out"], tvEqOn = w["eq"] == "1", layout = layout,
                        codes = it.codes + (tvName to code) + (name to code),
                        threeTests = w["caps"].orEmpty().split(',').containsAll(listOf("noise", "distances", "eqtest")),
                        pretend = w["pretend"] == "1", real = w["real"])
                }
                // this TV's or PC's own home theatre, never another device's results
                if (_ui.value.threeTests) openTestFor(tvName)
                if (_ui.value.threeTests) sendResult(_ui.value.focus) else _ui.value.report?.let(::show)
                Diag.log(ctx, "theatre_connected", mapOf("method" to method.name, "output" to w["out"], "kind" to (w["kind"] ?: Msg.KIND_TV)))
            } catch (e: Exception) {
                _ui.update { it.copy(connecting = false, error = e.message ?: "Could not connect.") }
            }
        }
    }

    fun disconnect() { stop(); link.close() }

    fun setLayout(l: Layout) {
        if (_ui.value.busy) return
        _ui.update { it.copy(layout = l) }
        _ui.value.latest?.let(::judge)
        judgeTest()
    }

    fun micGranted(ok: Boolean) = _ui.update { it.copy(needsMic = !ok) }

    private val failures = HashMap<Int, CompletableDeferred<String>>()
    /** When the TV said the clip that is being recorded has ended (0 = not yet). */
    @Volatile private var doneAt = 0L

    private fun onMessage(m: LinkMsg) {
        when (m.type) {
            Msg.FAILED -> m["id"]?.toIntOrNull()?.let { failures[it]?.complete(m["reason"] ?: "The ${_ui.value.device} could not play it.") }
            Msg.DONE -> m["id"]?.toIntOrNull()?.let { if (it == nextId) doneAt = System.currentTimeMillis() }
            // the PC changed its speakers (stereo, 5.1, 7.1): the layouts follow
            Msg.WELCOME -> PlayMethod.of(m["method"])?.let { method ->
                _ui.update {
                    it.copy(method = method, layout = if (it.layout in method.layouts) it.layout else method.layouts.first(), output = m["out"] ?: it.output, tvEqOn = m["eq"] == "1")
                }
                judgeTest()
            }
            Msg.EQ_STATE -> _ui.update { it.copy(tvEqOn = m["on"] == "1", error = m["error"] ?: it.error) }
            // the system test: the TV names each speaker as its sweep starts, then "all"
            Msg.PLAYING -> if (m["id"]?.toIntOrNull() == nextId && passing) {
                val all = m["speaker"] == Msg.ALL
                val s = Speaker.of(m["speaker"])
                _ui.update { it.copy(playing = s, allPlaying = all, status = (if (all) "every speaker" else s?.title?.lowercase() ?: "") + " · sweep") }
            }
        }
    }

    // ---- the two tests (2.15): levels with pink noise, then the whole system in one pass

    @Volatile private var advance = false
    @Volatile private var passing = false

    private fun micOk(): Boolean {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) return true
        _ui.update { it.copy(needsMic = true) }
        return false
    }

    /**
     * The level test: each speaker in turn plays pink noise until it is level with front left (held there for
     * [LevelMeter.STEADY_SECONDS]) or Next is pressed; the phone reads it once a second, so the change shows live
     * while you turn that speaker's level on the home theatre.
     */
    fun levelTest() {
        val u = _ui.value
        if (u.busy || u.tv == null || !u.threeTests || !micOk()) return
        val layout = u.layout
        val speakers = layout.speakers
        _ui.update { it.copy(busy = true, error = null, status = null, focus = RunKind.LEVELS) }
        startLive()
        job = viewModelScope.launch {
            val cal = loadCalibration()
            var ref: Double? = null
            val done = LinkedHashMap<Speaker, Double>()
            try {
                for ((k, s) in speakers.withIndex()) {
                    advance = false
                    _ui.update { it.copy(playing = s, allPlaying = false, level = LevelState(s, ref, null, 0, k, speakers.size), status = "${s.title.lowercase()} · level noise") }
                    link.send(Msg.noise(s, layout.name))
                    val readings = ArrayList<Double>()
                    var steady = 0
                    var last: Double? = null
                    MeasurementRunner.recordOnly(UsbMic.find(ctx), fs, 180, stopWhen = { buf, got ->
                        // the first seconds let the home theatre switch over and the noise settle at the seat
                        if (got / fs >= LEVEL_SETTLE_SECONDS) {
                            val lv = LevelMeter.bandDb(buf, got - fs, got, fs, LevelNoise.band(s), cal)
                            val r0 = ref
                            if (r0 == null) readings += lv
                            else {
                                val ch = LevelMeter.change(lv, r0, s, target)
                                last = ch
                                steady = if (LevelMeter.steady(ch)) steady + 1 else 0
                                _ui.update { it.copy(level = it.level?.copy(change = ch, steady = steady)) }
                            }
                        }
                        when {
                            ref == null -> readings.size >= 2 || (advance && readings.isNotEmpty())
                            else -> advance || steady >= LevelMeter.STEADY_SECONDS
                        }
                    }) {}
                    if (ref == null) {
                        // front left sets the level every other speaker is matched to
                        ref = 10 * kotlin.math.log10(readings.map { Math.pow(10.0, it / 10) }.average())
                        done[s] = 0.0
                    } else done[s] = last ?: 0.0
                }
                addRun(TheatreRun(RunKind.LEVELS, System.currentTimeMillis(), layout, u.tv, u.pretend, u.real, levels = done.toMap()))
                Diag.log(ctx, "theatre_levels", done.mapKeys { it.key.code })
                sendResult(RunKind.LEVELS)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _ui.update { it.copy(error = e.message ?: "The level test stopped.") }
            } finally {
                link.send(Msg.quiet())
                stopLive()
                _ui.update { it.copy(busy = false, playing = null, level = null, status = null) }
            }
        }
    }

    /** The level test moves on to the next speaker now. */
    fun nextLevel() { advance = true }

    // ---- distances and EQ: one set of start beeps, then the sweeps of the test

    /** The distances test: each speaker's sweep in turn after one set of beeps; run at setup, and when a speaker moves. */
    fun distancesTest() {
        val u = _ui.value
        if (u.busy || u.tv == null || !u.threeTests || !micOk()) return
        val layout = u.layout
        _ui.update { it.copy(busy = true, error = null, status = null, focus = RunKind.DISTANCES) }
        job = viewModelScope.launch {
            startLive()
            try {
                val pass = measurePass(layout, loadCalibration(), hold = false, kind = PassKind.DISTANCES)
                addRun(TheatreRun(RunKind.DISTANCES, System.currentTimeMillis(), layout, u.tv, u.pretend, u.real, readings = pass.speakers))
                Diag.log(ctx, "theatre_distances", mapOf("layout" to layout.name, "heard" to pass.speakers.count { it.heard }))
                sendResult(RunKind.DISTANCES)
            } finally {
                stopLive(); passing = false
                _ui.update { it.copy(busy = false, playing = null, allPlaying = false, status = null) }
            }
        }
    }

    private var seatGo: CompletableDeferred<Unit>? = null

    /**
     * The EQ test: every speaker together, at [TheatreUi.seats] seats (the phone waits at each for Next seat), the
     * readings power-averaged, one EQ for every speaker fitted from the test's first EQ run. The first one plays with
     * AutoTune's own EQ held off; later ones with it as it is, so they show what the EQ did.
     */
    fun eqTest() {
        val u = _ui.value
        if (u.busy || u.tv == null || !u.threeTests || !micOk()) return
        val layout = u.layout
        val hold = u.test?.eqStock == null
        _ui.update { it.copy(busy = true, error = null, status = null, focus = RunKind.EQ, eqReached = null) }
        job = viewModelScope.launch {
            startLive()
            val cal = loadCalibration()
            val seats = ArrayList<SpeakerReading?>()
            try {
                for (k in 1..u.seats) {
                    if (k > 1) {
                        val go = CompletableDeferred<Unit>()
                        seatGo = go
                        _ui.update { it.copy(seat = k, waitingSeat = true, status = "move the phone to seat $k") }
                        go.await()
                    }
                    _ui.update { it.copy(seat = k, waitingSeat = false) }
                    seats += measurePass(layout, cal, hold, PassKind.EQ).system
                }
                val sys = PassAnalysis.averageSeats(seats)
                addRun(TheatreRun(RunKind.EQ, System.currentTimeMillis(), layout, u.tv, u.pretend, u.real, system = sys, seats = seats.size,
                    eqInUse = if (!hold && u.tvEqOn && u.method != PlayMethod.DOLBY_51) "on the ${u.device}" else null))
                Diag.log(ctx, "theatre_eq", mapOf("layout" to layout.name, "seats" to seats.size, "heard" to (sys?.heard == true), "hold" to hold))
                sendResult(RunKind.EQ)
            } finally {
                stopLive(); passing = false; seatGo = null
                _ui.update { it.copy(busy = false, playing = null, allPlaying = false, status = null, seat = null, waitingSeat = false) }
            }
        }
    }

    /** The EQ test goes on at the next seat. */
    fun nextSeat() { seatGo?.complete(Unit) }

    fun setSeats(n: Int) {
        prefs.edit().putInt("theatreSeats", n).apply()
        _ui.update { it.copy(seats = n.coerceIn(1, 5)) }
    }

    /**
     * Whether the TV's or PC's EQ reaches the speakers: the EQ test twice at this seat, the EQ held, then on, and how
     * much of the EQ's shape changed between them.
     */
    fun eqCheck() {
        val u = _ui.value
        val eq = u.eq?.eq?.takeIf { it.isNotEmpty() } ?: return
        if (u.busy || u.tv == null || !u.threeTests || !micOk()) return
        val chain = EqChain(eq.sortedBy { it.freqHz }, EqChain.withSafePreamp(eq).preampDb)
        _ui.update { it.copy(busy = true, error = null, status = null, focus = RunKind.EQ, eqReached = null) }
        job = viewModelScope.launch {
            startLive()
            try {
                val cal = loadCalibration()
                // Dolby passes by the TV's EQ, so the check plays plain stereo through the TV's mix, both times
                val pcm = u.method == PlayMethod.DOLBY_51
                val off = measurePass(u.layout, cal, hold = true, PassKind.EQ, pcm).system
                val on = measurePass(u.layout, cal, hold = false, PassKind.EQ, pcm).system
                val r = EqJudge.reached(off, on, chain)
                _ui.update { it.copy(eqReached = r ?: -1.0) }
                Diag.log(ctx, "theatre_eq_check", mapOf("reached" to r))
            } finally {
                stopLive(); passing = false
                _ui.update { it.copy(busy = false, playing = null, allPlaying = false, status = null) }
            }
        }
    }

    private suspend fun measurePass(layout: Layout, cal: Calibration?, hold: Boolean, kind: PassKind, pcm: Boolean = false): PassReading {
        doneAt = 0L
        val id = ++nextId
        passing = true
        val failed = CompletableDeferred<String>()
        failures[id] = failed
        _ui.update { it.copy(status = "waiting for the start beeps", playing = null, allPlaying = false) }
        val after = (PassClip.afterMarkerSeconds(layout, kind) * fs).toInt()
        var markerAt = -1
        val seconds = (PassClip.seconds(layout, kind) + 9).toInt()
        fun none(why: String): PassReading {
            val r = PassClip.slots(layout, kind).map { SpeakerReading(it ?: ChannelClip.REFERENCE, null, null, null, why) }
            return PassReading(if (kind == PassKind.EQ) emptyList() else r, if (kind == PassKind.EQ) r.first() else null)
        }
        try {
            val rec = MeasurementRunner.recordOnly(
                UsbMic.find(ctx), fs, seconds,
                stopWhen = { buf, got ->
                    if (markerAt < 0) markerAt = Marker.find(buf, fs, max(0, got - 5 * fs), got) ?: -1
                    // the clip ended a while ago and no beeps came: stop waiting
                    val gaveUp = markerAt < 0 && doneAt > 0 && System.currentTimeMillis() - doneAt > 2500
                    failed.isCompleted || gaveUp || (markerAt >= 0 && got >= markerAt + after)
                },
            ) { sec -> if (sec == 1) link.send(Msg.pass(id, layout.name, hold, kind.code, pcm)) }
            if (failed.isCompleted) return none(failed.getCompleted())
            if (markerAt < 0) return none("The start beeps were not heard: turn the volume up and check front left plays.")
            _ui.update { it.copy(status = "reading", playing = null, allPlaying = false) }
            return withContext(Dispatchers.Default) { PassAnalysis.read(rec, fs, layout, cal, markerAt, kind) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return none(e.message ?: "The recording failed.")
        } finally {
            failures.remove(id)
        }
    }

    // ---- the named tests and their runs

    private fun openTest(t: TheatreTest) {
        prefs.edit().putString("theatreTest", t.id).apply()
        val layout = t.runs.lastOrNull()?.layout ?: _ui.value.layout
        _ui.update { it.copy(test = t, layout = if (it.method == null || layout in it.method.layouts) layout else it.layout, compare = emptySet(), eqReached = null) }
        judgeTest()
    }

    /**
     * Opens [device]'s home theatre: the newest made on it, or the open one when it has no runs yet (it becomes this
     * device's), else a new one named after the device.
     */
    private fun openTestFor(device: String) {
        val all = TheatreTests.list(ctx.filesDir)
        val mine = TheatreTests.forDevice(all, device)
        val open = _ui.value.test
        val t = when {
            mine != null -> mine
            open != null && open.runs.isEmpty() && open.device == null -> open.forDevice(device).also { TheatreTests.save(ctx.filesDir, it) }
            else -> TheatreTest(TheatreTests.newId(), device, System.currentTimeMillis(), emptyList(), device).also { TheatreTests.save(ctx.filesDir, it) }
        }
        _ui.update { it.copy(tests = TheatreTests.list(ctx.filesDir).map { r -> r.id to r.name }, distances = null, eq = null) }
        openTest(t)
    }

    fun selectTest(id: String) {
        if (_ui.value.busy) return
        TheatreTests.load(ctx.filesDir, id)?.let(::openTest)
    }

    /** A new home theatre, empty, named [name]; it becomes the open one. */
    fun newTest(name: String) {
        if (_ui.value.busy) return
        val t = TheatreTest(TheatreTests.newId(), name.trim().ifEmpty { "Home theatre" }, System.currentTimeMillis(), emptyList(), _ui.value.tv)
        TheatreTests.save(ctx.filesDir, t)
        _ui.update { it.copy(tests = listOf(t.id to t.name) + it.tests) }
        openTest(t)
    }

    fun renameTest(name: String) {
        val t = _ui.value.test?.renamed(name) ?: return
        TheatreTests.save(ctx.filesDir, t)
        _ui.update { it.copy(test = t, tests = it.tests.map { (id, n) -> id to if (id == t.id) t.name else n }) }
    }

    fun deleteTest() {
        val t = _ui.value.test ?: return
        if (_ui.value.busy) return
        TheatreTests.delete(ctx.filesDir, t.id)
        val rest = TheatreTests.list(ctx.filesDir)
        _ui.update { it.copy(tests = rest.map { r -> r.id to r.name }, test = null, distances = null, eq = null) }
        rest.firstOrNull()?.let(::openTest)
    }

    /** The note on the last run of [kind]. */
    fun setNote(kind: RunKind, note: String) {
        val t = _ui.value.test ?: return
        val i = t.runs.indexOfLast { it.kind == kind }
        if (i < 0) return
        val runs = t.runs.toMutableList().also { it[i] = it[i].withNote(note) }
        val nt = TheatreTest(t.id, t.name, t.createdMillis, runs, t.device)
        TheatreTests.save(ctx.filesDir, nt)
        _ui.update { it.copy(test = nt) }
    }

    /** Draws an EQ run on the chart or takes it off; three at most, the oldest pick making way. */
    fun toggleCompare(whenMillis: Long) = _ui.update {
        it.copy(compare = if (whenMillis in it.compare) it.compare - whenMillis else (it.compare.sorted().takeLast(2) + whenMillis).toSet())
    }

    fun setFocus(k: RunKind) = _ui.update { it.copy(focus = k) }

    /** Saves [r] to the open test (a new one, "Home theatre", when none is open) and judges it. */
    private fun addRun(r: TheatreRun) {
        val t = _ui.value.test ?: TheatreTest(TheatreTests.newId(), _ui.value.tv ?: "Home theatre", System.currentTimeMillis(), emptyList(), _ui.value.tv).also { nt ->
            _ui.update { it.copy(tests = listOf(nt.id to nt.name) + it.tests) }
            prefs.edit().putString("theatreTest", nt.id).apply()
        }
        val nt = t.with(r)
        TheatreTests.save(ctx.filesDir, nt)
        _ui.update { it.copy(test = nt) }
        judgeTest()
    }

    /** The open test's last distances and EQ, judged for the layout in use. */
    private fun judgeTest() {
        val t = _ui.value.test
        val layout = _ui.value.layout
        val d = t?.last(RunKind.DISTANCES)?.takeIf { it.layout == layout }?.let { DistanceJudge.judge(layout, it.readings, target) }
        val e = t?.last(RunKind.EQ)?.let { r -> EqJudge.judge(r.system, r.seats, target, t.eqStock?.takeIf { it !== r }?.system ?: r.system) }
        _ui.update { it.copy(distances = d, eq = e) }
    }

    /** The result of the last [kind] run, on the TV or PC: the room's rows, the readings, the chart and the notes. */
    private fun sendResult(kind: RunKind) {
        val u = _ui.value
        val layout = u.layout
        val levels = u.levelsDone
        val d = u.distances
        val rows = layout.speakers.map { s ->
            val dist = d?.speakers?.firstOrNull { it.speaker == s }
            ShownRow(s, levels[s]?.let { TheatreJudge.trimText(it) } ?: "—", dist?.let { TheatreJudge.distanceText(it.distanceM) } ?: "—", dist?.heard ?: true)
        }
        val (headline, readings, notes) = when (kind) {
            RunKind.LEVELS -> {
                val worst = levels.values.maxOfOrNull { kotlin.math.abs(it) } ?: 0.0
                Triple(if (worst <= LevelMeter.OK_DB) "Every speaker is level" else "Levels within ${fmt1(worst)} dB",
                    listOf(Triple("±${fmt1(worst)}", "dB", "levels")), emptyList())
            }
            RunKind.DISTANCES -> Triple(d?.headline ?: "Distances", listOfNotNull(d?.let { Triple(fmt2(it.spreadM), "m", "distance") }, d?.let { Triple("${it.heardCount}/${it.speakers.size}", "", "heard") }), d?.notes.orEmpty())
            else -> {
                val e = u.eq
                Triple(e?.headline ?: "EQ", listOfNotNull(e?.rmsDb?.let { Triple(fmt1(it), "dB", "from target") }, e?.let { Triple("${it.seats}", "", if (it.seats == 1) "seat" else "seats") }),
                    listOfNotNull(u.test?.last(RunKind.EQ)?.system?.fatal))
            }
        }
        link.send(Msg.show(layout.name, headline, rows))
        val grid = dev.autotune.dsp.FreqGrid()
        val resp = u.eq?.systemDb?.takeIf { kind == RunKind.EQ }?.let { m ->
            val sm = grid.smoothOctave(m, 3.0)
            val ref = TheatreJudge.level(m, ChannelClip.REFERENCE, grid)
            dev.autotune.dsp.Rta.CENTERS.map { f -> sm[grid.indexAtOrAbove(f).coerceAtMost(grid.size - 1)] - ref }
        }
        val tgt = resp?.let { dev.autotune.dsp.Rta.CENTERS.map { f -> target.dbAt(f) } }
        link.send(Msg.result(headline, readings, resp, tgt, notes, null))
    }

    private fun fmt1(x: Double) = String.format(java.util.Locale.US, "%.1f", x)
    private fun fmt2(x: Double) = String.format(java.util.Locale.US, "%.2f", x)

    // ---- what the phone hears, on the TV while it tests (a few times a second)

    private var liveJob: Job? = null

    private fun startLive() {
        liveJob?.cancel()
        val history = ArrayDeque<Float>()
        liveJob = viewModelScope.launch {
            while (true) {
                val lv = LiveCapture.level.value
                history.addLast(lv); while (history.size > 48) history.removeFirst()
                val wave = history.joinToString("") { (((it + 60f) / 60f * 35f).toInt().coerceIn(0, 35)).toString(36) }
                val u = _ui.value
                link.send(Msg.live(lv, wave, u.status, u.level?.change, Msg.sampleText(LiveCapture.wave.value)))
                kotlinx.coroutines.delay(150)
            }
        }
    }

    private fun stopLive() { liveJob?.cancel(); liveJob = null }

    /** The pinned key: tests what the last result asks for (one speaker, or all). */
    fun next() {
        val u = _ui.value
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
            var session = base ?: TheatreSession(System.currentTimeMillis(), layout, method, u.tv, emptyList(), pretend = u.pretend, real = u.real)
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
                if (session.complete && !u.pretend && session.system == null && (stock == null || stock?.layout != layout)) {
                    stock = session
                    TheatreStore.save(dir, "stock", session)
                }
                _ui.update { it.copy(latest = session, hasStock = stock != null) }
                judge(session)?.let(::show)
                addRun(TheatreRun(RunKind.SPEAKERS, session.whenMillis, layout, u.tv, u.pretend, u.real, readings = session.readings))
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
     * Sends each speaker's EQ to the TV or PC. One preamp for all, so the levels just set stay level: the largest any
     * speaker needs.
     */
    fun useEq() {
        // the two tests give one EQ for every speaker, as a soundbar's or receiver's own EQ has
        val u0 = _ui.value
        if (u0.threeTests) {
            val eq = u0.eq?.eq?.sortedBy { it.freqHz }?.takeIf { it.isNotEmpty() } ?: return
            val chain = EqChain(eq, EqChain.withSafePreamp(eq).preampDb)
            link.send(Msg.eq(u0.layout.speakers.associateWith { chain }))
            Diag.log(ctx, "theatre_eq_sent", mapOf("speakers" to u0.layout.speakers.size, "preampDb" to chain.preampDb, "one" to true))
            return
        }
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
        _ui.value.latest?.let(::judge)
    }

    override fun onCleared() {
        link.stopDiscovery()
        link.close()
        super.onCleared()
    }
}
