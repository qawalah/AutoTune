package dev.autotune.pc

import dev.autotune.dsp.ChannelClip
import dev.autotune.dsp.Channels
import dev.autotune.dsp.LevelNoise
import dev.autotune.dsp.PassClip
import dev.autotune.dsp.PassKind
import dev.autotune.dsp.EqChain
import dev.autotune.dsp.Layout
import dev.autotune.dsp.PlayMethod
import dev.autotune.dsp.Speaker
import dev.autotune.link.LinkMsg
import dev.autotune.link.LinkServer
import dev.autotune.link.Msg
import dev.autotune.link.Pairing
import dev.autotune.link.ShownRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the phone hears, as it sends it about seven times a second while it records. */
class PcLive(val levelDb: Float, val samples: FloatArray, val status: String?, val change: Double?, val atMillis: Long)

/** A test's result as the phone sends it: readings (value, unit, name), notes and the next step. */
class PcResult(val headline: String, val readings: List<Triple<String, String, String>>, val notes: List<String>, val next: String?)

/** The speaker layouts the PC offers, as the play method each needs. */
val PC_METHODS = listOf(PlayMethod.STEREO, PlayMethod.PCM_51, PlayMethod.PCM_71)

/** What the PC window shows. */
data class PcUi(
    val code: String = "",
    val pcName: String = "",
    val address: String? = null,
    val port: Int = -1,
    /** The paired phone, while it is connected. */
    val phone: String? = null,
    val outputs: List<String> = emptyList(),
    val output: String? = null,
    val apo: ApoStatus = ApoStatus(ApoState.UNKNOWN),
    val checking: Boolean = false,
    val chains: Map<Speaker, EqChain> = emptyMap(),
    val eqOn: Boolean = false,
    val eqFrom: String? = null,
    val eqWhen: Long? = null,
    val playing: Speaker? = null,
    /** Every speaker plays at once (the system test's last sweep). */
    val allPlaying: Boolean = false,
    /** What plays, in a word: "level noise", "sweep". */
    val what: String? = null,
    /** Seconds into the clip that is playing. */
    val playT: Double = 0.0,
    val layout: Layout = Layout.L20,
    val headline: String? = null,
    val rows: List<ShownRow> = emptyList(),
    val error: String? = null,
    /** How the PC plays: stereo, or 5.1 / 7.1 on an output with that many channels (HDMI to a receiver). */
    val method: PlayMethod = PlayMethod.STEREO,
    /** The most channels the output takes, when Windows says; null when it cannot be told. */
    val outputChannels: Int? = null,
    /** What the phone hears while it records, for the measuring display. */
    val live: PcLive? = null,
    /** The last result the phone sent. */
    val result: PcResult? = null,
) {
    val hasEq: Boolean get() = chains.isNotEmpty()
}

/**
 * The PC's end of a speaker test: waits for the phone on the Wi-Fi, plays the clip the phone asks for on the chosen
 * output (with AutoTune's EQ held off, so every reading is of the speakers as they are), shows what the phone found,
 * and keeps the EQ the phone sends in Equalizer APO. Only a phone that sent this PC's code is listened to.
 */
