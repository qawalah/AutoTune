package dev.autotune

import android.app.Application
import android.content.ContentValues
import android.content.Intent
import androidx.core.content.ContextCompat
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.autotune.audio.Clip
import dev.autotune.audio.ClipDecoder
import dev.autotune.audio.ClipPlayer
import dev.autotune.audio.MeasurementRunner
import dev.autotune.audio.OutChannel
import dev.autotune.audio.ProfileController
import dev.autotune.audio.Outputs
import dev.autotune.dsp.LevelCheck
import dev.autotune.dsp.Marker
import dev.autotune.audio.RtaRunner
import dev.autotune.audio.SystemEq
import dev.autotune.audio.SystemEqService
import dev.autotune.audio.UsbMic
import dev.autotune.ai.AiClient
import dev.autotune.ai.AiEq
import dev.autotune.ai.AiEqCheck
import dev.autotune.ai.AiModel
import dev.autotune.ai.AiProvider
import dev.autotune.ai.AiPrompt
import dev.autotune.ai.AiReview
import dev.autotune.ai.AiTurn
import dev.autotune.ai.KeyVault
import kotlin.math.max
import kotlin.math.min
import dev.autotune.dsp.Analyzer
import dev.autotune.dsp.Averaging
import dev.autotune.dsp.CombinedAnalyzer
import dev.autotune.dsp.Headroom
import dev.autotune.dsp.LeftRight
import dev.autotune.dsp.LrResult
import dev.autotune.dsp.SubHelper
import dev.autotune.dsp.SubResult
import dev.autotune.dsp.SystemJudge
import dev.autotune.dsp.SystemReport
import dev.autotune.dsp.Calibration
import dev.autotune.dsp.Describe
import dev.autotune.dsp.EqChain
import dev.autotune.dsp.EqFilter
import dev.autotune.dsp.EqFitter
import dev.autotune.dsp.EqProcessor
import dev.autotune.dsp.Export
import dev.autotune.dsp.FitConfig
import dev.autotune.dsp.FreqGrid
import dev.autotune.dsp.Measurement
import dev.autotune.dsp.NoiseAnalyzer
import dev.autotune.dsp.Region
import dev.autotune.dsp.Resonance
import dev.autotune.dsp.ResonanceDetector
import dev.autotune.dsp.Rta
import dev.autotune.dsp.Sweep
import dev.autotune.dsp.SweepTrim
import dev.autotune.dsp.Target
import dev.autotune.dsp.TestSignals
import dev.autotune.dsp.Wav
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt


/** Where the test sound is played from. The first value is "connected to my phone"; the others are "not connected". */
enum class Source(val title: String, val desc: String) {
    PHONE("Connected to my phone", "The phone plays the test sound into the system (Bluetooth, aux or USB) and listens."),
    TEST_FILE("Not connected: sweep file", "Play our sweep file on another device. Accurate, with ringing times."),
    YOUTUBE("Not connected: pink noise", "Play any pink noise on another device. Quickest, tone curve only."),
    COMBINED("Not connected: pink noise + sweep", "One file with both. Most reliable: the two readings cross-check each other."),
}

