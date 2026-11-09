package dev.autotune

import dev.autotune.dsp.EqChain
import dev.autotune.dsp.EqFilter
import dev.autotune.dsp.Export
import dev.autotune.dsp.FitConfig
import kotlin.math.log10

/*
 * The bookkeeping of the tuning loop, kept free of Android so it can be unit tested.
 *
 * Rule: what a run records as "applied" is exactly the chain the user was shown (or that was really in effect), so the
 * next fit starts from the truth instead of from a guess.
 */

/** How finely AutoTune EQ (the phone's system effect) is built: 31 bands (1/3 octave) or 61 bands (1/6 octave). */
object SystemEqConfig {
    @Volatile var fine: Boolean = false
}

/** Fit limits for the system EQ: only bands it can realise (wider bands for 31 bands, narrower allowed with 61). */
val SYSTEM_EQ_FIT: FitConfig
    get() = if (SystemEqConfig.fine) FitConfig(maxCutQ = 4.0, maxBoostQ = 2.5) else FitConfig(maxCutQ = 2.0, maxBoostQ = 1.4)

/** True when every band of [filters] is wide enough for the 31-band system EQ to reproduce it. */
fun fitsSystemEq(filters: List<EqFilter>): Boolean =
    filters.all { it.q <= (if (it.gainDb < 0) SYSTEM_EQ_FIT.maxCutQ else SYSTEM_EQ_FIT.maxBoostQ) + 1e-6 }

/** The complete EQ AutoTune suggests after [run], exactly as shown, applied and exported (boost-capped, safe preamp). */
fun suggestedFor(run: SavedRun, cfg: FitConfig = FitConfig()): EqChain {
    // runs saved before 1.9.1 stored only the further tweaks on top of what was applied
    // the fitter adds bands one by one and can leave twins at one frequency: merge them so the list is readable
    val full = if (run.complete) Export.combine(run.filters, cfg) else Export.combine(run.applied + run.filters, cfg)
    return EqChain.withSafePreamp(EqChain.capBoost(full))
}

/**
 * The EQ in effect on the user's equipment for the next run: what they were shown after [prev] (or their own exact
 * bands) plus any slider changes, in the same form the EQ tab displays it.
 */
fun inEffectAfter(prev: SavedRun, ownBands: List<EqFilter>?, sliderLayer: List<EqFilter>): EqChain =
    EqChain.withSafePreamp((ownBands ?: suggestedFor(prev).filters) + sliderLayer)

/** What a run records as applied for each way the EQ can reach the mic. */
fun appliedFor(way: ApplyWay, prev: SavedRun, shown: EqChain, inApp: EqChain?, systemBands: List<EqFilter>?): List<EqFilter> = when {
    inApp != null -> inApp.filters
    systemBands != null -> systemBands
    way == ApplyWay.NONE -> prev.eq
    else -> shown.filters
}

/** The suggestion after a new run, a line for the log when it was not simply the fit of that run, and whether tuning has converged. */
class NextSuggestion(val filters: List<EqFilter>, val note: String?, val converged: Boolean = false)

/** RMS difference of two EQs' responses between 30 Hz and 12 kHz (dB). */
fun eqDistanceDb(a: List<EqFilter>, b: List<EqFilter>): Double {
    val g = dev.autotune.dsp.FreqGrid()
    var s = 0.0; var n = 0
    for (f in g.freqs) {
        if (f < 30 || f > 12000) continue
        val d = a.sumOf { it.magnitudeDb(f) } - b.sumOf { it.magnitudeDb(f) }
        s += d * d; n++
    }
    return kotlin.math.sqrt(s / n.coerceAtLeast(1))
}

/**
 * Keeps the best run as the base: when the new run is worse than the best earlier one by more than [margin] dB, the
 * suggestion goes back to the EQ that produced the best run instead of building on a worse one.
 * [halfStepFromStock] supplies a gentler first step when the best run was the stock one (nothing applied).
 */
fun nextSuggestion(
    prior: List<SavedRun>, newRms: Double, newLabel: String, fitted: List<EqFilter>,
    halfStepFromStock: () -> List<EqFilter>, margin: Double = 0.3, newEq: List<EqFilter> = emptyList(),
): NextSuggestion {
    val bestIdx = prior.indices.minByOrNull { prior[it].rmsDb } ?: return NextSuggestion(fitted, null)
    val best = prior[bestIdx]
    if (newRms <= best.rmsDb + margin) {
        // the new run is (about) the best. If its fit is an EQ that was already tried and measured worse, going there
        // again only repeats the swing: tuning has converged on what is in effect now
        val bestRms = minOf(newRms, best.rmsDb)
        val candidate = EqChain.capBoost(Export.combine(fitted))
        // what was tried in run j: its own parametric EQ, or for older runs (which kept only the 31 realised bands)
        // the suggestion made before it
        fun tried(j: Int): List<List<EqFilter>> = listOfNotNull(
            prior[j].eq.takeIf { it.isNotEmpty() },
            if (prior[j].appliedParam == null && j > 0) suggestedFor(prior[j - 1]).filters else null,
        )
        val triedWorse = (1 until prior.size).map { prior[it] to it }.firstOrNull { (r, j) ->
            r.rmsDb > bestRms + margin && tried(j).any { eqDistanceDb(it, candidate) < 1.0 }
        }?.first
        if (triedWorse != null && newEq.isNotEmpty()) {
            return NextSuggestion(
                newEq,
                "The next suggestion would be the EQ of ${triedWorse.label}, which measured worse (%.2f dB). Tuning has converged: keep this EQ.".format(triedWorse.rmsDb),
                converged = true,
            )
        }
        return NextSuggestion(fitted, null)
    }
    val text = "$newLabel (%.2f dB) is worse than ${best.label} (%.2f dB), so the suggestion goes back to ".format(newRms, best.rmsDb)
    // going back to the best EQ a second time means the alternatives have been tried: that is convergence
    val wentBackBefore = prior.any { it !== best && it.eq.isNotEmpty() && eqDistanceDb(it.eq, best.eq) < 0.5 }
    return if (best.eq.isNotEmpty()) NextSuggestion(best.eq, text + "the EQ from ${best.label}.", converged = wentBackBefore)
    else NextSuggestion(halfStepFromStock(), text + "a gentler first step.")
}

/** One word for the calibration of a run, shown in lists and reports. */
fun calibrationLabel(name: String?): String = name?.takeIf { it.isNotBlank() } ?: "none"

/** Runs of one test measured with different calibrations are not comparable; null when they all agree (or are unknown). */
fun mixedCalibrationWarning(runs: List<SavedRun>): String? {
    val known = runs.map { it.calibration }.filter { it != UNKNOWN_CAL }.distinct()
    return if (known.size > 1) "These runs used different microphone calibrations (${known.joinToString(", ")}). Differences between them may come from the calibration, not from the EQ." else null
}

/** Latest run's own measurement warnings, plus the test-wide ones. */
fun resultWarnings(runs: List<SavedRun>, focus: Int): List<String> =
    (runs.getOrNull(focus)?.warnings.orEmpty() + listOfNotNull(mixedCalibrationWarning(runs))).distinct()

/** In the 2–8 kHz band a run is 12 dB or more below the stock run: the system EQ (or something else) is eating the treble. */
fun trebleCollapsed(stockMag: DoubleArray, newMag: DoubleArray, freqs: DoubleArray, dropDb: Double = 12.0): Boolean {
    fun level(m: DoubleArray, lo: Double, hi: Double): Double {
        var s = 0.0; var n = 0
        for (i in freqs.indices) if (freqs[i] in lo..hi) { s += m[i]; n++ }
        return if (n == 0) 0.0 else s / n
    }
    // compare against the 250–2500 Hz level so a general volume change doesn't count as lost treble
    val stockRel = level(stockMag, 2000.0, 8000.0) - level(stockMag, 250.0, 1500.0)
    val newRel = level(newMag, 2000.0, 8000.0) - level(newMag, 250.0, 1500.0)
    return stockRel - newRel > dropDb
}

// ---------------------------------------------------------------- what the app says after a measurement

/** One finding after a reading. severity: 0 fine, 1 worth a look, 2 a problem. */
class Finding(val severity: Int, val title: String, val detail: String)

