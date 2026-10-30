package dev.autotune

import dev.autotune.dsp.Region
import dev.autotune.dsp.ResonanceDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the app says and recommends after a measurement, and where Back goes. */
class FlowTest {
    private fun r(label: String, rms: Double, converged: Boolean = false) =
        SavedRun(1L, rms, DoubleArray(4), emptyList(), label, applied = if (label.startsWith("Stock")) emptyList() else listOf(dev.autotune.dsp.EqFilter(100.0, -3.0, 2.0)), complete = true, converged = converged)

    @Test
    fun stockAnalysisNamesWhatIsOffAndTrustsAGoodReading() {
        val regions = listOf(Region("Bass", 30.0, 120.0, 5.2), Region("Mids", 500.0, 2000.0, -1.0), Region("Presence", 2000.0, 6000.0, 7.0))
        val res = listOf(ResonanceDetector.rebuild(63.0, 8.0, 5.0, 450.0, false), ResonanceDetector.rebuild(3100.0, 4.5, 6.0, null, false))
        val a = analyzeStock(6.8, 2.9, 7, regions, res, emptyList(), null)
        assertFalse(a.suspicious)
        assertEquals("Harsh presence", a.findings.first().title) // the 7 dB one is the biggest problem
        assertTrue(a.findings.any { it.title == "Boomy bass" && it.severity == 1 })
        assertTrue(a.findings.any { it.title.startsWith("1 strong peak") })
        assertTrue(a.findings.any { it.title == "Ringing at 63 Hz" })
        assertTrue(a.verdict.startsWith("Far from the target"))
    }

    @Test
    fun aWeakOrAbsurdStockReadingIsCalledOut() {
        val weak = analyzeStock(7.0, 3.0, 6, emptyList(), emptyList(), listOf("Every frequency was barely heard between about 40 and 90 Hz (signal-to-noise 9 dB)."), null)
        assertTrue(weak.suspicious)
        assertEquals(2, weak.findings.first().severity)
        assertTrue(analyzeStock(14.0, 5.0, 8, emptyList(), emptyList(), emptyList(), null).suspicious)
        val fine = analyzeStock(2.0, 1.4, 3, listOf(Region("Bass", 30.0, 120.0, 1.0)), emptyList(), emptyList(), null)
        assertTrue(fine.findings.single().severity == 0)
    }

    @Test
    fun oneRecommendationAfterEachRun() {
        // first round helped a lot: go round again
        assertEquals(NextMove.REFINE, recommendNext(listOf(r("Stock", 6.0), r("Run 1", 3.2)), false).move)
        // the EQ never arrived
        assertEquals(NextMove.CHECK_EQ, recommendNext(listOf(r("Stock", 6.0), r("Run 1", 5.9)), true).move)
        // the latest got worse than an earlier run: keep that one
        val worse = recommendNext(listOf(r("Stock", 6.0), r("Run 1", 2.4), r("Run 2", 3.1)), false)
        assertEquals(NextMove.KEEP_BEST, worse.move)
        assertEquals(1, worse.bestIdx)
        // close to the target, or barely moving any more: keep it
        assertEquals(NextMove.KEEP_LATEST, recommendNext(listOf(r("Stock", 6.0), r("Run 1", 1.4)), false).move)
        assertEquals(NextMove.KEEP_LATEST, recommendNext(listOf(r("Stock", 6.0), r("Run 1", 2.6), r("Run 2", 2.5)), false).move)
        // converged on an earlier run
        assertEquals(NextMove.KEEP_BEST, recommendNext(listOf(r("Stock", 6.0), r("Run 1", 2.4), r("Run 2", 2.6, converged = true)), false).move)
    }

