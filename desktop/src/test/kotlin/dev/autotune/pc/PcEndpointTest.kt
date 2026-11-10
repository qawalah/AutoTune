package dev.autotune.pc

import dev.autotune.dsp.EqChain
import dev.autotune.dsp.EqFilter
import dev.autotune.dsp.Speaker
import dev.autotune.link.LinkMsg
import dev.autotune.link.Msg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PcEndpointTest {
    private class FakeApo(var state: ApoState = ApoState.READY) : ApoControl {
        val writes: MutableList<String> = Collections.synchronizedList(ArrayList())
        var setups = 0
        override fun status(output: String?) = ApoStatus(state, RenderDevice("{dev-guid}", output ?: "?", true, state == ApoState.READY), "C:\\cfg")
        override fun write(status: ApoStatus, text: String): String? { writes += text; return null }
        override fun setup(status: ApoStatus): String? { setups++; state = ApoState.READY; return null }
    }

    private class FakeAudio : AudioOut {
        val played: MutableList<Pair<String?, Int>> = Collections.synchronizedList(ArrayList())
        var eqDuringPlay: String? = null
        var apo: FakeApo? = null
        override fun outputs() = listOf("Speakers (Realtek(R) Audio)", "LG TV SSCR2 (NVIDIA High Definition Audio)")
        override suspend fun play(output: String?, pcm: ShortArray, channels: Int, fs: Int, onTime: (Double) -> Unit) {
            eqDuringPlay = apo?.writes?.lastOrNull()
            played += output to channels
            onTime(1.0)
        }
    }

    private val dir = kotlin.io.path.createTempDirectory("pc").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val apo = FakeApo()
    private val audio = FakeAudio().also { it.apo = apo }
    private val store = PcStore(File(dir, "pc.txt"))
    private val pc = PcEndpoint(scope, store, apo, audio, pcName = "DESK", address = "192.168.1.20", holdMs = 1)
    private val port = pc.start(0)

    @AfterTest
    fun done() { pc.close(); scope.cancel(); dir.deleteRecursively() }

    private class Phone(port: Int) {
        val s = Socket("127.0.0.1", port).apply { soTimeout = 4000 }
        val w = PrintWriter(s.getOutputStream().bufferedWriter(), true)
        val r = BufferedReader(InputStreamReader(s.getInputStream()))
        fun send(m: LinkMsg) = w.println(m.encode())
        fun read(): LinkMsg = LinkMsg.parse(r.readLine()!!)!!
        fun readUntil(type: String): LinkMsg { while (true) { val m = read(); if (m.type == type) return m } }
    }

    private fun pair(): Phone {
        val p = Phone(port)
        p.send(Msg.hello(pc.ui.value.code, "Galaxy S23 Ultra"))
        return p
    }

    private fun waitFor(what: () -> Boolean) {
        val until = System.currentTimeMillis() + 4000
        while (!what() && System.currentTimeMillis() < until) Thread.sleep(10)
        assertTrue(what())
    }

    @Test
    fun welcomesAsAStereoPcAndKeepsItsCode() {
        val p = pair()
        val w = p.read()
        assertEquals(Msg.WELCOME, w.type)
        assertEquals(Msg.KIND_PC, w["kind"])
        assertEquals("DESK", w["name"])
        assertEquals("STEREO", w["method"])
        assertEquals("Speakers (Realtek(R) Audio)", w["out"])
        waitFor { pc.ui.value.phone == "Galaxy S23 Ultra" }
        assertEquals(pc.ui.value.code, store.load().code)
    }

    @Test
    fun theEqIsWrittenPerChannelAndHeldOffWhileAClipPlays() {
        val p = pair()
        p.read()
        val chains = mapOf(
            Speaker.FL to EqChain(listOf(EqFilter(80.0, -5.0, 3.0)), -2.0),
            Speaker.FR to EqChain(listOf(EqFilter(3000.0, 1.5, 1.0)), -2.0),
        )
        p.send(Msg.eq(chains))
        val st = p.readUntil(Msg.EQ_STATE)
        assertEquals("1", st["on"])
        val written = apo.writes.last()
        assertTrue(written.contains("Device: {dev-guid}"))
        assertTrue(written.contains("Channel: L\nPreamp: -2.0 dB\nFilter 1: ON PK Fc 80 Hz Gain -5.0 dB Q 3.00"))
        assertTrue(written.contains("Channel: R"))
        assertEquals("Galaxy S23 Ultra", pc.ui.value.eqFrom)

        p.send(Msg.play(7, Speaker.FR, "L20"))
        assertEquals(Speaker.FR.code, p.readUntil(Msg.PLAYING)["speaker"])
        assertEquals("7", p.readUntil(Msg.DONE)["id"])
        assertEquals<List<Pair<String?, Int>>>(listOf("Speakers (Realtek(R) Audio)" to 2), audio.played.toList())
        // the clip played with AutoTune's EQ off, and the EQ came back after
        assertFalse(audio.eqDuringPlay!!.contains("Filter"))
        waitFor { apo.writes.last().contains("Filter 1") }

        // off from the phone keeps the bands for the PC's own key
        p.send(Msg.eqOff())
        assertEquals("0", p.readUntil(Msg.EQ_STATE)["on"])
        assertFalse(apo.writes.last().contains("Filter"))
        assertTrue(pc.ui.value.hasEq)
        pc.setEqOn(true)
        waitFor { apo.writes.last().contains("Filter 1") }
        assertTrue(store.load().eqOn)
        assertEquals(2, store.load().chains.size)
    }

    @Test
    fun aSpeakerStereoCannotReachFailsAtOnce() {
        val p = pair()
        p.read()
        p.send(Msg.play(3, Speaker.C, "L51"))
        val f = p.readUntil(Msg.FAILED)
        assertEquals("3", f["id"])
        assertTrue(f["reason"]!!.contains("stereo"))
        assertTrue(audio.played.isEmpty())
    }

    @Test
    fun withoutEqualizerApoThePhoneIsToldWhy() {
        apo.state = ApoState.MISSING
        runBlocking { pc.recheck().join() }
        val p = pair()
        assertEquals("0", p.readUntil(Msg.WELCOME)["eq"])
        val st = p.readUntil(Msg.EQ_STATE)
        assertEquals("0", st["on"])
        assertNotNull(st["error"])
        assertTrue(st["error"]!!.contains("Equalizer APO"))
    }

    @Test
    fun allowRunsTheSetupAndChecksAgain() {
        apo.state = ApoState.NEEDS_SETUP
        runBlocking { pc.recheck().join() }
        assertEquals(ApoState.NEEDS_SETUP, pc.ui.value.apo.state)
        runBlocking { pc.allow().join() }
        waitFor { pc.ui.value.apo.state == ApoState.READY }
        assertEquals(1, apo.setups)
    }

    @Test
    fun theOutputIsRemembered() {
        pc.pickOutput(1)
        waitFor { store.load().output == "LG TV SSCR2 (NVIDIA High Definition Audio)" }
        val again = PcEndpoint(scope, store, apo, audio, pcName = "DESK", address = null)
        again.start(0)
        assertEquals("LG TV SSCR2 (NVIDIA High Definition Audio)", again.ui.value.output)
        assertEquals(pc.ui.value.code, again.ui.value.code)
        again.close()
    }
}
