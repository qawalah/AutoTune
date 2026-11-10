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
import dev.autotune.theatre.RunKind
import dev.autotune.theatre.ThreeStep
import dev.autotune.theatre.TheatreRun
import dev.autotune.theatre.TheatreSession
import dev.autotune.theatre.TheatreTest
import dev.autotune.theatre.TheatreTests
import dev.autotune.theatre.threeKey
import dev.autotune.theatre.threeNext
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
    fun theThreeTestsSuggestLevelsThenDistancesThenTheEq() {
        val grid = FreqGrid()
        val flat = DoubleArray(grid.size)
        fun ui(vararg runs: TheatreRun, eq: dev.autotune.dsp.EqReport? = null, distOk: Boolean = true, eqOn: Boolean = false) = dev.autotune.theatre.TheatreUi(
            threeTests = true, test = TheatreTest("t", "Living room", 1L, runs.toList()), eq = eq, tvEqOn = eqOn,
            distances = dev.autotune.dsp.DistanceReport(emptyList(), emptyList(), "", distOk),
        )
        val levels = TheatreRun(RunKind.LEVELS, 10L, Layout.L51, "TV", levels = mapOf(Speaker.C to 0.5))
        val distances = TheatreRun(RunKind.DISTANCES, 20L, Layout.L51, "TV")
        val eqRun = TheatreRun(RunKind.EQ, 30L, Layout.L51, "TV", system = SpeakerReading(Speaker.FL, flat, 0.0, 50.0))
        assertEquals(ThreeStep.LEVELS, threeNext(dev.autotune.theatre.TheatreUi(threeTests = true)))
        assertEquals("Set the levels", threeKey(dev.autotune.theatre.TheatreUi(threeTests = true)).first)
        assertEquals(ThreeStep.DISTANCES, threeNext(ui(levels)))
        assertEquals(ThreeStep.EQ, threeNext(ui(levels, distances)))
        assertEquals("Test the EQ", threeKey(ui(levels, distances)).first)
        // set up once: from then on the EQ, never the distances again
        assertEquals("Test the EQ again", threeKey(ui(levels, distances, eqRun)).first)
        // distances found off: test them again, until an EQ test comes after
        assertEquals(ThreeStep.DISTANCES, threeNext(ui(levels, distances, distOk = false)))
        assertEquals(ThreeStep.EQ, threeNext(ui(levels, distances, eqRun, distOk = false)))
        // levels left off by more than the meter's ok: set them again
        val off = TheatreRun(RunKind.LEVELS, 40L, Layout.L51, "TV", levels = mapOf(Speaker.SUB to 3.0))
        assertEquals("Set the levels again", threeKey(ui(levels, distances, eqRun, off)).first)
        // an EQ worth using is offered until it is on
        val worth = dev.autotune.dsp.EqReport(flat, 3.0, null, listOf(EqFilter(60.0, -4.0, 2.0)), 1, "", true)
        assertEquals("Use the EQ on the TV", threeKey(ui(levels, distances, eqRun, eq = worth)).first)
        assertEquals(ThreeStep.EQ, threeNext(ui(levels, distances, eqRun, eq = worth, eqOn = true)))
    }

    @Test
    fun homeTheatreTestsKeepEveryRun() {
        val grid = FreqGrid()
        val mag = DoubleArray(grid.size) { -1.0 + it * 0.02 }
        val t = TheatreTest("ht1", "Living room 5.1 & sub", 3L, emptyList())
            .with(TheatreRun(RunKind.LEVELS, 4L, Layout.L51, "Box", levels = mapOf(Speaker.C to -1.5, Speaker.SUB to 2.0)))
            .with(TheatreRun(RunKind.DISTANCES, 5L, Layout.L51, "Box", readings = listOf(SpeakerReading(Speaker.FL, mag, 0.012, 40.0), SpeakerReading(Speaker.SUB, null, null, null, "not heard"))))
            .with(TheatreRun(RunKind.EQ, 6L, Layout.L51, "Box", pretend = true, real = "tablet · stereo", system = SpeakerReading(Speaker.FL, mag, 0.0, 55.0), seats = 3, eqInUse = "on the TV"))
            .with(TheatreRun(RunKind.EQ, 7L, Layout.L51, "Box", system = SpeakerReading(Speaker.FL, mag, 0.0, 55.0)).withNote("after moving the sub; a=b;c"))
        val back = TheatreTests.decode("ht1", TheatreTests.encode(t), grid)!!
        assertEquals("Living room 5.1 & sub", back.name)
        assertEquals(4, back.runs.size)
        assertEquals(mapOf(Speaker.C to -1.5, Speaker.SUB to 2.0), back.runs[0].levels)
        assertEquals(0.012, back.runs[1].readings[0].deltaMs!!, 1e-6)
        assertEquals("not heard", back.runs[1].readings[1].fatal)
        assertArrayEquals(mag, back.runs[2].system!!.magDb, 0.006)
        assertEquals(3, back.runs[2].seats)
        assertEquals("on the TV", back.runs[2].eqInUse)
        assertTrue(back.runs[2].pretend)
        assertEquals("after moving the sub; a=b;c", back.runs[3].note)
        // the EQ is fitted from the first real EQ run, never the pretend one
        assertEquals(7L, back.eqStock!!.whenMillis)
        assertEquals(7L, back.last(RunKind.EQ)!!.whenMillis)
        // a newer version's head is still read; junk is not
        assertEquals(4, TheatreTests.decode("x", TheatreTests.encode(t).replaceFirst("test 1", "test 2"), grid)!!.runs.size)
        assertNull(TheatreTests.decode("x", "hello"))
    }

    @Test
    fun theOldStockAndLatestBecomeTheFirstTest() {
        val base = kotlin.io.path.createTempDirectory("ht").toFile()
        try {
            val grid = FreqGrid()
            val mag = DoubleArray(grid.size)
            val old = java.io.File(base, "theatre")
            TheatreStore.save(old, "stock", TheatreSession(5L, Layout.L51, PlayMethod.DOLBY_51, "TV", listOf(SpeakerReading(Speaker.C, mag, 0.0, 40.0))))
            TheatreStore.save(old, "latest", TheatreSession(9L, Layout.L51, PlayMethod.DOLBY_51, "TV", listOf(SpeakerReading(Speaker.C, mag, 0.0, 40.0)), system = SpeakerReading(Speaker.FL, mag, 0.0, 50.0)))
            val t = TheatreTests.migrate(base)!!
            assertEquals(listOf(RunKind.SPEAKERS, RunKind.DISTANCES, RunKind.EQ), t.runs.map { it.kind })
            assertEquals(1, TheatreTests.list(base).size)
            // once only
            assertNull(TheatreTests.migrate(base))
            TheatreTests.delete(base, t.id)
            assertTrue(TheatreTests.list(base).isEmpty())
        } finally { base.deleteRecursively() }
    }

    @Test
    fun systemSessionsKeepTheWholeSystemAndThePretend() {
        val grid = FreqGrid()
        val mag = DoubleArray(grid.size) { -1.0 + it * 0.02 }
        val s = TheatreSession(
            7L, Layout.L51, PlayMethod.PCM_71, "Tab S9", listOf(SpeakerReading(Speaker.FL, mag, 0.01, 50.0)),
            system = SpeakerReading(Speaker.FL, mag, 0.0, 55.0), pretend = true, real = "tablet speakers · stereo only",
        )
        val back = TheatreStore.decode(TheatreStore.encode(s))!!
        assertArrayEquals(mag, back.system!!.magDb, 0.006)
        assertTrue(back.pretend)
        assertEquals("tablet speakers · stereo only", back.real)
        assertEquals(1, back.readings.size)
        // a file from 2.13 or 2.14 (version 1) still loads, with no system reading
        val old = TheatreStore.encode(TheatreSession(5L, Layout.L31, PlayMethod.DOLBY_51, "TV", listOf(SpeakerReading(Speaker.C, mag, 0.0, 40.0))))
            .replaceFirst("autotune-theatre 2", "autotune-theatre 1")
        val v1 = TheatreStore.decode(old)!!
        assertNull(v1.system)
        assertFalse(v1.pretend)
    }

    @Test
    fun theTvEqUsesTheMixsTwoChannels() {
        val one = EqChain(listOf(EqFilter(80.0, -4.0, 2.0)), -2.0)
        // one EQ for every speaker: both channels get it
        val (l, r) = dev.autotune.tv.TvEq.stereoChains(Layout.L51.speakers.associateWith { one })
        assertEquals(one, l); assertEquals(one, r)
        // per speaker: left and right keep their own
        val other = EqChain(listOf(EqFilter(2000.0, 1.0, 1.0)), -2.0)
        assertEquals(one to other, dev.autotune.tv.TvEq.stereoChains(mapOf(Speaker.FL to one, Speaker.FR to other, Speaker.C to other)))
        // only a centre EQ: it plays on both
        assertEquals(other to other, dev.autotune.tv.TvEq.stereoChains(mapOf(Speaker.C to other)))
        assertEquals(null to null, dev.autotune.tv.TvEq.stereoChains(emptyMap()))
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