/** The stock run, analysed: how far from the target, what the EQ can do, what it cannot, and whether to trust it. */
class StockAnalysis(
    val rmsDb: Double,
    /** Predicted distance from the target with the suggested EQ, or null without one. */
    val predictedDb: Double?,
    val bands: Int,
    val verdict: String,
    /** The reading looks like a measuring problem: measure again before building an EQ on it. */
    val suspicious: Boolean,
    val findings: List<Finding>,
)

private fun regionWords(name: String, err: Double): Pair<String, String> {
    val db = "%.1f dB".format(kotlin.math.abs(err))
    val loud = err > 0
    return when (name) {
        "Bass" -> if (loud) "Boomy bass" to "The bass is $db above the target. EQ cuts it cleanly."
        else "Thin bass" to "The bass is $db below the target. EQ lifts it by 3 dB at most; more needs the sub level or a subwoofer."
        "Low mids" -> if (loud) "Muddy low mids" to "120-500 Hz is $db too loud, often a cabin or door resonance. EQ cuts it."
        else "Hollow low mids" to "120-500 Hz is $db too quiet. Often a cancellation between speakers; check polarity before boosting."
        "Mids" -> if (loud) "Forward mids" to "500 Hz-2 kHz is $db too loud: voices sound pushed and honky. EQ cuts it."
        else "Recessed mids" to "500 Hz-2 kHz is $db too quiet: voices sound distant."
        "Presence" -> if (loud) "Harsh presence" to "2-6 kHz is $db too loud: fatiguing at volume. Often tweeters aimed at you or dash reflections."
        else "Dull presence" to "2-6 kHz is $db too quiet: voices lack clarity."
        "Treble" -> if (loud) "Bright treble" to "6-12 kHz is $db too loud: sibilant and sharp."
        else "Dark treble" to "6-12 kHz is $db too quiet. A phone mic also rolls off up here; check with a USB mic before boosting."
        else -> "$name off" to "$name is $db ${if (loud) "too loud" else "too quiet"}."
    }
}

/**
 * Plain-language analysis of the stock reading, from the numbers the app already has: the distance from the target,
 * the five regions, resonances (with ring times), the report card's hardware limits and the measuring warnings.
 */
fun analyzeStock(
    rmsDb: Double, predictedDb: Double?, bands: Int,
    regions: List<dev.autotune.dsp.Region>, resonances: List<dev.autotune.dsp.Resonance>,
    warnings: List<String>, report: dev.autotune.dsp.SystemReport?,
): StockAnalysis {
    val out = ArrayList<Finding>()
    val badReading = warnings.any { "barely heard" in it || "far too quiet" in it }
    val suspicious = rmsDb > 12.0 || badReading
    warnings.forEach { w ->
        val bad = "barely heard" in w || "signal-to-noise" in w.lowercase() || "far too quiet" in w
        out += Finding(if (bad) 2 else 1, "Measurement", w)
    }
    val off = regions.filter { kotlin.math.abs(it.errDb) >= 3.0 }.sortedByDescending { kotlin.math.abs(it.errDb) }
    off.forEach { r ->
        val (t, d) = regionWords(r.name, r.errDb)
        out += Finding(if (kotlin.math.abs(r.errDb) >= 6.0) 2 else 1, t, d)
    }
    if (regions.isNotEmpty() && off.isEmpty()) out += Finding(0, "Tonal balance", "Every region is within 3 dB of the target.")
    val strong = resonances.filter { it.prominenceDb >= 6.0 }.sortedByDescending { it.prominenceDb }
    if (strong.isNotEmpty()) {
        val list = strong.take(3).joinToString { "%.0f Hz (+%.0f dB)".format(it.freqHz, it.prominenceDb) }
        out += Finding(1, "${strong.size} strong peak${if (strong.size > 1) "s" else ""}", "$list. The EQ cuts these.")
    }
    resonances.filter { (it.t60Ms ?: 0.0) >= 300.0 }.sortedByDescending { it.t60Ms }.take(2).forEach { r ->
        out += Finding(
            1, "Ringing at %.0f Hz".format(r.freqHz),
            "It keeps sounding for about %.0f ms. EQ lowers its level but not the ring; moving the sub or adding damping does.".format(r.t60Ms),
        )
    }
    report?.categories?.filter { !it.eqFixable && it.score < 60 }?.forEach { c ->
        out += Finding(1, c.name, c.detail + " EQ cannot add what the speakers do not play.")
    }
    val verdict = when {
        suspicious -> "Further off than a real system usually is, or the reading was too weak. This looks like a measuring problem, not something to EQ."
        rmsDb < 2.5 -> "Already close to the target. The EQ will only fine-tune it."
        rmsDb < 6.0 -> "Typical for an untuned system. The suggested EQ should take it a long way."
        else -> "Far from the target. The EQ will help a lot; the findings below say where."
    }
    return StockAnalysis(rmsDb, predictedDb, bands, verdict, suspicious, out.sortedByDescending { it.severity })
}

/**
 * What to do after a run with the EQ: the one recommended move and why. New moves are appended (2.5.0): REPEAT after
 * an EQ check heard the EQ arrive, SET_ON_EQUIPMENT after one heard nothing, MEASURE_ON_PHONE_EQ before finishing a
 * tune that was only measured through the app's preview.
 */
enum class NextMove { CHECK_EQ, KEEP_LATEST, KEEP_BEST, REFINE, REPEAT, SET_ON_EQUIPMENT, MEASURE_ON_PHONE_EQ }

/** [keepIdx] is the run whose EQ a keep (or MEASURE_ON_PHONE_EQ) is about; null for the other moves. */
class Recommendation(val move: NextMove, val why: String, val bestIdx: Int, val keepIdx: Int? = null)

/**
 * The one move after a run. [eqCheckSeverity] is the verdict of an EQ check made after the latest run (see [EqCheck]),
 * or null when there was none. [phoneRoute] and [way] say how this test's EQ reaches the speakers, so a tune measured
 * only through the app's preview is measured once on the phone's EQ before it is kept there.
 */
fun recommendNext(
    runs: List<SavedRun>, eqMayNotBeApplied: Boolean, eqCheckSeverity: Int? = null,
    phoneRoute: Boolean = false, way: ApplyWay = ApplyWay.NONE,
): Recommendation {
    val base = baseRecommendation(runs, eqMayNotBeApplied, eqCheckSeverity)
    val keepIdx = when (base.move) {
        NextMove.KEEP_LATEST -> runs.lastIndex
        NextMove.KEEP_BEST -> base.bestIdx
        else -> return base
    }
    if (!needsPhoneEqRun(runs, keepIdx, phoneRoute, way)) return Recommendation(base.move, base.why, base.bestIdx, keepIdx)
    return Recommendation(
        NextMove.MEASURE_ON_PHONE_EQ,
        "${runs[keepIdx].label} was measured through the app's preview, which the phone's EQ plays differently. Measure it once on the phone's EQ before keeping it.",
        base.bestIdx, keepIdx,
    )
}

/**
 * Whether keeping run [keepIdx] needs one more run first: on the phone route with the app's preview as the way, the
 * kept EQ goes onto the phone's EQ (fixed bands, a different curve), so it is measured there unless a run on the
 * phone's EQ with the same EQ already exists.
 */
fun needsPhoneEqRun(runs: List<SavedRun>, keepIdx: Int, phoneRoute: Boolean, way: ApplyWay): Boolean {
    if (!phoneRoute || way != ApplyWay.IN_APP) return false
    val kept = runs.getOrNull(keepIdx) ?: return false
    if (kept.eq.isEmpty() || kept.appliedParam != null) return false
    return runs.none { it.appliedParam != null && eqDistanceDb(it.eq, kept.eq) < 0.5 }
}

/** The outputs the phone's EQ is known not to reach, after an EQ check on [output] with [severity] (see [EqCheck]). */
fun bypassAfterCheck(known: Set<String>, output: String?, severity: Int): Set<String> = when {
    output.isNullOrBlank() -> known
    severity == 2 -> known + output
    severity == 0 -> known - output
    else -> known
}

/**
 * The one line Set up adds before the stock reading when the phone's EQ may not reach [output]: an output an EQ check
 * already proved it misses ([knownBypass]), or Android Auto (by its name or the phone's car mode). Null otherwise.
 */
