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
        // a clipped stock reading, or one whose timing moved, is the wrong reference: measured again first (2.23)
        assertTrue(analyzeStock(4.0, 2.0, 6, emptyList(), emptyList(), listOf("The mic clipped around 2.0 kHz (too loud there). Turn the volume down a few steps and measure again."), null).suspicious)
        assertTrue(analyzeStock(4.0, 2.0, 6, emptyList(), emptyList(), listOf(stallMessage(-30.0, bluetooth = false)), null).suspicious)
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
        // 2.20 adds the equipment; a memory from before has none, and an unknown name reads as none
        val e = m.copy(equipment = Equipment.HOME)
        assertEquals(e, SetupMemory.fromText(e.toText()))
        assertEquals("1;5;0;3;0;2.0;-1.5", m.toText())
        assertEquals(null, SetupMemory.fromText("1;5;0;3;0;2.0;-1.5;BOAT")?.equipment)
    }

    /** Set up asks what is tuned first (owner, 2026-11-12); everything after it follows from the answer. */
    @Test
    fun setUpStartsWithTheEquipment() {
        // the output's own memory wins, then the last pick, then car mode, then the default target's kind
        assertEquals(Equipment.PC, startEquipment(Equipment.PC, Equipment.CAR, true, TargetKind.CAR))
        assertEquals(Equipment.HOME, startEquipment(null, Equipment.HOME, true, TargetKind.CAR))
        assertEquals(Equipment.CAR, startEquipment(null, null, true, TargetKind.HOME))
        assertEquals(Equipment.HOME, startEquipment(null, null, false, TargetKind.HOME))
        assertEquals(Equipment.OTHER, startEquipment(null, null, false, TargetKind.FLAT))
        // a test saved before 2.20 takes it from its target
        assertEquals(Equipment.CAR, equipmentOf("", TargetKind.CAR))
        assertEquals(Equipment.PC, equipmentOf("PC", TargetKind.CAR))
        assertEquals(Equipment.HOME, equipmentOf(null, TargetKind.HOME))
    }

    @Test
    fun onlyTheTvAndThePcHaveACompanion() {
        assertEquals(null, companionQuestion(Equipment.CAR))
        assertEquals(null, companionQuestion(Equipment.OTHER))
        assertEquals("AutoTune on the TV", companionQuestion(Equipment.HOME)?.label)
        assertEquals("AutoTune on the PC", companionQuestion(Equipment.PC)?.label)
        assertEquals(2, companionQuestion(Equipment.PC)?.answers?.size)
    }

    /** Set up names the equipment, then the test (owner, 2026-11-12); both carry over to a new home theatre. */
    @Test
    fun setUpNamesTheEquipmentThenTheTest() {
        assertEquals("Driver seat", newTestName(" Driver seat ", "F-150", Equipment.CAR, emptySet()))
        // nothing typed: the equipment's name, never a date or the target (2.32)
        assertEquals("F-150", newTestName("", " F-150 ", Equipment.CAR, emptySet()))
        assertEquals("Car", newTestName(" ", "", Equipment.CAR, emptySet()))
        assertEquals("Car 2", newTestName("", "", Equipment.CAR, setOf("car")))
        assertEquals("Car 3", newTestName("", "", Equipment.CAR, setOf("Car", "Car 2")))
        assertEquals("Home theatre", newTestName("", "", Equipment.HOME, setOf("Car")))
        assertEquals("PC speakers", newTestName("", "", Equipment.PC, emptySet()))
        assertEquals("Speakers 2", newTestName("", "", Equipment.OTHER, setOf("Speakers")))
    }

    /** One question per screen; the companion question only for a TV or a PC. */
    @Test
    fun setUpIsOneQuestionPerScreen() {
        assertEquals(listOf(SetupPage.EQUIPMENT, SetupPage.NAMES, SetupPage.SOUND), setupPages(Equipment.CAR))
        assertEquals(SetupPage.entries.toList(), setupPages(Equipment.HOME))
        assertEquals(SetupPage.COMPANION, nextSetupPage(Equipment.PC, SetupPage.EQUIPMENT))
        assertEquals(SetupPage.NAMES, nextSetupPage(Equipment.OTHER, SetupPage.EQUIPMENT))
        assertEquals(null, nextSetupPage(Equipment.CAR, SetupPage.SOUND))
        assertEquals(SetupPage.COMPANION, previousSetupPage(Equipment.HOME, SetupPage.NAMES))
        assertEquals(SetupPage.EQUIPMENT, previousSetupPage(Equipment.CAR, SetupPage.NAMES))
        assertEquals(null, previousSetupPage(Equipment.CAR, SetupPage.EQUIPMENT))
        // with AutoTune on the TV the names come last: their key connects (2.24)
        assertEquals(listOf(SetupPage.EQUIPMENT, SetupPage.COMPANION, SetupPage.NAMES), setupPages(Equipment.HOME, viaCompanion = true))
        assertEquals(null, nextSetupPage(Equipment.PC, SetupPage.NAMES, viaCompanion = true))
        assertEquals(SetupPage.SOUND, nextSetupPage(Equipment.PC, SetupPage.NAMES, viaCompanion = false))
        // a car has no companion: the flag changes nothing
        assertEquals(setupPages(Equipment.CAR), setupPages(Equipment.CAR, viaCompanion = true))
        assertEquals("LG soundbar, sofa", companionTheatreName(" LG soundbar", "sofa "))
        assertEquals("", companionTheatreName(" ", ""))
        // a home theatre's target with its taste; an unknown name falls back to Home theatre
        assertEquals(dev.autotune.dsp.Target.HARMAN_ROOM.name, dev.autotune.theatre.theatreTarget(dev.autotune.dsp.Target.HARMAN_ROOM.name, 0.0, 0.0).name)
        assertEquals(dev.autotune.dsp.Target.HOME_THEATER.dbAt(1000.0), dev.autotune.theatre.theatreTarget("gone", 0.0, 0.0).dbAt(1000.0), 1e-9)
        assertTrue(dev.autotune.theatre.theatreTarget("gone", 3.0, 0.0).dbAt(30.0) > dev.autotune.dsp.Target.HOME_THEATER.dbAt(30.0) + 2.0)
        // Simple does not ask the target: Harman-style in a car, the room curve elsewhere
        assertEquals(dev.autotune.dsp.Target.HARMAN_CAR, simpleTarget(Equipment.CAR))
        assertEquals(dev.autotune.dsp.Target.HOME_THEATER, simpleTarget(Equipment.PC))
    }

    @Test
    fun theTargetsFollowTheEquipment() {
        // the built-in order: Car, Home theatre, Flat, Harman car, Harman room, then a custom curve
        val kinds = listOf(TargetKind.CAR, TargetKind.HOME, TargetKind.FLAT, TargetKind.CAR, TargetKind.HOME, TargetKind.CUSTOM)
        assertEquals(listOf(0, 2, 3, 5), equipmentTargets(Equipment.CAR, kinds))
        assertEquals(listOf(1, 2, 4, 5), equipmentTargets(Equipment.HOME, kinds))
        assertEquals(listOf(1, 2, 4, 5), equipmentTargets(Equipment.PC, kinds))
        assertEquals(kinds.indices.toList(), equipmentTargets(Equipment.OTHER, kinds))
        // the default from Settings when it fits, else the remembered one, else the first that fits
        assertEquals(3, equipmentTarget(Equipment.CAR, kinds, 3, 0))
        assertEquals(0, equipmentTarget(Equipment.CAR, kinds, 1, 0))
        assertEquals(1, equipmentTarget(Equipment.HOME, kinds, 0, null))
        assertEquals(4, equipmentTarget(Equipment.HOME, kinds, 0, 4))
    }

    @Test
    fun theRoutesFollowTheEquipment() {
        assertEquals(Source.PHONE, equipmentRoutes(Equipment.CAR).first())
        // a PC plays our file itself
        assertEquals(Source.COMBINED, equipmentRoutes(Equipment.PC).first())
        assertEquals(Source.TEST_FILE, equipmentRoute(Equipment.HOME, Source.TEST_FILE))
        assertEquals(Source.COMBINED, equipmentRoute(Equipment.PC, null))
        assertEquals("The head unit plays our short test file", routeLabel(Equipment.CAR, Source.TEST_FILE))
        assertEquals("The PC plays our test file", routeLabel(Equipment.PC, Source.COMBINED))
        assertEquals("The TV plays noise from the web", routeLabel(Equipment.HOME, Source.YOUTUBE))
        Equipment.entries.forEach { e -> assertEquals(Source.entries.toSet(), equipmentRoutes(e).toSet()) }
        // the checklist and the place the EQ goes name the equipment's own controls
        assertTrue(stockChecklist(Equipment.CAR).contains("head unit"))
        assertTrue(stockChecklist(Equipment.PC).contains("Equalizer APO"))
        assertTrue(equipmentEqWhere(Equipment.PC).first.contains("equalizer apo"))
    }

    @Test
    fun theTuneScreenShowsTheFinishedTuneUntilSomethingChanges() {
        assertTrue(showsTuned("t1", 3, "t1", 3))
        assertFalse(showsTuned("t1", 3, "t1", 4)) // measured again since
        assertFalse(showsTuned("t1", 3, "t2", 3)) // another test open
        assertFalse(showsTuned(null, 0, null, 0))
    }

    @Test
    fun theTunedCardShowsTheRunKeepPutInUseNotTheLowestDb() {
        // stock 2.9, run 1 1.3, run 2 1.4: Keep on Run 2 puts Run 2's EQ in use, so Run 2 is what the card shows (2.30.1)
        val dbs = listOf(2.9, 1.3, 1.4)
        assertEquals(2, tunedRunIndex(dbs, 2))
        assertEquals(1, tunedRunIndex(dbs, 1))
        // not known (an older state), or the stock run: the lowest dB after the stock
        assertEquals(1, tunedRunIndex(dbs, -1))
        assertEquals(1, tunedRunIndex(dbs, 0))
        assertEquals(1, tunedRunIndex(dbs, 7))
        assertEquals(null, tunedRunIndex(listOf(2.9), -1))
    }

    @Test
    fun theDialMakesRoomForAFinishedTune() {
        assertTrue(dialShare(true) < dialShare(false))
    }

    @Test
    fun finishingKeepsTheCloserRunEvenWithinTheRoundMargin() {
        // Run 1 3.8 dB, Run 2 4.0 dB (seen on a phone): the last round "barely changed it", but Run 1 is the closer one
        val rec = recommendNext(listOf(r("Stock", 6.0), r("Run 1", 3.8), r("Run 2", 4.0)), false)
        assertEquals(NextMove.KEEP_BEST, rec.move)
        assertEquals(1, rec.bestIdx)
        // the latest being the closer one is unchanged
        assertEquals(NextMove.KEEP_LATEST, recommendNext(listOf(r("Stock", 6.0), r("Run 1", 2.6), r("Run 2", 2.5)), false).move)
    }

    @Test
    fun aFinishedTestOpensOnItsBestRun() {
        assertEquals(1, bestRunIndex(listOf(6.0, 3.8, 4.0))) // Run 1 beats Run 2, the stock run is never "best"
        assertEquals(2, bestRunIndex(listOf(6.0, 4.0, 3.8)))
        assertEquals(1, bestRunIndex(listOf(6.0, 3.8, 3.8))) // a tie keeps the earlier run
        assertEquals(null, bestRunIndex(listOf(6.0)))
    }

    @Test
    fun exitAsksOnlyWhenAHalfDoneTuneWouldBeLeft() {
        // readings taken, no EQ kept: ask
        assertTrue(exitAsks(TuneStep.RESULT, 2, keptEq = false))
        assertTrue(exitAsks(TuneStep.APPLY, 1, keptEq = false))
        // Set up has nothing measured; a test finished once just leaves
        assertFalse(exitAsks(TuneStep.SETUP, 0, keptEq = false))
        assertFalse(exitAsks(TuneStep.RESULT, 3, keptEq = true))
        assertFalse(exitAsks(TuneStep.HOME, 2, keptEq = false))
    }

    @Test
    fun aFinishedTestsMainKeyIsNotContinue() {
        assertTrue(testFinished(keptEq = true, resumeStep = null))
        assertFalse(testFinished(keptEq = true, resumeStep = TuneStep.RESULT)) // a round was left half done
        assertFalse(testFinished(keptEq = false, resumeStep = null))
        val p = EqProfile("1", "Truck · tuned (Run 1)", "tuning", "", "Truck", "measured in Run 1", 0L, emptyList())
        assertTrue(hasKeptEq(listOf(p), "Truck"))
        assertFalse(hasKeptEq(listOf(p), "Other"))
        assertFalse(hasKeptEq(listOf(p), ""))
    }

    @Test
    fun aFaderMovesByTheDragNotToTheFinger() {
        // a 152 px track spans 24 dB: 6.33 px per dB, in half-dB steps
        assertEquals(2.0, fadeDrag(0.0, -12.67f, 152f, 12.0), 0.01)
        assertEquals(-3.0, fadeDrag(0.0, 19f, 152f, 12.0), 0.01)
        assertEquals(1.5, fadeDrag(1.5, 0f, 152f, 12.0), 0.0) // touching without moving changes nothing
        assertEquals(12.0, fadeDrag(10.0, -500f, 152f, 12.0), 0.0) // never past the ends
        assertEquals(-12.0, fadeDrag(-10.0, 500f, 152f, 12.0), 0.0)
        assertEquals(4.0, fadeDrag(4.0, -3f, 0f, 12.0), 0.0) // no track, no move
    }
}
