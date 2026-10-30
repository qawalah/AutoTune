package dev.autotune.dsp

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

private fun us(fmt: String, vararg a: Any) = String.format(Locale.US, fmt, *a)

/** Average power (dB) of a magnitude response between two frequencies. */
internal fun bandPowerDb(magDb: DoubleArray, grid: FreqGrid, lo: Double, hi: Double): Double {
    var s = 0.0
    var n = 0
    for (i in grid.freqs.indices) if (grid.freqs[i] in lo..hi) { s += 10.0.pow(magDb[i] / 10.0); n++ }
    return if (n == 0) -200.0 else 10.0 * log10(s / n)
}

/** Several mic positions averaged into one measurement (power average), so one unlucky spot does not decide the EQ. */
object Averaging {
    /**
     * Power average of the magnitude over all spots (warnings merged, the worst SNR kept). The impulse response, and so
     * the decay times read from it, is the FIRST spot's only: decays are not averaged.
     */
    fun average(ms: List<Measurement>): Measurement {
        require(ms.isNotEmpty())
        if (ms.size == 1) return ms[0]
        val first = ms[0]
        val mag = DoubleArray(first.magDb.size) { i -> 10.0 * log10(ms.sumOf { 10.0.pow(it.magDb[i] / 10.0) } / ms.size) }
        return Measurement(
            first.grid, mag, first.ir, first.irPeakIndex, first.fs, first.latencyMs,
            ms.mapNotNull { it.snrDb }.minOrNull(),
            ms.flatMap { it.warnings }.distinct() + "Averaged ${ms.size} mic positions.",
            null, ms.last().distortion, first.peakFromMarker, ms.last().driftPpm,
        )
    }
}

class CombinedResult(val measurement: Measurement, val agreementRmsDb: Double?)

/**
 * One recording of [TestSignals.combinedFile]: the sweep gives the response (and impulse response for ringing), the pink
 * noise gives an independent second reading. Their agreement is a built-in quality check.
 */
object CombinedAnalyzer {
    /** [onsetHint] = where the sweep starts when the caller already heard the start beeps (saves searching again). */
    fun analyze(rec: FloatArray, fs: Int, cal: Calibration?, gateMs: Double, onsetHint: Int? = null): CombinedResult {
        // locate first, then cut, then analyse: only the part around the sweep is ever transformed
        val onset = onsetHint ?: SweepTrim.locate(rec, fs)
            ?: error("No sweep heard. Raise the volume on the other device, make sure the file is playing, and start recording first.")
        val trimmed = SweepTrim.trimAt(rec, fs, onset, TestSignals.SWEEP_SECONDS)
        val sweepM = Analyzer.analyze(trimmed, Sweep.generate(fs, TestSignals.SWEEP_SECONDS), fs, cal, gateMs)
        if (sweepM.fatal != null) return CombinedResult(sweepM, null)

        val ps = onset + ((TestSignals.SWEEP_SECONDS + TestSignals.COMBINED_GAP_SECONDS + 0.5) * fs).toInt()
        val pe = min(rec.size, ps + ((TestSignals.COMBINED_PINK_SECONDS - 1.0) * fs).toInt())
        if (pe - ps < 16384 * 4) {
            return CombinedResult(
                sweepM.withWarnings(sweepM.warnings + "The pink-noise part was not recorded (recording too short), so only the sweep was used."),
                null,
            )
        }
        val noise = NoiseAnalyzer.analyze(rec.copyOfRange(ps, pe), fs, cal, sweepM.grid)
        if (noise.fatal != null) {
            return CombinedResult(
                sweepM.withWarnings(sweepM.warnings + "The pink noise was not heard, so only the sweep was used."),
                null,
            )
        }
        val a = rel(sweepM.magDb, sweepM.grid)
        val b = rel(noise.magDb, sweepM.grid)
        var sum = 0.0
        var n = 0
        for (i in sweepM.grid.freqs.indices) if (sweepM.grid.freqs[i] in 63.0..10000.0) { sum += (a[i] - b[i]).pow(2); n++ }
        val diff = sqrt(sum / n)
        val warns = sweepM.warnings.toMutableList()
        warns += noise.warnings
        if (diff > 3.0) warns += us("The sweep and the pink noise disagree by %.1f dB RMS. Something changed during the recording (volume, mic moved, noise), or the analysis window hides strong room reflections. Measure again.", diff)
        return CombinedResult(
            sweepM.withWarnings(warns.distinct()),
            diff,
        )
    }