fun phoneEqReachWarning(output: String?, carMode: Boolean, knownBypass: Set<String>): String? = when {
    output != null && output in knownBypass ->
        "An EQ check found the phone's EQ does not reach $output. The EQ step starts with your equipment."
    carMode || output?.contains("Android Auto", ignoreCase = true) == true ->
        "Android Auto can take the sound past the phone's EQ. If the first run comes out like the stock, the EQ check will tell."
    else -> null
}

/** The Bluetooth device the phone is playing through now (from the media route). */
data class BtRoute(val address: String, val name: String)

/**
 * Whether Done offers to switch the tuned EQ on whenever [route] connects: the EQ plays on this phone ([playsOnPhone]),
 * a tuned profile was saved, and no rule already does exactly that with automatic switching on.
 */
fun offerAutoSwitch(route: BtRoute?, playsOnPhone: Boolean, profileId: String?, rules: List<AutoRule>, automationOn: Boolean): Boolean {
    if (route == null || !playsOnPhone || profileId == null) return false
    val done = automationOn && rules.any { it.address.equals(route.address, ignoreCase = true) && it.profileId == profileId }
    return !done
}

private fun baseRecommendation(runs: List<SavedRun>, eqMayNotBeApplied: Boolean, eqCheckSeverity: Int?): Recommendation {
    val latestIdx = runs.lastIndex
    val withEq = (1..latestIdx).toList()
    val bestIdx = withEq.minByOrNull { runs[it].rmsDb } ?: latestIdx
    val latest = runs[latestIdx]
    val best = runs[bestIdx]
    val prev = runs[latestIdx - 1]
    val gain = prev.rmsDb - latest.rmsDb
    return when {
        // the EQ check answered the question: the EQ does not reach the speakers this way
        eqMayNotBeApplied && eqCheckSeverity == 2 -> Recommendation(
            NextMove.SET_ON_EQUIPMENT,
            "The EQ check heard no change with the phone's EQ on, so this output takes the sound past it. Set the EQ on your equipment instead.",
            bestIdx,
        )
        // it does reach them now: the run missed it, so measure again before fitting anything to that run
        eqMayNotBeApplied && (eqCheckSeverity == 0 || eqCheckSeverity == 1) -> Recommendation(
            NextMove.REPEAT,
            "The EQ check heard the EQ arrive, so ${latest.label} probably missed it. Measure again with nothing changed.",
            bestIdx,
        )
        eqMayNotBeApplied && eqCheckSeverity == null -> Recommendation(
            NextMove.CHECK_EQ,
            "${latest.label} is about the same as the stock reading, so the EQ probably never reached the speakers. Check it is on (or set on your equipment), then apply it again.",
            bestIdx,
        )
        latest.converged && bestIdx != latestIdx -> Recommendation(NextMove.KEEP_BEST, "Tuning has converged: the other suggestions measured worse than ${best.label}.", bestIdx)
        latest.converged -> Recommendation(NextMove.KEEP_LATEST, "Tuning has converged: more rounds won't improve it.", bestIdx)
        latest.rmsDb > best.rmsDb + 0.3 -> Recommendation(
            NextMove.KEEP_BEST, "${best.label} measured better (%.1f dB against %.1f dB). Its EQ is the one to keep.".format(best.rmsDb, latest.rmsDb), bestIdx,
        )
        latest.rmsDb <= 1.5 -> Recommendation(NextMove.KEEP_LATEST, "%.1f dB from the target is as close as tuning gets.".format(latest.rmsDb), bestIdx)
        latestIdx >= 4 && gain < 0.5 -> Recommendation(
            NextMove.KEEP_LATEST, "The last round gained only %.1f dB. More rounds won't add much.".format(gain.coerceAtLeast(0.0)), bestIdx,
        )
        latestIdx >= 2 && gain < 0.2 -> Recommendation(NextMove.KEEP_LATEST, "The last round barely changed it, so this is about as good as it gets.", bestIdx)
        else -> Recommendation(
            NextMove.REFINE,
            if (latestIdx == 1) "The EQ took it from %.1f to %.1f dB. One more round with the new suggested EQ usually closes most of the rest.".format(prev.rmsDb, latest.rmsDb)
            else "The last round gained %.1f dB. Another round with the new suggested EQ should help.".format(gain),
            bestIdx,
        )
    }
}

/**
 * Where Back goes inside the Tune workflow: the previous screen, never deleting anything. Since 1.29 the workflow is
 * five steps (Set up, Stock, EQ, Verify, Result): the fresh-start question is part of Tune itself and the stock review
 * is the top of the EQ step, so FRESH and STOCK_RESULT only survive as names for older saved places.
 */
fun previousStep(step: TuneStep, runCount: Int): TuneStep = when (step) {
    TuneStep.HOME, TuneStep.FRESH, TuneStep.SETUP -> TuneStep.HOME
    TuneStep.STOCK -> if (runCount > 0) TuneStep.APPLY else TuneStep.SETUP
    TuneStep.STOCK_RESULT, TuneStep.APPLY -> if (runCount > 1) TuneStep.RESULT else TuneStep.HOME
    TuneStep.VERIFY -> TuneStep.APPLY
    TuneStep.RESULT, TuneStep.DONE -> TuneStep.HOME
}

/**
 * Whether moving to [step] keeps the place Continue remembers. Set up is a draft (2.5.0): the open test and the EQ stay
 * as they are until its key starts the stock reading, so Back from it still continues where the test was left.
 */
fun keepsResumePlace(step: TuneStep): Boolean = step == TuneStep.HOME || step == TuneStep.FRESH || step == TuneStep.SETUP

/**
 * Where a measurement that was closed, stopped or failed returns to (2.7.0: there is no Stock or Verify page any more):
 * the step it was started from when that still fits the test, else Set up for a test with no reading and the EQ
 * step otherwise.
 */
fun returnAfterMeasuring(from: TuneStep?, runCount: Int): TuneStep = when {
    from == TuneStep.SETUP || from == TuneStep.FRESH -> TuneStep.SETUP
    from == TuneStep.APPLY || from == TuneStep.STOCK_RESULT -> if (runCount >= 1) TuneStep.APPLY else TuneStep.SETUP
    from == TuneStep.RESULT && runCount >= 2 -> TuneStep.RESULT
    runCount == 0 -> TuneStep.SETUP
    else -> TuneStep.APPLY
}

/** Set up's choices, remembered per output (pref `setup_<output>`), so re-tuning the same system starts from them. */
data class SetupMemory(
    val targetIdx: Int, val sourceIdx: Int, val spots: Int,
    /** 0 = automatic. */
    val gateMs: Int,
    val bassDb: Double, val trebleDb: Double,
) {
    fun toText(): String = "1;$targetIdx;$sourceIdx;$spots;$gateMs;$bassDb;$trebleDb"

    companion object {
        /** Null for anything this version cannot read. */
        fun fromText(text: String?): SetupMemory? {
            val p = text?.split(';') ?: return null
            if (p.size < 7 || p[0] != "1") return null
            return runCatching {
                SetupMemory(p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4].toInt(), p[5].toDouble(), p[6].toDouble())
            }.getOrNull()?.takeIf { it.targetIdx >= 0 && it.sourceIdx >= 0 && it.spots in 1..9 && it.gateMs >= 0 }
        }
    }
}

/** The Tune screen shows a finished tune while the test it finished is open and has no run after it. */
fun showsTuned(tunedTestId: String?, tunedRuns: Int, openTestId: String?, runCount: Int): Boolean =
    tunedTestId != null && tunedTestId == openTestId && tunedRuns == runCount

/** Steps worth coming back to after Exit (Set up is asked again; Done is finished). */
val RESUMABLE = setOf(TuneStep.STOCK, TuneStep.STOCK_RESULT, TuneStep.APPLY, TuneStep.VERIFY, TuneStep.RESULT)

/** Where Continue lands: the remembered step when it still makes sense for the test, otherwise its latest result. */
fun resumeAt(saved: TuneStep?, runCount: Int): TuneStep {
    val s = if (saved == TuneStep.STOCK_RESULT) TuneStep.APPLY else saved
    val ok = when (s) {
        TuneStep.STOCK -> true
        TuneStep.APPLY, TuneStep.VERIFY -> runCount >= 1
        TuneStep.RESULT -> runCount >= 2
        else -> false
    }
    if (ok && s != null) return s
    return when {
        runCount == 0 -> TuneStep.STOCK
        runCount == 1 -> TuneStep.APPLY
        else -> TuneStep.RESULT
    }
}

