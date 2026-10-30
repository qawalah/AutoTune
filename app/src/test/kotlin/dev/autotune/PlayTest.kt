package dev.autotune

import dev.autotune.dsp.EqFilter
import dev.autotune.dsp.EqFitter
import dev.autotune.dsp.FitConfig
import dev.autotune.dsp.FreqGrid
import dev.autotune.dsp.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What plays (flavour and preamp) and how the speakers' response to the EQ is read. */
class PlayTest {
    private val g = FreqGrid()
    private val boost = listOf(EqFilter(60.0, 6.0, 0.7))

    @Test
    fun preampModes() {
        val safe = playChain(boost, Flavour.NONE, PreampMode.SAFE, 0.0, g)
        assertTrue("safe ${safe.preampDb}", safe.preampDb < -6.0 && safe.preampDb > -6.6)
        val half = playChain(boost, Flavour.NONE, PreampMode.BALANCED, 0.0, g)
        assertEquals(safe.preampDb / 2, half.preampDb, 0.3)
        assertEquals(0.0, playChain(boost, Flavour.NONE, PreampMode.OFF, 0.0, g).preampDb, 0.0)
        assertEquals(-4.5, playChain(boost, Flavour.NONE, PreampMode.MANUAL, -4.5, g).preampDb, 0.0)
        assertEquals(-15.0, playChain(boost, Flavour.NONE, PreampMode.MANUAL, -40.0, g).preampDb, 0.0)
        // cuts only: nothing to protect
        assertEquals(0.0, playChain(listOf(EqFilter(200.0, -5.0, 1.0)), Flavour.NONE, PreampMode.SAFE, 0.0, g).preampDb, 0.0)
    }

    @Test
    fun aFlavourSitsOnTopOfTheTune() {
        val c = playChain(boost, Flavour.SPEECH, PreampMode.SAFE, 0.0, g)
        assertEquals(boost.size + Flavour.SPEECH.filters.size, c.filters.size)
        // speech lifts the presence region relative to the tune alone
        val tune = playChain(boost, Flavour.NONE, PreampMode.OFF, 0.0, g)
        val sp = playChain(boost, Flavour.SPEECH, PreampMode.OFF, 0.0, g)
        assertTrue(sp.responseDb(3000.0) - tune.responseDb(3000.0) > 2.5)
        assertTrue(Flavour.entries.all { f -> f.filters.all { it.gainDb in -6.0..6.0 } })
    }

    @Test
    fun boostsTheSpeakersIgnoreAreFoundAndNotAskedForAgain() {
        // stock: falls off below 50 Hz; the EQ boosted 40 Hz by 6 dB and cut 1 kHz by 4 dB
        val stock = DoubleArray(g.size) { i -> if (g.freqs[i] < 50) -10.0 else 0.0 }
        val applied = listOf(EqFilter(35.0, 6.0, 1.0), EqFilter(1000.0, -4.0, 1.5))
        // the run: the cut arrived, the deep boost did not (the speakers cannot play it)
        val run = DoubleArray(g.size) { i -> stock[i] + (if (g.freqs[i] > 100) applied.sumOf { it.magnitudeDb(g.freqs[i]) } else 0.0) }
        val f = eqFollow(stock, run, applied, g)!!
        val deaf = f.deafBelowHz!!
        assertTrue("deaf below $deaf", deaf in 40.0..100.0)
        assertTrue(f.summary.contains("do not play that low"))
        // the fitter then leaves the deep dip alone
        val target = Target.FLAT
        val withFloor = EqFitter.fit(stock, g, target, FitConfig(boostMinHz = deaf)).filters
        assertTrue(withFloor.none { it.gainDb > 0 && it.freqHz < deaf })
        assertTrue(EqFitter.fit(stock, g, target).filters.any { it.gainDb > 0 && it.freqHz < 50 })
        // a run that followed fully has no floor
        val good = DoubleArray(g.size) { i -> stock[i] + applied.sumOf { it.magnitudeDb(g.freqs[i]) } }
        assertNull(eqFollow(stock, good, applied, g)!!.deafBelowHz)
    }

    @Test
    fun furtherTestsAreSuggested() {
        val weak = SavedRun(1L, 6.0, DoubleArray(4), emptyList(), "Stock (no EQ)", warnings = listOf("Signal a bit quiet (signal-to-noise 28 dB)."), complete = true)
        val run = SavedRun(1L, 5.0, DoubleArray(4), emptyList(), "Run 1", applied = boost, complete = true)
        val follow = EqFollow(0.3, emptyList(), null, "")
        val s = suggestChecks(listOf(weak, run), isCar = true, spots = 1, follow = follow, predictedLatestDb = 2.5)
        val acts = s.map { it.action }.toSet()
        assertEquals(setOf(CheckAction.REMEASURE_STOCK, CheckAction.MORE_SPOTS, CheckAction.EQ_CHECK, CheckAction.REPEAT), acts)
        assertTrue(suggestChecks(listOf(run.let { SavedRun(1L, 3.0, DoubleArray(4), emptyList(), "Stock (no EQ)", complete = true) }), false, 1, null, null).isEmpty())
    }

    @Test
    fun theBoostFloorIsStoredWithTheTest() {
        val t = SavedTest("x", "F", 1L, 0, 0, 80, listOf(SavedRun(1L, 6.0, DoubleArray(4), emptyList(), "Stock (no EQ)", complete = true)), boostFloorHz = 45.0)
        assertEquals(45.0, TestStore.decode(TestStore.encode(t)).boostFloorHz, 0.0)
    }
}
