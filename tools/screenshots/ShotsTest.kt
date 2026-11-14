@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.autotune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 3400), theme = "android:Theme.Material.NoActionBar", showSystemUi = false, useDeviceResolution = true)

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

    @Composable private fun Frame(dark: Boolean, look: Look = Look.BRAUN, adv: Boolean = false, nav: Tab? = Tab.MEASURE, accent: Accent = Accent.LOOK, content: @Composable () -> Unit) {
        AutoTuneTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, look, accent) {
            val slot = androidx.compose.runtime.remember { PinSlot() }
            androidx.compose.runtime.CompositionLocalProvider(LocalPin provides slot, LocalViewMode provides ViewMode(adv) {}) {
                androidx.compose.material3.Surface(color = At.c.bg, contentColor = At.c.text) {
                    Column(Modifier.fillMaxSize().background(At.c.bg)) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
                        slot.action?.let { BigActionBar(it) }
                        if (nav != null) AtNavBar(nav, busy = false) {}
                    }
                }
            }
        }
    }
    private fun shot(name: String, dark: Boolean = true, look: Look = Look.BRAUN, adv: Boolean = false, nav: Tab? = Tab.MEASURE, accent: Accent = Accent.LOOK, h: Int = 3400, content: @Composable () -> Unit) {
        if (h != 3400) paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = h))
        paparazzi.snapshot(name) { Frame(dark, look, adv, nav, accent, content) }
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
    @Test fun e1_eq() = shot("e1_eq", nav = Tab.EQ) { EqTab(s, vm, {}, pick) }
    @Test fun l1_loud() = shot("l1_loud") { LoudEqFold(s.copy(systemEqOn = true, loudMode = LoudMode.VOLUME, loudCuts = listOf(EqFilter(63.0, -4.5, 1.4), EqFilter(4000.0, -2.0, 1.4)), loudSource = "From the reading of 6 Nov 21:10", volCheck = VolCheck("Bluetooth: SYNC", 14, 1)), vm, startOpen = true) }
    @Test fun l3_level() { dev.autotune.audio.LoudEq.live.value = dev.autotune.audio.LoudEq.Live(amount = 0.5, route = "Android Auto · USB", carMode = true, levelDb = 82.4, listening = true); shot("l3_level") { LoudEqFold(s.copy(systemEqOn = true, loudMode = LoudMode.LEVEL, loudLevelDb = 86.5), vm, startOpen = true) } }
    @Test fun l2_loud_light() = shot("l2_loud_light", dark = false) { LoudEqFold(s.copy(systemEqOn = true, loudMode = LoudMode.VOLUME), vm, startOpen = true) }
    @Test fun r1_results() = shot("r1_results", nav = null) { androidx.compose.runtime.CompositionLocalProvider(LocalBack provides {}) { ResultsPage(s.copy(resultWarnings = listOf("The microphone barely heard the test sound between about 50 Hz and 74 Hz (it did not stand out from the background there). Treat the response there with care.", "Mic source: VOICE_RECOGNITION. This phone does not offer an unprocessed microphone, so automatic gain or noise suppression may slightly tilt the reading."), recommendation = recommendNext(savedRuns, false)), vm, {}, {}) } }
    @Test fun e2_export() = shot("e2_export") { ExportTab(s, vm, {}) }
    @Test fun r2_tests() = shot("r2_tests") { TestsTab(s, vm, {}) }
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
        paparazzi.snapshot(name) { AutoTuneTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { MeasureOverlay(mv, s.copy(busy = busy), vm) } }
    }
    @Test fun m1_measuring() = mshot("m1_measuring", MeasureView("Run 3", MeasureStage.MEASURING, "", checksWait, 1, 1, true), true)
    @Test fun m2_analysing() = mshot("m2_analysing", MeasureView("Run 3", MeasureStage.ANALYSING, "Checking the reading", checksDone.take(5) + checksWait.drop(5), 1, 1, true), true)
    @Test fun m3_saved() = mshot("m3_saved", MeasureView("Run 3", MeasureStage.SAVED, "Run 3 saved", checksDone, 1, 1, true, "5.6 dB from the target (stock 7.0)"), false)
    @Test fun m4_failed() = mshot("m4_failed", MeasureView("Stock reading", MeasureStage.FAILED, "The microphone barely heard the test sound (signal-to-noise 9 dB). Raise the volume, move the mic closer to the speakers, or check the sound is playing.", checksDone.map { if (it.label.startsWith("Signal")) CheckItem(it.label, 3, "9 dB: far too quiet") else it }, 1, 3, true), false)
    @Test fun m5_light() = mshot("m5_light", MeasureView("Run 3", MeasureStage.MEASURING, "", checksDone.take(3) + checksWait.drop(3), 1, 1, true), true, dark = false)
    // the settings overlay fills the screen itself, so it gets a plain surface rather than the scrolling frame
    private fun settingsShot(name: String, dark: Boolean) = paparazzi.also {
        // long page: a taller canvas so Appearance and About are in the picture
        it.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 6400))
    }.snapshot(name) {
        Frame(dark, nav = Tab.SETTINGS) { SettingsTab(s, AiUi(hasKey = true, keyMasked = "sk-ant-…a1F9", keyMessage = "The key works with Claude Sonnet."), vm, pick) }
    }

    // g: the first-start tutorial (guide), each card at a phone's size, its picture on the last frame (as in any preview)
    private fun ushot(name: String, card: Int, dark: Boolean = true, look: Look = Look.BRAUN) = paparazzi.also {
        it.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5)
    }.snapshot(name) {
        AutoTuneTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, look) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) {
                androidx.compose.material3.Surface(color = At.c.bg, contentColor = At.c.text) { Tutorial({}, start = card) }
            }
        }
    }
    @Test fun g1_tutorial_hello() = ushot("g1_tutorial_hello", 0)
    @Test fun g2_tutorial_measure() = ushot("g2_tutorial_measure", 1)
    @Test fun g3_tutorial_eq() = ushot("g3_tutorial_eq", 2)
    @Test fun g4_tutorial_verify() = ushot("g4_tutorial_verify", 3)
    @Test fun g5_tutorial_keep() = ushot("g5_tutorial_keep", 4)
    @Test fun g6_tutorial_white() = ushot("g6_tutorial_white", 1, dark = false, look = Look.WHITE)
    @Test fun g7_tutorial_tv() = ushot("g7_tutorial_tv", 5)
    @Test fun g8_tutorial_live() = ushot("g8_tutorial_live", 6)

    @Test fun x1_settings() = settingsShot("x1_settings", dark = true)
    @Test fun x2_settings_light() = settingsShot("x2_settings_light", dark = false)

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
        AutoTuneTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
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

    @Test fun u1_results_summary() = shot("u1_results_summary") { androidx.compose.runtime.CompositionLocalProvider(LocalBack provides {}) { ResultsPage(s.copy(stock = stockA, recommendation = recommendNext(savedRuns, false)), vm, {}, {}) } }
    @Test fun u2_apply() = shot("u2_apply") { tune(s.copy(tuneStep = TuneStep.APPLY, runs = runs3.take(1), eqPick = "suggest:a"))() }
    @Test fun u3_apply_other_eq() = shot("u3_apply_other_eq", dark = false) { tune(s.copy(tuneStep = TuneStep.APPLY, runs = runs3.take(1), eqPick = "run:b:8", eqSource = "LG (3) · Run 8"))() }
    @Test fun u4_home_resume() = shot("u4_home_resume") { tune(s.copy(tuneStep = TuneStep.HOME, resumeStep = TuneStep.APPLY, systemEqOn = true, eqSource = "F-150 driver seat · Suggested EQ"))() }

    private val boomy = curveOf { f -> 26.0 / (1 + (f / 110.0) * (f / 110.0)) + 3 * Math.sin(Math.log(f / 20) * 4) - (if (f > 12000) 12 else 0) }
    private val bigChart = ChartData(grid.freqs, listOf(Curve("Run 1", boomy, 1)), target, eqCurve, "AutoTune EQ", DoubleArray(grid.size) { boomy[it] + eqCurve[it] })
    @Test fun v1_chart_range_and_taste() = shot("v1_chart_range_and_taste") {
        Panel { ResponseChart(bigChart, height = 220.dp) }
        TargetSoundFold(s.copy(targetIdx = 3, bassDb = 4.0, trebleDb = -2.0), vm, startOpen = true)
    }

    @Test fun w1_look_gradient() = shot("w1_look_gradient", look = Look.GRADIENT) { EqTab(s, vm, {}, pick) }
    @Test fun w2_look_black() = shot("w2_look_black", look = Look.BRAUN_BLACK) { EqTab(s, vm, {}, pick) }
    @Test fun w3_look_white() = shot("w3_look_white", dark = false, look = Look.WHITE) { EqTab(s, vm, {}, pick) }
    @Test fun w4_look_graphite() = shot("w4_look_graphite", look = Look.BRAUN_GRAPHITE) { EqTab(s, vm, {}, pick) }
    @Test fun t6b_verify_advanced() = shot("t6b_verify_advanced", adv = true) { tune(s.copy(tuneStep = TuneStep.VERIFY, applyWay = ApplyWay.IN_APP, positions = 1))() }
    @Test fun t6c_verify_phone_mic() = shot("t6c_verify_phone_mic", look = Look.BRAUN_BLACK) { tune(s.copy(tuneStep = TuneStep.VERIFY, applyWay = ApplyWay.IN_APP, positions = 1, phoneMic = true, micName = null, calibrationName = null, outputName = "Phone speaker"))() }
    @Test fun w6_look_white() = shot("w6_look_white", look = Look.WHITE) { tune(s.copy(tuneStep = TuneStep.HOME))() }
    @Test fun w8_white_result() = shot("w8_white_result", dark = false, look = Look.WHITE) { tune(s.copy(tuneStep = TuneStep.RESULT, recommendation = recommendNext(savedRuns.take(2), false), runs = runs3.take(2)))() }
    @Test fun n1_tests_tab() = shot("n1_tests_tab", nav = Tab.TESTS) { TestsTab(s, vm, {}) }
    @Test fun n2_live_tab() = shot("n2_live_tab", nav = Tab.LIVE) { LiveTab(s, null, null, vm, pick, {}) }
    @Test fun w9_accent_green_light() = shot("w9_accent_green_light", dark = false, accent = Accent.GREEN) { tune(s.copy(tuneStep = TuneStep.HOME))() }
    @Test fun w10_accent_yellow_result() = shot("w10_accent_yellow_result", dark = false, accent = Accent.YELLOW) { tune(s.copy(tuneStep = TuneStep.RESULT, recommendation = recommendNext(savedRuns.take(2), false), runs = runs3.take(2)))() }
    @Test fun w11_graphite_light() = shot("w11_graphite_light", dark = false, look = Look.BRAUN_GRAPHITE, nav = Tab.TESTS) { TestsTab(s, vm, {}) }
    @Test fun w12_graphite_dark_home() = shot("w12_graphite_dark_home", look = Look.BRAUN_GRAPHITE) { tune(s.copy(tuneStep = TuneStep.HOME))() }
    @Test fun w13_black_dark_eq() = shot("w13_black_dark_eq", look = Look.BRAUN_BLACK, nav = Tab.EQ) { EqTab(s.copy(systemEqOn = true), vm, {}, pick) }
    @Test fun w14_black_light_live() = shot("w14_black_light_live", dark = false, look = Look.BRAUN_BLACK, nav = Tab.LIVE) { LiveTab(s, null, null, vm, pick, {}) }
    @Test fun i1_icon() = paparazzi.snapshot("i1_icon") {
        androidx.compose.foundation.layout.Box(Modifier.size(216.dp).background(androidx.compose.ui.graphics.Color(0xFFE3E3E0))) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(dev.autotune.R.drawable.ic_launcher_foreground), null, Modifier.size(216.dp))
        }
    }
    @Test fun w5_result_pinned() = shot("w5_result_pinned", look = Look.GRADIENT) { tune(s.copy(tuneStep = TuneStep.RESULT, recommendation = recommendNext(savedRuns.take(2), false), runs = runs3.take(2)))() }
    @Test fun w6_eq_check() = shot("w6_eq_check") {
        val g = grid
        val eqv = DoubleArray(g.size) { eqc.responseDb(g.freqs[it]) - eqc.preampDb }
        val ch = ChartData(g.freqs, listOf(Curve("Measured change", DoubleArray(g.size) { eqv[it] * 0.3 + Math.sin(it / 9.0) * 0.6 }, 1)), DoubleArray(g.size), DoubleArray(g.size) { eqv[it] * 3 }, "What the EQ asks for")
        val big = DoubleArray(g.size) { eqv[it] * 3 }
        EqCheckCard(EqCheckView(judgeEqCheck(g.freqs, DoubleArray(g.size), DoubleArray(g.size) { big[it] * 0.1 }, big), ch, "F-150 · Suggested EQ", "17 Oct 04:40"), vm)
    }
    @Test fun w7_settings_defaults() = paparazzi.snapshot("w7_settings_defaults") {
        AutoTuneTheme(ThemeMode.DARK, Look.BRAUN) {
            androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = At.c.bg, contentColor = At.c.text) {
                SettingsTab(s.copy(defTarget = Target.HARMAN_CAR.name, defBassDb = 2.0, exportFolder = "Documents/AutoTune"), AiUi(), vm, pick)
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

    // ---- 2.2.0: the icon family, the header, the latching key, the slider row's cue
    private fun iconSheet(name: String, dark: Boolean) = paparazzi.also { it.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 2300, screenHeight = 1500)) }.snapshot(name) {
        AutoTuneTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, Look.BRAUN) {
            androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = At.c.bg, contentColor = At.c.text) {
                androidx.compose.foundation.layout.FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ic.entries.forEach { ic ->
                        Column(Modifier.size(width = 76.dp, height = 74.dp).background(At.c.panel).padding(4.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                                AtIcon(ic, At.c.text, size = 32.dp); AtIcon(ic, At.c.text, size = 20.dp)
                            }
                            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { AtIcon(ic, At.c.signal, size = 18.dp); AtIcon(ic, At.c.muted, size = 14.dp) }
                            androidx.compose.material3.Text(ic.name.lowercase(), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = At.c.muted, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
    @Test fun z1_icons_light() = iconSheet("z1_icons_light", false)
    @Test fun z2_icons_dark() = iconSheet("z2_icons_dark", true)

    @Composable private fun Headers() {
        listOf("Tests", "EQ", "Tune", "Live", "Settings", "Export").forEach { ScreenTitle(it, if (it == "EQ") "9 Oct · car" else null) }
    }
    @Test fun z3_headers_light() = paparazzi.also { it.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 5200)) }.snapshot("z3_headers_light") { Frame(false, nav = null) { Headers() } }
    @Test fun z4_headers_dark() = paparazzi.also { it.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 5200)) }.snapshot("z4_headers_dark") { Frame(true, nav = null) { Headers() } }
    @Test fun z5_headers_graphite() = paparazzi.also { it.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 5200)) }.snapshot("z5_headers_graphite") { Frame(false, look = Look.BRAUN_GRAPHITE, nav = null) { Headers() } }
    @Test fun z6_headers_black() = paparazzi.also { it.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 5200)) }.snapshot("z6_headers_black") { Frame(true, look = Look.BRAUN_BLACK, nav = null) { Headers() } }

    /**
     * A phone screen the way the app shell draws it: the content keeps the header's room, and the header folds over it
     * as far as [scroll] says (pixels). [page] adds Back. Paparazzi cannot scroll, so the fold is shown at fixed offsets.
     */
    private fun phoneAt(name: String, dark: Boolean, look: Look = Look.BRAUN, nav: Tab?, scroll: Int, adv: Boolean = false, page: Boolean = false, w: Int = 1080, content: @Composable () -> Unit) =
        paparazzi.also { it.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = w)) }.snapshot(name) {
            AutoTuneTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, look) {
                val pin = androidx.compose.runtime.remember { PinSlot() }
                val title = androidx.compose.runtime.remember { TitleSlot() }
                val sc = androidx.compose.runtime.remember { androidx.compose.foundation.ScrollState(scroll) }
                androidx.compose.runtime.CompositionLocalProvider(LocalPin provides pin, LocalViewMode provides ViewMode(adv) {}, LocalBack provides (if (page) ({}) else null)) {
                    androidx.compose.material3.Surface(color = At.c.bg, contentColor = At.c.text) {
                        Column(Modifier.fillMaxSize().background(At.c.bg)) {
                            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                                androidx.compose.runtime.CompositionLocalProvider(LocalTitleSlot provides title) {
                                    Column(Modifier.fillMaxSize().scrollWithHeaderRoom(sc, headerRangePx()).padding(horizontal = 16.dp).padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
                                }
                                ScreenHeader(title, sc, Modifier.align(androidx.compose.ui.Alignment.TopCenter))
                            }
                            pin.action?.let { BigActionBar(it) }
                            if (nav != null) AtNavBar(nav, busy = false, eqOn = nav == Tab.EQ) {}
                        }
                    }
                }
            }
        }
    private fun phone(name: String, dark: Boolean, look: Look = Look.BRAUN, nav: Tab, tall: Boolean, content: @Composable () -> Unit) =
        phoneAt(name, dark, look, nav, if (tall) 0 else 99_999.coerceAtMost(fold), content = content)
    /** The fold's full range at the Pixel 5's 2.75 density: the header area of 0.27 x 851dp, held to 170..260dp. */
    private val fold = (minOf(260f, maxOf(170f, 851 * 0.27f)) * 2.75f).toInt()

    @Test fun z7_tests_tall() = phone("z7_tests_tall", false, nav = Tab.TESTS, tall = true) { TestsTab(s, vm, {}) }
    @Test fun z8_tests_compact() = phone("z8_tests_compact", false, nav = Tab.TESTS, tall = false) { TestsTab(s, vm, {}) }
    @Test fun z9_eq_tall_dark() = phone("z9_eq_tall_dark", true, nav = Tab.EQ, tall = true) { EqTab(s.copy(systemEqOn = true), vm, {}, pick) }
    @Test fun z10_eq_compact_dark() = phone("z10_eq_compact_dark", true, nav = Tab.EQ, tall = false) { EqTab(s.copy(systemEqOn = true), vm, {}, pick) }
    @Test fun z11_eq_light_full() = shot("z11_eq_light_full", dark = false, nav = Tab.EQ, h = 5000) { EqTab(s, vm, {}, pick) }
    @Test fun z12_eq_black_adv() = shot("z12_eq_black_adv", look = Look.BRAUN_BLACK, adv = true, nav = Tab.EQ, h = 6400) { EqTab(s.copy(systemEqOn = true, flavour = Flavour.ROCK), vm, {}, pick) }

    @Composable private fun Keys() {
        Panel {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { androidx.compose.material3.Text("AutoTune EQ", style = androidx.compose.material3.MaterialTheme.typography.titleLarge); Muted("On") }
                PowerToggle(true, {}, "AutoTune EQ")
            }
            SwitchRow("Keep the last 12 recordings", false, {})
            SwitchRow("Use my wallpaper colours", true, {})
            SwitchRow("Disabled", false, {}, enabled = false)
        }
        Group("Adjust bands") { GraphicSliders(s, vm, GRAPHIC_SETS[2].first, GRAPHIC_SETS[2].second) }
        Group("10 bands") { GraphicSliders(s, vm, GRAPHIC_SETS[1].first, GRAPHIC_SETS[1].second) }
    }
    @Test fun z13_keys_light() = shot("z13_keys_light", dark = false, nav = null, h = 3000) { Keys() }
    @Test fun z14_keys_dark() = shot("z14_keys_dark", nav = null, h = 3000) { Keys() }
    @Test fun z15_keys_graphite() = shot("z15_keys_graphite", dark = false, look = Look.BRAUN_GRAPHITE, nav = null, h = 3000) { Keys() }
    @Test fun z16_keys_black() = shot("z16_keys_black", look = Look.BRAUN_BLACK, nav = null, h = 3000) { Keys() }
    @Test fun z17_dynamic() = shot("z17_dynamic", dark = false, nav = null, h = 3600) { LoudEqFold(s.copy(systemEqOn = true, loudMode = LoudMode.LEVEL, loudLevelDb = 86.5), vm, startOpen = true) }
    @Test fun z18_flavours() = shot("z18_flavours", dark = false, adv = true, nav = null, h = 3600) {
        Panel { FlavourAndPreamp(s.copy(flavour = Flavour.ELECTRONIC), vm) }
        Panel { Flavour.entries.forEach { f -> androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { AtIcon(flavourIcon(f), At.c.signal, size = 20.dp); androidx.compose.material3.Text("  " + f.title) } } }
        TargetSoundFold(s.copy(bassDb = 2.0), vm, startOpen = true)
    }

    // ---- 2.3.0: the header folds into the slim bar; the mode key in the middle; Export in Settings and on EQ; knob and gear
    @Test fun h1_fold_0() = phoneAt("h1_fold_0", false, nav = Tab.TESTS, scroll = 0) { TestsTab(s, vm, {}) }
    @Test fun h2_fold_25() = phoneAt("h2_fold_25", false, nav = Tab.TESTS, scroll = fold / 4) { TestsTab(s, vm, {}) }
    @Test fun h3_fold_50() = phoneAt("h3_fold_50", false, nav = Tab.TESTS, scroll = fold / 2) { TestsTab(s, vm, {}) }
    @Test fun h4_fold_75() = phoneAt("h4_fold_75", false, nav = Tab.TESTS, scroll = fold * 3 / 4) { TestsTab(s, vm, {}) }
    @Test fun h5_fold_100() = phoneAt("h5_fold_100", false, nav = Tab.TESTS, scroll = fold) { TestsTab(s, vm, {}) }
    @Test fun h6_fold_far() = phoneAt("h6_fold_far", false, nav = Tab.TESTS, scroll = fold + 600) { TestsTab(s, vm, {}) }
    @Test fun h7_eq_dark_0() = phoneAt("h7_eq_dark_0", true, nav = Tab.EQ, scroll = 0) { EqTab(s.copy(systemEqOn = true), vm, {}, pick) }
    @Test fun h8_eq_dark_50() = phoneAt("h8_eq_dark_50", true, nav = Tab.EQ, scroll = fold / 2) { EqTab(s.copy(systemEqOn = true), vm, {}, pick) }
    @Test fun h9_eq_dark_100() = phoneAt("h9_eq_dark_100", true, nav = Tab.EQ, scroll = fold, adv = true) { EqTab(s.copy(systemEqOn = true), vm, {}, pick) }
    @Test fun h10_tune_graphite_0() = phoneAt("h10_tune_graphite_0", false, look = Look.BRAUN_GRAPHITE, nav = Tab.MEASURE, scroll = 0) { tune(s.copy(tuneStep = TuneStep.HOME))() }
    @Test fun h11_tune_graphite_60() = phoneAt("h11_tune_graphite_60", false, look = Look.BRAUN_GRAPHITE, nav = Tab.MEASURE, scroll = fold * 3 / 5) { tune(s.copy(tuneStep = TuneStep.HOME))() }
    @Test fun h12_live_black_0() = phoneAt("h12_live_black_0", true, look = Look.BRAUN_BLACK, nav = Tab.LIVE, scroll = 0) { LiveTab(s, null, null, vm, pick, {}) }
    @Test fun h13_live_black_100() = phoneAt("h13_live_black_100", true, look = Look.BRAUN_BLACK, nav = Tab.LIVE, scroll = fold) { LiveTab(s, null, null, vm, pick, {}) }
    @Test fun h14_settings_0() = phoneAt("h14_settings_0", false, nav = Tab.SETTINGS, scroll = 0) { SettingsTab(s, AiUi(), vm, pick) }
    @Test fun h15_settings_100_adv() = phoneAt("h15_settings_100_adv", false, nav = Tab.SETTINGS, scroll = fold, adv = true) { SettingsTab(s, AiUi(), vm, pick) }
    @Test fun h16_results_page_0() = phoneAt("h16_results_page_0", false, nav = null, scroll = 0, page = true) { ResultsPage(s, vm, {}, {}) }
    @Test fun h17_results_page_100() = phoneAt("h17_results_page_100", false, nav = null, scroll = fold, page = true) { ResultsPage(s, vm, {}, {}) }
    @Test fun h18_export_page_100() = phoneAt("h18_export_page_100", true, nav = null, scroll = fold, page = true) { ExportTab(s, vm, {}) }
    // 360dp wide (the narrowest the owner's phones go), and a large font
    @Test fun h19_narrow_0() = phoneAt("h19_narrow_0", false, nav = Tab.SETTINGS, scroll = 0, w = 990) { SettingsTab(s, AiUi(), vm, pick) }
    @Test fun h20_narrow_100_adv() = phoneAt("h20_narrow_100_adv", false, nav = Tab.SETTINGS, scroll = fold, adv = true, w = 990) { SettingsTab(s, AiUi(), vm, pick) }
    @Test fun h21_narrow_page_100() = phoneAt("h21_narrow_page_100", false, nav = null, scroll = fold, page = true, w = 990) { ResultsPage(s, vm, {}, {}) }
    @Test fun h22_bigfont_0() { paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 990, fontScale = 1.6f)); paparazzi.snapshot("h22_bigfont_0") { BigFontShell(0) } }
    @Test fun h23_bigfont_100() { paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 990, fontScale = 1.6f)); paparazzi.snapshot("h23_bigfont_100") { BigFontShell(fold) } }
    @Composable private fun BigFontShell(scroll: Int) {
        AutoTuneTheme(ThemeMode.LIGHT, Look.BRAUN) {
            val title = androidx.compose.runtime.remember { TitleSlot() }
            val sc = androidx.compose.runtime.remember { androidx.compose.foundation.ScrollState(scroll) }
            androidx.compose.runtime.CompositionLocalProvider(LocalPin provides androidx.compose.runtime.remember { PinSlot() }, LocalViewMode provides ViewMode(true) {}) {
                androidx.compose.material3.Surface(color = At.c.bg, contentColor = At.c.text) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                        androidx.compose.runtime.CompositionLocalProvider(LocalTitleSlot provides title) {
                            Column(Modifier.fillMaxSize().scrollWithHeaderRoom(sc, headerRangePx()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { SettingsTab(s, AiUi(), vm, pick) }
                        }
                        ScreenHeader(title, sc, Modifier.align(androidx.compose.ui.Alignment.TopCenter))
                    }
                }
            }
        }
    }

    @Composable private fun KnobAndGear() {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(Ic.TUNE, Ic.GEAR).forEach { ic ->
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    listOf(72, 32, 26, 20, 16).forEach { sz -> AtIcon(ic, At.c.text, size = sz.dp) }
                    // the Tune key in the tab row, and the main key's icon
                    androidx.compose.foundation.layout.Box(Modifier.size(50.dp).androidxClipCircle().background(At.c.signal), contentAlignment = androidx.compose.ui.Alignment.Center) { AtIcon(ic, At.c.onSignal, size = 26.dp) }
                    androidx.compose.foundation.layout.Box(Modifier.size(48.dp).androidxClipCircle().background(At.c.text), contentAlignment = androidx.compose.ui.Alignment.Center) { AtIcon(ic, At.c.signal, size = 26.dp) }
                }
            }
            AtNavBar(Tab.MEASURE, busy = false) {}
            AtNavBar(Tab.SETTINGS, busy = false) {}
            Actions { PrimaryButton("Start tuning", {}, icon = Ic.TUNE); TextAction("Continue tuning", {}, icon = Ic.TUNE) }
            // the launcher icon on its background, the themed icon, the status bar icon
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(Modifier.size(108.dp).androidxClipCircle().background(androidx.compose.ui.graphics.Color(0xFFE3E3E0))) {
                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
                }
                androidx.compose.foundation.layout.Box(Modifier.size(108.dp).androidxClipCircle().background(androidx.compose.ui.graphics.Color(0xFF3B4A5A))) {
                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_monochrome), null, Modifier.fillMaxSize(), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(androidx.compose.ui.graphics.Color(0xFFD5E3F5)))
                }
                androidx.compose.foundation.layout.Box(Modifier.size(width = 120.dp, height = 40.dp).background(androidx.compose.ui.graphics.Color(0xFF202124)), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(24, 18).forEach { sz -> androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_stat_autotune), null, Modifier.size(sz.dp)) }
                    }
                }
            }
        }
    }
    @Test fun k1_knob_gear_light() = shot("k1_knob_gear_light", dark = false, nav = null, h = 2600) { KnobAndGear() }
    @Test fun k2_knob_gear_dark() = shot("k2_knob_gear_dark", nav = null, h = 2600) { KnobAndGear() }
    @Test fun k3_knob_gear_black() = shot("k3_knob_gear_black", look = Look.BRAUN_BLACK, nav = null, h = 2600) { KnobAndGear() }
    private fun Modifier.androidxClipCircle(): Modifier = this.clip(androidx.compose.foundation.shape.CircleShape)
}
