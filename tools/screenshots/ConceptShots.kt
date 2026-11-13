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

/** The Concept look (2.29.0) on the main screens, by day and by night, with the tab row and the pinned key. */
class ConceptShots {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.NoActionBar", showSystemUi = false)

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

    @Composable private fun Frame(dark: Boolean, look: Look = Look.CONCEPT, nav: Tab = Tab.MEASURE, content: @Composable () -> Unit) {
        AutoTuneTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, look) {
            val slot = androidx.compose.runtime.remember { PinSlot() }
            // inspection mode draws every screen at rest (animationsOn() is false), so nothing is caught mid-arrival
            androidx.compose.runtime.CompositionLocalProvider(LocalPin provides slot, androidx.compose.ui.platform.LocalInspectionMode provides true) {
                androidx.compose.material3.Surface(color = At.c.bg, contentColor = At.c.text) {
                    Column(Modifier.fillMaxSize().background(At.c.bg)) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
                        slot.action?.let { BigActionBar(it) }
                        AtNavBar(nav, busy = false, eqOn = true) {}
                    }
                }
            }
        }
    }
    private fun shot(name: String, dark: Boolean = false, nav: Tab = Tab.MEASURE, content: @Composable () -> Unit) {
        paparazzi.snapshot(name) { Frame(dark, Look.CONCEPT, nav, content) }
    }
    private fun tune(st: UiState) = @Composable { TuneTab(st, vm, sharer, {}, {}, pick) }

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
        paparazzi.snapshot(name) { AutoTuneTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, Look.CONCEPT) { MeasureOverlay(mv, s.copy(busy = busy), vm) } }
    }
    // the settings overlay fills the screen itself, so it gets a plain surface rather than the scrolling frame
    private fun settingsShot(name: String, dark: Boolean) = paparazzi.also {
        // long page: a taller canvas so Appearance and About are in the picture
        it.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 6400))
    }.snapshot(name) {
        AutoTuneTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, Look.CONCEPT) {
            androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = At.c.bg, contentColor = At.c.text) {
                SettingsTab(s, AiUi(hasKey = true, keyMasked = "sk-ant-…a1F9", keyMessage = "The key works with Claude Sonnet."), vm, pick)
            }
        }
    }

    @Test fun c01_home() = shot("c01_home") { tune(s.copy(tuneStep = TuneStep.HOME))() }
    @Test fun c02_setup() = shot("c02_setup") { tune(s.copy(tuneStep = TuneStep.SETUP))() }
    @Test fun c03_result() = shot("c03_result") { tune(s.copy(tuneStep = TuneStep.RESULT, resultWarnings = listOf("Low signal-to-noise (31 dB) - raise the volume or reduce background noise.")))() }
    @Test fun c04_eq() = shot("c04_eq", nav = Tab.EQ) { EqTab(s.copy(systemEqOn = true), vm, {}, pick) }
    @Test fun c05_tests() = shot("c05_tests", nav = Tab.TESTS) { TestsTab(s, vm, {}) }
    @Test fun c06_results() = shot("c06_results", nav = Tab.TESTS) { androidx.compose.runtime.CompositionLocalProvider(LocalBack provides {}) { ResultsPage(s, vm, {}, {}) } }
    @Test fun c07_measuring() = mshot("c07_measuring", MeasureView("Run 3", MeasureStage.MEASURING, "", checksDone.take(3) + checksWait.drop(3), 1, 1, true), true, dark = false)
    @Test fun c08_settings() = settingsShot("c08_settings", dark = false)
    @Test fun c11_home_night() = shot("c11_home_night", dark = true) { tune(s.copy(tuneStep = TuneStep.HOME))() }
    @Test fun c13_result_night() = shot("c13_result_night", dark = true) { tune(s.copy(tuneStep = TuneStep.RESULT))() }
    @Test fun c14_eq_night() = shot("c14_eq_night", dark = true, nav = Tab.EQ) { EqTab(s.copy(systemEqOn = true), vm, {}, pick) }
}
