package dev.autotune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import dev.autotune.*
import dev.autotune.dsp.*
import dev.autotune.dsp.Target
import org.junit.Rule
import org.junit.Test

class ShotsTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 3400), theme = "android:Theme.Material.NoActionBar", showSystemUi = false)

    private val vm: TuneViewModel = run {
        val uc = Class.forName("sun.misc.Unsafe")
        val f = uc.getDeclaredField("theUnsafe"); f.isAccessible = true
        uc.getMethod("allocateInstance", Class::class.java).invoke(f.get(null), TuneViewModel::class.java) as TuneViewModel
    }
    private val sharer = object : Sharer { override fun text(t: String) {}; override fun files(list: List<java.io.File>, mime: String, title: String) {} }
    private val pick = Pickers({}, {}, {}, {}, {}, {})
    private val grid = FreqGrid()

    private fun curveOf(bump: (Double) -> Double) = DoubleArray(grid.size) { bump(grid.freqs[it]) }
    private val stock = curveOf { f -> 4 * Math.sin(Math.log(f / 20) * 3) + (if (f < 50) -6 else 0) + (if (f in 2800.0..3400.0) 5 else 0) }
    private val run1 = curveOf { f -> 1.5 * Math.sin(Math.log(f / 20) * 3) + (if (f < 40) -3 else 0) }
    private val target = DoubleArray(grid.size) { 0.0 }
    private val eqc = EqChain.withSafePreamp(listOf(EqFilter(45.0, 3.0, 1.0), EqFilter(63.0, -3.5, 4.0), EqFilter(125.0, -2.0, 2.0), EqFilter(3100.0, -4.0, 5.0), EqFilter(9000.0, 2.0, 1.2)))
    private val eqCurve = DoubleArray(grid.size) { eqc.responseDb(grid.freqs[it]) - eqc.preampDb }
    private val chart = ChartData(grid.freqs, listOf(Curve("Stock (no EQ)", stock, 0), Curve("Run 1", run1, 1)), target, eqCurve, "Further tweaks")
    private val eqChart = ChartData(grid.freqs, listOf(Curve("Run 1", run1, 1)), target, eqCurve, "AutoTune EQ", DoubleArray(grid.size) { run1[it] + eqCurve[it] * 0.8 })
    private val bars = BarsData(
        listOf(BarRun("Stock (no EQ)", 0, DoubleArray(Rta.CENTERS.size) { 5 * Math.sin(it / 3.0) }), BarRun("Run 1", 1, DoubleArray(Rta.CENTERS.size) { 2 * Math.sin(it / 3.0) })),
        DoubleArray(Rta.CENTERS.size),
    )
    private val report = SystemJudge.judge(run1, grid, Target.CAR, emptyList(), null)
    private val res = listOf(ResonanceDetector.rebuild(3100.0, 7.0, 5.0, 220.0, false), ResonanceDetector.rebuild(63.0, 5.0, 4.0, null, false))

    private val runs3 = listOf(RunInfo(0, "Stock (no EQ)", "9 Oct 14:02", 6.2, "", 0, "umik.txt"), RunInfo(1, "Run 1", "9 Oct 14:20", 3.4, "", 5, "umik.txt"), RunInfo(2, "Run 2", "9 Oct 14:41", 2.1, "", 6, "umik.txt"))
    private val s = UiState(
        hasPermission = true, micName = "UMIK-1", calibrationName = "7012345_90deg.txt", calibrationInfo = "sens -1.6 dB",
        targetNames = Target.ALL.map { it.name }, customTargets = listOf("My car sound"),
        testName = "F-150 driver seat", currentTestId = "a", targetIdx = 0, source = Source.PHONE,
        tests = listOf(TestSummary("a", "F-150 driver seat", "Car", TargetKind.CAR, "Connected", 3, 6.2, 2.1, "9 Oct 14:20"), TestSummary("b", "LG (3)", "Home theatre", TargetKind.HOME, "Connected", 9, 7.0, 5.6, "12 Oct 20:01")),
        runs = runs3, selectedRuns = setOf(0, 2), chart = chart, bars = bars, rmsDb = 2.1, resonances = res, report = report,
        regions = Describe.regions(run1, grid, Target.CAR),
        compare = Compare(listOf("Stock (no EQ)", "Run 2"), listOf(0, 2), listOf(6.2, 2.1), listOf("Bass", "Mids", "Treble"), listOf(listOf(-4.0, -1.0), listOf(1.5, 0.3), listOf(3.0, 0.4)), listOf(54, 83), "Better: Run 2 is 2.10 dB RMS from the target, down from 6.20 (Stock)."),
        suggestedChain = eqc, exportChain = eqc, eqChart = eqChart, eqSource = "Suggested by tuning · F-150 driver seat", eqLevelDb = -3.4,
        outputs = listOf(1 to "F-150 (Bluetooth)", 2 to "Phone speaker"), outputName = "F-150 (Bluetooth)", positions = 3,
        profiles = listOf(EqProfile("p1", "F-150 · first EQ", "tuning", "F-150", "F-150 driver seat", "from the stock reading", 1L, eqc.filters)),
        systemEqOn = false, clipName = "Pink noise",
        eqPick = "run:b:8", eqChoices = listOf(
            EqChoice("suggest:a", "F-150 driver seat", TargetKind.CAR, "Next suggestion", "After Run 2, not measured yet · 6 bands"),
            EqChoice("run:a:2", "F-150 driver seat", TargetKind.CAR, "Run 2", "Measured 2.10 dB from the target · best · 5 bands"),
            EqChoice("run:b:8", "LG (3)", TargetKind.HOME, "Run 8", "Measured 5.56 dB from the target · 10 bands"),
            EqChoice("run:b:1", "LG (3)", TargetKind.HOME, "Run 1", "Measured 5.45 dB from the target · best · 10 bands"),
        ),
    )

    @Composable private fun Frame(dark: Boolean, look: Look = Look.STUDIO, content: @Composable () -> Unit) {
        AutoTuneTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, false, look) {
            val slot = androidx.compose.runtime.remember { PinSlot() }
            androidx.compose.runtime.CompositionLocalProvider(LocalPin provides slot) {
                androidx.compose.material3.Surface(color = At.c.bg, contentColor = At.c.text) {
                    Column(Modifier.fillMaxSize().background(At.c.bg)) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
                        slot.action?.let { BigActionBar(it) }
                    }
                }
            }
        }
    }
    private fun shot(name: String, dark: Boolean = true, look: Look = Look.STUDIO, content: @Composable () -> Unit) {
        paparazzi.snapshot(name) { Frame(dark, look, content) }
    }
    private fun tune(st: UiState) = @Composable { TuneTab(st, vm, sharer, {}, {}, pick) }

    @Test fun t1_home() = shot("t1_home") { tune(s.copy(tuneStep = TuneStep.HOME))() }
    @Test fun t2_fresh() = shot("t2_fresh") { tune(s.copy(tuneStep = TuneStep.FRESH, systemEqOn = true))() }
    @Test fun t3_setup() = shot("t3_setup") { tune(s.copy(tuneStep = TuneStep.SETUP))() }
    @Test fun t4_stock() = shot("t4_stock") { tune(s.copy(tuneStep = TuneStep.STOCK, runs = emptyList(), positionsDone = 1))() }
    @Test fun t5_apply() = shot("t5_apply") { tune(s.copy(tuneStep = TuneStep.APPLY, runs = runs3.take(1)))() }
    @Test fun t6_verify() = shot("t6_verify") { tune(s.copy(tuneStep = TuneStep.VERIFY, applyWay = ApplyWay.IN_APP, positions = 1))() }
    @Test fun t7_result() = shot("t7_result") { tune(s.copy(tuneStep = TuneStep.RESULT, resultWarnings = listOf("Low signal-to-noise (31 dB) - raise the volume or reduce background noise.")))() }
    @Test fun t8_done() = shot("t8_done") { tune(s.copy(tuneStep = TuneStep.DONE, systemEqOn = true, eqSource = "Profile · F-150 · tuned (Run 2)"))() }
    @Test fun t9_light_home() = shot("t9_light_home", dark = false) { tune(s.copy(tuneStep = TuneStep.HOME))() }
    @Test fun e1_eq() = shot("e1_eq") { EqTab(s, vm, {}, pick) }
    @Test fun r1_results() = shot("r1_results") { TestsTab(s, vm, true, {}, {}, {}) }
    @Test fun e2_export() = shot("e2_export") { ExportTab(s, vm, {}) }
    @Test fun r2_tests() = shot("r2_tests") { TestsTab(s, vm, false, {}, {}, {}) }
    private val checksDone = listOf(
        CheckItem("Microphone", 1, "iMM-6C · unprocessed"), CheckItem("Sound output", 1, "Bluetooth: LG HTS"), CheckItem("Start beeps", 1, "found"),
        CheckItem("Whole sweep heard", 1), CheckItem("No clipping", 1), CheckItem("Signal above the background", 1, "63 dB"),
        CheckItem("Every frequency heard", 1), CheckItem("Clocks in step", 2, "-59 ppm apart: treble may vary a little"),
    )
    private val checksWait = checksDone.map { CheckItem(it.label, 0) }
    private fun mshot(name: String, mv: MeasureView, busy: Boolean, dark: Boolean = true) {
        dev.autotune.audio.LiveCapture.level.value = -28f
        dev.autotune.audio.LiveCapture.wave.value = FloatArray(512) { i -> (Math.sin(i * 0.31) * 0.6 + Math.sin(i * 1.7) * 0.25 + Math.sin(i * 0.05) * 0.3).toFloat() }
        dev.autotune.audio.LiveCapture.playStartMs = System.currentTimeMillis() - 7300
        paparazzi.snapshot(name) { AutoTuneTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, false) { MeasureOverlay(mv, s.copy(busy = busy), vm) } }
    }
    @Test fun m1_measuring() = mshot("m1_measuring", MeasureView("Run 3", MeasureStage.MEASURING, "", checksWait, 1, 1, true), true)
    @Test fun m2_analysing() = mshot("m2_analysing", MeasureView("Run 3", MeasureStage.ANALYSING, "Checking the reading", checksDone.take(5) + checksWait.drop(5), 1, 1, true), true)
    @Test fun m3_saved() = mshot("m3_saved", MeasureView("Run 3", MeasureStage.SAVED, "Run 3 saved", checksDone, 1, 1, true, "5.6 dB from the target (stock 7.0)"), false)
    @Test fun m4_failed() = mshot("m4_failed", MeasureView("Stock reading", MeasureStage.FAILED, "The microphone barely heard the test sound (signal-to-noise 9 dB). Raise the volume, move the mic closer to the speakers, or check the sound is playing.", checksDone.map { if (it.label.startsWith("Signal")) CheckItem(it.label, 3, "9 dB: far too quiet") else it }, 1, 3, true), false)
    @Test fun m5_light() = mshot("m5_light", MeasureView("Run 3", MeasureStage.MEASURING, "", checksDone.take(3) + checksWait.drop(3), 1, 1, true), true, dark = false)
    @Test fun x1_settings() = shot("x1_settings") { SetupScreen(s, AiUi(hasKey = true, keyMasked = "sk-ant-…a1F9", keyMessage = "The key works with Claude Sonnet."), vm, pick) {} }

    private val aiReview = dev.autotune.ai.AiReview.fromTool(
        mapOf(
            "summary" to "Run 2 is a real improvement (6.2 to 2.1 dB from the target). What is left is mostly a dip at 50-63 Hz that EQ cannot fill and a hot 3 kHz region from the dash speakers.",
            "issues" to listOf(
                mapOf("severity" to "problem", "area" to "speakers", "title" to "Bass dip at 50-63 Hz", "detail" to "-6 dB in every run, unchanged by the +3 dB boost: a cancellation, not a level problem.", "action" to "Flip the subwoofer phase (or polarity) and measure again."),
                mapOf("severity" to "check", "area" to "measurement", "title" to "Signal a little low", "detail" to "31 dB signal-to-noise on Run 2; the treble readings move about 1 dB between runs.", "action" to "Raise the volume one or two steps before the next run."),
                mapOf("severity" to "fine", "area" to "eq", "title" to "No over-correction", "detail" to "Every cut landed within 1 dB of what was predicted."),
            ),
            "eq_changes" to listOf(
                mapOf("freq_hz" to 45.0, "gain_db" to -3.0, "q" to 1.0, "reason" to "Remove the boost into the null"),
                mapOf("freq_hz" to 3000.0, "gain_db" to -1.5, "q" to 1.4, "reason" to "Still 2 dB hot"),
            ),
            "eq_note" to "AutoTune's suggestion is sound; drop the 45 Hz boost and take a little more off 3 kHz.",
            "next_step" to "Flip the sub's phase, then measure Run 3 with the AI-adjusted EQ.",
        ),
        "a", "claude-sonnet-5-5", 1760000000000L, 3, 2, false,
    )
    private val aiEq = dev.autotune.ai.AiEq(EqChain.withSafePreamp(eqc.filters.drop(1) + EqFilter(3000.0, -1.5, 1.4)), 2.4, 1.9, listOf("3.0 kHz: Q 5.0 held to 1.4."))
    private val aiState = AiUi(
        hasKey = true, review = aiReview, eq = aiEq, testName = "F-150 driver seat",
        chat = listOf(dev.autotune.ai.AiTurn("user", "Should I change the crossover?"), dev.autotune.ai.AiTurn("assistant", "Not yet. The 50-63 Hz dip is below the sub's crossover, so phase is the first thing to try; if flipping it does not help, then try moving the crossover from 80 to 63 Hz.")),
    )
    private fun ashot(name: String, a: AiUi, dark: Boolean = true) = paparazzi.snapshot(name) {
        AutoTuneTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, false) {
            androidx.compose.material3.Surface(color = At.c.bg, contentColor = At.c.text) { AiScreen(a, s, vm) {} }
        }
    }
    @Test fun a1_ai_review() = ashot("a1_ai_review", aiState)
    @Test fun a2_ai_nokey() = ashot("a2_ai_nokey", AiUi(testName = "F-150 driver seat"))
    @Test fun a3_ai_busy_light() = ashot("a3_ai_busy_light", AiUi(hasKey = true, busy = true, testName = "F-150 driver seat"), dark = false)
    @Test fun a4_ai_failure() = ashot("a4_ai_failure", AiUi(hasKey = true, testName = "LG (3)", review = dev.autotune.ai.AiReview.fromTool(mapOf(
        "summary" to "The measurement failed because the start beeps never reached the mic: the sound went to the phone speaker, not the soundbar.",
        "issues" to listOf(mapOf("severity" to "problem", "area" to "setup", "title" to "Wrong output", "detail" to "Output was 'Phone speaker' and every check after 'Sound output' failed.", "action" to "Connect the soundbar over Bluetooth and pick it as the output.")),
        "eq_changes" to emptyList<Any>(), "next_step" to "Pick the LG soundbar as the output and try again.",
    ), null, "claude-haiku-5-5", 1760000000000L, 0, null, true)))

    private val stockA = analyzeStock(
        6.2, 2.4, 6, Describe.regions(stock, grid, Target.FLAT), res, listOf("Mic source: VOICE_RECOGNITION. This phone does not offer an unprocessed microphone, so automatic gain or noise suppression may slightly tilt the reading."), report,
    )
    @Test fun t4b_stock_analysis() = shot("t4b_stock_analysis") { tune(s.copy(tuneStep = TuneStep.STOCK_RESULT, runs = runs3.take(1), selectedRuns = setOf(0), stock = stockA))() }
    @Test fun t4c_stock_suspicious() = shot("t4c_stock_suspicious", dark = false) {
        tune(s.copy(tuneStep = TuneStep.STOCK_RESULT, runs = runs3.take(1), selectedRuns = setOf(0), stock = analyzeStock(15.0, 9.0, 8, emptyList(), emptyList(), listOf("Every frequency was barely heard between about 40 and 90 Hz (signal-to-noise 9 dB)."), null)))()
    }
    private val savedRuns = listOf(
        SavedRun(1L, 6.2, DoubleArray(4), emptyList(), "Stock (no EQ)", complete = true),
        SavedRun(1L, 3.4, DoubleArray(4), emptyList(), "Run 1", applied = eqc.filters, complete = true),
        SavedRun(1L, 2.1, DoubleArray(4), emptyList(), "Run 2", applied = eqc.filters, complete = true),
    )
    @Test fun t7b_result_refine() = shot("t7b_result_refine") { tune(s.copy(tuneStep = TuneStep.RESULT, recommendation = recommendNext(savedRuns.take(2), false), runs = runs3.take(2)))() }
    @Test fun t7c_result_keep() = shot("t7c_result_keep") { tune(s.copy(tuneStep = TuneStep.RESULT, recommendation = recommendNext(savedRuns, false)))() }

    @Test fun u1_results_summary() = shot("u1_results_summary") { TestsTab(s.copy(stock = stockA, recommendation = recommendNext(savedRuns, false)), vm, true, {}, {}, {}) }
    @Test fun u2_apply() = shot("u2_apply") { tune(s.copy(tuneStep = TuneStep.APPLY, runs = runs3.take(1), eqPick = "suggest:a"))() }
    @Test fun u3_apply_other_eq() = shot("u3_apply_other_eq", dark = false) { tune(s.copy(tuneStep = TuneStep.APPLY, runs = runs3.take(1), eqPick = "run:b:8", eqSource = "LG (3) · Run 8"))() }
    @Test fun u4_home_resume() = shot("u4_home_resume") { tune(s.copy(tuneStep = TuneStep.HOME, resumeStep = TuneStep.APPLY, systemEqOn = true, eqSource = "F-150 driver seat · Suggested EQ"))() }

    private val boomy = curveOf { f -> 26.0 / (1 + (f / 110.0) * (f / 110.0)) + 3 * Math.sin(Math.log(f / 20) * 4) - (if (f > 12000) 12 else 0) }
    private val bigChart = ChartData(grid.freqs, listOf(Curve("Run 1", boomy, 1)), target, eqCurve, "AutoTune EQ", DoubleArray(grid.size) { boomy[it] + eqCurve[it] })
    @Test fun v1_chart_range_and_taste() = shot("v1_chart_range_and_taste") {
        Panel { ResponseChart(bigChart, height = 220.dp) }
        TargetSoundFold(s.copy(targetIdx = 3, bassDb = 4.0, trebleDb = -2.0), vm, startOpen = true)
    }

    @Test fun w1_look_console() = shot("w1_look_console", look = Look.CONSOLE) { EqTab(s, vm, {}, pick) }
    @Test fun w2_look_hifi() = shot("w2_look_hifi", look = Look.HIFI) { EqTab(s, vm, {}, pick) }
    @Test fun w3_look_vu() = shot("w3_look_vu", dark = false, look = Look.VU) { EqTab(s, vm, {}, pick) }
    @Test fun w4_look_synth() = shot("w4_look_synth", look = Look.SYNTH) { EqTab(s, vm, {}, pick) }
    @Test fun w5_result_pinned() = shot("w5_result_pinned", look = Look.CONSOLE) { tune(s.copy(tuneStep = TuneStep.RESULT, recommendation = recommendNext(savedRuns.take(2), false), runs = runs3.take(2)))() }
    @Test fun w6_eq_check() = shot("w6_eq_check") {
        val g = grid
        val eqv = DoubleArray(g.size) { eqc.responseDb(g.freqs[it]) - eqc.preampDb }
        val ch = ChartData(g.freqs, listOf(Curve("Measured change", DoubleArray(g.size) { eqv[it] * 0.3 + Math.sin(it / 9.0) * 0.6 }, 1)), DoubleArray(g.size), DoubleArray(g.size) { eqv[it] * 3 }, "What the EQ asks for")
        val big = DoubleArray(g.size) { eqv[it] * 3 }
        EqCheckCard(EqCheckView(judgeEqCheck(g.freqs, DoubleArray(g.size), DoubleArray(g.size) { big[it] * 0.1 }, big), ch, "F-150 · Suggested EQ", "17 Oct 04:40"), vm)
    }
    @Test fun w7_settings_defaults() = paparazzi.snapshot("w7_settings_defaults") {
        AutoTuneTheme(ThemeMode.DARK, false, Look.STUDIO) {
            androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = At.c.bg, contentColor = At.c.text) {
                SetupScreen(s.copy(defTarget = Target.HARMAN_CAR.name, defBassDb = 2.0, exportFolder = "Documents/AutoTune"), AiUi(), vm, pick) {}
            }
        }
    }

    @Test fun y1_flavour_preamp() = shot("y1_flavour_preamp") {
        Panel { FlavourAndPreamp(s.copy(flavour = Flavour.ROCK, preampMode = PreampMode.MANUAL, manualPreampDb = -4.5, systemEqOn = true, playPreampDb = -4.5), vm) }
    }
    @Test fun y2_follow_checks() = shot("y2_follow_checks") {
        val f = EqFollow(0.55, listOf(BandFollow(31.5, 3.0, 0.2), BandFollow(63.0, 2.0, 1.6), BandFollow(125.0, -4.0, -3.6), BandFollow(250.0, -1.0, -0.8), BandFollow(500.0, 0.0, 0.1), BandFollow(1000.0, -2.0, -1.9), BandFollow(2000.0, 0.0, 0.2), BandFollow(4000.0, -3.0, -2.5), BandFollow(8000.0, 1.0, 0.4)), 45.0,
            "The sound followed about 55% of the EQ. Boosts below about 45 Hz did nothing: the speakers do not play that low, so AutoTune stops boosting there.")
        FollowAndChecks(s.copy(eqFollow = f, checkSuggestions = suggestChecks(savedRuns, true, 1, f, 1.2)), vm)
    }
}
