package dev.autotune

import dev.autotune.dsp.EqChain
import dev.autotune.dsp.EqFilter
import dev.autotune.dsp.Export
import dev.autotune.dsp.FitConfig

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

/** What to do after a run with the EQ: the one recommended move and why. */
enum class NextMove { CHECK_EQ, KEEP_LATEST, KEEP_BEST, REFINE }

class Recommendation(val move: NextMove, val why: String, val bestIdx: Int)

fun recommendNext(runs: List<SavedRun>, eqMayNotBeApplied: Boolean): Recommendation {
    val latestIdx = runs.lastIndex
    val withEq = (1..latestIdx).toList()
    val bestIdx = withEq.minByOrNull { runs[it].rmsDb } ?: latestIdx
    val latest = runs[latestIdx]
    val best = runs[bestIdx]
    val prev = runs[latestIdx - 1]
    val gain = prev.rmsDb - latest.rmsDb
    return when {
        eqMayNotBeApplied -> Recommendation(
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

/** Where Back goes inside the Tune workflow: the previous screen, never deleting anything. */
fun previousStep(step: TuneStep, runCount: Int): TuneStep = when (step) {
    TuneStep.HOME, TuneStep.FRESH -> TuneStep.HOME
    TuneStep.SETUP -> TuneStep.FRESH
    TuneStep.STOCK -> if (runCount > 0) TuneStep.STOCK_RESULT else TuneStep.SETUP
    TuneStep.STOCK_RESULT -> TuneStep.HOME
    TuneStep.APPLY -> if (runCount > 1) TuneStep.RESULT else TuneStep.STOCK_RESULT
    TuneStep.VERIFY -> TuneStep.APPLY
    TuneStep.RESULT, TuneStep.DONE -> TuneStep.HOME
}

/** Steps worth coming back to after Exit (Fresh start and Set up are asked again; Done is finished). */
val RESUMABLE = setOf(TuneStep.STOCK, TuneStep.STOCK_RESULT, TuneStep.APPLY, TuneStep.VERIFY, TuneStep.RESULT)

/** Where Continue lands: the remembered step when it still makes sense for the test, otherwise its latest result. */
fun resumeAt(saved: TuneStep?, runCount: Int): TuneStep {
    val ok = when (saved) {
        TuneStep.STOCK -> true
        TuneStep.STOCK_RESULT, TuneStep.APPLY, TuneStep.VERIFY -> runCount >= 1
        TuneStep.RESULT -> runCount >= 2
        else -> false
    }
    if (ok && saved != null) return saved
    return when {
        runCount == 0 -> TuneStep.STOCK
        runCount == 1 -> TuneStep.STOCK_RESULT
        else -> TuneStep.RESULT
    }
}

/** The step names shown in the Tune progress bar, in order (Fresh start is step 1). */
val STEP_NAMES = listOf("Start", "Set up", "Stock", "Review", "EQ", "Verify", "Result")

fun stepIndex(step: TuneStep): Int = when (step) {
    TuneStep.HOME, TuneStep.FRESH -> 0
    TuneStep.SETUP -> 1
    TuneStep.STOCK -> 2
    TuneStep.STOCK_RESULT -> 3
    TuneStep.APPLY -> 4
    TuneStep.VERIFY -> 5
    TuneStep.RESULT, TuneStep.DONE -> 6
}

/** Where Continue picks up, in words. */
fun resumePlace(step: TuneStep): String = when (step) {
    TuneStep.STOCK -> "the stock reading"
    TuneStep.STOCK_RESULT -> "the stock review"
    TuneStep.APPLY -> "applying the suggested EQ"
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

/** The chain that plays: [filters] (the EQ in use) plus the flavour, with the preamp the mode asks for. */
fun playChain(filters: List<EqFilter>, flavour: Flavour, mode: PreampMode, manualDb: Double, grid: dev.autotune.dsp.FreqGrid = dev.autotune.dsp.FreqGrid()): EqChain {
    val f = (filters + flavour.filters).sortedBy { it.freqHz }
    val peak = (grid.freqs.maxOfOrNull { fr -> f.sumOf { it.magnitudeDb(fr) } } ?: 0.0).coerceAtLeast(0.0)
    val pre = when (mode) {
        PreampMode.SAFE -> if (peak > 0.0) -(peak + 0.3) else 0.0
        PreampMode.BALANCED -> -(peak * 0.5)
        PreampMode.OFF -> 0.0
        PreampMode.MANUAL -> manualDb.coerceIn(-15.0, 0.0)
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