    @Test
    fun backGoesToThePreviousScreen() {
        assertEquals(TuneStep.FRESH, previousStep(TuneStep.SETUP, 0))
        assertEquals(TuneStep.SETUP, previousStep(TuneStep.STOCK, 0))
        assertEquals(TuneStep.STOCK_RESULT, previousStep(TuneStep.STOCK, 1))
        assertEquals(TuneStep.STOCK_RESULT, previousStep(TuneStep.APPLY, 1))
        assertEquals(TuneStep.RESULT, previousStep(TuneStep.APPLY, 3))
        assertEquals(TuneStep.APPLY, previousStep(TuneStep.VERIFY, 2))
        assertEquals(TuneStep.HOME, previousStep(TuneStep.RESULT, 2))
        // every step can be left
        TuneStep.entries.forEach { st -> var x = st; repeat(10) { x = previousStep(x, 2) }; assertEquals(TuneStep.HOME, x) }
    }

    @Test
    fun continueComesBackToWhereYouLeft() {
        assertEquals(TuneStep.APPLY, resumeAt(TuneStep.APPLY, 1))
        assertEquals(TuneStep.VERIFY, resumeAt(TuneStep.VERIFY, 3))
        // a remembered step that no longer fits the test falls back to its latest result
        assertEquals(TuneStep.STOCK_RESULT, resumeAt(TuneStep.RESULT, 1))
        assertEquals(TuneStep.STOCK, resumeAt(TuneStep.APPLY, 0))
        assertEquals(TuneStep.RESULT, resumeAt(null, 4))
        assertEquals(TuneStep.STOCK_RESULT, resumeAt(TuneStep.DONE, 1))
        // the progress bar has a name for every step
        TuneStep.entries.forEach { assertTrue(stepIndex(it) in STEP_NAMES.indices) }
    }

    @Test
    fun tasteIsStoredWithTheTest() {
        val t = SavedTest("x", "F-150", 1L, 3, 0, 80, listOf(r("Stock (no EQ)", 6.0)), bassDb = 4.0, trebleDb = -2.0)
        val back = TestStore.decode(TestStore.encode(t))
        assertEquals(4.0, back.bassDb, 0.0)
        assertEquals(-2.0, back.trebleDb, 0.0)
        // older files have no taste line
        assertEquals(0.0, TestStore.decode(TestStore.encode(t.copy(bassDb = 0.0, trebleDb = 0.0))).bassDb, 0.0)
    }

    @Test
    fun theGraphRangeGrowsToShowEveryCurve() {
        val f = doubleArrayOf(30.0, 100.0, 1000.0, 10000.0)
        assertEquals(-15.0 to 15.0, dev.autotune.ui.chartRange(listOf(doubleArrayOf(3.0, -4.0, 0.0, 2.0)), f))
        // a cabin 27 dB over the target in the bass is no longer cut off at +15
        val (lo, hi) = dev.autotune.ui.chartRange(listOf(doubleArrayOf(27.0, 20.0, 0.0, -18.0)), f)
        assertEquals(30.0, hi, 0.0)
        assertEquals(-20.0, lo, 0.0)
    }

    @Test
    fun eqCheckTellsWhetherTheEqArrived() {
        val g = dev.autotune.dsp.FreqGrid()
        val eq = listOf(dev.autotune.dsp.EqFilter(80.0, -8.0, 1.5), dev.autotune.dsp.EqFilter(3000.0, -5.0, 2.0))
        val expected = DoubleArray(g.size) { i -> eq.sumOf { it.magnitudeDb(g.freqs[i]) } }
        val off = DoubleArray(g.size) { i -> 4 * Math.sin(i / 17.0) }
        val rnd = java.util.Random(1)
        fun with(share: Double, level: Double) = DoubleArray(g.size) { i -> off[i] + share * expected[i] + level + rnd.nextGaussian() * 0.3 }
        val full = judgeEqCheck(g.freqs, off, with(1.0, -3.0), expected)
        assertEquals(0, full.severity)
        assertTrue("effect ${full.effect}", kotlin.math.abs(full.effect - 1.0) < 0.1)
        assertEquals(1, judgeEqCheck(g.freqs, off, with(0.5, 0.0), expected).severity)
        // Android Auto case: the EQ never reached the car, only the level moved
        assertEquals(2, judgeEqCheck(g.freqs, off, with(0.0, 2.0), expected).severity)
        assertEquals(3, judgeEqCheck(g.freqs, off, off, DoubleArray(g.size)).severity)
    }
}