class PcEndpoint(
    private val scope: CoroutineScope,
    private val store: PcStore,
    private val apo: ApoControl,
    private val audio: AudioOut,
    pcName: String = Net.pcName(),
    address: String? = Net.addresses().firstOrNull()?.hostAddress,
    private val holdMs: Long = 400,
) {
    /** Stereo, or 5.1 / 7.1 when the output takes that many channels (chosen on the PC). */
    val method: PlayMethod get() = _ui.value.method

    private val _ui = MutableStateFlow(PcUi(pcName = pcName, address = address))
    val ui: StateFlow<PcUi> = _ui
    private val server = LinkServer(
        code = { _ui.value.code },
        welcome = ::welcome,
        onMessage = ::onMessage,
        // the welcome carries whether the EQ is on; a problem with it follows at once
        onPhone = { p -> _ui.update { it.copy(phone = p) }; if (p != null && problem() != null) tellEqState() },
        deniedText = "That is not the code on the PC.",
    )
    private var playJob: Job? = null
    private val writing = Mutex()

    /** Loads what was saved, opens the port and checks Equalizer APO. Returns the port. */
    fun start(preferredPort: Int = dev.autotune.link.LINK_PORT): Int {
        val s = store.load()
        val code = s.code ?: Pairing.newCode()
        val outputs = audio.outputs()
        val output = s.output?.takeIf { it in outputs } ?: outputs.firstOrNull()
        _ui.update {
            it.copy(code = code, outputs = outputs, output = output, chains = s.chains, eqOn = s.eqOn && s.chains.isNotEmpty(), eqFrom = s.from, eqWhen = s.whenMillis,
                method = s.method ?: PlayMethod.STEREO)
        }
        save()
        val port = server.start(preferredPort)
        _ui.update { it.copy(port = port) }
        recheck()
        return port
    }

    fun close() {
        playJob?.cancel()
        server.close()
    }

    /** Looks for Equalizer APO and the outputs again (after installing it, or plugging in speakers). */
    fun recheck(): Job = scope.launch(Dispatchers.IO) {
        _ui.update { it.copy(checking = true) }
        val outputs = audio.outputs().ifEmpty { _ui.value.outputs }
        val output = _ui.value.output?.takeIf { it in outputs } ?: outputs.firstOrNull()
        val st = apo.status(output)
        val ch = audio.maxChannels(output)
        _ui.update { it.copy(outputs = outputs, output = output, apo = st, checking = false, outputChannels = ch) }
        // what is saved goes back in, so the file always matches what the PC shows
        if (st.state == ApoState.READY || st.state == ApoState.UNKNOWN || st.state == ApoState.NOT_ON_OUTPUT) applyEq()
    }

    fun pickOutput(i: Int) {
        val o = _ui.value.outputs.getOrNull(i) ?: return
        if (o == _ui.value.output || _ui.value.playing != null) return
        _ui.update { it.copy(output = o) }
        save()
        recheck()
    }

    /** Opens Equalizer APO's Configurator, to tick this output; any problem shows under the output. */
    fun openConfigurator(): Job = scope.launch(Dispatchers.IO) {
        _ui.update { it.copy(error = null) }
        val err = apo.openConfigurator(_ui.value.apo)
        _ui.update { it.copy(error = err) }
    }

    /** Switches to an output Equalizer APO is already on ([name] as Windows names it). */
    fun useApoOutput(name: String) {
        val i = _ui.value.outputs.indexOfFirst { o -> ApoRegistry.match(o, listOf(RenderDevice("", name, true, true))) != null }
        if (i >= 0) pickOutput(i)
    }

    /** The one-time administrator step that lets AutoTune write into Equalizer APO's folder. */
    fun allow(): Job = scope.launch(Dispatchers.IO) {
        _ui.update { it.copy(checking = true, error = null) }
        val err = apo.setup(_ui.value.apo)
        _ui.update { it.copy(error = err) }
        recheck().join()
    }

    /** Plays stereo, 5.1 or 7.1 from now on; the phone is told at once, so its layouts follow. */
    fun setMethod(m: PlayMethod) {
        if (m == _ui.value.method || _ui.value.playing != null) return
        _ui.update { it.copy(method = m, layout = if (it.layout in m.layouts) it.layout else m.layouts.first()) }
        save()
        scope.launch(Dispatchers.IO) {
            applyEq()
            _ui.value.phone?.let { server.send(welcome(it)) }
        }
    }

    /** The EQ as edited on the PC: it replaces the phone's, is kept, and goes into Equalizer APO at once. */
    fun setChains(chains: Map<Speaker, EqChain>) {
        _ui.update { it.copy(chains = chains, eqOn = chains.isNotEmpty(), eqFrom = "this PC", eqWhen = System.currentTimeMillis()) }
        save()
        scope.launch(Dispatchers.IO) { applyEq(); tellEqState() }
    }

    fun setEqOn(on: Boolean) {
        if (on && !_ui.value.hasEq) return
        _ui.update { it.copy(eqOn = on) }
        save()
        scope.launch(Dispatchers.IO) { applyEq(); tellEqState() }
    }

    private fun welcome(phone: String): LinkMsg {
        val u = _ui.value
        return Msg.welcome(u.pcName, method.name, u.eqOn && problem() == null, u.output ?: "PC", Msg.KIND_PC)
    }

    private fun onMessage(m: LinkMsg) {
        when (m.type) {
            Msg.PLAY -> {
                val id = m["id"]?.toIntOrNull() ?: return
                val sp = Speaker.of(m["speaker"])
                if (sp == null) {
                    server.send(Msg.failed(id, "Unknown speaker"))
                    return
                }
                Layout.of(m["layout"])?.let { l -> _ui.update { it.copy(layout = l) } }
                if (ApoText.channelOf(sp, method) == null) {
                    server.send(Msg.failed(id, if (method == PlayMethod.STEREO) "This PC plays stereo: front left and front right only. Choose 5.1 or 7.1 on the PC for more." else "This PC has no ${sp.title.lowercase()} in ${method.title}."))
                    return
                }
                play(id, sp)
            }
            Msg.LIVE -> _ui.update {
                it.copy(live = PcLive(m["lv"]?.toFloatOrNull() ?: -90f, Msg.samples(m["ws"]), m["st"], m["ch"]?.toDoubleOrNull(), System.currentTimeMillis()))
            }
            Msg.RESULT -> _ui.update {
                it.copy(live = null, result = PcResult(m["headline"].orEmpty(), Msg.readings(m["readings"]), m["notes"]?.split('|')?.filter { n -> n.isNotBlank() }.orEmpty(), m["next"]))
            }
            Msg.SHOW -> _ui.update { it.copy(layout = Layout.of(m["layout"]) ?: it.layout, headline = m["headline"], rows = Msg.rows(m["rows"])) }
            Msg.EQ -> {
                val c = Msg.chains(m["chains"])
                _ui.update { it.copy(chains = c, eqOn = c.isNotEmpty(), eqFrom = it.phone, eqWhen = System.currentTimeMillis()) }
                save()
                scope.launch(Dispatchers.IO) { applyEq(); tellEqState() }
            }
            // the phone switched it off: the bands stay, so the PC's key can bring them back
            Msg.EQ_OFF -> setEqOn(false)
            Msg.NOISE -> Speaker.of(m["speaker"])?.takeIf { ApoText.channelOf(it, method) != null }?.let(::noise)
            Msg.QUIET -> playJob?.cancel()
            Msg.PASS -> {
                val id = m["id"]?.toIntOrNull() ?: return
                // no kind: a phone from 2.15, which plays the whole system test
                pass(id, Layout.of(m["layout"])?.takeIf { it in method.layouts } ?: Layout.L20, hold = m["hold"] == "1", PassKind.of(m["kind"]))
            }
        }
    }

    /** Loops [sp]'s level noise until the phone says quiet (two minutes at most, should the phone vanish). */
    private fun noise(sp: Speaker) {
        playJob?.cancel()
        playJob = scope.launch(Dispatchers.IO) {
            _ui.update { it.copy(playing = sp, allPlaying = false, what = "level noise", playT = 0.0) }
            try {
                if (_ui.value.eqOn) { writeEq(on = false); delay(holdMs) }
                kotlinx.coroutines.withTimeout(120_000) {
                    audio.play(_ui.value.output, Channels.interleave16(LevelNoise.planes(sp), method), method.slots.size, LevelNoise.FS, loop = true) {}
                }
            } catch (_: Exception) {
            } finally {
                withContext(NonCancellable) { if (_ui.value.eqOn) writeEq(on = true) }
                _ui.update { it.copy(playing = null, what = null) }
            }
        }
    }

    /** A test in one clip after one set of beeps: each speaker's sweep (distances), both together (EQ), or both (system). */
    private fun pass(id: Int, layout: Layout, hold: Boolean, kind: PassKind) {
        playJob?.cancel()
        playJob = scope.launch(Dispatchers.IO) {
            _ui.update { it.copy(playing = null, allPlaying = false, what = "starting", playT = 0.0, layout = layout) }
            val slots = PassClip.slots(layout, kind)
            var last = -1
            var err: String? = null
            try {
                if (hold && _ui.value.eqOn) { writeEq(on = false); delay(holdMs) }
                audio.play(_ui.value.output, Channels.interleave16(PassClip.planes(layout, kind = kind), method), method.slots.size, PassClip.FS) { t ->
                    val now = PassClip.nowPlaying(layout, t, kind)?.first
                    if (now != null && now != last) {
                        last = now
                        val who = slots[now]
                        server.send(if (who == null) Msg.playingAll(id) else Msg.playing(id, who))
                    }
                    val who = now?.let { slots[it] }
                    _ui.update { it.copy(playT = t, playing = who, allPlaying = now != null && who == null, what = if (now == null) it.what else "sweep") }
                }
            } catch (e: CancellationException) {
                err = "Stopped."
                throw e
            } catch (e: Exception) {
                err = "The PC could not play it (${e.message ?: e.javaClass.simpleName})."
            } finally {
                withContext(NonCancellable) { if (hold && _ui.value.eqOn) writeEq(on = true) }
                _ui.update { it.copy(playing = null, allPlaying = false, what = null) }
                server.send(if (err == null) Msg.done(id) else Msg.failed(id, err))
            }
        }
    }

    private fun play(id: Int, sp: Speaker) {
        playJob?.cancel()
        playJob = scope.launch(Dispatchers.IO) {
            _ui.update { it.copy(playing = sp, playT = 0.0) }
            server.send(Msg.playing(id, sp))
            var err: String? = null
            try {
                // a test reads the speakers as they are: AutoTune's EQ is held off while the clip plays
                if (_ui.value.eqOn) { writeEq(on = false); delay(holdMs) }
                audio.play(_ui.value.output, ChannelClip.interleaved16(sp, method), method.slots.size, ChannelClip.FS) { t ->
                    _ui.update { it.copy(playT = t) }
                }
            } catch (e: CancellationException) {
                err = "Stopped."
                throw e
            } catch (e: Exception) {
                err = "The PC could not play it (${e.message ?: e.javaClass.simpleName})."
            } finally {
                withContext(NonCancellable) { if (_ui.value.eqOn) writeEq(on = true) }
                _ui.update { it.copy(playing = null) }
                server.send(if (err == null) Msg.done(id) else Msg.failed(id, err))
            }
        }
    }

    /** Writes the EQ as it now stands (or off, while a clip plays). Returns what went wrong, or null. */
    private suspend fun writeEq(on: Boolean): String? = writing.withLock {
        val u = _ui.value
        val note = listOfNotNull(u.eqFrom?.let { "from $it" }, u.eqWhen?.let { stamp(it) }).joinToString(", ").ifEmpty { "no EQ yet" }
        val text = ApoText.config(if (on) u.chains else emptyMap(), method, u.apo.device?.guid, note)
        apo.write(u.apo, text)
    }

    private suspend fun applyEq(): String? {
        val err = writeEq(on = _ui.value.eqOn)
        _ui.update { it.copy(error = err) }
        return err
    }

    /** Why the EQ cannot reach the speakers, or null when it can (or might). */
    private fun problem(): String? = _ui.value.let { it.error ?: it.apo.problem(it.output) }

    /** Tells the phone whether the EQ reaches the speakers, and why not when it cannot. */
    private fun tellEqState() {
        val p = problem()
        server.send(Msg.eqState(_ui.value.eqOn && p == null, p))
    }

    private fun save() {
        val u = _ui.value
        store.save(PcStore.Saved(u.code, u.output, u.chains, u.eqOn, u.eqFrom, u.eqWhen, u.method))
    }

    companion object {
        fun stamp(ms: Long): String = SimpleDateFormat("d MMM, HH:mm", Locale.UK).format(Date(ms))
    }
}