enum class ThemeMode(val title: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** Where the open test is in the tuning loop. The Tune tab always shows the one thing to do next. */
enum class LiveSignal { PINK, SWEEP, NONE }
/** Where the Tune workflow is: the big button, the fresh-start question, set-up, stock reading, apply, verify, result, done. */
enum class TuneStep { HOME, FRESH, SETUP, STOCK, STOCK_RESULT, APPLY, VERIFY, RESULT, DONE }

/** How the EQ is applied for the next run. */
/** How the EQ reached the mic for the next run. NONE = nothing changed since the previous run (a repeat). */
enum class ApplyWay { AUTOTUNE_EQ, EQUIPMENT, IN_APP, NONE }

enum class TestFileKind { SWEEP, PINK, COMBINED, SWEEP_LEFT, SWEEP_RIGHT }

const val YOUTUBE_SEARCH = "https://www.youtube.com/results?search_query=pink+noise"

/** Files for the share sheet (test files for the other device, or exported EQ files). */
class ShareFiles(val files: List<File>, val mime: String, val title: String)

/** One response line. [colorIdx] is the run's index, so a run keeps its colour in every chart. */
class Curve(val label: String, val values: DoubleArray, val colorIdx: Int)

class ChartData(
    val freqs: DoubleArray,
    val curves: List<Curve>,
    val target: DoubleArray,
    /** The EQ AutoTune suggests (computed from the focus run). Not measured, not applied to anything. */
    val eq: DoubleArray?,
    val eqLabel: String,
    /** Focus run plus the EQ, i.e. what the response should become once the EQ is applied (a prediction). */
    val predicted: DoubleArray? = null,
)

/** 1/3-octave RTA bars from run measurements, relative to the target level. */
class BarRun(val label: String, val colorIdx: Int, val bars: DoubleArray)
class BarsData(val runs: List<BarRun>, val target: DoubleArray)

/** Table comparing the ticked runs. [regionErr] has one row per region, one column per run. */
class Compare(
    val labels: List<String>,
    val colorIdx: List<Int>,
    val rms: List<Double>,
    val regionNames: List<String>,
    val regionErr: List<List<Double>>,
    /** System score (0-100) of each run. */
    val scores: List<Int>,
    val verdict: String,
    /** The latest run is about the same as the first although an EQ should have changed it. */
    val eqMayNotBeApplied: Boolean = false,
)

/** A run as shown in the run list. */
data class RunInfo(
    val index: Int,
    val label: String,
    val whenText: String,
    val rmsDb: Double,
    val note: String,
    val appliedBands: Int,
    /** Calibration file used for this run ("none", or "unknown" for runs saved before it was recorded). */
    val calibration: String = UNKNOWN_CAL,
    /** No better EQ was found after this run. */
    val converged: Boolean = false,
)

/** Left/right check result for display. */
class LrInfo(val result: LrResult, val chart: ChartData)

/** Broadband level from the live meter. [splDb] is an estimate (see the live panel). */
class LevelInfo(val rmsDb: Double, val peakDb: Double, val leqDb: Double, val splDb: Double)

/** A paired Bluetooth device, with enough detail to tell identical names apart. */
data class BtDevice(val address: String, val name: String, val kind: String, val lastSeen: Long)

/** A saved test as shown in the list on the first page. */
/** Stage of the full-screen measuring view. */
enum class MeasureStage { MEASURING, ANALYSING, SPOT_DONE, SAVED, FAILED }

/** One check on the measuring screen. state: 0 waiting, 1 fine, 2 worth a look, 3 failed. */
data class CheckItem(val label: String, val state: Int, val detail: String? = null)

/** The full-screen measuring view: what is happening, the checks, and the result. */
data class MeasureView(
    val title: String,
    val stage: MeasureStage,
    val message: String,
    val checks: List<CheckItem>,
    val spot: Int,
    val spots: Int,
    /** The phone plays the sweep (its timing is known); otherwise another device plays it. */
    val phoneRoute: Boolean,
    val result: String? = null,
)

/** What kind of system a test tunes; picks its icon. */
enum class TargetKind { CAR, HOME, FLAT, CUSTOM, HAND }

fun targetKind(t: Target?): TargetKind = when {
    t == null -> TargetKind.HAND
    t === Target.CAR || t === Target.HARMAN_CAR -> TargetKind.CAR
    t === Target.HOME_THEATER || t === Target.HARMAN_ROOM -> TargetKind.HOME
    t === Target.FLAT -> TargetKind.FLAT
    else -> TargetKind.CUSTOM
}

/** The EQ check on screen: the verdict and a graph of the measured change against what the EQ should do. */
data class EqCheckView(val check: EqCheck, val chart: ChartData, val eqName: String, val whenText: String)

/** One EQ the user can put in use: a test's next suggestion, the EQ of one of its runs, or a saved EQ. */
data class EqChoice(
    val key: String, val group: String, val kind: TargetKind, val title: String, val detail: String?,
    /** Shown straight away; secondary choices wait behind "Show more" in their group. */
    val primary: Boolean = true,
)

data class TestSummary(
    val id: String,
    val name: String,
    val target: String,
    val kind: TargetKind = TargetKind.CUSTOM,
    val source: String,
    val runCount: Int,
    val stockRms: Double?,
    val latestRms: Double?,
    val whenText: String,
)

/** The AI assistant: its settings, the review on screen and the follow-up conversation. */
data class AiUi(
    val hasKey: Boolean = false,
    val keyMasked: String? = null,
    val provider: dev.autotune.ai.AiProvider = dev.autotune.ai.AiProvider.CLAUDE,
    val model: dev.autotune.ai.AiModel = dev.autotune.ai.AiModel.SONNET,
    /** Result of saving or testing the key. */
    val keyMessage: String? = null,
    val testingKey: Boolean = false,
    /** The review screen is open. */
    val open: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val review: dev.autotune.ai.AiReview? = null,
    /** The review's EQ changes after the app's checks, with the predicted result. */
    val eq: dev.autotune.ai.AiEq? = null,
    /** The review is older than the test (runs were added since). */
    val stale: Boolean = false,
    val testName: String? = null,
    val chat: List<dev.autotune.ai.AiTurn> = emptyList(),
    val asking: Boolean = false,
    val message: String? = null,
)

data class UiState(
    val hasPermission: Boolean = false,
    val micName: String? = null,
    /** No USB mic: the phone's own microphone is used. Fine for checks, less accurate for tuning. */
    val phoneMic: Boolean = false,
    val liveSignal: LiveSignal = LiveSignal.PINK,
    val calibrationName: String? = null,
    val calibrationInfo: String? = null,
    /** Warnings of the focus run (and about mixed calibrations) shown at the top of Results. */
    val resultWarnings: List<String> = emptyList(),
    /** Shown on Results above the suggestion when the EQ seems to have had no effect. */
    val eqMayNotBeApplied: Boolean = false,
    /** Slider changes on top of the tuned EQ (a delta layer: the tuned bands stay intact). */
    val sliderLayer: List<EqFilter> = emptyList(),
    /** Result of the last "Convert to N bands" action. */
    val convertMessage: String? = null,
    val source: Source = Source.PHONE,
    val targetIdx: Int = 0,
    val channel: OutChannel = OutChannel.BOTH,
    val gateMs: Int = 80,
    // --- the test being worked on
    val testName: String = "",
    val currentTestId: String? = null,
    val tests: List<TestSummary> = emptyList(),
    val runs: List<RunInfo> = emptyList(),
    /** Runs drawn in the charts. Recommendations and export use the latest ticked run. */
    val selectedRuns: Set<Int> = emptySet(),
    /** What the user changed before the next run (saved with that run). */
    val runNote: String = "",
    /** Mic positions averaged per run (1, 3 or 5), and how many are already measured for the run in progress. */
    val positions: Int = 1,
    val positionsDone: Int = 0,
    val testNotes: String = "",
    val targetNames: List<String> = emptyList(),
    val customTargets: List<String> = emptyList(),
    // --- progress of the current action
    val busy: Boolean = false,
    /** 0..1 while a timed recording is running; null when unknown. */
    val progress: Float? = null,
    /** Graph for the EQ tab: focus run, the EQ curve and the predicted result. */
    val eqChart: ChartData? = null,
    /** Message about the last action on this page; cleared whenever the page changes. */
    val status: String? = null,
    val warnings: List<String> = emptyList(),
    /** Step of the Tune workflow. */
    val tuneStep: TuneStep = TuneStep.HOME,
    /** The stock run analysed in plain words (the Stock analysis step). */
    val stock: StockAnalysis? = null,
    /** After a run with the EQ: the one recommended next move. */
    val recommendation: Recommendation? = null,
    /** Defaults for new tests (Settings): target by name (null = Car), taste, mic spots and window (0 = automatic). */
    val defTarget: String? = null,
    val defBassDb: Double = 0.0,
    val defTrebleDb: Double = 0.0,
    val defSpots: Int = 0,
    val defGateMs: Int = 0,
    /** Where saved files go: a folder the user picked (shown by name), or null for Downloads/AutoTune. */
    val exportFolder: String? = null,
    /** What plays on top of the EQ in use: a flavour, and how the preamp keeps boosts from clipping. */
    val flavour: Flavour = Flavour.NONE,
    val preampMode: PreampMode = PreampMode.SAFE,
    val manualPreampDb: Double = -6.0,
    /** The preamp AutoTune EQ is playing with now, dB. */
    val playPreampDb: Double? = null,
    /** AutoTune EQ built from 61 bands (1/6 octave) instead of 31. */
    val systemEqFine: Boolean = false,
    /** How the sound followed the latest run's EQ, octave by octave. */
    val eqFollow: EqFollow? = null,
    /** Further tests worth doing, from what the readings show. */
    val checkSuggestions: List<CheckSuggestion> = emptyList(),
    /** The last EQ check: did the EQ reach the speakers (with the measured change against the EQ). */
    val eqCheck: EqCheckView? = null,
    /** Taste on top of the open test's target: bass and treble shelves in dB. */
    val bassDb: Double = 0.0,
    val trebleDb: Double = 0.0,
    /** Where Continue picks up the open test: the step it was left on (Exit keeps the place). */
    val resumeStep: TuneStep? = null,
    /** How the EQ reaches the speakers for the next run (AutoTune EQ, equipment or inside the app). */
    val applyWay: ApplyWay = ApplyWay.EQUIPMENT,
    /** The next run repeats the previous one with nothing changed. */
    val repeatNext: Boolean = false,
    /** Where the EQ in effect comes from, in words ("LG · Run 8", "LG · Suggested EQ", "Your own bands"). */
    val eqSource: String = "",
    /** Key of the EQ in use (see [EqChoice.key]): "suggest:<test>", "run:<test>:<i>", "profile:<id>", "own", or "". */
    val eqPick: String = "",
    /** Every EQ that can be put in use, grouped by test. */
    val eqChoices: List<EqChoice> = emptyList(),
    /** Overall level change of the EQ in effect, 100 Hz-5 kHz average, preamp included (dB). */
    val eqLevelDb: Double? = null,
    /** Keep the last recordings for the diagnostics export. */
    val keepRecordings: Boolean = true,
    /** Full-screen measuring view; null when closed. */
    val measureView: MeasureView? = null,
    /** Warnings from the last saved measurement (kept until the next one). */
    val lastWarnings: List<String> = emptyList(),
    /** Places the phone can play to (id, name), the user's choice (null = automatic) and the one actually used. */
    val outputs: List<Pair<Int, String>> = emptyList(),
    val outputChoice: Int? = null,
    val outputName: String? = null,
    /** The USB mic also registers as headphones: other sound may be routed into it. */
    val micAlsoOutput: Boolean = false,
    /** The sound is (or was) going into the USB mic's own headphone output. */
    val outputToMic: Boolean = false,
    /** Another app (screen recorder, cast) is capturing the phone's sound. */
    val audioCaptured: Boolean = false,
    /** AutoTune EQ (the system effect) damaged a measurement on this phone before. */
    val systemEqSuspect: Boolean = false,
    /** Saved EQs, the one in use, and what switches them automatically. */
    val profiles: List<EqProfile> = emptyList(),
    val activeProfileId: String? = null,
    val rules: List<AutoRule> = emptyList(),
    val btDevices: List<BtDevice> = emptyList(),
    /** The EQ in effect when the focus run was measured: what the suggestion is compared with. */
    val previousChain: EqChain? = null,
    val btPermission: Boolean = false,
    val automationOn: Boolean = false,
    val offOnDisconnect: Boolean = true,
    val showTargetEq: Boolean = false,
    val showTargetResults: Boolean = true,
    /** REW-style frequency-dependent window in cycles; 0 keeps the fixed gate. */
    val fdwCycles: Int = 0,
    /** Smoothing of the plotted response: 1/N octave, 0 = none. */
    val smoothing: Int = 6,
    /** Harmonic distortion of the latest sweep. */
    val distortion: dev.autotune.dsp.Distortion? = null,
    /** Result of the last level check, kept on screen. */
    val levelResult: String? = null,
    // --- results of the current test
    val chart: ChartData? = null,
    val bars: BarsData? = null,
    val rmsDb: Double? = null,
    val log: List<String> = emptyList(),
    /** The EQ in effect: the tuned (or own) bands plus slider changes. What is applied, previewed, exported and saved. */
    val exportChain: EqChain? = null,
    /** AutoTune's complete suggestion for the focus run, before the user's own changes. */
    val suggestedChain: EqChain? = null,
    val regions: List<Region> = emptyList(),
    val resonances: List<Resonance> = emptyList(),
    val compare: Compare? = null,
    val report: SystemReport? = null,
    val exportWarnings: List<String> = emptyList(),
    val lr: LrInfo? = null,
    val sub: SubResult? = null,
    val subDone: Set<String> = emptySet(),
    // --- listening / RTA / export
    val clipName: String = "Pink noise",
    val playing: Boolean = false,
    val playingWithEq: Boolean = true,
    val rtaRunning: Boolean = false,
    val rtaWithEq: Boolean = false,
    /** Phone route: play pink noise from the phone while the live RTA runs. Off = just listen (music, other device). */
    val rtaNoise: Boolean = true,
    val showBands: Boolean = false,
    val bandSetIdx: Int = 2,
    val splOffsetDb: Double = 0.0,
    // --- AutoTune as the EQ
    /** The user's own band list while editing (null = use the suggested EQ). */
    val editFilters: List<EqFilter>? = null,
    val systemEqOn: Boolean = false,
    val systemEqMessage: String? = null,
    // --- appearance
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** The app's look (colours and slider style). */
    val look: dev.autotune.ui.Look = dev.autotune.ui.Look.STUDIO,
    val dynamicColor: Boolean = false,
)

class TuneViewModel(app: Application) : AndroidViewModel(app) {
    val state = MutableStateFlow(UiState())

    /** The AI assistant's own state (kept apart so the review screen does not redraw the whole app). */
    val ai = MutableStateFlow(AiUi())

    /** Live RTA bars (dB relative to the target level); separate flow so the whole screen is not redrawn at 10 fps. */
    val rtaBars = MutableStateFlow<DoubleArray?>(null)

    /** Live sound level (updated about 20 times a second while the live meter runs). */
    val level = MutableStateFlow<LevelInfo?>(null)

    /** Files ready for the share sheet. */
    val fileToShare = MutableSharedFlow<ShareFiles>(extraBufferCapacity = 1)

    /** Fires when a reading was measured and saved, so the screen can open Results. */
    val runDone = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val fs = 48000
    private val grid = FreqGrid()
    private var calibration: Calibration? = null
    private var job: Job? = null
    private val player = ClipPlayer()
    private val rta = RtaRunner()
    private var clip: Clip = pinkClip()
    private val prefs = app.getSharedPreferences("autotune_prefs", android.content.Context.MODE_PRIVATE)

    private var savedTests: List<SavedTest> = emptyList()
    /** The saved test being worked on; null until a new test has its stock run. */
    private var current: SavedTest? = null

    // measurement in progress at several mic positions
    private val pendingPositions = ArrayList<Measurement>()
    private var lrLeft: Measurement? = null
    private var lrRight: Measurement? = null
    private var lrResult: LrResult? = null
    private val subMeasurements = HashMap<String, Measurement>()
    private var leqEnergy = 0.0
    private var leqBlocks = 0

    init {
        Target.ALL = Target.BUILT_IN + TargetStore.load(app)
        aiInit()
        watchOutside()
        refreshMic()
        refreshProfiles()
        state.update {
            it.copy(
                targetNames = Target.ALL.map { t -> t.name },
                customTargets = Target.ALL.drop(Target.BUILT_IN.size).map { t -> t.name },
                splOffsetDb = prefs.getFloat("splOffset", 0f).toDouble(),
                themeMode = runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
                dynamicColor = prefs.getBoolean("dynamic", false),
                look = dev.autotune.ui.Look.of(prefs.getString("look", null)),
                flavour = Flavour.of(prefs.getString("flavour", null)),
                preampMode = PreampMode.of(prefs.getString("preampMode", null)),
                manualPreampDb = prefs.getFloat("preampManual", -6f).toDouble(),
                systemEqFine = prefs.getBoolean("eqFine", false).also { SystemEqConfig.fine = it },
                systemEqSuspect = prefs.getBoolean("systemEqSuspect", false),
                automationOn = prefs.getBoolean("automationOn", false),
                offOnDisconnect = prefs.getBoolean("offOnDisconnect", true),
                showTargetEq = prefs.getBoolean("showTargetEq", false),
                showTargetResults = prefs.getBoolean("showTargetResults", true),
                fdwCycles = prefs.getInt("fdwCycles", 0),
                keepRecordings = prefs.getBoolean("keepRecordings", true),
                smoothing = prefs.getInt("smoothing", 6),
                defTarget = prefs.getString("defTarget", null),
                defBassDb = prefs.getFloat("defBass", 0f).toDouble(),
                defTrebleDb = prefs.getFloat("defTreble", 0f).toDouble(),
                defSpots = prefs.getInt("defSpots", 0),
                defGateMs = prefs.getInt("defGate", 0),
                exportFolder = prefs.getString("exportTree", null)?.let { folderName(Uri.parse(it)) },
            )
        }
        MeasurementRunner.appContext = app.applicationContext
        loadCalibration()
        TestStore.migrateLegacy(app, Target.ALL.map { it.name })
        refreshTests()
        cleanAutoProfiles()
        loadSliderLayer()?.let { l -> state.update { it.copy(sliderLayer = l) } }
        val savedPick = prefs.getString("eqPick", null)
        loadMyEq()?.let { mine -> setEdit(mine, savedPick ?: "own") } ?: rebuildActive()
    }

    private var lastRecordingSeconds = 0

    private val calibrationFile get() = File(getApplication<Application>().filesDir, "calibration.txt")

    /** Name used for the calibration in saved runs ("none" without one). */
    private fun calName(): String = calibrationLabel(state.value.calibrationName.takeIf { calibration != null })

    private fun loadCalibration() {
        val f = calibrationFile
        if (!f.exists()) return
        runCatching {
            val cal = Calibration.parse(f.readText())
            calibration = cal
            val sens = cal.sensFactorDb?.let { ", sens %.2f dB".format(it) } ?: ""
            state.update { it.copy(calibrationName = prefs.getString("calibrationName", "calibration.txt"), calibrationInfo = "${cal.pointCount} points$sens") }
        }.onFailure { f.delete() }
    }

    fun removeCalibration() {
        calibration = null
        calibrationFile.delete()
        prefs.edit().remove("calibrationName").apply()
        state.update { it.copy(calibrationName = null, calibrationInfo = null, status = "Calibration removed. New runs are measured without it.") }
    }

    private fun pinkClip(): Clip {
        val mono = Sweep.pinkNoise(fs, 10.0)
        val st = FloatArray(mono.size * 2)
        for (i in mono.indices) { st[2 * i] = mono[i]; st[2 * i + 1] = mono[i] }
        return Clip("Pink noise", st, fs)
    }

    // ---------------------------------------------------------------- appearance

    fun setThemeMode(m: ThemeMode) {
        prefs.edit().putString("theme", m.name).apply()
        state.update { it.copy(themeMode = m) }
    }

    fun setDynamicColor(on: Boolean) {
        prefs.edit().putBoolean("dynamic", on).apply()
        state.update { it.copy(dynamicColor = on) }
    }

    // ---------------------------------------------------------------- tests (named, saved)

    private fun refreshTests() {
        savedTests = TestStore.list(getApplication())
        state.update { it.copy(tests = savedTests.map(::summary)) }
        rebuildChoices()
    }

    // ---------------------------------------------------------------- which EQ is in use

    private fun setPick(key: String) {
        prefs.edit().putString("eqPick", key).apply()
        state.update { it.copy(eqPick = key) }
        rebuildChoices()
    }

    /** Every EQ that can be put in use, grouped by test (newest first), then the saved EQs that belong to no test. */
    /**
     * Every EQ that can be put in use, grouped by test (the open test first). In each group the useful few come first
     * (the suggested EQ, the best run, the kept EQ); the rest are marked secondary and stay folded in the picker.
     */
    private fun rebuildChoices() {
        val s = state.value
        val out = ArrayList<EqChoice>()
        val names = savedTests.map { it.name }.toSet()
        val ordered = savedTests.filter { it.runs.isNotEmpty() }.sortedByDescending { it.id == current?.id }
        ordered.forEach { t ->
            val kind = targetKind(Target.ALL.getOrNull(t.targetIdx))
            val withEq = t.runs.indices.drop(1).filter { t.runs[it].eq.isNotEmpty() }
            val best = withEq.minByOrNull { t.runs[it].rmsDb }
            val last = t.runs.last()
            val next = runCatching { suggestedFor(last) }.getOrNull()
            if (next != null && next.filters.isNotEmpty() && !last.converged) {
                out += EqChoice("suggest:${t.id}", t.name, kind, "Suggested EQ", "After ${last.label}, not measured yet · ${next.filters.size} bands")
            }
            best?.let { i ->
                val r = t.runs[i]
                out += EqChoice("run:${t.id}:$i", t.name, kind, "Best: ${r.label}", "Measured %.2f dB from the target · ${r.eq.size} bands".format(r.rmsDb))
            }
            val kept = s.profiles.filter { it.test == t.name }
            kept.filter { it.name.startsWith("${t.name} · tuned") }.forEach { p ->
                out += EqChoice("profile:${p.id}", t.name, kind, "Kept: " + p.name.removePrefix("${t.name} · "), "Saved when tuning finished · ${p.filters.size} bands")
            }
            // the rest, folded until asked for
            withEq.reversed().filter { it != best }.forEach { i ->
                val r = t.runs[i]
                out += EqChoice("run:${t.id}:$i", t.name, kind, r.label, "Measured %.2f dB from the target · ${r.eq.size} bands".format(r.rmsDb), primary = false)
            }
            aiStored(t.id)?.takeIf { it.bands.isNotEmpty() }?.let { r ->
                out += EqChoice("ai:${t.id}", t.name, kind, "AI-adjusted EQ", "Suggestion after ${t.runs.getOrNull(r.focusRun ?: t.runs.lastIndex)?.label ?: "the last run"} + ${r.bands.size} changes from the AI review · not measured yet", primary = false)
            }
            kept.filter { !it.name.startsWith("${t.name} · tuned") }.forEach { p ->
                out += EqChoice("profile:${p.id}", t.name, kind, p.name.removePrefix("${t.name} · "), "Saved · ${p.filters.size} bands", primary = false)
            }
        }
        s.profiles.filter { it.test !in names }.forEach { p ->
            out += EqChoice("profile:${p.id}", "Saved EQs", TargetKind.HAND, p.name, p.origin.ifBlank { "${p.filters.size} bands" })
        }
        if (s.editFilters != null && s.eqPick == "own") out += EqChoice("own", "Made here", TargetKind.HAND, "Your own bands", "Edited on the EQ tab · ${s.editFilters.size} bands")
        state.update { it.copy(eqChoices = out) }
    }

    /** Puts one EQ in use (see [EqChoice.key]). Never switches the EQ on by itself; changes it live when it is on. */
    fun selectEq(key: String) {
        val p = key.split(":")
        when (p[0]) {
            "suggest" -> {
                val t = savedTests.firstOrNull { it.id == p.getOrNull(1) } ?: return
                if (t.id == current?.id) { useSuggested(); return }
                clearSliders()
                setEdit(suggestedFor(t.runs.last()).filters, key)
            }
            "run" -> {
                val t = savedTests.firstOrNull { it.id == p.getOrNull(1) } ?: return
                val r = t.runs.getOrNull(p.getOrNull(2)?.toIntOrNull() ?: -1) ?: return
                clearSliders()
                setEdit(r.eq, key)
            }
            "profile" -> { selectProfile(p.getOrNull(1) ?: return); return }
            "ai" -> {
                val c = aiChainFor(p.getOrNull(1)) ?: return
                clearSliders()
                setEdit(c.filters, key)
            }
            else -> return
        }
        Diag.log(ctx(), "eq_selected", mapOf("key" to key))
    }

    private fun clearSliders() {
        state.update { it.copy(sliderLayer = emptyList(), convertMessage = null) }
        saveSliderLayer(emptyList())
    }

    private fun fmtDate(ms: Long) = SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(ms))

    private fun summary(t: SavedTest): TestSummary {
        val target = Target.ALL.getOrNull(t.targetIdx)?.name ?: "?"
        val src = Source.entries.getOrNull(t.sourceIdx)?.title ?: "?"
        return TestSummary(
            t.id, t.name, target, targetKind(Target.ALL.getOrNull(t.targetIdx)), src, t.runs.size,
            t.runs.firstOrNull()?.rmsDb, t.runs.lastOrNull()?.rmsDb,
            fmtDate(t.runs.lastOrNull()?.whenMillis ?: t.created),
        )
    }

    private fun uniqueName(base: String): String {
        val taken = savedTests.map { it.name.lowercase() }.toSet()
        if (base.lowercase() !in taken) return base
        var n = 2
        while ("$base ($n)".lowercase() in taken) n++
        return "$base ($n)"
    }

    /** Starts a clean test. Nothing from any earlier test is carried over (results, EQ, runs). */
    fun newTest() {
        stopRta(); stopClip()
        current = null
        state.update {
            it.copy(
                testName = "", currentTestId = null, runs = emptyList(), selectedRuns = emptySet(), runNote = "",
                suggestedChain = null, previousChain = null, eqMayNotBeApplied = false, convertMessage = null,
                exportChain = (it.editFilters.orEmpty() + it.sliderLayer).takeIf { l -> l.isNotEmpty() }?.let { l -> EqChain.withSafePreamp(l) },
                chart = null, bars = null, rmsDb = null, log = emptyList(),
                regions = emptyList(), resonances = emptyList(), compare = null, status = null, warnings = emptyList(),
                report = null, exportWarnings = emptyList(), lr = null, sub = null, subDone = emptySet(),
                positionsDone = 0, testNotes = "", lastWarnings = emptyList(), repeatNext = false,
            )
        }
        resetChecks()
    }

    private fun resetChecks() {
        pendingPositions.clear(); subMeasurements.clear()
        lrLeft = null; lrRight = null; lrResult = null
    }

    /** Creates the (not yet saved) test the user just named. The test is saved with its first, stock run. */
    fun startNewTest(name: String, targetIdx: Int, source: Source) {
        newTest()
        val idx = targetIdx.coerceIn(0, Target.ALL.size - 1)
        pendingPositions.clear()
        val nm = uniqueName(name.trim().ifBlank { "${Target.ALL[idx].name} test, ${fmtDate(System.currentTimeMillis())}" })
        state.update {
            it.copy(
                testName = nm, targetIdx = idx, source = source,
                // Car cabins: keep only the direct sound + first reflections. Rooms: keep room modes.
                gateMs = if (targetKind(Target.ALL[idx]) == TargetKind.CAR) 80 else 400,
                // a cabin response changes with every cm: average three mic spots by default
                positions = if (targetKind(Target.ALL[idx]) == TargetKind.CAR) 3 else 1,
            )
        }
    }

    /** Opens a saved test exactly as it was. Returns the wizard step to continue at (0-based). */
    fun openTest(id: String): Int {
        val t = savedTests.firstOrNull { it.id == id } ?: return 0
        stopRta(); stopClip()
        current = t
        val idx = t.targetIdx.coerceIn(0, Target.ALL.size - 1)
        state.update {
            it.copy(
                testName = t.name, currentTestId = t.id,
                source = Source.entries.getOrElse(t.sourceIdx) { Source.PHONE },
                targetIdx = idx, gateMs = t.gateMs, distortion = null,
                selectedRuns = if (t.runs.size > 1) setOf(0, t.runs.lastIndex) else setOf(0),
                resonances = t.resonances.map { r ->
                    ResonanceDetector.rebuild(r[0], r[1], r[2], r[3].takeUnless { v -> v.isNaN() }, r[4] > 0.5)
                },
                runNote = "", log = emptyList(), status = null, warnings = emptyList(),
                testNotes = t.notes, positionsDone = 0, lr = null, sub = null, subDone = emptySet(),
                lastWarnings = emptyList(), repeatNext = false, tuneStep = TuneStep.HOME,
            )
        }
        resetChecks()
        refreshView()
        return 0
    }

    fun renameTest(id: String, name: String) {
        val t = savedTests.firstOrNull { it.id == id } ?: return
        val nm = name.trim()
        if (nm.isBlank() || nm == t.name) return
        val updated = t.copy(name = uniqueName(nm))
        TestStore.save(getApplication(), updated)
        if (current?.id == id) {
            current = updated
            state.update { it.copy(testName = updated.name) }
        }
        refreshTests()
    }

    fun deleteTest(id: String) {
        TestStore.delete(getApplication(), id)
        runCatching { aiFile(id).delete() }
        if (current?.id == id) { newTest(); state.update { it.copy(tuneStep = TuneStep.HOME) } }
        refreshTests()
    }

    private fun runInfos(t: SavedTest) = t.runs.mapIndexed { i, r ->
        RunInfo(i, r.label, fmtDate(r.whenMillis), r.rmsDb, r.note, r.eq.size, r.calibration, r.converged)
    }

    // ---------------------------------------------------------------- run selection (overlays)

    fun toggleRun(i: Int) {
        val t = current ?: return
        val sel = state.value.selectedRuns
        val next = if (i in sel) sel - i else sel + i
        if (next.isEmpty() || next.any { it !in t.runs.indices }) return
        state.update { it.copy(selectedRuns = next) }
        refreshView()
    }

    /** All runs, or just the stock run plus the latest one. */
    fun selectRuns(all: Boolean) {
        val t = current ?: return
        val next = if (all) t.runs.indices.toSet() else setOf(0, t.runs.lastIndex)
        state.update { it.copy(selectedRuns = next) }
        refreshView()
    }

    fun setRunNote(s: String) = state.update { it.copy(runNote = s) }

    private fun normalized(mag: DoubleArray, g: FreqGrid, target: Target): DoubleArray {
        val off = EqFitter.offsetDb(mag, g, target)
        val sm = state.value.smoothing
        return (if (sm <= 0) mag else g.smoothOctave(mag, sm.toDouble())).map { it - off }.toDoubleArray()
    }

    /** Rebuilds everything shown from the saved test and the ticked runs. */
    private fun refreshView() {
        val t = current
        if (t == null || t.runs.isEmpty()) {
            state.update {
                it.copy(
                    runs = emptyList(), chart = null, bars = null, suggestedChain = null, regions = emptyList(), resultWarnings = emptyList(), stock = null, recommendation = null, bassDb = 0.0, trebleDb = 0.0, eqFollow = null, checkSuggestions = emptyList(),
                    compare = null, rmsDb = null, report = null, previousChain = null, eqMayNotBeApplied = false,
                )
            }
            rebuildActive()
            buildEqChart()
            return
        }
        val target = targetFor(t)
        val sel = state.value.selectedRuns.filter { it in t.runs.indices }.sorted().ifEmpty { listOf(t.runs.lastIndex) }
        val focus = sel.last()
        val run = t.runs[focus]
        // the same chain is shown, graphed, applied and recorded as applied (suggestedFor), so none of them can drift apart
        val suggested = suggestedFor(run)
        val tgtBars = Rta.targetBars(target)
        val chart = ChartData(
            grid.freqs,
            sel.map { Curve(t.runs[it].label, normalized(t.runs[it].magDb, grid, target), it) },
            DoubleArray(grid.size) { target.dbAt(grid.freqs[it]) },
            suggested.takeIf { it.filters.isNotEmpty() }?.let { c -> DoubleArray(grid.size) { c.responseDb(grid.freqs[it]) - c.preampDb } },
            if (focus == 0) "Suggested EQ (not applied yet)" else "Suggested EQ after ${run.label} (not applied yet)",
        )
        val bars = BarsData(
            sel.map { BarRun(t.runs[it].label, it, Rta.relativeToTarget(Rta.barsFromResponse(t.runs[it].magDb, grid), tgtBars)) },
            tgtBars,
        )
        val compare = buildCompare(t, sel, target)
        val stock = runCatching { stockAnalysis(t, target) }.getOrNull()
        val lastRun = t.runs.last()
        val follow = if (t.runs.size > 1) runCatching { eqFollow(t.runs[0].magDb, lastRun.magDb, lastRun.applied, grid) }.getOrNull() else null
        val predicted = if (t.runs.size > 1 && lastRun.applied.isNotEmpty()) runCatching {
            EqFitter.score(DoubleArray(grid.size) { i -> t.runs[0].magDb[i] + lastRun.applied.sumOf { it.magnitudeDb(grid.freqs[i]) } }, grid, target).first
        }.getOrNull() else null
        val checks = suggestChecks(t.runs, targetKind(targetAt(t.targetIdx)) == TargetKind.CAR, state.value.positions, follow, predicted)
        val rec = if (t.runs.size > 1) recommendNext(t.runs, compare?.eqMayNotBeApplied == true && sel.last() == t.runs.lastIndex) else null
        state.update {
            it.copy(
                stock = stock, recommendation = rec, bassDb = t.bassDb, trebleDb = t.trebleDb, eqFollow = follow, checkSuggestions = checks,
                runs = runInfos(t),
                selectedRuns = sel.toSet(),
                chart = chart, bars = bars,
                suggestedChain = suggested,
                previousChain = if (run.eq.isNotEmpty()) EqChain(run.eq, 0.0) else null,
                report = judgeRun(t, focus, target, lrResult),
                regions = Describe.regions(run.magDb, grid, target),
                rmsDb = run.rmsDb,
                compare = compare,
                eqMayNotBeApplied = compare?.eqMayNotBeApplied == true,
                resultWarnings = resultWarnings(t.runs, focus),
            )
        }
        rebuildActive()
        buildEqChart()
    }

    /** The EQ in effect = the user's own bands, or the tuned suggestion, plus the slider layer. */
    private fun rebuildActive() {
        val s = state.value
        val base = s.editFilters ?: s.suggestedChain?.filters.orEmpty()
        val chain = (base + s.sliderLayer).takeIf { it.isNotEmpty() }?.let { EqChain.withSafePreamp(it) }
        val picked = s.eqChoices.firstOrNull { it.key == s.eqPick }
        val sliders = if (s.sliderLayer.isNotEmpty()) " + your slider changes" else ""
        val source = when {
            chain == null -> "No EQ"
            picked != null && picked.key != "own" -> "${picked.group} · ${picked.title}$sliders"
            s.editFilters == null -> (s.testName.takeIf { it.isNotBlank() }?.let { "$it · " } ?: "") + "Suggested EQ$sliders"
            else -> "Your own bands$sliders"
        }
        // what the EQ does to the overall loudness (cuts and preamp), so a quieter next run is expected, not a fault
        val level = chain?.let { c -> grid.freqs.filter { it in 100.0..5000.0 }.map { c.responseDb(it) }.average() }
        state.update {
            it.copy(
                exportChain = chain, exportWarnings = chain?.let { c -> Headroom.check(c, grid).warnings } ?: emptyList(),
                eqSource = source, eqLevelDb = level,
            )
        }
    }

    /** Graph for the EQ tab: the focus run, the EQ in effect, and the response predicted once it is applied. */
    private fun buildEqChart() {
        val t = current
        val chain = activeChain()
        val target = t?.let { targetFor(it) } ?: targetAt(state.value.targetIdx)
        val eqCurve = chain?.takeIf { it.filters.isNotEmpty() }
            ?.let { c -> DoubleArray(grid.size) { c.responseDb(grid.freqs[it]) - c.preampDb } }
        var curves = emptyList<Curve>()
        var predicted: DoubleArray? = null
        if (t != null && t.runs.isNotEmpty()) {
            val sel = state.value.selectedRuns.filter { it in t.runs.indices }.sorted().ifEmpty { listOf(t.runs.lastIndex) }
            val focus = sel.last()
            val base = normalized(t.runs[focus].magDb, grid, target)
            curves = listOf(Curve(t.runs[focus].label, base, focus))
            if (eqCurve != null) {
                // the focus run already contains whatever was applied for it, so only the change since then is predicted
                val already = t.runs[focus].applied.takeIf { it.isNotEmpty() }?.let { EqChain(it, 0.0) }
                predicted = DoubleArray(grid.size) { i ->
                    base[i] + eqCurve[i] - (already?.responseDb(grid.freqs[i]) ?: 0.0)
                }
            }
        }
        state.update {
            it.copy(
                eqChart = ChartData(
                    grid.freqs, curves, DoubleArray(grid.size) { i -> target.dbAt(grid.freqs[i]) },
                    eqCurve, "AutoTune EQ", predicted,
                ),
            )
        }
    }

    private fun resonancesFor(t: SavedTest, idx: Int): List<Resonance> =
        if (idx == 0) t.resonances.map { r ->
            ResonanceDetector.rebuild(r[0], r[1], r[2], r[3].takeUnless { v -> v.isNaN() }, r[4] > 0.5)
        } else ResonanceDetector.detect(Measurement(grid, t.runs[idx].magDb, DoubleArray(0), 0, fs, 0.0, null, emptyList()))

    /** The stock run in plain words, with the result its suggested EQ is predicted to reach. */
    private fun stockAnalysis(t: SavedTest, target: Target): StockAnalysis {
        val stock = t.runs[0]
        val sug = suggestedFor(stock)
        val predicted = sug.filters.takeIf { it.isNotEmpty() }?.let { f ->
            EqFitter.score(DoubleArray(grid.size) { i -> stock.magDb[i] + f.sumOf { b -> b.magnitudeDb(grid.freqs[i]) } }, grid, target).first
        }
        return analyzeStock(
            stock.rmsDb, predicted, sug.filters.size, Describe.regions(stock.magDb, grid, target), resonancesFor(t, 0),
            stock.warnings, judgeRun(t, 0, target, lrResult),
        )
    }

    private fun judgeRun(t: SavedTest, idx: Int, target: Target, lr: LrResult?): SystemReport =
        SystemJudge.judge(t.runs[idx].magDb, grid, target, resonancesFor(t, idx), lr)

    private fun buildCompare(t: SavedTest, sel: List<Int>, target: Target): Compare? {
        if (sel.size < 2) return null
        val runs = sel.map { t.runs[it] }
        val regions = runs.map { Describe.regions(it.magDb, grid, target) }
        val first = runs.first()
        val last = runs.last()
        val verdict = when {
            last.rmsDb <= 1.2 -> "Excellent: ${last.label} is within %.1f dB of the target.".format(last.rmsDb)
            last.rmsDb < first.rmsDb * 0.85 ->
                "Better: ${last.label} is %.2f dB RMS from the target, down from %.2f (${first.label}).".format(last.rmsDb, first.rmsDb)
            last.rmsDb > first.rmsDb * 1.1 ->
                "Worse than ${first.label}. Check the EQ is applied correctly, and that the volume and mic position match the earlier run."
            else -> "About the same as ${first.label}. The EQ may not be applied, or another sound effect is cancelling it."
        }
        val notApplied = verdict.startsWith("About the same") && last.applied.isNotEmpty()
        return Compare(
            runs.map { it.label }, sel,
            runs.map { it.rmsDb },
            regions[0].map { it.name },
            regions[0].indices.map { ri -> regions.map { it[ri].errDb } },
            sel.map { judgeRun(t, it, target, null).overall },
            verdict, notApplied,
        )
    }

    // ---------------------------------------------------------------- setup

    fun setPermission(granted: Boolean) = state.update { it.copy(hasPermission = granted) }

    fun refreshMic() {
        val ctx = getApplication<Application>()
        val d = UsbMic.find(ctx)
        val list = Outputs.list(ctx)
        val choice = state.value.outputChoice?.takeIf { id -> list.any { it.id == id } }
        // Only route by hand when the user picked an output; otherwise play wherever the phone plays music.
        val sys = Outputs.systemRoute(ctx)
        val used = list.firstOrNull { it.id == choice }?.device ?: sys?.takeIf { it.type != android.media.AudioDeviceInfo.TYPE_REMOTE_SUBMIX }
        player.output = list.firstOrNull { it.id == choice }?.device
        state.update {
            it.copy(
                micName = d?.productName?.toString() ?: "Phone microphone", phoneMic = d == null,
                outputs = list.map { o -> o.id to o.name }, outputChoice = choice, outputName = used?.let { o -> Outputs.nameOf(o) },
                outputToMic = Outputs.isTheMic(used, d),
                audioCaptured = sys?.type == android.media.AudioDeviceInfo.TYPE_REMOTE_SUBMIX,
                micAlsoOutput = Outputs.micIsAlsoOutput(list, d),
            )
        }
    }

    /** Where the test sound plays; null = automatic (Bluetooth first, never the mic itself). */
    fun setOutput(id: Int?) {
        state.update { it.copy(outputChoice = id) }
        refreshMic()
    }

    private fun currentOutput(): android.media.AudioDeviceInfo? {
        val ctx = getApplication<Application>()
        val list = Outputs.list(ctx)
        return list.firstOrNull { it.id == state.value.outputChoice }?.device
    }

    /** Called with the device the sound actually went to. Stops the measurement if it went into the mic. */
    private fun routed(d: android.media.AudioDeviceInfo?) {
        if (d == null) return
        if (d.type == android.media.AudioDeviceInfo.TYPE_REMOTE_SUBMIX) {
            // a screen recorder or cast app is capturing the sound; the real output is not reported
            state.update { it.copy(audioCaptured = true) }
            return
        }
        val toMic = Outputs.isTheMic(d, UsbMic.find(getApplication()))
        state.update { it.copy(outputName = Outputs.nameOf(d), outputToMic = toMic, audioCaptured = false) }
        if (toMic) error("The test sound went to the USB mic (it also acts as headphones), not your speakers. Under Plays through, tap Change and pick your car, then measure again.")
    }

    fun setChannel(c: OutChannel) = state.update { it.copy(channel = c) }
    /** The analysis window is fixed once the stock run exists, so every run of a test is analysed the same way. */
    fun setGate(ms: Int) { if (current == null) state.update { it.copy(gateMs = ms) } }
    fun setShowBands(on: Boolean) = state.update { it.copy(showBands = on) }
    fun setBandSet(i: Int) = state.update { it.copy(bandSetIdx = i) }

    /** Called whenever the tab changes: stop any sound and drop messages that belonged to the previous tab. */
    fun onTabChanged() {
        refreshMic()
        stopRta()
        stopClip()
        // a failure stays until the next measurement starts: it is the reason the tab changed or the run is missing
        state.update { it.copy(status = it.status?.takeIf { s -> s.startsWith("Failed: ") }, warnings = emptyList()) }
    }

    fun importCalibration(uri: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            try {
                val (name, text) = withContext(Dispatchers.IO) {
                    val n = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        val col = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (c.moveToFirst() && col >= 0) c.getString(col) else null
                    } ?: "calibration.txt"
                    val t = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                    n to t
                }
                val cal = Calibration.parse(text)
                withContext(Dispatchers.IO) { calibrationFile.writeText(text) }
                prefs.edit().putString("calibrationName", name).apply()
                calibration = cal
                val sens = cal.sensFactorDb?.let { ", sens %.2f dB".format(it) } ?: ""
                state.update { it.copy(calibrationName = name, calibrationInfo = "${cal.pointCount} points$sens", status = "Calibration loaded.") }
            } catch (e: Exception) {
                state.update { it.copy(status = "Calibration file problem: ${e.message}") }
            }
        }
    }


    // ---------------------------------------------------------------- measuring

    private fun run(block: suspend () -> Unit) {
        if (job?.isActive == true) return
        stopClip()
        stopRta()
        job = viewModelScope.launch {
            state.update { it.copy(busy = true, warnings = emptyList(), levelResult = null, status = null) }
            try {
                block()
            } catch (e: CancellationException) {
                state.update { it.copy(status = "Cancelled.", measureView = null) }
                throw e
            } catch (e: Throwable) {
                // an OutOfMemoryError must not take the app (and the recording) with it
                pendingPositions.clear()
                val msg = if (e is OutOfMemoryError) "Failed: Out of memory while analysing - the recording was $lastRecordingSeconds s long. Try again; if it keeps happening, close other apps first."
                else "Failed: ${e.message}"
                state.update {
                    it.copy(
                        status = msg, positionsDone = 0,
                        measureView = it.measureView?.copy(stage = MeasureStage.FAILED, message = msg.removePrefix("Failed: ")),
                    )
                }
                Diag.log(ctx(), "failure", mapOf("message" to msg, "error" to e.javaClass.simpleName, "step" to state.value.tuneStep))
            } finally {
                state.update { it.copy(busy = false, progress = null) }
                // measuring plays the EQ without flavour and with the safe preamp; put back what was playing
                if (state.value.systemEqOn) runCatching { SystemEq.apply(playChain()) }
            }
        }
    }

    fun cancel() { job?.cancel() }

    /** Two seconds of pink noise through the current output, so the user can hear where it comes out. */
    fun testSound() {
        if (state.value.busy) return
        stopRta()
        refreshMic()
        player.play(viewModelScope, Clip("Test sound", Sweep.pinkNoise(fs, 2.0).let { m -> FloatArray(m.size * 2) { i -> m[i / 2] } }, fs), null) {
            state.update { it.copy(playing = false) }
        }
        state.update { it.copy(playing = true, playingWithEq = false) }
    }

    /** Plays pink noise (or listens to the other device) and reports background, sound level and peak in plain words. */
    fun levelCheck() = run {
        requireReady()
        val mic = UsbMic.find(getApplication())
        val rec = if (state.value.source == Source.PHONE) {
            state.update { it.copy(status = "Checking the level: a short burst of pink noise...", levelResult = null) }
            MeasurementRunner.capture(mic, Sweep.pinkNoise(fs, 3.0), fs, state.value.channel, currentOutput(), ::routed)
        } else {
            state.update { it.copy(status = "Start the sound on the other device now. Listening for 6 seconds...", levelResult = null) }
            MeasurementRunner.recordOnly(mic, fs, 6) { }
        }
        val r = withContext(Dispatchers.Default) { LevelCheck.analyze(rec, fs) }
        state.update { it.copy(status = null, levelResult = r.verdict) }
    }

    private fun clearResults() = state.update {
        it.copy(chart = null, bars = null, exportChain = it.editFilters?.let { f -> EqChain.withSafePreamp(f + it.sliderLayer) }, resonances = emptyList(), regions = emptyList(), log = emptyList(), rmsDb = null, compare = null)
    }

    /** The stock run: one measurement (or the average of several mic positions) with no EQ, then the recommendation. */
    fun stockRun() = run {
        requireReady()
        if (state.value.systemEqOn) setSystemEq(false) // the stock run must be measured with no EQ
        val s0 = state.value
        openMeasureView("Stock reading", s0.source)
        val m = acquire(s0.source, s0.gateMs, s0.channel) ?: return@run
        clearResults()
        stockFromMeasurement(m, s0.targetIdx, s0.gateMs, s0.source)
    }

    fun setPositions(n: Int) {
        pendingPositions.clear()
        state.update { it.copy(positions = n, positionsDone = 0) }
    }

    fun resetPositions() {
        pendingPositions.clear()
        state.update { it.copy(positionsDone = 0, status = "Position count reset.") }
    }

    /**
     * One reading for the chosen route. With several mic positions it keeps each one and returns the average when the
     * last position is done (null until then).
     */
    private suspend fun acquire(src: Source, gateMs: Int, channel: OutChannel, appChain: EqChain? = null): Measurement? {
        val m = measureOnce(src, gateMs, channel, appChain)
        checkUsable(m)
        pendingPositions += m
        val n = state.value.positions
        if (pendingPositions.size < n) {
            state.update {
                it.copy(
                    positionsDone = pendingPositions.size,
                    status = "Position ${pendingPositions.size} of $n measured. Move the mic to the next position, then tap again.",
                    measureView = it.measureView?.copy(
                        stage = MeasureStage.SPOT_DONE,
                        message = "Spot ${pendingPositions.size} of $n done. Move the mic a hand's width to the next spot, then measure again.",
                    ),
                )
            }
            return null
        }
        state.update { it.copy(measureView = it.measureView?.copy(message = if (n > 1) "Averaging the $n spots and saving..." else "Saving...")) }
        val avg = Averaging.average(pendingPositions.toList())
        pendingPositions.clear()
        state.update { it.copy(positionsDone = 0) }
        return avg
    }

    /** A reading that can't be trusted is not saved: the user measures again instead of tuning to a bad curve. */
    private fun checkUsable(m: Measurement) {
        m.fatal?.let {
            val hint = if (state.value.systemEqOn) " AutoTune EQ is on: if the speakers stayed silent, this phone may be muting audio through it. Turn it off on the EQ tab and try again." else ""
            error(it + hint)
        }
        m.warnings.firstOrNull { it.contains("clipped") }?.let { error("$it This reading was not saved.") }
    }

    private suspend fun measureOnce(src: Source, gateMs: Int, channel: OutChannel, appChain: EqChain? = null): Measurement {
        lastCapture = emptyMap()
        state.update { it.copy(measureView = it.measureView?.copy(stage = MeasureStage.MEASURING, message = "", checks = startChecks(), spot = pendingPositions.size + 1)) }
        val m = if (src == Source.PHONE) phoneSweep(gateMs, channel, appChain) else captureExternal(src, gateMs)
        if (m.fatal == null) state.update { it.copy(distortion = m.distortion) }
        state.update { it.copy(measureView = it.measureView?.copy(stage = MeasureStage.ANALYSING, message = "Checking the reading", checks = checksFor(m, src))) }
        val st = state.value
        Diag.log(
            ctx(), "measurement",
            lastCapture + mapOf(
                "test" to st.testName, "source" to src, "route" to if (current?.runs.isNullOrEmpty()) "STOCK" else st.applyWay.name,
                "repeat" to st.repeatNext, "systemEqOn" to st.systemEqOn, "systemEq" to Diag.chain(SystemEq.chain), "inApp" to Diag.chain(appChain),
                "output" to st.outputName, "mic" to st.micName, "micSource" to MeasurementRunner.lastMicSource, "micEffects" to MeasurementRunner.lastMicEffects, "calibration" to calName(),
                "gateMs" to gateMs, "fdw" to st.fdwCycles, "spot" to pendingPositions.size + 1, "spots" to st.positions,
                "snrDb" to m.snrDb, "latencyMs" to m.latencyMs, "peakFromMarker" to m.peakFromMarker, "driftPpm" to m.driftPpm,
                "fatal" to m.fatal, "warnings" to m.warnings,
                // the response itself, 1/3-octave, so the EQ's effect can be read straight from the log
                "thirdOctaveDb" to Rta.barsFromResponse(m.magDb, grid).map { Math.round(it * 10) / 10.0 },
            ),
        )
        return m
    }

    /** Details of the last capture for the diagnostics log (levels, lift, recording file). */
    private var lastCapture: Map<String, Any?> = emptyMap()

    private fun noteCapture(rec: FloatArray, tag: String, extra: Map<String, Any?>) {
        val (peak, rms) = Diag.levels(rec)
        val file = if (state.value.keepRecordings) Diag.keepRecording(ctx(), rec, fs, tag) else null
        lastCapture = mapOf("recPeakDbfs" to peak, "recRmsDbfs" to rms, "recSeconds" to rec.size.toDouble() / fs, "recording" to file) + extra
    }

    private fun fdw(): Double? = state.value.fdwCycles.takeIf { it > 0 }?.toDouble()

    private suspend fun phoneSweep(gateMs: Int, channel: OutChannel, appChain: EqChain? = null): Measurement {
        val sweep = Sweep.generate(fs, 10.0) // slower sweep: more energy at every frequency
        // an EQ with a deep preamp makes the test too quiet for the mic: raise the sweep by what the EQ takes away
        val inEffect = appChain ?: if (state.value.systemEqOn) SystemEq.chain else null
        val grid = FreqGrid()
        val lift = inEffect?.let { c -> (2.9 - (grid.freqs.maxOfOrNull { c.responseDb(it) } ?: 0.0)).coerceIn(0.0, 12.0) } ?: 0.0
        var gain = Math.pow(10.0, lift / 20.0).toFloat()
        val peak = sweep.maxOfOrNull { abs(it) } ?: 1f
        if (peak * gain > 1f) gain = 1f / peak
        val tone = Marker.waveform(fs)
        val lead = (Marker.SWEEP_OFFSET_SEC * fs).toInt()
        val played = FloatArray(lead + sweep.size)
        // the beeps are lifted too, so the EQ's preamp can't hide the start marker
        for (i in tone.indices) played[i] = (tone[i] * gain).coerceIn(-1f, 1f)
        for (i in sweep.indices) played[lead + i] = sweep[i] * gain
        if (appChain != null) {
            EqProcessor(appChain, fs).process(played)
            // after the EQ, bring the loudest part of the sweep back to the level of an un-EQ'd sweep plus the lift
            var mx = 0f
            for (i in lead until played.size) mx = maxOf(mx, abs(played[i]))
            val want = (peak * Math.pow(10.0, 2.9 / 20.0)).toFloat().coerceAtMost(0.9f)
            if (mx > 1e-6f && mx < want * 0.7f) { val k = want / mx; for (i in played.indices) played[i] = (played[i] * k).coerceIn(-1f, 1f) }
        }
        state.update { it.copy(status = "Playing a sweep and recording...") }
        val rec = MeasurementRunner.capture(UsbMic.find(getApplication()), played, fs, channel, currentOutput(), ::routed)
        lastRecordingSeconds = rec.size / fs
        var playedPeak = 0f
        for (x in played) playedPeak = maxOf(playedPeak, abs(x))
        noteCapture(rec, "phone", mapOf("liftDb" to lift, "playedPeakDbfs" to 20 * kotlin.math.log10(playedPeak + 1e-9)))
        state.update { it.copy(status = "Analysing...") }
        return withContext(Dispatchers.Default) {
            // a "marker" so late that the sweep could not fit after it is the sweep itself (seen with weak beeps)
            val m = Marker.find(rec, fs)?.takeIf { it + lead + sweep.size <= rec.size }
            val viaMarker = m?.let {
                // locate, then cut: 1 s of quiet before the beeps, the sweep, and 4 s of decay. Nothing longer is transformed.
                val from = max(0, it - fs)
                val to = min(rec.size, it + lead + sweep.size + 4 * fs)
                Analyzer.analyze(rec.copyOfRange(from, to), sweep, fs, calibration, gateMs.toDouble(), expectedStart = it - from + lead, fdwCycles = fdw())
            }
            val markerGood = viaMarker != null && viaMarker.fatal == null && (viaMarker.snrDb ?: 0.0) >= 30.0
            lastCapture = lastCapture + mapOf("markerAt" to m?.let { it.toDouble() / fs })
            if (markerGood) viaMarker!!
            else {
                // no marker, or a doubtful result through it: search the whole recording and keep the better reading
                val full = Analyzer.analyze(rec, sweep, fs, calibration, gateMs.toDouble(), fdwCycles = fdw())
                val better = viaMarker == null || (full.fatal == null && (viaMarker.fatal != null || (full.snrDb ?: 0.0) > (viaMarker.snrDb ?: 0.0) + 6.0))
                lastCapture = lastCapture + mapOf("usedFullSearch" to better)
                if (better) full else viaMarker!!
            }
        }
    }

    private suspend fun captureExternal(source: Source, gateMs: Int): Measurement {
        val mic = UsbMic.find(getApplication())
        val fileSource = source == Source.TEST_FILE || source == Source.COMBINED
        // test files carry a start marker: wait for it (at most 30 s), then stop as soon as the sweep is captured
        val seconds = when (source) { Source.COMBINED -> 55; Source.TEST_FILE -> 40; else -> 22 }
        val after = when (source) { // seconds from the marker's first sample to the end of what is needed
            Source.COMBINED -> Marker.SWEEP_OFFSET_SEC + TestSignals.SWEEP_SECONDS + TestSignals.COMBINED_GAP_SECONDS + TestSignals.COMBINED_PINK_SECONDS + 0.5
            else -> Marker.SWEEP_OFFSET_SEC + TestSignals.SWEEP_SECONDS + 1.2
        }
        var markerAt = -1
        val what = if (source == Source.YOUTUBE) "Capturing pink noise" else "Recording - press play on the other device now"
        state.update { it.copy(status = if (fileSource) "$what. Waiting for the start beeps..." else "$what ($seconds s left)...", progress = null) }
        val stop: ((FloatArray, Int) -> Boolean)? = if (!fileSource) null else { buf, got ->
            if (markerAt < 0) markerAt = Marker.find(buf, fs, max(0, got - 5 * fs), got) ?: -1
            markerAt >= 0 && got >= markerAt + (after * fs).toInt()
        }
        val rec = MeasurementRunner.recordOnly(mic, fs, seconds, stop) { sec ->
            state.update {
                if (fileSource) {
                    val heard = markerAt >= 0
                    it.copy(
                        status = if (heard) "Start beeps heard. Recording the sweep..." else "$what. Waiting for the start beeps...",
                        progress = if (heard) (((sec * fs - markerAt) / fs.toFloat()) / after.toFloat()).coerceIn(0f, 1f) else null,
                    )
                } else it.copy(status = "$what (${seconds - sec} s left)...", progress = (sec / seconds.toFloat()).coerceIn(0f, 1f))
            }
        }
        lastRecordingSeconds = rec.size / fs
        noteCapture(rec, source.name.lowercase(), mapOf("markerAt" to markerAt))
        state.update { it.copy(status = "Analysing...") }
        return withContext(Dispatchers.Default) {
            when (source) {
                Source.TEST_FILE -> {
                    val onset = if (markerAt >= 0) markerAt + (Marker.SWEEP_OFFSET_SEC * fs).toInt() else SweepTrim.locate(rec, fs)
                    val trimmed = onset?.let { SweepTrim.trimAt(rec, fs, it, TestSignals.SWEEP_SECONDS) }
                        ?: error("No sweep heard. Raise the volume on the other device, make sure the file is playing, and start recording first.")
                    Analyzer.analyze(trimmed, Sweep.generate(fs, TestSignals.SWEEP_SECONDS), fs, calibration, gateMs.toDouble(), fdwCycles = fdw())
                }
                Source.COMBINED -> CombinedAnalyzer.analyze(rec, fs, calibration, gateMs.toDouble(), markerAt.takeIf { it >= 0 }?.let { it + (Marker.SWEEP_OFFSET_SEC * fs).toInt() }).measurement
                else -> NoiseAnalyzer.analyze(rec.copyOfRange(2 * fs, rec.size), fs, calibration)
            }
        }
    }

    // ---------------------------------------------------------------- extra checks

    private fun checkTarget(): Target = Target.ALL[(current?.targetIdx ?: state.value.targetIdx).coerceIn(0, Target.ALL.size - 1)]
    private fun checkGate(): Int = current?.gateMs ?: state.value.gateMs

    /** Left or right speaker alone. Phone route: the phone plays one channel. Other device: play the matching sweep file. */
    fun measureSide(side: OutChannel) = run {
        requireReady()
        val s = state.value
        val m = if (s.source == Source.PHONE) phoneSweep(checkGate(), side) else captureExternal(Source.TEST_FILE, checkGate())
        m.fatal?.let { error(it) }
        if (side == OutChannel.LEFT) lrLeft = m else lrRight = m
        val l = lrLeft
        val r = lrRight
        if (l == null || r == null) {
            state.update { it.copy(status = "${if (side == OutChannel.LEFT) "Left" else "Right"} measured. Now measure the ${if (side == OutChannel.LEFT) "right" else "left"} side.") }
            return@run
        }
        val res = LeftRight.analyze(l, r, delayValid = s.source == Source.PHONE)
        lrResult = res
        val target = checkTarget()
        val off = (EqFitter.offsetDb(l.magDb, grid, target) + EqFitter.offsetDb(r.magDb, grid, target)) / 2
        val chart = ChartData(
            grid.freqs,
            listOf(
                Curve("Left", grid.smoothOctave(l.magDb, 6.0).map { it - off }.toDoubleArray(), 1),
                Curve("Right", grid.smoothOctave(r.magDb, 6.0).map { it - off }.toDoubleArray(), 2),
            ),
            DoubleArray(grid.size) { target.dbAt(grid.freqs[it]) }, null, "",
        )
        state.update { it.copy(lr = LrInfo(res, chart), status = "Left and right compared.") }
        refreshView()
    }

    fun resetLr() {
        lrLeft = null; lrRight = null; lrResult = null
        state.update { it.copy(lr = null, status = null) }
        refreshView()
    }

    /** kind: MAINS, SUB, SUM (both, sub polarity normal), SUM_INV (both, sub polarity inverted). */
    fun measureSub(kind: String) = run {
        requireReady()
        val s = state.value
        val m = measureOnce(s.source, checkGate(), s.channel)
        m.fatal?.let { error(it) }
        subMeasurements[kind] = m
        val res = SubHelper.analyze(subMeasurements["MAINS"], subMeasurements["SUB"], subMeasurements["SUM"], subMeasurements["SUM_INV"], checkTarget())
        state.update { it.copy(sub = res, subDone = subMeasurements.keys.toSet(), status = "Measured.") }
    }

    fun resetSub() {
        subMeasurements.clear()
        state.update { it.copy(sub = null, subDone = emptySet(), status = null) }
    }

    // ---------------------------------------------------------------- notes and target curves

    private var notesJob: Job? = null

    fun setTestNotes(s: String) {
        state.update { it.copy(testNotes = s) }
        val cur = current ?: return
        val updated = cur.copy(notes = s)
        current = updated
        notesJob?.cancel()
        notesJob = viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            withContext(Dispatchers.IO) { TestStore.save(getApplication(), updated) }
        }
    }

    private fun reloadTargets() {
        Target.ALL = Target.BUILT_IN + TargetStore.load(getApplication())
        state.update {
            it.copy(
                targetNames = Target.ALL.map { t -> t.name },
                customTargets = Target.ALL.drop(Target.BUILT_IN.size).map { t -> t.name },
                targetIdx = it.targetIdx.coerceIn(0, Target.ALL.size - 1),
            )
        }
        refreshTests()
    }

    private fun uniqueTargetName(base: String): String {
        val taken = Target.ALL.map { it.name.lowercase() }.toSet()
        val b = base.trim().ifBlank { "My curve" }
        if (b.lowercase() !in taken) return b
        var n = 2
        while ("$b ($n)".lowercase() in taken) n++
        return "$b ($n)"
    }

    /** Adds a target curve from a text/CSV file of "frequency, dB" lines (house curves, Harman-style curves...). */
    fun importTargetCurve(uri: Uri) {
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val (name, text) = withContext(Dispatchers.IO) {
                    val n = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        val col = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (c.moveToFirst() && col >= 0) c.getString(col) else null
                    } ?: "My curve"
                    n.substringBeforeLast('.') to ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                }
                val tg = Target.parse(uniqueTargetName(name), text)
                if (tg == null) {
                    state.update { it.copy(status = "That file doesn't look like a curve: it needs lines of frequency and dB, covering about 20 Hz to 20 kHz.") }
                    return@launch
                }
                withContext(Dispatchers.IO) { TargetStore.save(ctx, tg) }
                reloadTargets()
                state.update { it.copy(status = "Curve “${tg.name}” added. Choose it when you start a test.") }
            } catch (e: Exception) {
                state.update { it.copy(status = "Could not read the curve: ${e.message}") }
            }
        }
    }

    /** Saves the response of the latest ticked run as a target for future tests ("this is how I like it to sound"). */
    fun saveRunAsTarget(name: String) {
        val t = current ?: return
        val focus = state.value.selectedRuns.maxOrNull() ?: return
        val run = t.runs.getOrNull(focus) ?: return
        val target = targetFor(t)
        val off = EqFitter.offsetDb(run.magDb, grid, target)
        val s = grid.smoothOctave(run.magDb, 6.0)
        val pts = (listOf(20.0) + Export.THIRD_OCTAVE_CENTERS + listOf(20000.0)).map { f -> f to s[grid.indexAtOrAbove(f).coerceAtMost(grid.size - 1)] - off }
        val tg = Target(uniqueTargetName(name), pts)
        TargetStore.save(getApplication(), tg)
        reloadTargets()
        state.update { it.copy(status = "Saved “${tg.name}” as a target curve. Choose it when you start a test.") }
    }

    fun deleteTarget(name: String) {
        TargetStore.delete(getApplication(), name)
        reloadTargets()
    }

    private fun targetAt(idx: Int): Target = Target.ALL[idx.coerceIn(0, Target.ALL.size - 1)]

    /** Settings: what a new test starts with. [target] by name (null = Car); spots and window 0 = automatic. */
    fun setLook(l: dev.autotune.ui.Look) {
        prefs.edit().putString("look", l.name).apply()
        // a look's own colours only show with the wallpaper colours off
        state.update { it.copy(look = l, dynamicColor = if (l != dev.autotune.ui.Look.STUDIO) false else it.dynamicColor) }
        if (l != dev.autotune.ui.Look.STUDIO) prefs.edit().putBoolean("dynamic", false).apply()
    }

    fun setDefaults(target: String?, bassDb: Double, trebleDb: Double, spots: Int, gateMs: Int) {
        prefs.edit().putString("defTarget", target).putFloat("defBass", bassDb.toFloat()).putFloat("defTreble", trebleDb.toFloat())
            .putInt("defSpots", spots).putInt("defGate", gateMs).apply()
        state.update { it.copy(defTarget = target, defBassDb = bassDb, defTrebleDb = trebleDb, defSpots = spots, defGateMs = gateMs) }
    }

    /** Settings: save files into a folder the user picked (kept across restarts), or back to Downloads/AutoTune with null. */
    fun setExportFolder(tree: Uri?) {
        val cr = ctx().contentResolver
        prefs.getString("exportTree", null)?.let { old ->
            runCatching { cr.releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        }
        if (tree == null) {
            prefs.edit().remove("exportTree").apply()
            state.update { it.copy(exportFolder = null) }
            return
        }
        runCatching { cr.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        prefs.edit().putString("exportTree", tree.toString()).apply()
        state.update { it.copy(exportFolder = folderName(tree)) }
    }

    private fun folderName(tree: Uri): String = runCatching {
        android.provider.DocumentsContract.getTreeDocumentId(tree).substringAfter(':').ifBlank { "Phone storage" }
    }.getOrDefault("Chosen folder")

    /** The test's target curve with its taste (bass and treble shelves) on top: what every fit and score uses. */
    private fun targetFor(t: SavedTest): Target = targetAt(t.targetIdx).withTaste(t.bassDb, t.trebleDb)

    /**
     * Changes the open test's target or taste. Every run's distance is scored again against the new curve and the
     * latest suggestion is refitted (closed loop for runs with an EQ), so the next EQ follows the new taste at once.
     */
    fun setTargetSound(targetIdx: Int, bassDb: Double, trebleDb: Double) {
        val t = current ?: return
        val u0 = t.copy(targetIdx = targetIdx.coerceIn(0, Target.ALL.size - 1), bassDb = bassDb, trebleDb = trebleDb)
        if (u0 == t) return
        val target = targetFor(u0)
        viewModelScope.launch {
            val u = withContext(Dispatchers.Default) {
                val stock = u0.runs.firstOrNull()
                val last = u0.runs.lastIndex
                val runs = u0.runs.mapIndexed { i, r ->
                    val rms = EqFitter.score(r.magDb, grid, target).first
                    val filters = when {
                        i != last || stock == null -> r.filters
                        i == 0 -> EqFitter.fit(r.magDb, grid, target, (if (fitsSystemEq(r.filters) && r.filters.isNotEmpty()) SYSTEM_EQ_FIT else FitConfig()).copy(boostMinHz = u0.boostFloorHz)).filters
                        else -> EqFitter.fitFromStock(stock.magDb, r.applied, r.magDb, grid, target, (if (r.appliedParam != null) SYSTEM_EQ_FIT else FitConfig()).copy(boostMinHz = u0.boostFloorHz)).fit.filters
                    }
                    SavedRun(r.whenMillis, rms, r.magDb, filters, r.label, r.note, r.applied, r.warnings, r.calibration, complete = true, appliedParam = r.appliedParam, converged = false)
                }
                u0.copy(runs = runs)
            }
            withContext(Dispatchers.IO) { TestStore.save(ctx(), u) }
            current = u
            refreshTests()
            state.update { it.copy(targetIdx = u.targetIdx) }
            refreshView()
            // the suggestion in use follows the new curve; when it is playing, play the new one
            if (state.value.systemEqOn && state.value.editFilters == null) refreshSystemEq()
            Diag.log(ctx(), "target_sound", mapOf("test" to u.name, "target" to target.name))
        }
    }

    /** Saves the stock run (replacing the test's runs, if any) and creates the named test on first save. */
    private suspend fun saveStock(
        targetIdx: Int, gateMs: Int, source: Source, rms: Double, mag: DoubleArray,
        filters: List<EqFilter>, res: List<Resonance>, warnings: List<String>,
    ) {
        val ctx = getApplication<Application>()
        val now = System.currentTimeMillis()
        val cur = current
        val name = cur?.name ?: state.value.testName.trim().ifBlank { "${targetAt(targetIdx).name} test, ${fmtDate(now)}" }
        val t = SavedTest(
            id = cur?.id ?: UUID.randomUUID().toString(),
            name = name,
            created = cur?.created ?: now,
            targetIdx = targetIdx, sourceIdx = source.ordinal, gateMs = gateMs,
            runs = listOf(SavedRun(now, rms, mag, filters, TestStore.STOCK_LABEL, warnings = warnings, calibration = calName(), complete = true)),
            resonances = res.map { doubleArrayOf(it.freqHz, it.prominenceDb, it.q, it.t60Ms ?: Double.NaN, if (it.t60IsLowerBound) 1.0 else 0.0) },
            notes = state.value.testNotes,
            bassDb = cur?.bassDb ?: state.value.defBassDb, trebleDb = cur?.trebleDb ?: state.value.defTrebleDb,
        )
        withContext(Dispatchers.IO) { TestStore.save(ctx, t) }
        current = t
        refreshTests()
        state.update { it.copy(currentTestId = t.id, testName = t.name, selectedRuns = setOf(0)) }
        runDone.tryEmit(Unit)
        current?.let { t -> showSaved(t) }
    }

    /** Shared by every route: one un-EQ'd measurement -> recommendation, saved as the stock run. */
    private suspend fun stockFromMeasurement(m: Measurement, targetIdx: Int, gateMs: Int, source: Source) {
        m.fatal?.let { error(it) }
        // a stock reading measured again keeps the test's taste
        val target = current?.takeIf { it.targetIdx == targetIdx }?.let { targetFor(it) }
            ?: targetAt(targetIdx).withTaste(state.value.defBassDb, state.value.defTrebleDb)
        val baselineRms = EqFitter.score(m.magDb, m.grid, target).first
        val res = withContext(Dispatchers.Default) { ResonanceDetector.detect(m) }
        val fit = EqFitter.fit(m.magDb, m.grid, target)
        val warns = (m.warnings + micSourceWarning() + largeErrorWarning(baselineRms)).distinct()
        saveStock(targetIdx, gateMs, source, baselineRms, m.magDb, fit.filters, res, warns)
        state.update { it.copy(eqMayNotBeApplied = false) }
        Diag.log(
            ctx(), "stock_saved",
            mapOf("test" to state.value.testName, "target" to target.name, "rmsDb" to baselineRms, "predictedRmsDb" to fit.rmsAfter, "suggestion" to Diag.bands(fit.filters), "warnings" to warns),
        )
        resetToSuggestion()
        refreshView()
        state.update {
            it.copy(
                resonances = res,
                log = listOf("Stock run: %.2f dB RMS; predicted with the suggested EQ: %.2f dB".format(baselineRms, fit.rmsAfter)),
                warnings = emptyList(), lastWarnings = warns,
                status = null, tuneStep = TuneStep.STOCK_RESULT, repeatNext = false,
            )
        }
    }

    /** Names the microphone source when it is not the unprocessed one: gain control and noise suppression tilt a sweep. */
    private fun micSourceWarning(): List<String> {
        val src = MeasurementRunner.lastMicSource
        return if (src.isNullOrBlank() || src == "UNPROCESSED") emptyList()
        else listOf("Mic source: $src. This phone does not offer an unprocessed microphone, so automatic gain or noise suppression may slightly tilt the reading.")
    }

    private fun largeErrorWarning(rms: Double): List<String> =
        if (rms > 12.0) listOf(
            "%.0f dB from the target is far more than a typical system (3-8 dB). Check the sound is coming from your speakers and not the phone's own speaker, the mic is at the listening position, and the right target is selected.".format(rms),
        ) else emptyList()

    /** One button for everything: the first measurement of a test is its stock run, every later one is a new run. */
    fun measure() {
        val cur = current
        // on the stock step a test that already has runs is measured again from scratch (the stock reading is replaced)
        if (cur == null || cur.runs.isEmpty() || state.value.tuneStep == TuneStep.STOCK) stockRun() else measureRun()
    }

    /**
     * Adds another run. Every measurement is saved with the EQ that was really in effect and the user's note, whichever
     * way the EQ reaches the speakers: their own equipment, AutoTune's system EQ or inside the app, changed or not.
     * The new suggestion is fitted against the stock response and the EQ that was in effect (a closed loop), so measuring
     * the same correction again can never double it.
     */
    fun measureRun() = run {
        requireReady()
        val cur = current ?: error("No test is open - start a test first")
        check(cur.runs.isNotEmpty()) { "This test has no stock run yet" }
        val src = Source.entries.getOrElse(cur.sourceIdx) { Source.PHONE }
        val target = targetFor(cur)
        val st = state.value
        val viaSystemEq = st.systemEqOn
        val prev = cur.runs.last()
        // the EQ on screen follows the latest ticked run; that is the one the user set, so that is what was applied
        val focusRun = cur.runs[st.selectedRuns.filter { it in cur.runs.indices }.maxOrNull() ?: cur.runs.lastIndex]
        val shown = inEffectAfter(focusRun, st.editFilters, st.sliderLayer)
        val repeat = st.repeatNext
        // the in-app route keeps playing through the EQ for every run until the route changes ("Measure again" too)
        val appChain = if (st.applyWay == ApplyWay.IN_APP && src == Source.PHONE && !viaSystemEq) {
            if (repeat) EqChain.withSafePreamp(prev.eq) else shown
        } else null
        // measure the EQ itself: no flavour, safe preamp (a limiter at work would flatten the sweep)
        if (viaSystemEq) SystemEq.apply(EqChain.withSafePreamp(shown.filters))
        val systemBands = if (viaSystemEq) SystemEq.applied.ifEmpty { shown.filters } else null
        openMeasureView(if (repeat) "Run ${cur.runs.size} (repeat)" else "Run ${cur.runs.size}", src)
        val m = acquire(src, cur.gateMs, st.channel, appChain) ?: return@run
        val rms = EqFitter.score(m.magDb, m.grid, target).first
        val idx = cur.runs.size
        val applied = appliedFor(if (repeat) ApplyWay.NONE else st.applyWay, prev, shown, appChain, systemBands)

        // the treble vanishing with the system EQ on is the Samsung failure: switch it off and say why
        if (viaSystemEq && trebleCollapsed(cur.runs[0].magDb, m.magDb, m.grid.freqs)) {
            setSystemEq(false)
            prefs.edit().putBoolean("systemEqSuspect", true).apply()
            state.update { it.copy(systemEqSuspect = true, tuneStep = TuneStep.APPLY) }
            Diag.log(ctx(), "system_eq_damaged", mapOf("run" to idx))
            error(
                "With AutoTune EQ on, the treble (2-8 kHz) dropped by more than 12 dB compared with the stock run. This phone's " +
                    "system EQ seems to damage the sound, so I switched it off and did not save this reading. Preview it inside the app or set the EQ on your equipment.",
            )
        }

        // a clean reading through the system EQ clears an old "damaged the treble" verdict
        if (viaSystemEq && st.systemEqSuspect) { prefs.edit().putBoolean("systemEqSuspect", false).apply(); state.update { it.copy(systemEqSuspect = false) } }

        // the system EQ has 31 fixed bands: only suggest what it can realise
        // how the speakers followed this EQ: boosts they could not play (deep bass) are not asked for again
        val follow = eqFollow(cur.runs[0].magDb, m.magDb, applied, m.grid)
        val floor = maxOf(cur.boostFloorHz, follow?.deafBelowHz ?: 0.0)
        val cfg = (if (viaSystemEq) SYSTEM_EQ_FIT else FitConfig()).copy(boostMinHz = floor)
        val label = if (repeat) "Run $idx (repeat)" else "Run $idx"
        val loop = withContext(Dispatchers.Default) {
            EqFitter.fitFromStock(cur.runs[0].magDb, applied, m.magDb, m.grid, target, cfg)
        }
        // with AutoTune EQ on, keep the parametric EQ behind the 31 fixed bands (that is what the user chose and keeps)
        val param = if (systemBands != null) (SystemEq.chain?.filters ?: shown.filters) else null
        val next = nextSuggestion(cur.runs, rms, label, loop.fit.filters, {
            EqFitter.fit(cur.runs[0].magDb, m.grid, target, cfg, stepScale = 0.5).filters
        }, newEq = param ?: applied)
        val warns = (m.warnings + micSourceWarning() + (if (loop.ineffective) listOf(
            "The EQ that was supposed to be on had almost no effect on this reading, so the suggestion was not stacked on top of it. Check it is really applied.",
        ) else emptyList())).distinct()
        val run = SavedRun(
            System.currentTimeMillis(), rms, m.magDb,
            filters = next.filters, label = label, note = st.runNote.trim(), applied = applied,
            warnings = warns, calibration = calName(), complete = true, appliedParam = param, converged = next.converged,
        )
        val updated = cur.copy(runs = cur.runs + run, boostFloorHz = floor)
        withContext(Dispatchers.IO) { TestStore.save(getApplication(), updated) }
        current = updated
        refreshTests()
        Diag.log(
            ctx(), "run_saved",
            mapOf(
                "test" to cur.name, "run" to label, "route" to st.applyWay, "repeat" to repeat, "rmsDb" to rms,
                "stockRmsDb" to cur.runs[0].rmsDb, "applied" to Diag.bands(applied), "appliedPreampDb" to EqChain.withSafePreamp(applied).preampDb,
                "eqEffect" to loop.effectiveness, "eqIneffective" to loop.ineffective, "nextSuggestion" to Diag.bands(next.filters),
                "note" to next.note, "warnings" to warns,
            ),
        )
        // a new suggestion exists: show it, not a profile or slider changes from before
        resetToSuggestion()
        state.update { it.copy(selectedRuns = it.selectedRuns + idx, runNote = "") }
        refreshView()
        val line = "$label: %.2f dB RMS".format(rms) + (next.note?.let { " - $it" } ?: "")
        state.update {
            it.copy(
                warnings = emptyList(), lastWarnings = warns, log = (it.log + line).takeLast(12),
                status = null, tuneStep = TuneStep.RESULT, repeatNext = false,
            )
        }
        runDone.tryEmit(Unit)
        current?.let { t -> showSaved(t) }
    }

    // ---------------------------------------------------------------- the tuning workflow (Tune tab)

    // ---------------------------------------------------------------- the measuring screen

    private fun openMeasureView(title: String, src: Source) {
        state.update {
            it.copy(
                measureView = MeasureView(
                    title, MeasureStage.MEASURING, "", startChecks(), pendingPositions.size + 1, it.positions, src == Source.PHONE,
                ),
            )
        }
    }

    fun closeMeasureView() = state.update { it.copy(measureView = null) }

    /**
     * Does the EQ really reach the speakers? Measures once with AutoTune EQ off and once with the EQ in use on, then
     * compares the change with what the EQ should do. Leaves AutoTune EQ the way it was. Phone route only.
     */
    fun eqCheck() = run {
        requireReady()
        val chain = activeChain()?.takeIf { it.filters.isNotEmpty() } ?: error("Pick an EQ with some bands first.")
        val wasOn = state.value.systemEqOn
        val gate = current?.gateMs ?: state.value.gateMs
        val ch = state.value.channel
        openMeasureView("EQ check 1 of 2: EQ off", Source.PHONE)
        try {
            SystemEq.off()
            val off = measureOnce(Source.PHONE, gate, ch)
            checkUsable(off)
            SystemEq.apply(chain)?.let { error(it) }
            // what the phone's 31 bands really do with this EQ (preamp left out: only the shape is compared)
            val realised = SystemEq.applied
            state.update { it.copy(measureView = it.measureView?.copy(title = "EQ check 2 of 2: EQ on")) }
            val on = measureOnce(Source.PHONE, gate, ch)
            checkUsable(on)
            val so = grid.smoothOctave(off.magDb, 6.0)
            val sn = grid.smoothOctave(on.magDb, 6.0)
            val expected = DoubleArray(grid.size) { i -> realised.sumOf { it.magnitudeDb(grid.freqs[i]) } }
            val result = judgeEqCheck(grid.freqs, so, sn, expected)
            // the measured change, level-matched to the EQ over the same band the check uses
            val idx = grid.freqs.indices.filter { grid.freqs[it] in 60.0..10000.0 }
            val shift = idx.sumOf { sn[it] - so[it] - expected[it] } / idx.size
            val change = DoubleArray(grid.size) { sn[it] - so[it] - shift }
            val chart = ChartData(grid.freqs, listOf(Curve("Measured change", change, 1)), DoubleArray(grid.size), expected, "What the EQ asks for")
            val view = EqCheckView(result, chart, state.value.eqSource, fmtDate(System.currentTimeMillis()))
            state.update {
                it.copy(
                    eqCheck = view,
                    measureView = it.measureView?.copy(
                        stage = MeasureStage.SAVED, message = "EQ check done",
                        result = when (result.severity) { 0 -> "The EQ reaches the speakers"; 1 -> "Only part of the EQ arrives"; 2 -> "The EQ does not arrive"; else -> "EQ too flat to check" },
                    ),
                )
            }
            Diag.log(ctx(), "eq_check", mapOf("effect" to result.effect, "residualDb" to result.residualDb, "severity" to result.severity, "output" to state.value.outputName, "chain" to Diag.chain(chain)))
        } finally {
            // back to how it was: on with the EQ in use, or off
            if (wasOn) SystemEq.apply(playChain()) else SystemEq.off()
        }
    }

    fun clearEqCheck() = state.update { it.copy(eqCheck = null) }

    /** Runs the test a check suggestion asks for. */
    fun doCheck(a: CheckAction) {
        when (a) {
            CheckAction.REMEASURE_STOCK -> tuneGo(TuneStep.STOCK)
            CheckAction.MORE_SPOTS -> { setPositions(3); refreshView() }
            CheckAction.REPEAT -> tuneRepeat()
            CheckAction.EQ_CHECK -> eqCheck()
            CheckAction.NONE -> Unit
        }
    }

    private fun startChecks(): List<CheckItem> = listOf(
        CheckItem("Microphone (raw sound)", 0), CheckItem("Sound output", 0), CheckItem("Start beeps", 0), CheckItem("Whole sweep heard", 0),
        CheckItem("No clipping", 0), CheckItem("Signal above the background", 0), CheckItem("Every frequency heard", 0),
    )

    /** The checks behind a reading, in plain words, from what the analysis found. */
    private fun checksFor(m: Measurement, src: Source): List<CheckItem> {
        val st = state.value
        val all = m.warnings + listOfNotNull(m.fatal)
        fun has(t: String) = all.firstOrNull { t in it }
        val micSrc = MeasurementRunner.lastMicSource
        val out = mutableListOf<CheckItem>()
        // the source decides whether Android may process the sound; any voice processing found is switched off
        val fx = MeasurementRunner.lastMicEffects
        val processed = fx != null && fx.contains("could not")
        out += CheckItem(
            "Microphone (raw sound)", if ((micSrc == null || micSrc == "UNPROCESSED") && !processed) 1 else 2,
            listOfNotNull(st.micName, micSrc?.lowercase(), fx).joinToString(" · "),
        )
        if (src == Source.PHONE) {
            out += CheckItem("Sound output", if (st.outputToMic) 3 else 1, st.outputName ?: "where the phone plays music")
            val full = lastCapture["usedFullSearch"] == true
            val marker = lastCapture["markerAt"] != null
            out += CheckItem("Start beeps", if (marker && !full) 1 else 2, if (marker && !full) "found" else "not clear: the sweep was found by searching the whole recording")
        } else {
            out += CheckItem("Sound output", 1, "another device")
            out += CheckItem("Start beeps", if ((lastCapture["markerAt"] as? Int ?: -1) >= 0) 1 else 2, if ((lastCapture["markerAt"] as? Int ?: -1) >= 0) "found" else "not heard: found the sound by when it started")
        }
        out += has("cut off")?.let { CheckItem("Whole sweep heard", 3, "the end was cut off") } ?: CheckItem("Whole sweep heard", 1)
        out += has("clipped")?.let { CheckItem("No clipping", 3, "too loud: turn the volume down") } ?: CheckItem("No clipping", 1)
        val snr = m.snrDb
        out += when {
            snr == null -> CheckItem("Signal above the background", if (m.fatal != null) 3 else 1)
            snr < 20 -> CheckItem("Signal above the background", 3, "%.0f dB: far too quiet".format(snr))
            snr < 35 -> CheckItem("Signal above the background", 2, "%.0f dB: a bit quiet".format(snr))
            else -> CheckItem("Signal above the background", 1, "%.0f dB".format(snr))
        }
        out += has("barely heard between")?.let { w ->
            CheckItem("Every frequency heard", if (m.fatal?.contains("barely heard between") == true) 3 else 2, w.substringAfter("between about ").substringBefore(" (").let { "not between $it" })
        } ?: CheckItem("Every frequency heard", 1)
        return out
    }

    private fun showSaved(t: SavedTest) {
        val last = t.runs.last()
        val result = if (t.runs.size == 1) "%.1f dB from the target".format(last.rmsDb)
        else "%.1f dB from the target (stock %.1f)".format(last.rmsDb, t.runs[0].rmsDb)
        state.update { it.copy(measureView = it.measureView?.copy(stage = MeasureStage.SAVED, message = "${last.label} saved", result = result)) }
    }

    /** Mic spots measured for a run belong to one EQ state: changing how the EQ is applied starts the spots again. */
    private fun restartSpots() {
        pendingPositions.clear()
        state.update { it.copy(positionsDone = 0) }
    }

    fun tuneGo(step: TuneStep) {
        // moving on inside the workflow makes a remembered place stale; only the Tune button screen keeps it
        state.update {
            it.copy(
                tuneStep = step, status = it.status?.takeIf { s -> s.startsWith("Failed: ") && step == it.tuneStep },
                resumeStep = if (step == TuneStep.HOME) it.resumeStep else null,
            )
        }
        Diag.log(ctx(), "tune_step", mapOf("step" to step))
    }

    /** Back inside the workflow: the previous screen (see [previousStep]); leaving keeps the place like Exit. */
    fun tuneBack() {
        val to = previousStep(state.value.tuneStep, current?.runs?.size ?: 0)
        if (to == TuneStep.HOME) tuneExit() else tuneGo(to)
    }

    /** The big Tune button: always asks first whether this is a fresh start. */
    fun tuneStart() = tuneGo(TuneStep.FRESH)

    /** Fresh start: every EQ off (AutoTune's here, the user's on their equipment), then a new test is set up. */
    fun tuneFresh() {
        if (state.value.systemEqOn) setSystemEq(false)
        newTest()
        resetToSuggestion()
        tuneGo(TuneStep.SETUP)
    }

    /** Carry on with the open test where it stopped. */
    fun tuneContinue() {
        val t = current
        if (t == null) { tuneGo(TuneStep.SETUP); return }
        tuneGo(resumeAt(state.value.resumeStep.takeIf { resumeTestId == t.id }, t.runs.size))
    }

    private var resumeTestId: String? = null

    /** Leaves the workflow and remembers the step, so Continue comes back to the same place. */
    fun tuneExit() {
        val st = state.value.tuneStep
        val t = current
        if (t != null && st in RESUMABLE) { resumeTestId = t.id; state.update { it.copy(resumeStep = st) } }
        tuneGo(TuneStep.HOME)
    }

    fun tuneSetup(name: String, targetIdx: Int, source: Source, positions: Int, gateMs: Int?) {
        startNewTest(name, targetIdx, source)
        setPositions(positions)
        gateMs?.let { setGate(it) }
        tuneGo(TuneStep.STOCK)
    }

    /** Apply the suggested EQ the chosen way, then measure it. */
    fun tuneApply(way: ApplyWay) {
        when (way) {
            ApplyWay.AUTOTUNE_EQ -> applyWithAutoTuneEq()
            ApplyWay.IN_APP -> tryInApp()
            else -> appliedOnEquipment()
        }
        if (way != ApplyWay.AUTOTUNE_EQ || state.value.systemEqOn) tuneGo(TuneStep.VERIFY)
    }

    /** From the result: take the new suggestion and apply it (another round). */
    fun tuneRefine() { resetToSuggestion(); tuneGo(TuneStep.APPLY) }

    /** From the result: measure again with nothing changed. */
    fun tuneRepeat() { measureUnchanged(); tuneGo(TuneStep.VERIFY) }

    fun tuneDiscard() {
        discardLastRun()
        val t = current ?: return
        tuneGo(if (t.runs.size <= 1) TuneStep.STOCK_RESULT else TuneStep.RESULT)
    }

    /**
     * Finish: keep the EQ that was measured best (the latest run's, unless an earlier run was better), save it as a
     * profile, make it the one in use, and for the phone route keep AutoTune EQ on with exactly that EQ.
     */
    fun tuneKeep(useBest: Boolean) {
        val t = current ?: return
        val withEq = t.runs.indices.filter { t.runs[it].eq.isNotEmpty() }
        val idx = if (useBest) withEq.minByOrNull { t.runs[it].rmsDb } else withEq.lastOrNull()
        if (idx == null) {
            // no run was measured with an EQ: nothing to keep, the system stays as it is
            state.update { it.copy(status = "No run had an EQ applied, so there is nothing to keep.") }
            tuneGo(TuneStep.DONE)
            return
        }
        val run = t.runs[idx]
        // the system EQ records its 31 sampled bands as applied; keep the parametric EQ that was given to it instead
        val bands = run.eq
        // one kept EQ per test: finishing again updates it (same id, so automatic switching keeps working)
        val old = state.value.profiles.firstOrNull { it.test == t.name && it.name.startsWith("${t.name} · tuned") }
        val p = EqProfile(
            old?.id ?: java.util.UUID.randomUUID().toString(),
            uniqueProfileName("${t.name} · tuned (${run.label})", old?.id),
            "tuning", deviceText(), t.name, "measured in ${run.label}", System.currentTimeMillis(), bands,
        )
        storeProfiles(state.value.profiles.filter { it.id != p.id } + p)
        prefs.edit().putString("activeProfile", p.id).apply()
        state.update { it.copy(activeProfileId = p.id, sliderLayer = emptyList()) }
        saveSliderLayer(emptyList())
        setEdit(bands, "run:${t.id}:$idx")
        val way = state.value.applyWay
        if (t.sourceIdx == Source.PHONE.ordinal && way != ApplyWay.EQUIPMENT) {
            if (state.value.systemEqOn) refreshSystemEq() else setSystemEq(true)
        }
        Diag.log(ctx(), "tune_done", mapOf("test" to t.name, "run" to run.label, "rmsDb" to run.rmsDb, "profile" to p.name, "bands" to Diag.bands(bands)))
        tuneGo(TuneStep.DONE)
    }

    /** Apply the EQ by switching on AutoTune's own EQ for this phone's audio, then measure. */
    fun applyWithAutoTuneEq() {
        restartSpots()
        systemRealisableStock()
        if (state.value.systemEqOn) refreshSystemEq() else setSystemEq(true)
        if (state.value.systemEqOn) {
            state.update { it.copy(applyWay = ApplyWay.AUTOTUNE_EQ, repeatNext = false, status = null) }
        }
    }

    /**
     * Before the first AutoTune EQ: the stock suggestion was fitted for exact (narrow) bands, which the 31 fixed bands
     * of the system EQ cannot reproduce, so the first run would only ask to cut the same spots again. Refit it with
     * bands the system EQ can realise, so what is shown, applied and recorded is the same EQ from the start.
     * Left alone once the user changed the EQ (own bands, sliders, a profile) or a run already followed.
     */
    private fun systemRealisableStock() {
        val t = current ?: return
        val st = state.value
        if (t.runs.size != 1 || st.editFilters != null || st.sliderLayer.isNotEmpty() || !st.eqPick.startsWith("suggest:")) return
        val stock = t.runs[0]
        if (fitsSystemEq(stock.filters)) return
        val f = runCatching { EqFitter.fit(stock.magDb, grid, targetFor(t), SYSTEM_EQ_FIT).filters }.getOrNull() ?: return
        val run = SavedRun(
            stock.whenMillis, stock.rmsDb, stock.magDb, f, stock.label, stock.note, stock.applied, stock.warnings,
            stock.calibration, complete = true, appliedParam = stock.appliedParam, converged = stock.converged,
        )
        val u = t.copy(runs = listOf(run))
        current = u
        viewModelScope.launch { withContext(Dispatchers.IO) { TestStore.save(ctx(), u) }; refreshTests() }
        Diag.log(ctx(), "stock_refit_for_system_eq", mapOf("test" to t.name, "before" to Diag.bands(stock.filters), "after" to Diag.bands(f)))
        resetToSuggestion()
        refreshView()
    }

    /** The user set the EQ on their own equipment (head unit, DSP, EQ app). AutoTune's EQ goes off so the two don't stack. */
    fun appliedOnEquipment() {
        restartSpots()
        if (state.value.systemEqOn) setSystemEq(false)
        state.update { it.copy(applyWay = ApplyWay.EQUIPMENT, repeatNext = false, status = null) }
    }

    /** Phone route: play the sweep through the EQ inside the app, to preview it before changing anything. */
    fun tryInApp() {
        restartSpots()
        if (state.value.systemEqOn) setSystemEq(false)
        state.update { it.copy(applyWay = ApplyWay.IN_APP, repeatNext = false, status = null) }
    }

    /** Nothing was changed since the previous run: the same route, the same EQ, to see how repeatable the reading is. */
    fun measureUnchanged() {
        restartSpots()
        state.update { it.copy(repeatNext = true, status = null) }
    }

    /** Throws away the latest run (not the stock run), for a reading that went wrong. The suggestion is rebuilt from the run before. */
    fun discardLastRun() {
        val t = current ?: return
        if (t.runs.size < 2) return
        val updated = t.copy(runs = t.runs.dropLast(1))
        TestStore.save(getApplication(), updated)
        current = updated
        refreshTests()
        Diag.log(ctx(), "run_discarded", mapOf("test" to t.name, "run" to t.runs.last().label))
        state.update { it.copy(selectedRuns = it.selectedRuns.filter { i -> i < updated.runs.size }.toSet() + updated.runs.lastIndex, lastWarnings = emptyList()) }
        refreshView()
        state.update { it.copy(status = "Run discarded.") }
    }

    /** The EQ to show and apply is AutoTune's suggestion for the open test, not a profile or older hand edits. */
    private fun resetToSuggestion() {
        state.update { it.copy(sliderLayer = emptyList(), convertMessage = null) }
        saveSliderLayer(emptyList())
        if (state.value.editFilters != null) { saveMyEq(null); state.update { it.copy(editFilters = null) } }
        setPick(current?.id?.let { "suggest:$it" } ?: "")
        rebuildActive()
        buildEqChart()
    }

    /** EQ tab: use the tuning suggestion of the open test (and stop using a profile). */
    fun useSuggested() {
        prefs.edit().remove("activeProfile").apply()
        state.update { it.copy(activeProfileId = null) }
        resetToSuggestion()
        if (state.value.systemEqOn) refreshSystemEq()
    }

    private fun requireReady() {
        check(state.value.hasPermission) { "Microphone permission is needed" }
    }

    /** Writes a test file for the other device and opens the share sheet. */
    fun shareTestFile(kind: TestFileKind) {
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val file = withContext(Dispatchers.IO) {
                    val (name, samples) = when (kind) {
                        TestFileKind.SWEEP -> "autotune_sweep.wav" to TestSignals.sweepFile(fs)
                        TestFileKind.PINK -> "autotune_pink_noise.wav" to TestSignals.pinkFile(fs)
                        TestFileKind.COMBINED -> "autotune_sweep_and_pink.wav" to TestSignals.combinedFile(fs)
                        TestFileKind.SWEEP_LEFT -> "autotune_sweep_left_only.wav" to TestSignals.sweepFile(fs)
                        TestFileKind.SWEEP_RIGHT -> "autotune_sweep_right_only.wav" to TestSignals.sweepFile(fs)
                    }
                    val only = when (kind) { TestFileKind.SWEEP_LEFT -> 0; TestFileKind.SWEEP_RIGHT -> 1; else -> -1 }
                    File(ctx.cacheDir, name).also { it.writeBytes(Wav.encodePcm16(samples, fs, 2, only)) }
                }
                fileToShare.emit(ShareFiles(listOf(file), "audio/wav", "Send the test file to the other device"))
            } catch (e: Exception) {
                state.update { it.copy(status = "Could not create the test file: ${e.message}") }
            }
        }
    }

    // ---------------------------------------------------------------- live RTA

    fun startRta(withEq: Boolean) {
        if (state.value.busy) return
        try {
            requireReady()
        } catch (e: Exception) {
            state.update { it.copy(status = "Failed: ${e.message}") }
            return
        }
        stopClip()
        val target = targetAt(state.value.targetIdx)
        val tgt = Rta.targetBars(target)
        val chain = activeChain()
        if (state.value.source == Source.PHONE && state.value.liveSignal != LiveSignal.NONE) {
            player.play(viewModelScope, liveClip(state.value.liveSignal), if (withEq) chain else null) { }
        }
        rtaBars.value = null
        level.value = null
        leqEnergy = 0.0
        leqBlocks = 0
        rta.start(
            viewModelScope, UsbMic.find(getApplication()), fs, calibration,
            onBars = { rtaBars.value = Rta.relativeToTarget(it, tgt) },
            onLevel = { rms, peak ->
                leqEnergy += Math.pow(10.0, rms / 10.0)
                leqBlocks++
                val prev = level.value
                val smooth = if (prev == null) rms else 0.8 * prev.rmsDb + 0.2 * rms
                val hold = if (prev == null) peak else maxOf(peak, prev.peakDb - 1.0)
                // Estimate for a UMIK-1 at its default gain: 0 dBFS is about 112 dB SPL, adjusted by the calibration file's
                // sensitivity factor and the user's offset. Reliable only if Android's mic gain is unchanged.
                val spl = smooth + 112.0 - (calibration?.sensFactorDb ?: 0.0) + state.value.splOffsetDb
                level.value = LevelInfo(smooth, hold, 10.0 * log10(leqEnergy / leqBlocks), spl)
            },
            onStopped = { err ->
                player.stop()
                state.update { s -> s.copy(rtaRunning = false, status = err?.let { e -> "RTA stopped: $e" } ?: s.status) }
            },
        )
        state.update { it.copy(rtaRunning = true, rtaWithEq = withEq) }
    }

    fun setRtaNoise(on: Boolean) = setLiveSignal(if (on) LiveSignal.PINK else LiveSignal.NONE)

    private fun liveClip(sig: LiveSignal): Clip = when (sig) {
        LiveSignal.SWEEP -> {
            // a 10 s sweep and a short pause, repeated, so the live spectrum keeps showing it
            val sw = Sweep.generate(fs, 10.0)
            val mono = FloatArray(sw.size + fs * 3 / 2)
            sw.copyInto(mono)
            val st = FloatArray(mono.size * 2)
            for (i in mono.indices) { st[2 * i] = mono[i]; st[2 * i + 1] = mono[i] }
            Clip("Sweep", st, fs)
        }
        else -> clip.takeIf { it.name == "Pink noise" } ?: pinkClip()
    }

    /** Pink noise, a repeating sweep, or nothing, played from the phone while the live spectrum runs. */
    fun setLiveSignal(sig: LiveSignal) {
        state.update { it.copy(liveSignal = sig, rtaNoise = sig != LiveSignal.NONE) }
        val st = state.value
        if (st.rtaRunning && st.source == Source.PHONE) {
            player.stop()
            if (sig != LiveSignal.NONE) player.play(viewModelScope, liveClip(sig), if (st.rtaWithEq) activeChain() else null) { }
        }
    }

    /** Fine-tune the SPL estimate against a reference meter or calibrator. */
    fun adjustSplOffset(delta: Double) {
        val v = (state.value.splOffsetDb + delta).coerceIn(-30.0, 30.0)
        prefs.edit().putFloat("splOffset", v.toFloat()).apply()
        state.update { it.copy(splOffsetDb = v) }
    }

    fun setRtaEq(withEq: Boolean) {
        player.setChain(if (withEq) activeChain() else null)
        state.update { it.copy(rtaWithEq = withEq) }
    }

    fun stopRta() {
        rta.stop()
        if (state.value.rtaRunning) player.stop()
        state.update { it.copy(rtaRunning = false) }
    }


    // ---------------------------------------------------------------- listening (A/B)

    fun pickClip(uri: Uri) {
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val col = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && col >= 0) c.getString(col) else null
                } ?: "clip"
                state.update { it.copy(status = "Decoding $name...") }
                clip = ClipDecoder.decode(ctx, uri, name)
                state.update { it.copy(clipName = name, status = "Clip ready: $name") }
            } catch (e: Exception) {
                state.update { it.copy(status = "Could not load that clip: ${e.message}") }
            }
        }
    }

    fun playClip(withEq: Boolean) {
        val chain = activeChain()
        if (player.isPlaying) {
            player.setChain(if (withEq) chain else null)
            state.update { it.copy(playingWithEq = withEq) }
            return
        }
        stopRta()
        player.play(viewModelScope, clip, if (withEq) chain else null) {
            state.update { it.copy(playing = false) }
        }
        state.update { it.copy(playing = true, playingWithEq = withEq) }
    }

    fun stopClip() {
        if (!state.value.rtaRunning) player.stop()
        state.update { it.copy(playing = false) }
    }


    // ---------------------------------------------------------------- profiles and automation

    private fun ctx() = getApplication<Application>()

    private fun refreshProfiles() {
        val list = ProfileStore.load(ctx())
        val rules = ProfileStore.loadRules(ctx())
        state.update { it.copy(profiles = list, rules = rules, activeProfileId = prefs.getString("activeProfile", null)?.takeIf { id -> list.any { p -> p.id == id } }) }
        ProfileController.publishShortcuts(ctx(), list)
        rebuildChoices()
    }

    /** Called when the app comes to the front: pick up anything a shortcut, routine or the Bluetooth watcher changed. */
    fun syncExternal() {
        val list = ProfileStore.load(ctx())
        val activeId = prefs.getString("activeProfile", null)?.takeIf { id -> list.any { p -> p.id == id } }
        val changed = activeId != state.value.activeProfileId
        state.update {
            it.copy(
                profiles = list, rules = ProfileStore.loadRules(ctx()), activeProfileId = activeId, systemEqOn = SystemEq.chain != null,
                automationOn = prefs.getBoolean("automationOn", false),
            )
        }
        if (changed && activeId != null) list.firstOrNull { p -> p.id == activeId }?.let { p -> setEdit(p.filters, "profile:${p.id}") }
        refreshBluetooth()
    }

    private fun uniqueProfileName(base: String, except: String? = null): String {
        val taken = state.value.profiles.filter { it.id != except }.map { it.name.lowercase() }.toSet()
        if (base.lowercase() !in taken) return base
        var n = 2
        while ("$base $n".lowercase() in taken) n++
        return "$base $n"
    }

    private fun deviceText(): String = state.value.outputName ?: state.value.source.title

    private fun storeProfiles(list: List<EqProfile>) {
        ProfileStore.save(ctx(), list)
        refreshProfiles()
    }

    /** Saves the EQ being used now. Where it came from decides how it is described. */
    fun saveProfile(name: String) {
        val s = state.value
        val chain = activeChain()?.takeIf { it.filters.isNotEmpty() } ?: return
        val t = current
        val fromTuning = s.editFilters == null && t != null && t.runs.isNotEmpty()
        val focus = s.selectedRuns.maxOrNull() ?: 0
        val p = EqProfile(
            id = java.util.UUID.randomUUID().toString(),
            name = uniqueProfileName(name.trim().ifBlank { if (fromTuning) "${t!!.name} EQ" else "My EQ" }),
            kind = if (fromTuning) "tuning" else "hand",
            device = if (fromTuning) deviceText() else "",
            test = if (fromTuning) t!!.name else "",
            run = if (fromTuning) runWording(t!!, focus) else "",
            created = System.currentTimeMillis(),
            filters = chain.filters,
        )
        storeProfiles(state.value.profiles + p)
        prefs.edit().putString("activeProfile", p.id).apply()
        state.update { it.copy(activeProfileId = p.id) }
    }

    private fun runWording(t: SavedTest, focus: Int) =
        if (focus == 0) "from the stock reading" else "after ${t.runs.getOrNull(focus)?.label ?: "run $focus"}"

    /**
     * Up to 1.10 every applied tuning step was also saved as a profile ("LG · Run 3"), which buried the real ones. Each
     * run keeps its own EQ now, so those automatic copies are removed once (only when their test still exists).
     */
    private fun cleanAutoProfiles() {
        if (prefs.getBoolean("autoProfilesCleaned", false)) return
        val tests = savedTests.map { it.name }.toSet()
        val auto = Regex(" · (first EQ|Run \\d+)$")
        val all = ProfileStore.load(ctx())
        val keep = all.filterNot { it.kind == "tuning" && it.test in tests && auto.containsMatchIn(it.name) }
        if (keep.size != all.size) {
            storeProfiles(keep)
            Diag.log(ctx(), "auto_profiles_removed", mapOf("count" to all.size - keep.size))
        }
        prefs.edit().putBoolean("autoProfilesCleaned", true).apply()
    }

    /** Loads a profile as the working EQ and switches it on (live-switches it if the EQ is already on). */
    fun useProfile(id: String) {
        val p = state.value.profiles.firstOrNull { it.id == id } ?: return
        prefs.edit().putString("activeProfile", id).apply()
        state.update { it.copy(activeProfileId = id, sliderLayer = emptyList()) }
        saveSliderLayer(emptyList())
        setEdit(p.filters, "profile:$id")
        if (state.value.systemEqOn) refreshSystemEq() else setSystemEq(true)
        Diag.log(ctx(), "profile_used", mapOf("profile" to p.name, "bands" to Diag.bands(p.filters)))
    }

    /** Makes a profile the EQ in use without switching the EQ on (it changes live when the EQ is already on). */
    fun selectProfile(id: String) {
        val p = state.value.profiles.firstOrNull { it.id == id } ?: return
        prefs.edit().putString("activeProfile", id).apply()
        state.update { it.copy(activeProfileId = id, sliderLayer = emptyList()) }
        saveSliderLayer(emptyList())
        setEdit(p.filters, "profile:$id")
        Diag.log(ctx(), "profile_selected", mapOf("profile" to p.name))
    }

    // ---------------------------------------------------------------- diagnostics

    fun setKeepRecordings(on: Boolean) {
        prefs.edit().putBoolean("keepRecordings", on).apply()
        state.update { it.copy(keepRecordings = on) }
    }

    fun clearDiagnostics() {
        Diag.clear(ctx())
        state.update { it.copy(status = "Diagnostics cleared.") }
    }

    /** One zip with the log, the last recordings, the saved tests and a summary of the settings, for the share sheet. */
    fun shareDiagnostics() {
        viewModelScope.launch {
            try {
                val st = state.value
                val info = buildString {
                    appendLine("AutoTune ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("Device: ${Diag.deviceLine()}")
                    appendLine("Mic: ${st.micName} (phone mic: ${st.phoneMic}), last source: ${MeasurementRunner.lastMicSource}")
                    appendLine("Calibration: ${calName()} ${st.calibrationInfo.orEmpty()}")
                    appendLine("Output: ${st.outputName} (choice ${st.outputChoice})")
                    appendLine("Open test: ${st.testName.ifBlank { "none" }}, target ${targetAt(st.targetIdx).name}, source ${st.source}, gate ${st.gateMs} ms, FDW ${st.fdwCycles}, spots ${st.positions}")
                    appendLine("Route: ${st.applyWay}, tune step ${st.tuneStep}")
                    appendLine("AutoTune EQ: ${if (st.systemEqOn) "on" else "off"}, source: ${st.eqSource}, suspect: ${st.systemEqSuspect}")
                    st.exportChain?.let { c -> appendLine("EQ in effect: preamp %.2f dB, ".format(c.preampDb) + c.filters.joinToString { f -> "%.0f Hz %+.1f dB Q%.2f".format(f.freqHz, f.gainDb, f.q) }) }
                    SystemEq.chain?.let { c -> appendLine("System EQ chain: preamp %.2f dB, ${c.filters.size} bands; realised as ${SystemEq.applied.size} fixed bands".format(c.preampDb)) }
                    appendLine("Profiles: " + st.profiles.joinToString { p -> p.name + (if (p.id == st.activeProfileId) " (active)" else "") })
                    appendLine("Automation: ${st.automationOn}, rules ${st.rules.size}")
                }
                val file = withContext(Dispatchers.IO) { Diag.export(ctx(), info) }
                fileToShare.emit(ShareFiles(listOf(file), "application/zip", "Send AutoTune diagnostics"))
            } catch (e: Exception) {
                state.update { it.copy(status = "Could not collect diagnostics: ${e.message}") }
            }
        }
    }

    fun updateProfile(id: String) {
        val chain = activeChain()?.takeIf { it.filters.isNotEmpty() } ?: return
        storeProfiles(state.value.profiles.map { if (it.id == id) it.copy(filters = chain.filters) else it })
    }

    fun renameProfile(id: String, name: String) {
        val nm = name.trim()
        if (nm.isBlank()) return
        storeProfiles(state.value.profiles.map { if (it.id == id) it.copy(name = uniqueProfileName(nm, id)) else it })
    }

    fun deleteProfile(id: String) {
        storeProfiles(state.value.profiles.filter { it.id != id })
        ProfileStore.saveRules(ctx(), ProfileStore.loadRules(ctx()).filter { it.profileId != id })
        if (prefs.getString("activeProfile", null) == id) prefs.edit().remove("activeProfile").apply()
        refreshProfiles()
    }

    fun refreshBluetooth() {
        val granted = android.os.Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(ctx(), android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val devices = if (!granted) emptyList() else runCatching {
            val bm = ctx().getSystemService(android.bluetooth.BluetoothManager::class.java)
            @Suppress("MissingPermission")
            bm?.adapter?.bondedDevices.orEmpty().map { d ->
                @Suppress("MissingPermission")
                val cls = d.bluetoothClass
                val kind = when (cls?.deviceClass) {
                    android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> "Car audio"
                    android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES, android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET -> "Headphones"
                    android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER, android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO -> "Speaker"
                    android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> "Hands-free"
                    else -> when (cls?.majorDeviceClass) {
                        android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio"
                        android.bluetooth.BluetoothClass.Device.Major.PHONE -> "Phone"
                        android.bluetooth.BluetoothClass.Device.Major.COMPUTER -> "Computer"
                        android.bluetooth.BluetoothClass.Device.Major.WEARABLE -> "Wearable"
                        android.bluetooth.BluetoothClass.Device.Major.PERIPHERAL -> "Peripheral"
                        else -> "Bluetooth"
                    }
                }
                @Suppress("MissingPermission")
                BtDevice(d.address, d.name ?: d.address, kind, prefs.getLong("btSeen_" + d.address, 0L))
            }.sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())
        state.update { it.copy(btPermission = granted, btDevices = devices) }
    }

    fun setRule(address: String, deviceName: String, profileId: String) {
        val rules = ProfileStore.loadRules(ctx()).filter { !it.address.equals(address, true) } + AutoRule(address, deviceName, profileId)
        ProfileStore.saveRules(ctx(), rules)
        state.update { it.copy(rules = rules) }
        if (state.value.automationOn) startWatcher()
    }

    fun removeRule(address: String) {
        val rules = ProfileStore.loadRules(ctx()).filter { !it.address.equals(address, true) }
        ProfileStore.saveRules(ctx(), rules)
        state.update { it.copy(rules = rules) }
    }

    fun setAutomation(on: Boolean) {
        prefs.edit().putBoolean("automationOn", on).apply()
        dev.autotune.audio.AutoTuneWidgets.update(ctx())
        state.update { it.copy(automationOn = on) }
        if (on) startWatcher()
        else runCatching { ctx().startService(Intent(ctx(), SystemEqService::class.java).setAction(SystemEqService.ACTION_REFRESH)) }
    }

    private fun startWatcher() {
        runCatching { ContextCompat.startForegroundService(ctx(), Intent(ctx(), SystemEqService::class.java).setAction(SystemEqService.ACTION_WATCH)) }
    }

    fun setOffOnDisconnect(on: Boolean) {
        prefs.edit().putBoolean("offOnDisconnect", on).apply()
        state.update { it.copy(offOnDisconnect = on) }
    }

    fun setSmoothing(n: Int) {
        prefs.edit().putInt("smoothing", n).apply()
        state.update { it.copy(smoothing = n) }
        refreshView()
    }

    fun setFdw(cycles: Int) {
        prefs.edit().putInt("fdwCycles", cycles).apply()
        state.update { it.copy(fdwCycles = cycles) }
    }

    fun setShowTarget(onResults: Boolean, on: Boolean) {
        prefs.edit().putBoolean(if (onResults) "showTargetResults" else "showTargetEq", on).apply()
        state.update { if (onResults) it.copy(showTargetResults = on) else it.copy(showTargetEq = on) }
    }

    /** Deletes a test, and the open one is left alone unless it is the one removed. */
    fun deleteTestKeepingOthers(id: String) = deleteTest(id)

    // ---------------------------------------------------------------- AutoTune as the EQ

    /** The EQ in effect: tuned (or the user's own) bands plus the slider layer, with the safe preamp. */
    private fun activeChain(): EqChain? = state.value.exportChain

    /** What AutoTune EQ plays: the EQ in use with the flavour on top and the chosen preamp (never used for measuring). */
    private fun playChain(): EqChain = ProfileController.shaped(ctx(), activeChain()?.filters.orEmpty())

    /** Puts the playing chain on AutoTune EQ when it is on, and shows its preamp. */
    private fun replay() {
        if (!state.value.systemEqOn) return
        val c = playChain()
        SystemEq.apply(c)
        state.update { it.copy(playPreampDb = c.preampDb) }
    }

    fun setFlavour(f: Flavour) {
        prefs.edit().putString("flavour", f.name).apply()
        state.update { it.copy(flavour = f) }
        replay()
    }

    fun setPreampMode(m: PreampMode) {
        prefs.edit().putString("preampMode", m.name).apply()
        state.update { it.copy(preampMode = m) }
        replay()
    }

    fun setManualPreamp(db: Double) {
        val v = (Math.round(db * 2) / 2.0).coerceIn(-15.0, 0.0)
        prefs.edit().putFloat("preampManual", v.toFloat()).apply()
        state.update { it.copy(manualPreampDb = v) }
        replay()
    }

    /** 31 or 61 bands for AutoTune EQ; the EQ that is on is rebuilt at once, and later fits use the matching band widths. */
    fun setSystemEqFine(on: Boolean) {
        prefs.edit().putBoolean("eqFine", on).apply()
        SystemEq.setFine(on)
        state.update { it.copy(systemEqFine = on) }
        replay()
    }

    /** Puts [list] in use (null = the open test's suggestion); [pick] records where it came from. */
    private fun setEdit(list: List<EqFilter>?, pick: String = "own") {
        saveMyEq(list)
        state.update { it.copy(editFilters = list) }
        setPick(if (list == null) current?.id?.let { "suggest:$it" } ?: "" else pick)
        afterEqChange()
    }

    private fun afterEqChange() {
        rebuildActive()
        buildEqChart()
        if (state.value.systemEqOn) activeChain()?.let {
            val c = playChain()
            if (SystemEq.apply(c) == null) { ProfileController.remember(ctx(), it); state.update { st -> st.copy(playPreampDb = c.preampDb) } }
        }
    }

    /** The user's own EQ survives restarts. */
    private fun saveMyEq(list: List<EqFilter>?) {
        if (list == null) prefs.edit().remove("myEq").apply()
        else prefs.edit().putString("myEq", list.joinToString(";") { "${it.freqHz},${it.gainDb},${it.q}" }).apply()
    }

    private fun loadMyEq(): List<EqFilter>? = parseBands(prefs.getString("myEq", null))

    private fun parseBands(raw: String?): List<EqFilter>? {
        raw ?: return null
        return runCatching {
            raw.split(";").filter { it.isNotBlank() }.map { p -> p.split(",").map { it.toDouble() }.let { EqFilter(it[0], it[1], it[2]) } }
        }.getOrNull()
    }

    private fun saveSliderLayer(list: List<EqFilter>) {
        if (list.isEmpty()) prefs.edit().remove("sliderEq").apply()
        else prefs.edit().putString("sliderEq", list.joinToString(";") { "${it.freqHz},${it.gainDb},${it.q}" }).apply()
    }

    private fun loadSliderLayer(): List<EqFilter>? = parseBands(prefs.getString("sliderEq", null))?.takeIf { it.isNotEmpty() }

    /** Start editing exact bands: from the EQ in effect (tuned bands plus sliders), otherwise from an empty list. */
    fun editStart() {
        val l = state.value.exportChain?.filters ?: emptyList()
        state.update { it.copy(sliderLayer = emptyList()) }
        saveSliderLayer(emptyList())
        setEdit(l)
    }
    /** Removes only the slider changes; the EQ in use stays. */
    fun resetSliders() {
        clearSliders()
        afterEqChange()
    }

    fun editReset() {
        state.update { it.copy(sliderLayer = emptyList(), convertMessage = null) }
        saveSliderLayer(emptyList())
        setEdit(null)
    }
    fun editClear() {
        state.update { it.copy(sliderLayer = emptyList(), convertMessage = null) }
        saveSliderLayer(emptyList())
        setEdit(emptyList())
    }

    /**
     * Classic graphic-EQ slider. The tuned EQ stays exactly as it is; a slider adds a small band of its own on top, sized
     * so that the EQ really does the value shown at that frequency (neighbouring bands overlap, so the raw gain would differ).
     */
    fun graphicSet(centres: List<Double>, q: Double, idx: Int, gain: Double) {
        val st = state.value
        val chain = st.exportChain ?: EqChain.EMPTY
        val c = centres[idx]
        val shownNow = chain.responseDb(c) - chain.preampDb
        if (abs(gain - Math.round(shownNow * 2) / 2.0) < 0.01) return // the slider is already there
        val delta = gain - shownNow
        val layer = st.sliderLayer.toMutableList()
        val at = layer.indexOfFirst { abs(kotlin.math.ln(it.freqHz / c)) < 0.01 }
        if (at >= 0) layer[at] = layer[at].copy(gainDb = layer[at].gainDb + delta) else layer += EqFilter(c, delta, q)
        val next = layer.filter { abs(it.gainDb) >= 0.1 }.sortedBy { it.freqHz }
        state.update { it.copy(sliderLayer = next, convertMessage = null) }
        saveSliderLayer(next)
        afterEqChange()
    }

    /**
     * Turns the EQ in effect (tuned bands plus sliders) into N peaking bands at the sliders' centres, the way a head unit
     * with fixed bands would hold it. This is lossy, so the difference it makes is reported.
     */
    fun convertToBands(centres: List<Double>, q: Double) {
        val chain = state.value.exportChain ?: return
        val gains = Export.graphicBands(chain, centres)
        val fl = gains.filter { abs(it.second) >= 0.1 }.map { EqFilter(it.first, it.second, q) }
        val after = EqChain(fl, 0.0)
        var sum = 0.0; var n = 0
        for (i in grid.freqs.indices) {
            val f = grid.freqs[i]
            if (f < 30 || f > 12000) continue
            val d = chain.responseDb(f) - chain.preampDb - after.responseDb(f)
            sum += d * d; n++
        }
        val rmsDiff = sqrt(sum / n.coerceAtLeast(1))
        state.update { it.copy(sliderLayer = emptyList(), convertMessage = "Converted to ${centres.size} bands. The converted EQ differs from the tuned one by %.1f dB RMS between 30 Hz and 12 kHz.".format(rmsDiff)) }
        saveSliderLayer(emptyList())
        setEdit(fl)
    }

    fun editAdd() { val l = state.value.editFilters ?: emptyList(); setEdit((l + EqFilter(1000.0, 0.0, 1.0)).sortedBy { it.freqHz }) }
    fun editRemove(i: Int) { val l = state.value.editFilters ?: return; setEdit(l.filterIndexed { k, _ -> k != i }) }
    fun editSort() { val l = state.value.editFilters ?: return; setEdit(l.sortedBy { it.freqHz }) }
    fun editSet(i: Int, f: EqFilter) {
        val list = state.value.editFilters ?: return
        setEdit(list.mapIndexed { k, old -> if (k == i) f else old })
    }

    /** Switches AutoTune's own EQ on or off for this phone's audio. */
    fun setSystemEq(on: Boolean) {
        val ctx = getApplication<Application>()
        if (!on) {
            SystemEq.off()
            ProfileController.remember(ctx, null)
            ctx.startService(Intent(ctx, SystemEqService::class.java).setAction(SystemEqService.ACTION_STOP))
            dev.autotune.audio.AutoTuneWidgets.update(ctx)
            state.update { it.copy(systemEqOn = false, systemEqMessage = "EQ off.") }
            Diag.log(ctx, "system_eq_off")
            return
        }
        // nothing picked yet: switch on flat; whatever is picked next plays at once (afterEqChange)
        val c = activeChain()?.takeIf { it.filters.isNotEmpty() } ?: EqChain.EMPTY
        val play = playChain()
        val err = SystemEq.apply(play)
        if (err != null) {
            state.update { it.copy(systemEqOn = false, systemEqMessage = err) }
            return
        }
        ProfileController.remember(ctx, c)
        runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, SystemEqService::class.java)) }
        dev.autotune.audio.AutoTuneWidgets.update(ctx())
        state.update {
            it.copy(
                systemEqOn = true, playPreampDb = play.preampDb,
                systemEqMessage = if (c.filters.isEmpty()) "On, flat. Pick an EQ in “EQ currently in use” and it plays at once."
                else "On: ${it.eqSource} · ${c.filters.size} bands, preamp %.1f dB.".format(c.preampDb),
            )
        }
        Diag.log(ctx, "system_eq_on", mapOf("source" to state.value.eqSource, "chain" to Diag.chain(c), "realised" to Diag.bands(SystemEq.applied)))
    }

    /** Re-applies the current EQ (for example after a new run changed the suggestion). */
    fun refreshSystemEq() {
        val c = activeChain() ?: return
        val play = playChain()
        val err = SystemEq.apply(play)
        if (err == null) ProfileController.remember(ctx(), c)
        Diag.log(ctx(), "system_eq_update", mapOf("source" to state.value.eqSource, "chain" to Diag.chain(c), "error" to err))
        state.update { it.copy(systemEqMessage = err ?: "EQ updated: ${c.filters.size} bands, preamp %.1f dB.".format(c.preampDb)) }
    }

    // ---------------------------------------------------------------- export (files)

    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifBlank { "test" }

    private fun us(fmt: String, vararg a: Any) = String.format(Locale.US, fmt, *a)

    private fun reportText(t: SavedTest, focus: Int, full: EqChain): String = buildString {
        val target = targetFor(t)
        appendLine("AutoTune report")
        appendLine("Test: ${t.name}")
        appendLine("Target: ${target.name}   Source: ${Source.entries.getOrNull(t.sourceIdx)?.title}")
        if (t.notes.isNotBlank()) appendLine("Notes: ${t.notes}")
        appendLine()
        listOf(0, focus).distinct().forEach { idx ->
            val r = judgeRun(t, idx, target, if (idx == focus) lrResult else null)
            appendLine("System score, ${t.runs[idx].label}: ${r.overall}/100 (${r.grade})")
            r.categories.forEach { appendLine("  - ${it.name}: ${it.score}/100, ${it.detail}") }
            appendLine("  ${r.verdict}")
        }
        appendLine()
        appendLine("Runs (RMS = distance from the target curve):")
        t.runs.forEach { r ->
            appendLine(us("- %s, %s: %.2f dB RMS", r.label, fmtDate(r.whenMillis), r.rmsDb))
            if (r.eq.isEmpty()) appendLine("    EQ applied: none (stock system)")
            else {
                appendLine("    EQ applied (${r.eq.size} bands):")
                r.eq.forEach { appendLine(us("      %.0f Hz  %+.1f dB  Q %.2f", it.freqHz, it.gainDb, it.q)) }
            }
            if (r.note.isNotBlank()) appendLine("    Note: ${r.note}")
            appendLine("    Mic calibration: ${r.calibration}")
            r.warnings.forEach { appendLine("    Warning: $it") }
        }
        appendLine()
        Headroom.check(full, grid).warnings.forEach { appendLine("WARNING: $it") }
        appendLine("Keep the volume, the mic position and the other device's settings identical when you measure again.")
        appendLine()
        mixedCalibrationWarning(t.runs)?.let { appendLine("WARNING: $it") }
        appendLine("EQ to apply after ${t.runs[focus].label} (the complete EQ, replacing what is set now):")
        appendLine(Export.parametricText(full))
        if (full.filters.isNotEmpty()) {
            appendLine("What each band does:")
            full.filters.forEach { appendLine(us("  %.0f Hz  %+.1f dB: %s", it.freqHz, it.gainDb, Describe.filter(it))) }
        }
    }

    private fun csvText(t: SavedTest): String {
        val target = targetFor(t)
        val cols = t.runs.map { normalized(it.magDb, grid, target) }
        return buildString {
            appendLine("Frequency Hz,Target dB," + t.runs.joinToString(",") { it.label.replace(',', ' ') + " dB" })
            for (i in 0 until grid.size) {
                append(us("%.1f,%.2f", grid.freqs[i], target.dbAt(grid.freqs[i])))
                cols.forEach { append(us(",%.2f", it[i])) }
                appendLine()
            }
        }
    }

    /** Where saved files went, in words. */
    private fun saveWhere(): String = state.value.exportFolder
        ?: if (Build.VERSION.SDK_INT >= 29) "Downloads/AutoTune" else "the app's Download folder"

    private fun saveToDownloads(name: String, mime: String, bytes: ByteArray): String {
        val ctx = getApplication<Application>()
        // a folder picked in Settings wins; if it is gone (card removed, access withdrawn) fall back to Downloads
        prefs.getString("exportTree", null)?.let { t ->
            val ok = runCatching {
                val tree = Uri.parse(t)
                val dir = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, android.provider.DocumentsContract.getTreeDocumentId(tree))
                val doc = android.provider.DocumentsContract.createDocument(ctx.contentResolver, dir, mime, name) ?: error("no file")
                ctx.contentResolver.openOutputStream(doc)!!.use { it.write(bytes) }
            }.isSuccess
            if (ok) return name
        }
        return if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/AutoTune")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: error("Could not create $name")
            ctx.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
            name
        } else {
            val d = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "AutoTune").also { it.mkdirs() }
            File(d, name).writeBytes(bytes)
            "${d.absolutePath}/$name"
        }
    }

    /**
     * Writes the EQ of the latest ticked run as files: ParametricEQ (Poweramp Equalizer, Equalizer APO), GraphicEQ
     * (Wavelet), a report with every run and what was applied, and a CSV of all responses. Saved to Downloads/AutoTune,
     * or handed to the share sheet when [share] is true.
     */
    /** The EQ behind a choice key, the test it belongs to (if any) and a short name for the files. */
    private fun resolveEq(key: String): Triple<EqChain, SavedTest?, String>? {
        val p = key.split(":")
        return when (p[0]) {
            "current", "own", "" -> state.value.exportChain?.let { Triple(it, current, current?.let { t -> "${safe(t.name)}_current" } ?: "MyEQ") }
            "suggest" -> savedTests.firstOrNull { it.id == p.getOrNull(1) }?.let { t -> Triple(suggestedFor(t.runs.last()), t, "${safe(t.name)}_next") }
            "run" -> savedTests.firstOrNull { it.id == p.getOrNull(1) }?.let { t ->
                val i = p.getOrNull(2)?.toIntOrNull() ?: return null
                t.runs.getOrNull(i)?.let { r -> Triple(EqChain.withSafePreamp(r.eq), t, "${safe(t.name)}_${safe(r.label)}") }
            }
            "ai" -> savedTests.firstOrNull { it.id == p.getOrNull(1) }?.let { t ->
                aiChainFor(t.id)?.let { c -> Triple(c, t, "${safe(t.name)}_AI") }
            }
            "profile" -> state.value.profiles.firstOrNull { it.id == p.getOrNull(1) }?.let { pr ->
                Triple(EqChain.withSafePreamp(pr.filters), savedTests.firstOrNull { it.name == pr.test }, safe(pr.name))
            }
            else -> null
        }
    }

    /** Bands and preamp of a choice, for the Export page's summary line. */
    fun eqSummary(key: String): String? = resolveEq(key)?.first?.let { c ->
        if (c.filters.isEmpty()) null else "${c.filters.size} bands · preamp %.1f dB".format(c.preampDb)
    }

    /**
     * Saves or shares one EQ. [format]: 0 = ParametricEQ.txt (Poweramp, Equalizer APO), 1 = GraphicEQ.txt (Wavelet),
     * 2 = both, 3 = both plus the test's report and measurements.
     */
    fun exportEq(key: String, format: Int, share: Boolean) {
        val (chain, test, base) = resolveEq(key) ?: return
        if (chain.filters.isEmpty()) { state.update { it.copy(status = "That EQ has no bands.") }; return }
        viewModelScope.launch {
            try {
                val name = "AutoTune_$base"
                val files = buildList {
                    if (format == 0 || format >= 2) add(Triple("${name}_ParametricEQ.txt", "text/plain", Export.parametricText(chain)))
                    if (format >= 1) add(Triple("${name}_GraphicEQ.txt", "text/plain", Export.graphicEqLine(chain) + "\n"))
                    if (format == 3 && test != null) {
                        add(Triple("${name}_report.txt", "text/plain", reportText(test, test.runs.lastIndex, chain)))
                        add(Triple("${name}_responses.csv", "text/csv", csvText(test)))
                    }
                }
                val msg = withContext(Dispatchers.IO) {
                    if (share) {
                        val out = files.map { (n, _, c) -> File(ctx().cacheDir, n).also { it.writeText(c) } }
                        fileToShare.emit(ShareFiles(out, "text/plain", "Share the EQ"))
                        "Choose where to send it."
                    } else {
                        val saved = files.map { (n, m, c) -> saveToDownloads(n, m, c.toByteArray()) }
                        val where = saveWhere()
                        "Saved to $where:\n" + saved.joinToString("\n") { "• " + it.substringAfterLast('/') }
                    }
                }
                state.update { it.copy(status = msg) }
            } catch (e: Exception) {
                state.update { it.copy(status = "Could not save the files: ${e.message}") }
            }
        }
    }

    fun exportFiles(share: Boolean) {
        val t = current?.takeIf { it.runs.isNotEmpty() }
        // the files hold exactly the EQ in effect (own or tuned bands plus slider changes), the same one that is applied
        val chain = state.value.exportChain ?: return
        viewModelScope.launch {
            try {
                val focus = t?.let { state.value.selectedRuns.filter { i -> i in it.runs.indices }.maxOrNull() ?: it.runs.lastIndex } ?: 0
                val base = (if (t != null) "AutoTune_${safe(t.name)}_${safe(t.runs[focus].label)}" else "AutoTune_MyEQ")
                val files = listOfNotNull(
                    Triple("${base}_ParametricEQ.txt", "text/plain", Export.parametricText(chain)),
                    Triple("${base}_GraphicEQ.txt", "text/plain", Export.graphicEqLine(chain) + "\n"),
                    t?.let { Triple("${base}_report.txt", "text/plain", reportText(it, focus, chain)) },
                    t?.let { Triple("${base}_responses.csv", "text/csv", csvText(it)) },
                )
                val msg = withContext(Dispatchers.IO) {
                    if (share) {
                        val ctx = getApplication<Application>()
                        val out = files.filter { it.second == "text/plain" }.map { (n, _, c) -> File(ctx.cacheDir, n).also { it.writeText(c) } }
                        fileToShare.emit(ShareFiles(out, "text/plain", "Share the EQ files"))
                        "Choose where to send the files."
                    } else {
                        val saved = files.map { (n, m, c) -> saveToDownloads(n, m, c.toByteArray()) }
                        val where = saveWhere()
                        "Saved ${saved.size} files to $where:\n" + saved.joinToString("\n") { "• " + it.substringAfterLast('/') }
                    }
                }
                state.update { it.copy(status = msg) }
            } catch (e: Exception) {
                state.update { it.copy(status = "Could not save the files: ${e.message}") }
            }
        }
    }

    // ---------------------------------------------------------------- AI assistant

    private var aiJob: Job? = null
    /** The last finished or failed measuring screen, so a review can see its checks after it closed. */
    private var lastMeasureView: MeasureView? = null

    /** A widget, the notification or the Bluetooth watcher changed the EQ while the app is open: show it. */
    private fun watchOutside() {
        viewModelScope.launch { dev.autotune.audio.EqBus.changed.collect { if (it != 0L) syncExternal() } }
    }

    private fun aiInit() {
        aiLoadProvider(AiProvider.of(prefs.getString("aiProvider", null)))
        viewModelScope.launch {
            state.collect { st -> st.measureView?.takeIf { it.stage == MeasureStage.SAVED || it.stage == MeasureStage.FAILED }?.let { lastMeasureView = it } }
        }
    }

    private fun aiLoadProvider(p: AiProvider, message: String? = null) {
        val key = KeyVault.load(ctx(), p)
        val model = AiModel.pick(p, prefs.getString("aiModel_${p.name}", null) ?: prefs.getString("aiModel", null))
        ai.update { it.copy(provider = p, hasKey = key != null, keyMasked = KeyVault.masked(key), model = model, keyMessage = message) }
    }

    private fun aiKey(): String? = KeyVault.load(ctx(), ai.value.provider)

    fun aiSetProvider(p: AiProvider) {
        prefs.edit().putString("aiProvider", p.name).apply()
        aiLoadProvider(p)
    }

    fun aiSaveKey(key: String) {
        val k = key.trim()
        val p = ai.value.provider
        if (!p.looksLikeKey(k)) { ai.update { it.copy(keyMessage = "That does not look like a ${p.title} API key (they start with ${p.keyHint}).") }; return }
        runCatching { KeyVault.save(ctx(), p, k) }.onFailure { e -> ai.update { it.copy(keyMessage = "Could not store the key: ${e.message}") }; return }
        ai.update { it.copy(hasKey = true, keyMasked = KeyVault.masked(k), keyMessage = "Key saved on this phone.") }
        Diag.log(ctx(), "ai_key_saved", mapOf("provider" to p.name))
        aiTestKey()
    }

    fun aiRemoveKey() {
        KeyVault.clear(ctx(), ai.value.provider)
        ai.update { it.copy(hasKey = false, keyMasked = null, keyMessage = "Key removed.") }
    }

    fun aiSetModel(m: AiModel) {
        prefs.edit().putString("aiModel_${m.provider.name}", m.id).apply()
        ai.update { it.copy(model = m, keyMessage = null) }
    }

    fun aiTestKey() {
        val key = aiKey() ?: return
        val model = ai.value.model
        ai.update { it.copy(testingKey = true, keyMessage = "Checking the key…") }
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) { runCatching { AiClient.of(key, model).ping() }.exceptionOrNull() }
            ai.update { it.copy(testingKey = false, keyMessage = err?.let { e -> e.message ?: "The key did not work." } ?: "The key works with ${model.title}.") }
        }
    }

    private fun aiFile(testId: String) = File(File(ctx().filesDir, "ai").also { it.mkdirs() }, "$testId.json")

    private fun aiStored(testId: String?): AiReview? =
        testId?.let { id -> runCatching { aiFile(id).takeIf { it.exists() }?.readText()?.let(AiReview::fromJson) }.getOrNull() }

    /** Opens the review screen for the open test: the last review if there is one, otherwise a new one is started. */
    fun aiOpen() {
        val t = current
        val focus = t?.let { tt -> state.value.selectedRuns.filter { it in tt.runs.indices }.maxOrNull() ?: tt.runs.lastIndex }
        // a stored review only stands for the same runs and the same focus run; otherwise the newest run is reviewed now
        val stored = aiStored(t?.id)?.takeIf { r -> t != null && r.runCount == t.runs.size && r.focusRun == focus }
        ai.update { it.copy(open = true, error = null, message = null, chat = if (stored != null && it.review?.testId == t?.id) it.chat else emptyList(), testName = t?.name) }
        if (stored != null) aiShow(stored) else {
            ai.update { it.copy(review = null, eq = null, stale = false) }
            if (ai.value.hasKey) aiReview(false)
        }
    }

    /** From a failed measurement: open the review screen and ask why it failed. */
    fun aiExplainFailure() {
        closeMeasureView()
        ai.update { it.copy(open = true, error = null, message = null, chat = emptyList(), review = null, eq = null, stale = false, testName = current?.name) }
        if (ai.value.hasKey) aiReview(true)
    }

    fun aiClose() = ai.update { it.copy(open = false) }

    private fun aiShow(r: AiReview) {
        val t = current?.takeIf { it.id == r.testId }
        val eq = t?.let { aiEqFor(it, r) }
        ai.update { it.copy(review = r, eq = eq, stale = t != null && t.runs.size != r.runCount, error = null) }
    }

    private fun aiSystemRoute(t: SavedTest) =
        t.sourceIdx == Source.PHONE.ordinal && (state.value.systemEqOn || state.value.applyWay == ApplyWay.AUTOTUNE_EQ)

    private fun aiEqFor(t: SavedTest, r: AiReview): AiEq? {
        if (r.bands.isEmpty() || t.runs.isEmpty()) return null
        val f = (r.focusRun ?: t.runs.lastIndex).coerceIn(0, t.runs.lastIndex)
        val run = t.runs[f]
        val base = runCatching { suggestedFor(run).filters }.getOrNull() ?: return null
        return runCatching {
            AiEqCheck.build(base, r.bands.map { it.filter }, aiSystemRoute(t), run, run.applied, targetFor(t), grid)
        }.getOrNull()
    }

    private fun aiExtra(failure: String?): AiPrompt.Extra {
        val st = state.value
        val mv = st.measureView ?: lastMeasureView
        fun stateWord(n: Int) = when (n) { 1 -> "fine"; 2 -> "worth a look"; 3 -> "failed"; else -> "not done" }
        return AiPrompt.Extra(
            appVersion = BuildConfig.VERSION_NAME,
            device = Diag.deviceLine(),
            mic = if (st.phoneMic) "the phone's own microphone" else (st.micName ?: "unknown"),
            micSource = MeasurementRunner.lastMicSource,
            calibration = st.calibrationName.takeIf { calibration != null },
            output = st.outputName,
            route = when (st.applyWay) {
                ApplyWay.AUTOTUNE_EQ -> "AutoTune EQ (phone system effect)"
                ApplyWay.EQUIPMENT -> "set by the user on their equipment"
                ApplyWay.IN_APP -> "in-app preview"
                ApplyWay.NONE -> "unchanged (repeat)"
            },
            systemEqOn = st.systemEqOn,
            systemEqDamagedBefore = st.systemEqSuspect,
            eqInUse = st.eqSource.ifBlank { "none" },
            eqInUseChain = st.exportChain,
            sliderChanges = st.sliderLayer,
            checks = mv?.checks.orEmpty().map { Triple(it.label, stateWord(it.state), it.detail) },
            failure = failure,
            lastWarnings = st.lastWarnings,
        )
    }

    /** Sends the open test (or the failed measurement) to Claude and shows what comes back. */
    fun aiReview(aboutFailure: Boolean = false) {
        if (aiJob?.isActive == true) return
        val key = aiKey()
        if (key == null) { ai.update { it.copy(hasKey = false, error = "Add your ${it.provider.title} API key in Settings > AI assistant first.") }; return }
        val t = current
        val st = state.value
        val failure = if (aboutFailure) (lastMeasureView?.takeIf { it.stage == MeasureStage.FAILED }?.message ?: st.status?.removePrefix("Failed: ")) else null
        if (!aboutFailure && (t == null || t.runs.isEmpty())) { ai.update { it.copy(error = "Measure a stock run first, then ask for a review.") }; return }
        val focus = t?.let { tt -> st.selectedRuns.filter { it in tt.runs.indices }.maxOrNull() ?: tt.runs.lastIndex }
        val target = t?.let { targetFor(it) }
        val model = ai.value.model
        ai.update { it.copy(busy = true, error = null, message = null) }
        aiJob = viewModelScope.launch {
            try {
                val context = withContext(Dispatchers.Default) {
                    AiPrompt.context(
                        t, target, target?.let { k -> targetKind(k).name.lowercase() }, t?.let { Source.entries.getOrNull(it.sourceIdx)?.title },
                        focus, aiExtra(failure), grid,
                    )
                }
                val ask = if (failure != null) "The last measurement failed with: \"$failure\". Explain the likely cause from the checks and the setup, and what to do so the next measurement works. Then review the test as usual if there is one."
                else "Review this test. Focus on run $focus."
                val input = withContext(Dispatchers.IO) { AiClient.of(key, model).review(context, ask) }
                val r = AiReview.fromTool(input, t?.id, model.id, System.currentTimeMillis(), t?.runs?.size ?: 0, focus, failure != null)
                if (t != null && failure == null) withContext(Dispatchers.IO) { runCatching { aiFile(t.id).writeText(r.toJson()) } }
                aiShow(r)
                ai.update { it.copy(chat = emptyList()) }
                rebuildChoices()
                Diag.log(ctx(), "ai_review", mapOf("model" to model.id, "test" to t?.name, "failure" to failure, "issues" to r.issues.size, "eqChanges" to r.bands.size, "contextChars" to context.length))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ai.update { it.copy(error = e.message ?: "The review failed.") }
                Diag.log(ctx(), "ai_error", mapOf("message" to e.message, "error" to e.javaClass.simpleName))
            } finally {
                ai.update { it.copy(busy = false) }
            }
        }
    }

    /** A follow-up question about the review on screen. */
    fun aiAsk(question: String) {
        val q = question.trim()
        if (q.isEmpty() || ai.value.asking) return
        val key = aiKey()
        if (key == null) { ai.update { it.copy(error = "Add your ${it.provider.title} API key in Settings > AI assistant first.") }; return }
        val t = current
        val st = state.value
        val focus = t?.let { tt -> st.selectedRuns.filter { it in tt.runs.indices }.maxOrNull() ?: tt.runs.lastIndex }
        val r = ai.value.review
        val history = ai.value.chat
        val model = ai.value.model
        ai.update { it.copy(asking = true, error = null, chat = history + AiTurn("user", q)) }
        viewModelScope.launch {
            try {
                val target = t?.let { targetFor(it) }
                val context = withContext(Dispatchers.Default) {
                    AiPrompt.context(t, target, target?.let { k -> targetKind(k).name.lowercase() }, t?.let { Source.entries.getOrNull(it.sourceIdx)?.title }, focus, aiExtra(null), grid)
                }
                val earlier = r?.let { "\n\nYour earlier review:\n" + Diag.json(it.raw) } ?: ""
                // the test data rides on the first question only; later turns follow as a normal conversation
                val turns = (history + AiTurn("user", q)).mapIndexed { i, turn ->
                    if (i == 0) AiTurn("user", "Test data:\n$context$earlier\n\nQuestion: ${turn.text}") else turn
                }
                val answer = withContext(Dispatchers.IO) { AiClient.of(key, model).answer(turns) }
                ai.update { it.copy(chat = it.chat + AiTurn("assistant", answer)) }
                Diag.log(ctx(), "ai_question", mapOf("model" to model.id, "chars" to answer.length))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // drop the unanswered question so the conversation stays user/assistant in turn
                ai.update { it.copy(error = e.message ?: "No answer.", chat = history) }
            } finally {
                ai.update { it.copy(asking = false) }
            }
        }
    }

    /** Puts the AI's EQ in use (the open test's suggestion plus its checked changes). Never switches the EQ on by itself. */
    fun aiUseEq() {
        val t = current ?: return
        val eq = ai.value.eq ?: return
        clearSliders()
        setEdit(eq.chain.filters, "ai:${t.id}")
        ai.update { it.copy(message = "The AI's EQ is now the EQ in use" + if (state.value.systemEqOn) " and AutoTune EQ plays it." else ". Measure it to see if it helps.") }
        Diag.log(ctx(), "ai_eq_used", mapOf("test" to t.name, "chain" to Diag.chain(eq.chain)))
    }

    /** The checked AI EQ of a test, for the EQ picker and export. */
    private fun aiChainFor(testId: String?): EqChain? {
        val t = savedTests.firstOrNull { it.id == testId } ?: return null
        val r = aiStored(t.id) ?: return null
        return aiEqFor(t, r)?.chain
    }

    override fun onCleared() {
        rta.stop()
        // the EQ keeps running in its service while the user has it switched on
        player.stop()
        super.onCleared()
    }
}


/**
 * What the graphic sliders show: what the EQ in effect really does at each centre (tuned bands, own bands and slider
 * changes together, neighbouring bands included), so a slider never shows a number the sound does not follow.
 */
fun graphicGains(chain: EqChain?, centres: List<Double>): DoubleArray = DoubleArray(centres.size) { i ->
    val c = chain ?: return@DoubleArray 0.0
    (Math.round((c.responseDb(centres[i]) - c.preampDb) * 2) / 2.0).coerceIn(-12.0, 12.0)
}