/** The step names shown in the Tune progress bar, in order. */
val STEP_NAMES = listOf("Set up", "Stock", "EQ", "Verify", "Result")

fun stepIndex(step: TuneStep): Int = when (step) {
    TuneStep.HOME, TuneStep.FRESH, TuneStep.SETUP -> 0
    TuneStep.STOCK -> 1
    TuneStep.STOCK_RESULT, TuneStep.APPLY -> 2
    TuneStep.VERIFY -> 3
    TuneStep.RESULT, TuneStep.DONE -> 4
}

/** Where Continue picks up, in words. */
fun resumePlace(step: TuneStep): String = when (step) {
    TuneStep.STOCK -> "the stock reading"
    TuneStep.STOCK_RESULT, TuneStep.APPLY -> "the EQ"
    TuneStep.VERIFY -> "measuring with the EQ"
    TuneStep.RESULT, TuneStep.DONE -> "the result"
    TuneStep.HOME, TuneStep.FRESH, TuneStep.SETUP -> "the set-up"
}

/** The recommended move as the words on its button. */
fun moveLabel(move: NextMove, bestLabel: String?): String = when (move) {
    NextMove.CHECK_EQ -> "Apply the EQ again"
    NextMove.KEEP_LATEST -> "Keep this EQ and finish"
    NextMove.KEEP_BEST -> "Keep the EQ of ${bestLabel ?: "the best run"} and finish"
    NextMove.REFINE -> "Apply the new suggested EQ"
    NextMove.REPEAT -> "Measure again"
    NextMove.SET_ON_EQUIPMENT -> "Set it on my equipment"
    NextMove.MEASURE_ON_PHONE_EQ -> "Switch on the phone's EQ and measure"
}

/** The result of measuring with the EQ off and then on: how much of the EQ really reached the speakers. */
class EqCheck(
    /** Share of the EQ found in the measured change (1 = all of it, 0 = none), from a least-squares fit. */
    val effect: Double,
    /** What is left when that share of the EQ is taken out of the measured change, RMS dB. */
    val residualDb: Double,
    /** 0 the EQ arrives, 1 only partly, 2 it does not arrive, 3 the EQ is too flat to tell. */
    val severity: Int,
    val verdict: String,
)

/**
 * Compares the measured change ([onDb] − [offDb], both smoothed) with what the EQ should do ([expectedDb]) between
 * 60 Hz and 10 kHz. A level offset is allowed (volume, preamp), so only the shape counts.
 */
fun judgeEqCheck(freqs: DoubleArray, offDb: DoubleArray, onDb: DoubleArray, expectedDb: DoubleArray): EqCheck {
    val idx = freqs.indices.filter { freqs[it] in 60.0..10000.0 }
    val d = idx.map { onDb[it] - offDb[it] }
    val e = idx.map { expectedDb[it] }
    val me = e.average(); val md = d.average()
    val ve = e.sumOf { (it - me) * (it - me) } / e.size
    if (ve < 1.0) return EqCheck(0.0, 0.0, 3, "This EQ changes the sound by less than about 1 dB, too little to check. Pick an EQ with clear bands and check again.")
    val k = idx.indices.sumOf { (e[it] - me) * (d[it] - md) } / e.size / ve
    val c = md - k * me
    val res = kotlin.math.sqrt(idx.indices.sumOf { val r = d[it] - c - k * e[it]; r * r } / idx.size)
    val pct = (k * 100).coerceIn(0.0, 150.0)
    return when {
        k >= 0.7 -> EqCheck(k, res, 0, "The EQ reaches the speakers: the sound changed by about %.0f%% of what the EQ asks for.".format(pct))
        k >= 0.3 -> EqCheck(
            k, res, 1,
            "Only about %.0f%% of the EQ reaches the speakers. Something after the phone may be adjusting the sound, or the readings were noisy: try again a step louder.".format(pct),
        )
        else -> EqCheck(
            k, res, 2,
            "The EQ is not reaching the speakers: the sound hardly changed when it was switched on. Android Auto (wireless or cable) and some car systems take the sound past the phone's own EQ. Play over plain Bluetooth, or set the bands on the head unit.",
        )
    }
}


// ---------------------------------------------------------------- what plays: the tuned EQ, a flavour, the preamp

/** How the preamp keeps a boosting EQ from clipping. */
enum class PreampMode(val title: String, val detail: String) {
    SAFE("Safe", "Lowers everything by the biggest boost: never clips, but boosts sound quieter"),
    BALANCED("Balanced", "Lowers by half the biggest boost; the limiter catches the rare peak. Louder, rarely audible"),
    OFF("Off", "Full level; the limiter does all the work. Loudest, can squash bass-heavy music"),
    MANUAL("Manual", "Set the preamp yourself with the slider"),
    ;

    companion object { fun of(name: String?) = entries.firstOrNull { it.name == name } ?: SAFE }
}

/**
 * A flavour laid on top of the tuned EQ, to taste or to the kind of listening: the tune makes the system right, the
 * flavour makes it the way you want it now. Gentle starting points; never used while measuring.
 */
enum class Flavour(val title: String, val detail: String, val filters: List<EqFilter>) {
    NONE("As tuned", "Exactly the tuned sound", emptyList()),
    BASS("More bass", "+4 dB in the low bass", listOf(EqFilter(60.0, 4.0, 0.7))),
    ROCK("Rock", "Punchy bass, a little less boxiness, clearer guitars and cymbals",
        listOf(EqFilter(80.0, 3.0, 0.8), EqFilter(400.0, -1.5, 1.0), EqFilter(3000.0, 2.0, 1.0), EqFilter(10000.0, 2.0, 0.8))),
    POP("Pop", "A little more bass, presence and air", listOf(EqFilter(100.0, 2.0, 0.8), EqFilter(2500.0, 1.5, 1.0), EqFilter(10000.0, 2.0, 0.8))),
    ELECTRONIC("Electronic / hip-hop", "Deep sub-bass, cleaner low mids, some sparkle",
        listOf(EqFilter(50.0, 5.0, 0.8), EqFilter(500.0, -1.0, 1.0), EqFilter(10000.0, 1.5, 0.8))),
    ACOUSTIC("Acoustic / classical", "A touch of warmth and air, nothing hyped", listOf(EqFilter(200.0, 1.0, 0.8), EqFilter(6000.0, 1.0, 0.8))),
    SPEECH("Speech / podcasts", "Less rumble, clearer voices", listOf(EqFilter(80.0, -4.0, 0.7), EqFilter(250.0, -1.0, 1.0), EqFilter(3000.0, 3.0, 1.2), EqFilter(10000.0, -2.0, 0.8))),
    LOUDNESS("Low volume", "More bass and treble so quiet listening stays full", listOf(EqFilter(60.0, 6.0, 0.7), EqFilter(10000.0, 4.0, 0.7))),
    NIGHT("Late night", "Less bass, voices kept clear", listOf(EqFilter(60.0, -3.0, 0.7), EqFilter(2500.0, 1.5, 1.0))),
    ;

    companion object { fun of(name: String?) = entries.firstOrNull { it.name == name } ?: NONE }
}

/** The manual preamp's range: down to −15 dB, and up to +6 dB for a quiet system (the limiter catches the peaks). */
const val PREAMP_MIN = -15.0
const val PREAMP_MAX = 6.0

/** The chain that plays: [filters] (the EQ in use) plus the flavour, with the preamp the mode asks for. */
fun playChain(filters: List<EqFilter>, flavour: Flavour, mode: PreampMode, manualDb: Double, grid: dev.autotune.dsp.FreqGrid = dev.autotune.dsp.FreqGrid()): EqChain {
    val f = (filters + flavour.filters).sortedBy { it.freqHz }
    val peak = (grid.freqs.maxOfOrNull { fr -> f.sumOf { it.magnitudeDb(fr) } } ?: 0.0).coerceAtLeast(0.0)
    val pre = when (mode) {
        PreampMode.SAFE -> if (peak > 0.0) -(peak + 0.3) else 0.0
        PreampMode.BALANCED -> -(peak * 0.5)
        PreampMode.OFF -> 0.0
        PreampMode.MANUAL -> manualDb.coerceIn(PREAMP_MIN, PREAMP_MAX)
    }
    return EqChain(f, pre)
}