    private fun rel(mag: DoubleArray, g: FreqGrid): DoubleArray {
        val s = g.smoothOctave(mag, 6.0)
        var off = 0.0
        var n = 0
        for (i in g.freqs.indices) if (g.freqs[i] in 250.0..2500.0) { off += s[i]; n++ }
        off /= n
        return DoubleArray(s.size) { s[it] - off }
    }
}

/** Will the exported EQ clip, and does it need a big volume change? */
object Headroom {
    class Result(val peakDb: Double, val maxBoostDb: Double, val warnings: List<String>)

    fun check(chain: EqChain, grid: FreqGrid = FreqGrid()): Result {
        val peak = grid.freqs.maxOfOrNull { chain.responseDb(it) } ?: 0.0
        val boost = grid.freqs.maxOfOrNull { f -> chain.filters.sumOf { it.magnitudeDb(f) } } ?: 0.0
        val w = ArrayList<String>()
        if (peak > 0.0) w += us("This EQ boosts the signal by up to %.1f dB after the preamp, so loud music can clip. Lower the preamp by %.1f dB.", peak, peak + 0.3)
        if (boost > 6.0) w += us("The EQ boosts by up to %.1f dB. Large boosts use a lot of amplifier power and can distort at high volume.", boost)
        if (chain.preampDb < -6.0) w += us("Preamp is %.1f dB: the system will be quieter by that amount. Raise the amplifier or head-unit gain to compensate, in small steps.", chain.preampDb)
        if (chain.preampDb < -0.5) w += us("If your EQ app has no preamp setting, lower its output level by %.1f dB, or boosts can clip.", -chain.preampDb)
        return Result(peak, boost, w)
    }
}

class LrResult(
    /** Right minus left, dB (positive = right louder). */
    val levelDiffDb: Double,
    /** Right arrival minus left arrival, ms (positive = right is later); null when timing is not reliable. */
    val delayMs: Double?,
    val toneDiffRmsDb: Double,
    val advice: List<String>,
)

object LeftRight {
    fun analyze(left: Measurement, right: Measurement, delayValid: Boolean): LrResult {
        val g = left.grid
        val lv = bandPowerDb(left.magDb, g, 200.0, 4000.0)
        val rv = bandPowerDb(right.magDb, g, 200.0, 4000.0)
        val level = rv - lv
        val delay = if (delayValid && left.ir.isNotEmpty() && right.ir.isNotEmpty())
            (right.irPeakIndex - left.irPeakIndex) * 1000.0 / left.fs else null
        val ls = g.smoothOctave(left.magDb, 6.0)
        val rs = g.smoothOctave(right.magDb, 6.0)
        var sum = 0.0
        var n = 0
        for (i in g.freqs.indices) if (g.freqs[i] in 100.0..8000.0) { sum += (rs[i] - ls[i] - level).pow(2); n++ }
        val tone = sqrt(sum / n)
        val a = ArrayList<String>()
        if (abs(level) >= 1.0) a += us("%s is %.1f dB louder. Use the balance control, or lower that side by %.1f dB.", if (level > 0) "Right" else "Left", abs(level), abs(level))
        else a += "Left and right levels match within 1 dB."
        if (delay != null) {
            if (abs(delay) >= 0.15) a += us("The %s side arrives %.2f ms earlier (about %.0f cm closer). Delay that side by %.2f ms if your head unit has time alignment.", if (delay > 0) "left" else "right", abs(delay), abs(delay) * 34.3, abs(delay))
            else a += "Arrival times match within 0.15 ms."
        } else a += "Arrival time can only be compared when the phone plays the sweep."
        if (tone > 3.0) a += us("Left and right differ in tone by %.1f dB RMS. Check the speakers, their placement, or the mic position between the two measurements.", tone)
        return LrResult(level, delay, tone, a)
    }
}

class SubResult(
    val crossoverHz: Double?,
    val subAdviceDb: Double?,
    /** True = flip the sub's polarity (180°); null = no clear difference. */
    val invertPolarity: Boolean?,
    val polarityDeltaDb: Double?,
    val notes: List<String>,
)

