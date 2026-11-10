package dev.autotune.pc

import dev.autotune.dsp.ChannelClip
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
    /** Seconds into the clip that is playing. */
    val playT: Double = 0.0,
    val layout: Layout = Layout.L20,
    val headline: String? = null,
    val rows: List<ShownRow> = emptyList(),
    val error: String? = null,
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
    /** The PC plays two channels for now; multichannel (5.1, 7.1 over HDMI) needs WASAPI and comes later. */
    val method = PlayMethod.STEREO

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
        _ui.update { it.copy(code = code, outputs = outputs, output = output, chains = s.chains, eqOn = s.eqOn && s.chains.isNotEmpty(), eqFrom = s.from, eqWhen = s.whenMillis) }
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
        _ui.update { it.copy(outputs = outputs, output = output, apo = st, checking = false) }
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

    /** The one-time administrator step that lets AutoTune write into Equalizer APO's folder. */
    fun allow(): Job = scope.launch(Dispatchers.IO) {
        _ui.update { it.copy(checking = true, error = null) }
        val err = apo.setup(_ui.value.apo)
        _ui.update { it.copy(error = err) }
        recheck().join()
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
                    server.send(Msg.failed(id, "This PC plays stereo: front left and front right only."))
                    return
                }
                play(id, sp)
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
        store.save(PcStore.Saved(u.code, u.output, u.chains, u.eqOn, u.eqFrom, u.eqWhen))
    }

    companion object {
        fun stamp(ms: Long): String = SimpleDateFormat("d MMM, HH:mm", Locale.UK).format(Date(ms))
    }
}