// ---------------------------------------------------------------- how the speakers followed the EQ

/** One octave: what the EQ asked for there and what the mic heard change (level-matched). */
class BandFollow(val centreHz: Double, val askedDb: Double, val gotDb: Double)

class EqFollow(
    /** Share of the whole EQ that showed up (least squares), or null when the EQ was too flat to tell. */
    val share: Double?,
    val bands: List<BandFollow>,
    /** Boosts below this frequency did nothing: the speakers do not play that low. Null when they followed. */
    val deafBelowHz: Double?,
    val summary: String,
)

private val OCTAVES = listOf(31.5, 63.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0)

/**
 * Compares a run with the stock run, octave by octave: what [applied] asked for against what the mic heard change.
 * Finds boosts the speakers could not follow at the bottom end (the usual case: a boost below what the speakers play).
 */
fun eqFollow(stockDb: DoubleArray, runDb: DoubleArray, applied: List<EqFilter>, grid: dev.autotune.dsp.FreqGrid): EqFollow? {
    if (applied.isEmpty() || stockDb.size != grid.size || runDb.size != grid.size) return null
    val st = grid.smoothOctave(stockDb, 3.0)
    val rn = grid.smoothOctave(runDb, 3.0)
    val asked = DoubleArray(grid.size) { i -> applied.sumOf { it.magnitudeDb(grid.freqs[i]) } }
    // overall level changes (volume, preamp) are not EQ: match the levels over 250 Hz-2.5 kHz
    val ref = grid.freqs.indices.filter { grid.freqs[it] in 250.0..2500.0 }
    val shift = ref.sumOf { (rn[it] - st[it]) - asked[it] } / ref.size
    val bands = OCTAVES.mapNotNull { c ->
        val idx = grid.freqs.indices.filter { grid.freqs[it] in c / 1.414..c * 1.414 }
        if (idx.isEmpty()) null else BandFollow(c, idx.sumOf { asked[it] } / idx.size, idx.sumOf { rn[it] - st[it] - shift } / idx.size)
    }
    val share = judgeEqCheck(grid.freqs, st, rn, asked).takeIf { it.severity != 3 }?.effect
    // boosts at the bottom that the speakers ignored
    var deaf: Double? = null
    for (b in bands) {
        if (b.askedDb >= 1.5 && b.gotDb < 0.3 * b.askedDb) deaf = b.centreHz * 1.414 else break
    }
    val summary = buildString {
        if (share != null) append("The sound followed about %.0f%% of the EQ.".format((share * 100).coerceIn(0.0, 150.0)))
        deaf?.let { append(" Boosts below about %.0f Hz did nothing: the speakers do not play that low, so AutoTune stops boosting there.".format(it)) }
    }.trim()
    return EqFollow(share, bands, deaf, summary)
}

// ---------------------------------------------------------------- further tests worth doing

enum class CheckAction { REMEASURE_STOCK, MORE_SPOTS, REPEAT, EQ_CHECK, NONE }

class CheckSuggestion(val title: String, val why: String, val action: CheckAction)

/**
 * Tests that would make the result more trustworthy, from what the readings show: a weak stock reading, a single mic
 * spot in a car, an EQ the sound did not follow, a run far from what was predicted with no repeat to compare.
 */
fun suggestChecks(
    runs: List<SavedRun>, isCar: Boolean, spots: Int, follow: EqFollow?, predictedLatestDb: Double?,
): List<CheckSuggestion> {
    val out = ArrayList<CheckSuggestion>()
    val stock = runs.firstOrNull() ?: return out
    if (stock.warnings.any { "quiet" in it || "signal-to-noise" in it.lowercase() || "barely heard" in it }) {
        out += CheckSuggestion("Measure the stock again, a step louder", "The stock reading was weak, and every suggestion is built on it.", CheckAction.REMEASURE_STOCK)
    }
    if (isCar && spots < 3) {
        out += CheckSuggestion("Use 3 mic spots", "In a car the response changes with every centimetre; averaging three spots keeps one unlucky spot from steering the EQ.", CheckAction.MORE_SPOTS)
    }
    if (runs.size >= 2) {
        val latest = runs.last()
        follow?.share?.let { sh ->
            if (sh < 0.6) out += CheckSuggestion("Check the EQ reaches the speakers", "The sound followed only about %.0f%% of the EQ.".format((sh * 100).coerceAtLeast(0.0)), CheckAction.EQ_CHECK)
        }
        val repeated = runs.any { it.label.contains("repeat") }
        if (!repeated && predictedLatestDb != null && latest.rmsDb > predictedLatestDb + 1.5) {
            out += CheckSuggestion(
                "Measure again with nothing changed",
                "${latest.label} came out %.1f dB from the target where about %.1f dB was expected. A repeat shows whether the reading or the mic spot moved.".format(latest.rmsDb, predictedLatestDb),
                CheckAction.REPEAT,
            )
        }
        follow?.deafBelowHz?.let {
            out += CheckSuggestion("Bass below %.0f Hz is out of reach".format(it), "The speakers did not follow boosts down there. A subwoofer, or its level, is the fix; EQ is not.", CheckAction.NONE)
        }
    }
    return out
}

// ---------------------------------------------------------------- navigation (1.21)

/** The bottom bar, start to end, with Tune (the main action) raised in the centre. Names of `ui.Tab`. */
val NAV_ORDER = listOf("TESTS", "EQ", "MEASURE", "LIVE", "SETTINGS")

/**
 * Which way a change of tab slides: +1 when the new tab sits after the old one on the bar, -1 before it, 0 for the
 * same tab or one not on the bar. Left and right therefore always mean something.
 */
fun navSlide(from: String, to: String): Int {
    val a = NAV_ORDER.indexOf(from)
    val b = NAV_ORDER.indexOf(to)
    if (a < 0 || b < 0) return 0
    return b.compareTo(a)
}

/** Which way the Tune workflow slides between two steps: forward +1, back -1. */
fun stepSlide(from: TuneStep, to: TuneStep): Int = to.ordinal.compareTo(from.ordinal)

/** The tab a widget, shortcut or intent asks for ("eq", "live", "settings"; anything else opens Tune). */
fun requestedTab(request: String): String = when (request.lowercase()) {
    "eq" -> "EQ"
    "live" -> "LIVE"
    "settings" -> "SETTINGS"
    "tests" -> "TESTS"
    else -> "MEASURE"
}

// ---------------------------------------------------------------- EQ picker (1.22)

private val AUTO_TEST_NAME = Regex("""^(.+?) test, (\d{1,2} \S+ \d{1,2}:\d{2})(?: \((\d+)\))?$""")

/** A target's name without its bracketed note: "Car, Harman-style (+8 dB bass)" -> "Car, Harman-style". */
fun shortTarget(target: String): String = target.replace(Regex("""\s*\([^)]*\)"""), "").trim()

/**
 * The heading and the line under it for a test in the EQ picker. An automatic name ("<target> test, 5 Nov 01:52",
 * maybe with " (2)") becomes the date over the short target; a name the user typed stays, over the short target.
 */
fun shortTestName(name: String, target: String): Pair<String, String?> {
    val m = AUTO_TEST_NAME.matchEntire(name.trim())
    if (m != null) {
        val (t, date, n) = m.destructured
        return (if (n.isNotEmpty()) "$date ($n)" else date) to shortTarget(t)
    }
    val sub = shortTarget(target).takeIf { it.isNotBlank() && !name.contains(it) }
    return name to sub
}

// ---------------------------------------------------------------- loud EQ (dynamic EQ for high volume)

/** When the loud EQ comes in. It only plays: measurements never use it. */
enum class LoudMode(val title: String, val detail: String) {
    OFF("Off", "One EQ at every volume"),
    VOLUME("Follow the volume", "Comes in as you turn up. The button and volume keys still work"),
    BUTTON("Button only", "Only when you switch it on: notification, quick tile or volume keys"),
    LEVEL("Follow the sound level", "Listens with the phone's mic while music plays and comes in when it gets loud"),
    ;

    companion object { fun of(name: String?) = entries.firstOrNull { it.name == name } ?: OFF }
}

/** THD above this, at a loud reading, means the speakers are being pushed too hard at [f]. */
fun loudLimitPct(f: Double): Double = when {
    f < 200.0 -> 6.0
    f < 2500.0 -> 3.0
    else -> 2.0
}