/** Crossover point, sub level and polarity from four measurements: mains only, sub only, both, both with the sub inverted. */
object SubHelper {
    fun analyze(mains: Measurement?, sub: Measurement?, sumNormal: Measurement?, sumInverted: Measurement?, target: Target): SubResult {
        val notes = ArrayList<String>()
        var xo: Double? = null
        var advice: Double? = null
        if (mains != null && sub != null) {
            val g = mains.grid
            val m = g.smoothOctave(mains.magDb, 3.0)
            val s = g.smoothOctave(sub.magDb, 3.0)
            for (i in 1 until g.size) {
                val f = g.freqs[i]
                if (f < 40.0) continue
                if (f > 300.0) break
                val d0 = m[i - 1] - s[i - 1]
                val d1 = m[i] - s[i]
                if (d0 < 0 && d1 >= 0) {
                    val fr = g.freqs[i - 1] * (g.freqs[i] / g.freqs[i - 1]).pow(-d0 / (d1 - d0))
                    xo = Math.round(fr / 5.0) * 5.0
                    break
                }
            }
            val want = (40..80 step 4).map { target.dbAt(it.toDouble()) }.average() - (200..1000 step 100).map { target.dbAt(it.toDouble()) }.average()
            val have = bandPowerDb(sub.magDb, g, 40.0, 80.0) - bandPowerDb(mains.magDb, g, 200.0, 1000.0)
            advice = want - have
            notes += if (xo != null) us("The mains and the sub are equally loud at about %.0f Hz. Set the crossover close to this (the mains high-pass and sub low-pass at the same frequency).", xo)
            else "No crossing found between 40 and 300 Hz: the sub is louder or quieter than the mains everywhere there. Check the sub is on and its level."
            notes += if (abs(advice) < 1.5) "Sub level is about right for the target." else us("%s the sub level by about %.1f dB to match the target bass.", if (advice > 0) "Raise" else "Lower", abs(advice))
        } else notes += "Measure the mains alone and the sub alone to get a crossover point and a sub level."

        var invert: Boolean? = null
        var delta: Double? = null
        if (sumNormal != null && sumInverted != null) {
            val c = xo ?: 80.0
            val g = sumNormal.grid
            delta = bandPowerDb(sumNormal.magDb, g, c / 1.4, c * 1.4) - bandPowerDb(sumInverted.magDb, g, c / 1.4, c * 1.4)
            invert = when { delta < -1.0 -> true; delta > 1.0 -> false; else -> null }
            notes += when (invert) {
                true -> us("Inverting the sub polarity is %.1f dB louder around the crossover. Set the sub polarity to 180° (inverted).", -delta)
                false -> us("The current polarity is %.1f dB louder around the crossover. Keep it as it is.", delta)
                null -> "Both polarities sound the same around the crossover. Try adjusting the sub's phase or delay, or the crossover frequency."
            }
        }
        return SubResult(xo, advice, invert, delta, notes)
    }
}

class Category(val name: String, val score: Int, val detail: String, val eqFixable: Boolean)

class SystemReport(
    val overall: Int,
    val grade: String,
    val verdict: String,
    val categories: List<Category>,
    /** Estimated overall score once the suggested EQ is applied. */
    val potential: Int,
)

/** Scores how good a sound system is, in categories, and says what EQ can and cannot fix. */
object SystemJudge {
    private fun clamp(x: Double) = x.coerceIn(0.0, 100.0)

    fun grade(score: Int) = when {
        score >= 85 -> "Excellent"
        score >= 70 -> "Good"
        score >= 55 -> "Fair"
        score >= 40 -> "Poor"
        else -> "Very poor"
    }

