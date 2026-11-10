package dev.autotune

import dev.autotune.dsp.EqChain
import dev.autotune.dsp.EqFilter
import dev.autotune.dsp.FreqGrid
import dev.autotune.dsp.Layout
import dev.autotune.dsp.PlayMethod
import dev.autotune.dsp.Speaker
import dev.autotune.dsp.SpeakerReading
import dev.autotune.dsp.SpeakerResult
import dev.autotune.dsp.TheatreMove
import dev.autotune.dsp.TheatreReport
import dev.autotune.link.LinkMsg
import dev.autotune.link.Msg
import dev.autotune.link.Pairing
import dev.autotune.link.ShownRow
import dev.autotune.theatre.TheatreSession
import dev.autotune.theatre.TheatreStore
import dev.autotune.theatre.theatreKey
import dev.autotune.theatre.theatreNextSpeakers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TheatreLinkTest {
    @Test
    fun messagesSurviveTheLine() {
        val m = Msg.hello("0042", "Galaxy S23 Ultra = mine; ok")
        val back = LinkMsg.parse(m.encode())!!
        assertEquals("hello", back.type)
        assertEquals("0042", back["code"])
        assertEquals("Galaxy S23 Ultra = mine; ok", back["name"])
        assertFalse(m.encode().contains('\n'))
        assertNull(LinkMsg.parse(""))
        assertNull(LinkMsg.parse("=x"))
        assertEquals("bye", LinkMsg.parse("bye  junk")!!.type)
    }

    @Test
    fun rowsAndChainsRoundTrip() {
        val rows = listOf(ShownRow(Speaker.FL, "−1.0 dB", "in time", true), ShownRow(Speaker.SUB, "—", "—", false))
        val back = Msg.rows(LinkMsg.parse(Msg.show("L51", "Levels differ", rows).encode())!!["rows"])
        assertEquals(listOf(Speaker.FL, Speaker.SUB), back.map { it.speaker })
        assertEquals("−1.0 dB", back[0].trim)
        assertFalse(back[1].heard)

        val chains = mapOf(Speaker.FL to EqChain(listOf(EqFilter(63.0, -4.5, 2.0), EqFilter(2500.0, 2.0, 1.4)), -2.0))
        val c = Msg.chains(LinkMsg.parse(Msg.eq(chains).encode())!!["chains"])
        assertEquals(2, c.getValue(Speaker.FL).filters.size)
        assertEquals(-4.5, c.getValue(Speaker.FL).filters[0].gainDb, 1e-9)
        assertEquals(-2.0, c.getValue(Speaker.FL).preampDb, 1e-9)
        // the TV holds what it receives to safe limits
        val wild = Msg.chains("FL/5/100,40,0.01;99999,1,1")
        assertEquals(1, wild.getValue(Speaker.FL).filters.size)
        assertEquals(6.0, wild.getValue(Speaker.FL).filters[0].gainDb, 1e-9)
        assertEquals(0.0, wild.getValue(Speaker.FL).preampDb, 1e-9)
    }

    @Test
    fun aPcSaysWhatItIs() {
        val w = LinkMsg.parse(Msg.welcome("DESK", "STEREO", false, "Speakers", Msg.KIND_PC).encode())!!
        assertEquals("PC", dev.autotune.theatre.TheatreUi(kind = w["kind"] ?: Msg.KIND_TV).device)
        // a TV from before the kind was sent
        val old = LinkMsg.parse("welcome name=TV method=DOLBY_51 eq=0 out=HDMI v=1")!!
        assertEquals("TV", dev.autotune.theatre.TheatreUi(kind = old["kind"] ?: Msg.KIND_TV).device)
    }

    @Test
    fun pairingCodes() {
        val code = Pairing.newCode(java.util.Random(1))
        assertEquals(4, code.length)
        assertTrue(Pairing.matches(" $code ", code))
        assertFalse(Pairing.matches(null, code))
        assertFalse(Pairing.matches("1", "0001"))
    }

    @Test
    fun sessionsSaveAndLoad() {
        val grid = FreqGrid()
        val mag = DoubleArray(grid.size) { -3.0 + it * 0.01 }
        val s = TheatreSession(
            123L, Layout.L51, PlayMethod.DOLBY_51, "Living room TV",
            listOf(SpeakerReading(Speaker.FL, mag, 0.012, 61.5), SpeakerReading(Speaker.SL, null, null, 12.0, "Too quiet; really")),
        )
        val back = TheatreStore.decode(TheatreStore.encode(s))!!
        assertEquals(123L, back.whenMillis)
        assertEquals(Layout.L51, back.layout)
        assertEquals("Living room TV", back.tv)
        assertArrayEquals(mag, back.readings.first { it.speaker == Speaker.FL }.magDb, 0.006)
        assertEquals(0.012, back.readings.first { it.speaker == Speaker.FL }.deltaMs!!, 1e-9)
        assertEquals("Too quiet; really", back.readings.first { it.speaker == Speaker.SL }.fatal)
        assertFalse(back.complete)
        assertNull(TheatreStore.decode("something else"))
        // unknown lines from a later version are skipped
        assertEquals(Layout.L51, TheatreStore.decode(TheatreStore.encode(s) + "future=1\n")!!.layout)
    }

    @Test
    fun theKeyFollowsTheMove() {
        fun report(move: TheatreMove, about: Speaker? = null) = TheatreReport(Layout.L51, emptyList<SpeakerResult>(), emptyList(), "", move, about)
        assertEquals("Test surround left again", theatreKey(report(TheatreMove.CHECK_SPEAKER, Speaker.SL)).first)
        assertEquals("after setting the levels", theatreKey(report(TheatreMove.SET_LEVELS)).second)
        assertEquals("Use the EQ on the TV", theatreKey(report(TheatreMove.USE_EQ)).first)
        assertEquals("Use the EQ on the PC", theatreKey(report(TheatreMove.USE_EQ), "PC").first)
        assertEquals(listOf(Speaker.SL), theatreNextSpeakers(report(TheatreMove.CHECK_SPEAKER, Speaker.SL), Layout.L51))
        assertEquals(Layout.L51.speakers, theatreNextSpeakers(report(TheatreMove.SET_LEVELS), Layout.L51))
        // a different layout tests everything
        assertEquals(Layout.L31.speakers, theatreNextSpeakers(report(TheatreMove.CHECK_SPEAKER, Speaker.SL), Layout.L31))
    }
}