private val LOUD_OCTAVES = listOf(31.5, 63.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0)

/**
 * Cuts for loud listening from the distortion of a reading made at a loud volume: one octave-wide cut per octave whose
 * THD is over [loudLimitPct]. Harmonics fall faster than the level (2 dB per dB for the 3rd), so the cut is 2/3 of the
 * excess, at most 6 dB. Empty when nothing distorts.
 */
fun loudCutsFromDistortion(freqs: DoubleArray, thdPct: DoubleArray): List<EqFilter> {
    if (freqs.size != thdPct.size) return emptyList()
    return LOUD_OCTAVES.mapNotNull { c ->
        val v = freqs.indices.filter { freqs[it] in c / 1.414..c * 1.414 && thdPct[it].isFinite() && thdPct[it] > 0.0 }.map { thdPct[it] }.sorted()
        if (v.size < 2) return@mapNotNull null
        val median = v[v.size / 2]
        val excessDb = 20.0 * log10(median / loudLimitPct(c))
        val cut = (Math.round(excessDb / 1.5 * 2.0) / 2.0).coerceIn(0.0, 6.0)
        if (cut < 1.0) null else EqFilter(c, -cut, 1.4)
    }
}

/** The safe loud EQ when there is no loud reading: less of the deep bass small speakers can't play loud, a softer 3.5 kHz. */
val LOUD_DEFAULT_CUTS = listOf(EqFilter(40.0, -3.0, 0.7), EqFilter(3500.0, -1.5, 1.0))

/**
 * Everything the loud EQ changes at full strength, on top of [play] (the EQ that plays, flavour included): the
 * distortion cuts (or [LOUD_DEFAULT_CUTS] when there is no reading), and 60% of every boost below 150 Hz or above 5 kHz
 * taken back, since boosts are what run out of room first. Only cuts, each at most 6 dB.
 */
fun loudDelta(play: List<EqFilter>, cuts: List<EqFilter>?): List<EqFilter> {
    val pullBack = play.filter { it.gainDb > 0.5 && (it.freqHz < 150.0 || it.freqHz > 5000.0) }
        .map { EqFilter(it.freqHz, -(it.gainDb * 0.6).coerceAtMost(6.0), it.q) }
    return ((cuts ?: LOUD_DEFAULT_CUTS).map { EqFilter(it.freqHz, it.gainDb.coerceIn(-6.0, 0.0), it.q) } + pullBack)
        .filter { it.gainDb < 0.0 }.sortedBy { it.freqHz }
}

/** How much of the loud EQ plays at [volume] (0..1): none up to [start], all of it at full volume, smooth between. */
fun loudAmount(volume: Double, start: Double): Double {
    val s = start.coerceIn(0.3, 0.95)
    if (volume <= s) return 0.0
    val t = ((volume - s) / (1.0 - s)).coerceIn(0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)
}

/** [chain] with [amount] of the loud [delta]. The preamp is kept: the loud EQ only cuts, so it can never clip. */
fun withLoud(chain: EqChain, delta: List<EqFilter>, amount: Double): EqChain {
    val a = amount.coerceIn(0.0, 1.0)
    if (a <= 0.0 || delta.isEmpty()) return chain
    return EqChain((chain.filters + delta.map { EqFilter(it.freqHz, it.gainDb * a, it.q) }).sortedBy { it.freqHz }, chain.preampDb)
}

/** Bands as text for the settings ("f,gain,q;..."), and back. Bad text reads as no bands. */
fun filtersToText(f: List<EqFilter>): String = f.joinToString(";") { "${it.freqHz},${it.gainDb},${it.q}" }
fun filtersFromText(s: String?): List<EqFilter>? = s?.let {
    runCatching { it.split(";").filter { p -> p.isNotBlank() }.map { p -> p.split(",").map { n -> n.toDouble() }.let { n -> EqFilter(n[0], n[1], n[2]) } } }.getOrNull()
}

/**
 * The volume-key switch for the loud EQ: four quick presses that alternate (down, up, down, up, or up, down, up, down)
 * within [windowMs]. It leaves the volume where it was. Holding a key gives steps in one direction, which never count.
 * An app only sees the volume change, so presses at the top or bottom of the range can't be seen.
 */
class VolumeGesture(private val windowMs: Long = 2500, private val presses: Int = 4) {
    private val steps = ArrayDeque<Pair<Long, Int>>()

    /** Feeds one volume step ([dir] +1 up, -1 down) at [timeMs]; true when the pattern is complete. */
    fun step(timeMs: Long, dir: Int): Boolean {
        if (dir == 0) return false
        val last = steps.lastOrNull()
        if (last != null && (last.second == dir || timeMs - last.first > windowMs)) steps.clear()
        steps.addLast(timeMs to dir)
        while (steps.isNotEmpty() && timeMs - steps.first().first > windowMs) steps.removeFirst()
        if (steps.size >= presses) { steps.clear(); return true }
        return false
    }
}

/** The volume check: the car's knob moved the phone's volume (at least two steps were seen) or it didn't. */
fun volumeFollows(levels: List<Int>): Boolean = levels.zipWithNext().count { (a, b) -> a != b } >= 2

// ---------------------------------------------------------------- loud EQ by sound level (the phone's mic)

/** The mic level a loud EQ by sound level starts from before the user sets one, dB (the Live tab's SPL scale). */
const val LOUD_LEVEL_DEFAULT = 85.0

/** Mic blocks quieter than this (dBFS) are silence handed out while another app holds the mic: they don't count. */
const val MIC_SILENCED_DBFS = -100.0

/** The Live tab's SPL scale for the phone's own mic: dBFS + 112 dB + the user's SPL offset. */
fun micSpl(dbfs: Double, offsetDb: Double): Double = dbfs + 112.0 + offsetDb

/**
 * The cabin level, smoothed so a drum hit doesn't count: it rises with a [attackS] time constant and falls with a
 * slower [releaseS] one, so the loud EQ doesn't pump between songs or quiet bars.
 */
class LevelFollower(private val attackS: Double = 2.0, private val releaseS: Double = 6.0) {
    var level: Double? = null
        private set

    /** Feeds one block's level [db] lasting [dtS] seconds; returns the smoothed level. Non-finite levels are skipped. */
    fun feed(db: Double, dtS: Double): Double? {
        if (!db.isFinite()) return level
        val l = level
        level = if (l == null) db else {
            val tau = if (db > l) attackS else releaseS
            l + (db - l) * (1.0 - kotlin.math.exp(-dtS / tau))
        }
        return level
    }
}

/** How much of the loud EQ plays at the cabin [levelDb]: none [rangeDb] below [loudDb], all of it at [loudDb], smooth between. */
fun levelAmount(levelDb: Double, loudDb: Double, rangeDb: Double = 6.0): Double {
    val t = ((levelDb - (loudDb - rangeDb)) / rangeDb).coerceIn(0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)
}

/**
 * The amount to play next, in 10% steps, changed only by 20% or more (or to none or all), so small level wobbles
 * don't rebuild the EQ every second.
 */
fun steadyAmount(current: Double, wanted: Double): Double {
    val n = Math.round(wanted.coerceIn(0.0, 1.0) * 10.0) / 10.0
    return if (n == 0.0 || n == 1.0 || kotlin.math.abs(n - current) >= 0.2 - 1e-9) n else current
}

// ---------------------------------------------------------------- result headline (1.25)

/**
 * The one-line summary that opens a result: how the latest run compares with the stock, and whether an earlier run
 * is still better. Differences under 0.3 dB count as the same.
 */
fun resultHeadline(stockDb: Double, latestDb: Double, bestDb: Double? = null, bestLabel: String? = null): String {
    val gain = stockDb - latestDb
    val head = when {
        gain > 0.3 -> "%.1f dB closer to the target than stock.".format(gain)
        gain < -0.3 -> "%.1f dB further from the target than stock.".format(-gain)
        else -> "About the same as stock."
    }
    val tail = if (bestDb != null && bestLabel != null && bestDb < latestDb - 0.3) " $bestLabel is still the best." else ""
    return head + tail
}

/** The count on the folded notes line ([dev.autotune.ui.Alerts]): "1 note", "3 notes". */
fun alertsCount(n: Int): String = if (n == 1) "1 note" else "$n notes"

// ---------------------------------------------------------------- tuning scale (1.27)