    fun judge(magDb: DoubleArray, grid: FreqGrid, target: Target, resonances: List<Resonance>, lr: LrResult? = null): SystemReport {
        val (rms, maxAbs) = EqFitter.score(magDb, grid, target)
        val s = grid.smoothOctave(magDb, 6.0)
        val off = EqFitter.offsetDb(magDb, grid, target)
        fun err(i: Int) = s[i] - target.dbAt(grid.freqs[i]) - off

        // Bass extension: walk down from 100 Hz until the response is more than 6 dB below the target.
        var i = grid.indexAtOrBelow(100.0)
        var lowHz = 100.0
        if (err(i) < -6.0) lowHz = 120.0
        else {
            while (i > 0 && err(i) >= -6.0 && grid.freqs[i] > 20.0) i--
            lowHz = if (grid.freqs[i] <= 20.0 && err(i) >= -6.0) 20.0 else grid.freqs[i + 1]
        }
        val bass = clamp(100.0 - 80.0 * ln(max(lowHz, 35.0) / 35.0) / ln(120.0 / 35.0))

        // Treble extension: walk up from 8 kHz.
        var j = grid.indexAtOrAbove(8000.0)
        var highHz = 6000.0
        if (err(j) >= -6.0) {
            while (j < grid.size - 1 && err(j) >= -6.0 && grid.freqs[j] < 20000.0) j++
            highHz = grid.freqs[j - 1]
        }
        val treble = clamp(100.0 * ln(max(highHz, 6000.0) / 6000.0) / ln(16000.0 / 6000.0))

        val resPenalty = resonances.sumOf { r ->
            (8.0 + 2.0 * r.prominenceDb) * (if ((r.t60Ms ?: 0.0) > 350.0) 1.5 else 1.0)
        }
        val res = clamp(100.0 - resPenalty)

        val tonal = clamp(100.0 - 9.0 * rms)
        val smooth = clamp(100.0 - 6.0 * maxAbs)

        val cats = ArrayList<Pair<Category, Double>>()
        cats += Category("Tonal balance", tonal.toInt(), us("%.1f dB RMS from the %s target", rms, target.name), true) to 0.35
        cats += Category("Smoothness", smooth.toInt(), us("Largest deviation %.1f dB", maxAbs), true) to 0.20
        cats += Category("Bass extension", bass.toInt(),
            if (lowHz >= 100.0) "Bass fades out above 100 Hz" else us("Reaches %.0f Hz (within 6 dB of the target)", lowHz), false) to 0.15
        cats += Category("Treble extension", treble.toInt(),
            if (highHz <= 6000.0) "Treble drops off below 6 kHz" else us("Reaches %.1f kHz (within 6 dB of the target)", highHz / 1000.0), false) to 0.10
        cats += Category("Resonances", res.toInt(),
            if (resonances.isEmpty()) "No strong resonances" else us("%d found, strongest %.0f Hz at +%.1f dB", resonances.size,
                resonances.maxByOrNull { it.prominenceDb }!!.freqHz, resonances.maxOf { it.prominenceDb }), false) to 0.20
        if (lr != null) {
            val lrScore = clamp(100.0 - 20.0 * abs(lr.levelDiffDb) - 40.0 * abs(lr.delayMs ?: 0.0) - 5.0 * max(0.0, lr.toneDiffRmsDb - 1.5))
            cats += Category("Left/right match", lrScore.toInt(),
                us("%.1f dB level difference", abs(lr.levelDiffDb)) + (lr.delayMs?.let { us(", %.2f ms timing", abs(it)) } ?: ""), true) to 0.10
        }
        val wsum = cats.sumOf { it.second }
        val overall = (cats.sumOf { it.first.score * it.second } / wsum).toInt()

        val fit = EqFitter.fit(magDb, grid, target)
        val potentialScores = cats.sumOf { (c, w) ->
            val sc = when (c.name) {
                "Tonal balance" -> clamp(100.0 - 9.0 * fit.rmsAfter)
                "Smoothness" -> clamp(100.0 - 6.0 * fit.maxAfter)
                else -> c.score.toDouble()
            }
            sc * w
        } / wsum
        val potential = max(overall, potentialScores.toInt())

        val weakest = cats.maxByOrNull { (c, w) -> (100 - c.score) * w }!!.first
        val verdict = buildString {
            append("${grade(overall)}. ")
            if (weakest.score >= 85) append("Nothing stands out as a problem.")
            else {
                append("Biggest weakness: ${weakest.name.lowercase()} (${weakest.detail}). ")
                append(if (weakest.eqFixable) "EQ can fix most of this." else "EQ cannot fully fix this: it comes from the speakers, the room or the cabin.")
            }
            if (potential > overall + 2) append(" With the suggested EQ the score should rise to about $potential.")
        }
        return SystemReport(overall, grade(overall), verdict, cats.map { it.first }, potential)
    }
}
