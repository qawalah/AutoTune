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
        assertEquals(TuneStep.HOME, previousStep(TuneStep.SETUP, 0))
        assertEquals(TuneStep.SETUP, previousStep(TuneStep.STOCK, 0))
        assertEquals(TuneStep.APPLY, previousStep(TuneStep.STOCK, 1))
        assertEquals(TuneStep.HOME, previousStep(TuneStep.APPLY, 1))
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
        assertEquals(TuneStep.APPLY, resumeAt(TuneStep.RESULT, 1))
        assertEquals(TuneStep.STOCK, resumeAt(TuneStep.APPLY, 0))
        assertEquals(TuneStep.RESULT, resumeAt(null, 4))
        assertEquals(TuneStep.APPLY, resumeAt(TuneStep.DONE, 1))
        // a place saved by an older version on the old stock review lands on the EQ step
        assertEquals(TuneStep.APPLY, resumeAt(TuneStep.STOCK_RESULT, 1))
        assertEquals(5, STEP_NAMES.size)
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

    // ---------------------------------------------------------------- 2.5.0: draft Set up, EQ check verdict, preview tunes

    private val eq1 = listOf(dev.autotune.dsp.EqFilter(100.0, -3.0, 2.0))

    /** A run measured on the phone's EQ: [applied] is what its fixed bands did, [appliedParam] the EQ behind them. */
    private fun onPhoneEq(label: String, rms: Double, eq: List<dev.autotune.dsp.EqFilter> = eq1) =
        SavedRun(1L, rms, DoubleArray(4), emptyList(), label, applied = listOf(dev.autotune.dsp.EqFilter(100.0, -2.6, 1.4)), complete = true, appliedParam = eq)

    @Test
    fun setUpIsADraftThatKeepsTheContinuePlace() {
        assertTrue(keepsResumePlace(TuneStep.SETUP))
        assertTrue(keepsResumePlace(TuneStep.HOME))
        assertTrue(keepsResumePlace(TuneStep.FRESH))
        listOf(TuneStep.STOCK, TuneStep.APPLY, TuneStep.VERIFY, TuneStep.RESULT, TuneStep.DONE).forEach { assertFalse(it.name, keepsResumePlace(it)) }
        // Back from Set up leaves the workflow, and Continue still finds the open test's place
        assertEquals(TuneStep.HOME, previousStep(TuneStep.SETUP, 3))
        assertEquals(TuneStep.APPLY, resumeAt(TuneStep.APPLY, 3))
    }

    @Test
    fun theEqCheckVerdictDecidesTheNextMove() {
        val runs = listOf(r("Stock", 6.0), r("Run 1", 5.9))
        // no check yet: check first
        assertEquals(NextMove.CHECK_EQ, recommendNext(runs, true, null).move)
        // nothing arrives on this output: equipment
        assertEquals(NextMove.SET_ON_EQUIPMENT, recommendNext(runs, true, 2).move)
        // the EQ arrives (all or part of it): the run missed it, measure again before fitting to it
        assertEquals(NextMove.REPEAT, recommendNext(runs, true, 0).move)
        assertEquals(NextMove.REPEAT, recommendNext(runs, true, 1).move)
        // too flat to tell: the usual recommendation, never the check again
        assertEquals(NextMove.REFINE, recommendNext(runs, true, 3).move)
        // a verdict without the doubt changes nothing
        assertEquals(NextMove.KEEP_LATEST, recommendNext(listOf(r("Stock", 6.0), r("Run 1", 1.4)), false, 2).move)
        assertEquals("Set it on my equipment", moveLabel(NextMove.SET_ON_EQUIPMENT, null))
        assertEquals("Measure again", moveLabel(NextMove.REPEAT, null))
    }

    @Test
    fun outputsThePhonesEqMissesAreRemembered() {
        val known = bypassAfterCheck(emptySet(), "Car (Android Auto)", 2)
        assertEquals(setOf("Car (Android Auto)"), known)
        assertEquals(known, bypassAfterCheck(known, "Car (Android Auto)", 1)) // partly: no change
        assertEquals(known, bypassAfterCheck(known, null, 2))
        assertEquals(emptySet<String>(), bypassAfterCheck(known, "Car (Android Auto)", 0)) // it arrives now
        assertEquals(setOf("Car (Android Auto)", "F-150"), bypassAfterCheck(known, "F-150", 2))
    }

    @Test
    fun aPreviewTuneIsMeasuredOnThePhonesEqBeforeItIsKept() {
        // Run 1 is close enough to keep, but it was measured through the app's preview
        val preview = listOf(r("Stock", 6.0), r("Run 1", 1.4))
        val rec = recommendNext(preview, false, null, phoneRoute = true, way = ApplyWay.IN_APP)
        assertEquals(NextMove.MEASURE_ON_PHONE_EQ, rec.move)
        assertEquals(1, rec.keepIdx)
        assertEquals("Switch on the phone's EQ and measure", moveLabel(rec.move, null))
        // set on equipment, or another device: keep as before
        assertEquals(NextMove.KEEP_LATEST, recommendNext(preview, false, null, phoneRoute = true, way = ApplyWay.EQUIPMENT).move)
        assertEquals(NextMove.KEEP_LATEST, recommendNext(preview, false, null, phoneRoute = false, way = ApplyWay.IN_APP).move)
        assertEquals(1, recommendNext(preview, false).keepIdx)
        // measured on the phone's EQ already: keep it
        assertEquals(NextMove.KEEP_LATEST, recommendNext(listOf(r("Stock", 6.0), onPhoneEq("Run 1", 1.4)), false, null, true, ApplyWay.IN_APP).move)
        // the preview run measured best, and its EQ has since been measured on the phone's EQ: keeping it finishes
        val verified = recommendNext(listOf(r("Stock", 6.0), r("Run 1", 1.4), onPhoneEq("Run 2", 1.9)), false, null, true, ApplyWay.IN_APP)
        assertEquals(NextMove.KEEP_BEST, verified.move)
        assertEquals(1, verified.keepIdx)
        // a phone run with a different EQ does not count
        val other = listOf(dev.autotune.dsp.EqFilter(2000.0, 4.0, 1.0))
        assertEquals(NextMove.MEASURE_ON_PHONE_EQ, recommendNext(listOf(r("Stock", 6.0), r("Run 1", 1.4), onPhoneEq("Run 2", 1.9, other)), false, null, true, ApplyWay.IN_APP).move)
        assertFalse(needsPhoneEqRun(preview, 0, true, ApplyWay.IN_APP)) // the stock run has no EQ to keep
    }

    // ---------------------------------------------------------------- 2.6.0: Set up warns, Done offers automatic switching

    @Test
    fun setUpWarnsWhenThePhonesEqMayNotReachTheOutput() {
        val known = setOf("Bluetooth: F-150")
        assertTrue(phoneEqReachWarning("Bluetooth: F-150", false, known)!!.startsWith("An EQ check found"))
        assertTrue(phoneEqReachWarning("Car (Android Auto)", false, emptySet())!!.startsWith("Android Auto"))
        assertTrue(phoneEqReachWarning("Bluetooth: F-150", true, emptySet())!!.startsWith("Android Auto"))
        // a proven bypass says more than a guess
        assertTrue(phoneEqReachWarning("Car (Android Auto)", true, setOf("Car (Android Auto)"))!!.startsWith("An EQ check found"))
        assertEquals(null, phoneEqReachWarning("Bluetooth: LG soundbar", false, known))
        assertEquals(null, phoneEqReachWarning(null, false, known))
    }

    @Test
    fun doneOffersToSwitchTheTunedEqOnWithTheCar() {
        val car = BtRoute("AA:BB:CC:DD:EE:FF", "F-150")
        assertTrue(offerAutoSwitch(car, true, "p1", emptyList(), false))
        // nothing to offer: no Bluetooth route, the EQ is on the head unit, or nothing was kept
        assertFalse(offerAutoSwitch(null, true, "p1", emptyList(), true))
        assertFalse(offerAutoSwitch(car, false, "p1", emptyList(), true))
        assertFalse(offerAutoSwitch(car, true, null, emptyList(), true))
        // already set up (address case does not matter), but only while automatic switching is on
        val rule = listOf(AutoRule("aa:bb:cc:dd:ee:ff", "F-150", "p1"))
        assertFalse(offerAutoSwitch(car, true, "p1", rule, true))
        assertTrue(offerAutoSwitch(car, true, "p1", rule, false))
        // a rule for another EQ is replaced by the new tune
        assertTrue(offerAutoSwitch(car, true, "p2", rule, true))
    }

    // ---------------------------------------------------------------- 2.7.0: no Stock or Verify page, Done on the Tune screen, Set up remembered

    @Test
    fun aClosedMeasurementGoesBackWhereItStarted() {
        assertEquals(TuneStep.SETUP, returnAfterMeasuring(TuneStep.SETUP, 0))
        assertEquals(TuneStep.APPLY, returnAfterMeasuring(TuneStep.APPLY, 1))
        assertEquals(TuneStep.RESULT, returnAfterMeasuring(TuneStep.RESULT, 3))
        // the stock measured again from the EQ step, which no longer fits after it failed with no reading
        assertEquals(TuneStep.SETUP, returnAfterMeasuring(TuneStep.APPLY, 0))
        // started from elsewhere (the EQ tab, the AI review): the EQ step, or Set up without a reading
        assertEquals(TuneStep.APPLY, returnAfterMeasuring(TuneStep.HOME, 2))
        assertEquals(TuneStep.APPLY, returnAfterMeasuring(null, 1))
        assertEquals(TuneStep.SETUP, returnAfterMeasuring(null, 0))
        assertEquals(TuneStep.APPLY, returnAfterMeasuring(TuneStep.RESULT, 1))
    }

    @Test
    fun setUpRemembersEachOutputsChoices() {
        val m = SetupMemory(5, 0, 3, 0, 2.0, -1.5)
        assertEquals(m, SetupMemory.fromText(m.toText()))
        assertEquals(null, SetupMemory.fromText(null))
        assertEquals(null, SetupMemory.fromText("2;5;0;3;0;2.0;-1.5")) // a later format
        assertEquals(null, SetupMemory.fromText("1;5;0;x;0;2.0;-1.5"))
        assertEquals(null, SetupMemory.fromText("1;5;0;0;0;2.0;-1.5")) // no spots
    }

    @Test
    fun theTuneScreenShowsTheFinishedTuneUntilSomethingChanges() {
        assertTrue(showsTuned("t1", 3, "t1", 3))
        assertFalse(showsTuned("t1", 3, "t1", 4)) // measured again since
        assertFalse(showsTuned("t1", 3, "t2", 3)) // another test open
        assertFalse(showsTuned(null, 0, null, 0))
    }
}