/** The end of the tuning scale for these readings: the next of 4, 8, 12, 16, 24, 32 dB above the largest. */
fun scaleMax(values: List<Double>): Double {
    val top = values.maxOrNull()?.takeIf { it.isFinite() } ?: 0.0
    return listOf(4.0, 8.0, 12.0, 16.0, 24.0, 32.0).firstOrNull { it >= top + 0.5 } ?: (Math.ceil((top + 0.5) / 8.0) * 8.0)
}

// ---------------------------------------------------------------- accent colour (1.28)

/**
 * The accent: what is live or acted on (the record label, the needle, the pilot light, the EQ). LOOK keeps the look's
 * own; the others follow Braun's own colour keys (the ET 66's yellow and green, the SK 4's red). Colours are ARGB,
 * for light and dark, each with the colour of text set on it.
 */
enum class Accent(val title: String, val light: Long, val onLight: Long, val dark: Long, val onDark: Long) {
    LOOK("As the look", 0, 0, 0, 0),
    RED("Red", 0xFFC8401AL, 0xFFFFFFFFL, 0xFFF0663AL, 0xFF1A0A03L),
    ORANGE("Orange", 0xFFC2410CL, 0xFFFFFFFFL, 0xFFFF8A3DL, 0xFF1F0B00L),
    YELLOW("Yellow", 0xFF8A6200L, 0xFFFFFFFFL, 0xFFF2C12EL, 0xFF1F1600L),
    GREEN("Green", 0xFF2F7A3AL, 0xFFFFFFFFL, 0xFF6CB866L, 0xFF0A1A08L),
    BLUE("Blue", 0xFF2456A6L, 0xFFFFFFFFL, 0xFF7FA8F0L, 0xFF061226L),
    GRAPHITE("Graphite", 0xFF353534L, 0xFFF2F2EFL, 0xFFD2D2CFL, 0xFF1B1B1BL),
    ;

    companion object { fun of(name: String?): Accent = entries.firstOrNull { it.name == name } ?: LOOK }
}

/** WCAG contrast ratio of two opaque ARGB colours (1 to 21). */
fun contrast(a: Long, b: Long): Double {
    fun lum(c: Long): Double {
        fun ch(v: Long): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch((c shr 16) and 0xFF) + 0.7152 * ch((c shr 8) and 0xFF) + 0.0722 * ch(c and 0xFF)
    }
    val (x, y) = lum(a) to lum(b)
    return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
}

// ---------------------------------------------------------------- looks and the palette (2.8)

/** Looks removed in 2.8 (only the Braun looks stay). A saved setting that names one opens the default look. */
val RETIRED_LOOKS = setOf("DYNO", "STUDIO", "CONSOLE", "HIFI", "VU", "SYNTH", "NAVY")

/** The look to open for the saved name [saved]: the kept look of that name, else [fallback] (retired, unknown or none). */
fun resolveLookName(saved: String?, kept: List<String>, fallback: String): String =
    saved?.takeIf { it in kept } ?: fallback

/** The colours of the palette the user can set. [key] is the preference that stores it as "#RRGGBB". */
enum class PaletteSlot(val title: String, val key: String) {
    BODY("Body", "palBody"),
    DECK("Deck", "palDeck"),
    INK("Ink", "palInk"),
    ACCENT("Accent", "palAccent"),
    RULE("Hairline", "palRule"),
}

/** The user's own colours, opaque ARGB per slot; a slot that is not set keeps the look's own colour. */
data class Palette(val colours: Map<PaletteSlot, Long> = emptyMap()) {
    operator fun get(slot: PaletteSlot): Long? = colours[slot]
    fun with(slot: PaletteSlot, argb: Long?): Palette =
        Palette(if (argb == null) colours - slot else colours + (slot to (0xFF000000L or (argb and 0xFFFFFFL))))
    val isDefault: Boolean get() = colours.isEmpty()
}

/**
 * A colour typed by hand: "#RGB" or "#RRGGBB", the # optional, any case, spaces around ignored. Returns opaque ARGB,
 * or null for anything else (so a bad entry never reaches the theme).
 */
fun parseHexColour(text: String?): Long? {
    val t = text?.trim()?.removePrefix("#") ?: return null
    if (t.length != 3 && t.length != 6) return null
    if (!t.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    val six = if (t.length == 3) t.map { "$it$it" }.joinToString("") else t
    return 0xFF000000L or six.toLong(16)
}

/** "#RRGGBB" for an ARGB colour (alpha dropped). */
fun hexColour(argb: Long): String = "#%06X".format(argb and 0xFFFFFFL)

/** The saved palette, read slot by slot through [read] (the preference for a key); a missing or bad value is not set. */
fun paletteFrom(read: (String) -> String?): Palette =
    Palette(PaletteSlot.entries.mapNotNull { s -> parseHexColour(read(s.key))?.let { s to it } }.toMap())

/** A named colour offered in the palette's drop-downs. */
data class PaletteColour(val name: String, val argb: Long)

/** Body, deck, ink and hairline choices: Braun's whites, greys and graphites. */
val PALETTE_NEUTRALS = listOf(
    PaletteColour("White", 0xFFFFFFFFL),
    PaletteColour("Warm white", 0xFFF3F2EEL),
    PaletteColour("Paper", 0xFFE9E7E1L),
    PaletteColour("Light grey", 0xFFD5D5D2L),
    PaletteColour("Aluminium", 0xFFB9BAB8L),
    PaletteColour("Stone", 0xFF85857FL),
    PaletteColour("Slate", 0xFF5E5C57L),
    PaletteColour("Graphite", 0xFF353533L),
    PaletteColour("Charcoal", 0xFF2B2B29L),
    PaletteColour("Near black", 0xFF1C1C1BL),
    PaletteColour("Black", 0xFF121211L),
)

/** Accent choices, after Braun's colour keys. */
val PALETTE_ACCENTS = listOf(
    PaletteColour("Braun red", 0xFFBF3C18L),
    PaletteColour("Light red", 0xFFF0663AL),
    PaletteColour("Orange", 0xFFC2410CL),
    PaletteColour("Yellow", 0xFFF2C12EL),
    PaletteColour("Green", 0xFF3D7F3AL),
    PaletteColour("Blue", 0xFF2456A6L),
    PaletteColour("Graphite", 0xFF353534L),
)

fun paletteChoices(slot: PaletteSlot): List<PaletteColour> = if (slot == PaletteSlot.ACCENT) PALETTE_ACCENTS else PALETTE_NEUTRALS

/** [a] mixed towards [b] by [t] (0 = a, 1 = b), per channel, opaque. */
fun mixArgb(a: Long, b: Long, t: Double): Long {
    fun ch(c: Long, sh: Int) = (c shr sh) and 0xFF
    fun m(sh: Int) = Math.round(ch(a, sh) + (ch(b, sh) - ch(a, sh)) * t.coerceIn(0.0, 1.0)) shl sh
    return 0xFF000000L or m(16) or m(8) or m(0)
}

private const val INK_DARK = 0xFF1B1B1AL
private const val INK_LIGHT = 0xFFF2F1ECL

/** The lowest contrast of [c] against each of [grounds]. */
fun minContrast(c: Long, grounds: List<Long>): Double = grounds.minOf { contrast(c, it) }

/**
 * The ink for text on [grounds]: the [wanted] ink when it reads (4.5:1 on every ground), else the look's [current]
 * ink when that reads, else near-black or near-white, whichever reads better. A palette can never make text vanish.
 */
fun readableInk(wanted: Long?, current: Long, grounds: List<Long>): Long = when {
    wanted != null && minContrast(wanted, grounds) >= 4.5 -> wanted
    minContrast(current, grounds) >= 4.5 -> current
    else -> if (minContrast(INK_DARK, grounds) >= minContrast(INK_LIGHT, grounds)) INK_DARK else INK_LIGHT
}

/** The muted ink for labels: [ink] mixed as far towards the first ground as still reads 4.5:1 on every ground. */
fun mutedInk(ink: Long, grounds: List<Long>): Long {
    var t = 0.40
    while (t > 0.0) {
        val m = mixArgb(ink, grounds.first(), t)
        if (minContrast(m, grounds) >= 4.5) return m
        t -= 0.05
    }
    return ink
}

/** The text colour on an accent key: white or near-black, whichever reads better on [accent]. */
fun onAccent(accent: Long): Long = if (contrast(0xFFFFFFFFL, accent) >= contrast(INK_DARK, accent)) 0xFFFFFFFFL else INK_DARK

// ---------------------------------------------------------------- deleting a run (2.1)

/** Whether run [index] of [count] can be deleted on its own: any run but the stock reading (index 0). */
fun canDeleteRun(index: Int, count: Int): Boolean = index in 1 until count

/**
 * The runs ticked for the graph after run [removed] is deleted from a test that now has [newCount] runs: later runs
 * move down one, and the latest run stays shown so the graph is never empty.
 */
fun selectionAfterDelete(selected: Set<Int>, removed: Int, newCount: Int): Set<Int> {
    val moved = selected.filter { it != removed }.map { if (it > removed) it - 1 else it }.filter { it in 0 until newCount }.toSet()
    return if (newCount == 0) emptySet() else moved + (newCount - 1)
}

// ---------------------------------------------------------------- fewer stops (2.1.1)

/**
 * Whether "Apply the new suggested EQ" can skip the Apply step: on the phone routes, when the user already chose to
 * use AutoTune EQ (and it is not known to damage the sound on this phone) or to preview in the app. Setting bands on
 * the user's own equipment always shows the Apply step, because they have to see the bands to set them.
 */
fun refineGoesStraightToMeasure(phoneRoute: Boolean, way: ApplyWay, systemEqSuspect: Boolean): Boolean =
    phoneRoute && ((way == ApplyWay.AUTOTUNE_EQ && !systemEqSuspect) || way == ApplyWay.IN_APP)

// ---------------------------------------------------------------- the tall header on a fresh open (2.2.0)

/** After this long in the background, coming back counts as a fresh open: 15 minutes. */
const val FRESH_OPEN_AFTER_MS = 15L * 60 * 1000

/**
 * Whether opening the app now is a fresh open: a cold start (never backgrounded, [lastBackgroundedAtMs] null), or a
 * return after the app was in the background for [thresholdMs] or more. A clock that went backwards (a reboot between
 * the two readings) counts as fresh too. Both times come from the same monotonic clock.
 */
fun freshOpen(lastBackgroundedAtMs: Long?, nowMs: Long, thresholdMs: Long = FRESH_OPEN_AFTER_MS): Boolean =
    lastBackgroundedAtMs == null || nowMs < lastBackgroundedAtMs || nowMs - lastBackgroundedAtMs >= thresholdMs

/**
 * The screens that open with the tall header when the app comes to the front: every one of [all] on a fresh open
 * (see [freshOpen]), otherwise only those still in [current] (not yet visited since the last fresh open).
 */
fun tallOnStart(current: Set<String>, all: Set<String>, lastBackgroundedAtMs: Long?, nowMs: Long): Set<String> =
    if (freshOpen(lastBackgroundedAtMs, nowMs)) all else current

/**
 * Leaving screen [left] uses up its tall header: jumping back to it, or going Back to it, opens it compact. The
 * other screens keep theirs until they are visited.
 */
fun tallAfterLeaving(current: Set<String>, left: String): Set<String> = current - left

// ---------------------------------------------------------------- the slider row's scroll cue (2.2.0)

/**
 * What the cue on a sideways row shows: a fade and chevron on the side where more waits, and the position bar
 * (thumb start and width, both as fractions of the row). [shown] is false when everything fits.
 */
data class ScrollCue(val moreLeft: Boolean, val moreRight: Boolean, val thumbStart: Float, val thumbWidth: Float) {
    val shown: Boolean get() = moreLeft || moreRight
}

/**
 * The cue for a row scrolled to [scrollPx] of [maxScrollPx] with [viewportPx] in view. [slackPx] absorbs rounding,
 * so a row a pixel short of its end reads as at the end.
 */
fun scrollCue(scrollPx: Int, maxScrollPx: Int, viewportPx: Int, slackPx: Int = 2): ScrollCue {
    if (maxScrollPx <= slackPx || viewportPx <= 0) return ScrollCue(false, false, 0f, 1f)
    val pos = scrollPx.coerceIn(0, maxScrollPx)
    val width = viewportPx.toFloat() / (viewportPx + maxScrollPx)
    val start = (1f - width) * pos / maxScrollPx
    return ScrollCue(pos > slackPx, pos < maxScrollPx - slackPx, start, width)
}

// ---------------------------------------------------------------- the header that folds into a slim bar (2.3.0)

/**
 * How far the tall header has folded into the slim bar, 0 (tall, at the very top) to 1 (slim): the content's scroll
 * [scrollPx] over the height the header gives up, [rangePx]. The header's space is part of the content, so the full
 * header only comes back when the content is scrolled all the way to its top.
 */
fun headerProgress(scrollPx: Float, rangePx: Float): Float =
    if (rangePx <= 0f) 1f else (scrollPx / rangePx).coerceIn(0f, 1f)

/**
 * The progress the header's parts follow. With animations on it is [headerProgress]; with "Remove animations" it
 * switches: 0 until the header has scrolled away, then 1.
 */
fun headerPhase(scrollPx: Float, rangePx: Float, animate: Boolean): Float =
    if (animate) headerProgress(scrollPx, rangePx) else if (scrollPx >= rangePx) 1f else 0f

/** The full header is only shown with the content at its very top. */
fun headerFull(scrollPx: Int): Boolean = scrollPx <= 0

/** Where the header's lower edge is drawn: it follows the content up, from [tallPx] down to the slim bar's [slimPx]. */
fun headerBottomPx(scrollPx: Float, tallPx: Float, slimPx: Float): Float =
    (tallPx - scrollPx.coerceAtLeast(0f)).coerceIn(slimPx, tallPx.coerceAtLeast(slimPx))

/**
 * A ramp: 0 at or before [from], 1 at or after [to], straight between. The parts of the header fade on their own
 * stretch of the fold (the wordmark leaves before the name arrives).
 */
fun ramp(p: Float, from: Float, to: Float): Float =
    if (to <= from) (if (p >= to) 1f else 0f) else ((p - from) / (to - from)).coerceIn(0f, 1f)

/** A rectangle in pixels, for the header's graphic as it moves. */
data class HeaderRect(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right: Float get() = x + w
    val bottom: Float get() = y + h
}

/**
 * The small version of the header's graphic in the slim bar: [slimH] high, the same proportions as [tall], its right
 * edge on [rowEndX] and centred on the bar's middle line [rowCentreY].
 */
fun slimArtRect(tall: HeaderRect, rowEndX: Float, rowCentreY: Float, slimH: Float): HeaderRect {
    val w = if (tall.h <= 0f) slimH else tall.w * slimH / tall.h
    return HeaderRect(rowEndX - w, rowCentreY - slimH / 2f, w, slimH)
}

/** The graphic's place at fold [p]: it shrinks and glides from [tall] to [slim], keeping its proportions. */
fun artAt(p: Float, tall: HeaderRect, slim: HeaderRect): HeaderRect {
    val t = p.coerceIn(0f, 1f)
    fun mix(a: Float, b: Float) = a + (b - a) * t
    return HeaderRect(mix(tall.x, slim.x), mix(tall.y, slim.y), mix(tall.w, slim.w), mix(tall.h, slim.h))
}

/**
 * Where a screen that opens compact starts: high enough that the header has folded into the slim bar, or where the
 * user left it if that is further down (so the content keeps its place). A screen without the header ([hasHeader]
 * false, a Tune step) keeps its scroll.
 */
fun compactOpenScroll(scrollPx: Int, rangePx: Int, hasHeader: Boolean = true): Int =
    if (!hasHeader) scrollPx else maxOf(scrollPx, rangePx.coerceAtLeast(0))

/**
 * The type size that lets the longest word of a name fit [availablePx] at [basePx]: never larger than the base, never
 * smaller than [minPx], so a long name or a large font scale wraps between words, not inside one.
 */
fun fitTitlePx(basePx: Float, longestWordPx: Float, availablePx: Float, minPx: Float): Float =
    if (longestWordPx <= 0f || longestWordPx <= availablePx) basePx
    else (basePx * availablePx / longestWordPx).coerceIn(minPx.coerceAtMost(basePx), basePx)
/** True when more players are active than before: audio just started (or came back after a route change). */
fun playbackStarted(before: Int, now: Int): Boolean = now > before
