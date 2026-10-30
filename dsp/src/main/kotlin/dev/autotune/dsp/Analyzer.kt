package dev.autotune.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class Measurement(
    val grid: FreqGrid,
    /** Calibrated magnitude response in dB on [grid] (unsmoothed, arbitrary absolute level). */
    val magDb: DoubleArray,
    /** Gated + tapered impulse response; sample 0 is [PRE_MS] before the main peak. */
    val ir: DoubleArray,
    val irPeakIndex: Int,
    val fs: Int,
    val latencyMs: Double,
    val snrDb: Double?,
    val warnings: List<String>,
    /** Set when the recording is unusable (no signal / buried in noise). Callers must not save or fit it. */
    val fatal: String? = null,
    /** Harmonic distortion read from the sweep, when it could be measured. */
    val distortion: Distortion? = null,
    /** True when the direct sound was taken from the window the start beeps pointed at. */
    val peakFromMarker: Boolean = false,
    /** Output/microphone clock difference in parts per million, read from where the harmonic copies sit; null when unknown. */
    val driftPpm: Double? = null,
) {
    /** The same measurement with other warnings (everything else carried over). */
    fun withWarnings(w: List<String>, snr: Double? = snrDb): Measurement =
        Measurement(grid, magDb, ir, irPeakIndex, fs, latencyMs, snr, w, fatal, distortion, peakFromMarker, driftPpm)

    companion object {
        const val PRE_MS = 2.0
    }
}

/**
 * Harmonic distortion against the frequency of the test tone (REW style): [hd2Db] and [hd3Db] are the second and third
 * harmonic levels in dB relative to the fundamental, [thdPct] their combined level in percent, all on [freqs].
 */
class Distortion(val freqs: DoubleArray, val hd2Db: DoubleArray, val hd3Db: DoubleArray, val thdPct: DoubleArray) {
    /** Average THD in percent between [lo] and [hi] Hz. */
    fun averagePct(lo: Double, hi: Double): Double {
        var s = 0.0; var c = 0
        for (i in freqs.indices) if (freqs[i] in lo..hi) { s += thdPct[i]; c++ }
        return if (c == 0) 0.0 else s / c
    }
}

object Analyzer {
    /**
     * Deconvolves [recording] against the [sweep] that was played, finds the direct sound,
     * gates the impulse response and returns the calibrated frequency response.
     *
     * @param gateMs how much of the response after the direct sound to keep. Short (30-80 ms) for a
     *   car cabin or to remove room reflections, long (300-500 ms) to include room modes.
     */
    fun analyze(
        recording: FloatArray,
        sweep: FloatArray,
        fs: Int,
        cal: Calibration?,
        gateMs: Double = 400.0,
        grid: FreqGrid = FreqGrid(),
        /** Where the sweep should start in [recording] (from the start beeps). Limits the search for the direct sound. */
        expectedStart: Int? = null,
        /**
         * Frequency-dependent window, as in REW: each frequency is read through a window of this many cycles of its own
         * period (long at the bass, short at the treble), so late reflections count less where they cannot be separated
         * anyway. Null keeps the single fixed gate.
         */
        fdwCycles: Double? = null,
    ): Measurement {
        val warnings = ArrayList<String>()

        // --- Deconvolution in the frequency domain with regularisation outside the sweep band.
        val n = Fft.nextPow2(recording.size + sweep.size)
        val xr = DoubleArray(n); val xi = DoubleArray(n)
        val yr = DoubleArray(n); val yi = DoubleArray(n)
        for (i in sweep.indices) xr[i] = sweep[i].toDouble()
        for (i in recording.indices) yr[i] = recording[i].toDouble()
        Fft.transform(xr, xi)
        Fft.transform(yr, yi)
        var maxPow = 0.0
        for (k in 0 until n) maxPow = max(maxPow, xr[k] * xr[k] + xi[k] * xi[k])
        val eps = 1e-5 * maxPow
        // H = Y * conj(X) / (|X|^2 + eps), written over Y: four arrays of n instead of six
        for (k in 0 until n) {
            val p = xr[k] * xr[k] + xi[k] * xi[k] + eps
            val r = (yr[k] * xr[k] + yi[k] * xi[k]) / p
            val i = (yi[k] * xr[k] - yr[k] * xi[k]) / p
            yr[k] = r; yi[k] = i
        }
        Fft.transform(yr, yi, inverse = true)
        val hr = yr

        // --- Direct-sound arrival (causal half only). With a known start the search is windowed, but the window is
        // only trusted when nothing far stronger sits elsewhere: a wrong start would otherwise give a plausible
        // but meaningless response.
        var peak = 0
        var peakVal = 0.0
        val searchFrom = if (expectedStart != null) max(0, expectedStart - (0.2 * fs).toInt()) else 0
        val searchTo = if (expectedStart != null) min(n / 2, expectedStart + (0.2 * fs).toInt()) else n / 2
        for (i in searchFrom until searchTo) {
            val v = abs(hr[i])
            if (v > peakVal) { peakVal = v; peak = i }
        }
        var fromMarker = expectedStart != null
        val warnings0 = ArrayList<String>()
        if (expectedStart != null) {
            var gPeak = 0; var gVal = 0.0
            for (i in 0 until n / 2) { val v = abs(hr[i]); if (v > gVal) { gVal = v; gPeak = i } }
            if (gVal > 2.0 * peakVal) { // more than 6 dB stronger elsewhere
                warnings0 += "The sweep was found %.2f s away from where the start beeps said it would be.".format(abs(gPeak - peak).toDouble() / fs)
                peak = gPeak; peakVal = gVal; fromMarker = false
            }
        }
        warnings += warnings0
        val latencyMs = peak * 1000.0 / fs
        var fatal: String? = null
        if (peakVal <= 1e-9) fatal = "No signal detected - check the microphone and where the sound is playing."

        // --- Was the whole sweep heard, unclipped? (Bluetooth can start late, drop out, or route elsewhere.)
        if (fatal == null && sweep.size > fs) {
            val chk = SweepCheck.inspect(recording, peak, sweep.size, fs)
            chk.clipMessage?.let { warnings += it }
            fatal = chk.fatal
        }

        val preSamples = (Measurement.PRE_MS * fs / 1000.0).toInt()
        val start = max(0, peak - preSamples)
        val gateSamples = (gateMs * fs / 1000.0).toInt().coerceAtMost(n / 2 - start)

        // --- Signal-to-noise estimate. The deconvolved stretch just before the direct sound holds only background
        // (the first harmonic copy sits much further back); after the gate a cabin is still ringing, so that stretch
        // is only the fallback.
        var snr: Double? = null
        val bbLen = (0.3 * fs).toInt()
        val bbEnd = peak - (0.04 * fs).toInt()
        val k2Gap = (sweep.size * Math.log(2.0) / Math.log(1000.0)).toInt()
        val bbBefore = sweep.size > fs && bbEnd - bbLen >= 0 && (0.04 * fs).toInt() + bbLen < k2Gap - (0.05 * fs).toInt()
        val nStart = if (bbBefore) bbEnd - bbLen else peak + gateSamples
        val nEnd = if (bbBefore) bbEnd else min(nStart + bbLen, n / 2)
        if (nEnd - nStart > fs / 20) {
            var s = 0.0
            for (i in nStart until nEnd) s += hr[i] * hr[i]
            val rms = sqrt(s / (nEnd - nStart))
            snr = 20.0 * log10(peakVal / (rms + 1e-30))
            if (snr < 20.0) fatal = "The microphone barely heard the test sound (signal-to-noise %.0f dB). Raise the volume, move the mic closer to the speakers, or check the sound is playing.".format(snr)
            else if (snr < 35.0) warnings += "Low signal-to-noise (%.0f dB) - raise the volume or reduce background noise.".format(snr)
        }

        // --- Gate + taper.
        val ir = DoubleArray(gateSamples)
        val fallStart = (gateSamples * 0.7).toInt()
        for (i in 0 until gateSamples) {
            var w = 1.0
            if (i < preSamples) w = 0.5 * (1.0 - cos(PI * i / preSamples))
            if (i >= fallStart) w = 0.5 * (1.0 + cos(PI * (i - fallStart) / (gateSamples - fallStart)))
            ir[i] = hr[start + i] * w
        }

        // --- Magnitude on the log grid (power average inside each 1/48-octave cell).
        val m = Fft.nextPow2(gateSamples) * 4
        val binHz = fs.toDouble() / m
        val halfCell = Math.pow(2.0, 1.0 / (2.0 * grid.ppo))
        fun powerDb(seg: DoubleArray): DoubleArray {
            val fr = DoubleArray(m); val fi = DoubleArray(m)
            for (i in seg.indices) fr[i] = seg[i]
            Fft.transform(fr, fi)
            return DoubleArray(grid.size) { gi ->
                val f = grid.freqs[gi]
                val b0 = Math.ceil(f / halfCell / binHz).toInt().coerceAtLeast(1)
                val b1 = Math.floor(f * halfCell / binHz).toInt().coerceAtMost(m / 2 - 1)
                val p: Double = if (b1 >= b0) {
                    var s = 0.0
                    for (b in b0..b1) s += fr[b] * fr[b] + fi[b] * fi[b]
                    s / (b1 - b0 + 1)
                } else {
                    val pos = f / binHz
                    val lo = pos.toInt().coerceIn(1, m / 2 - 2)
                    val t = pos - lo
                    val p0 = fr[lo] * fr[lo] + fi[lo] * fi[lo]
                    val p1 = fr[lo + 1] * fr[lo + 1] + fi[lo + 1] * fi[lo + 1]
                    p0 + t * (p1 - p0)
                }
                10.0 * log10(p + 1e-30)
            }
        }
        val raw = powerDb(ir)
        val shaped = if (fdwCycles != null && fdwCycles > 0) fdwDb(hr, start, preSamples, min(n / 2, hr.size) - start, fs, grid, fdwCycles) else raw
        val magDb = DoubleArray(grid.size) { gi -> shaped[gi] - (cal?.gainDbAt(grid.freqs[gi]) ?: 0.0) }

        // --- Per-frequency signal-to-noise, read from the response itself: the same deconvolution that turns the
        // sweep into the response turns the background into the stretch after the gate, so no silent stretch
        // before or after the sweep is needed. Same window, same length, so the two spectra compare directly.
        // The stretch just BEFORE the direct sound in the deconvolved response holds only the background (the first
        // harmonic copy is a whole second further back); the stretch after the gate is still the room's ringing tail.
        val harmonicGap = (sweep.size * Math.log(2.0) / Math.log(1000.0)).toInt()
        val nzBefore = peak - (0.04 * fs).toInt() - gateSamples
        val beforeOk = nzBefore >= 0 && (0.04 * fs).toInt() + gateSamples < harmonicGap - (0.05 * fs).toInt() && sweep.size > fs
        val nz0 = if (beforeOk) nzBefore else peak + gateSamples + (0.02 * fs).toInt()
        if (nz0 >= 0 && nz0 + gateSamples <= n / 2) {
            val noiseSeg = DoubleArray(gateSamples)
            for (i in 0 until gateSamples) {
                var w = 1.0
                if (i < preSamples) w = 0.5 * (1.0 - cos(PI * i / preSamples))
                if (i >= fallStart) w = 0.5 * (1.0 + cos(PI * (i - fallStart) / (gateSamples - fallStart)))
                noiseSeg[i] = hr[nz0 + i] * w
            }
            val noiseDbArr = powerDb(noiseSeg)
            val cell = max(1, grid.ppo / 3)            // a third of an octave
            val snrAt = DoubleArray(grid.size) { gi ->
                val a = max(0, gi - cell / 2); val z = min(grid.size - 1, gi + cell / 2)
                var s = 0.0
                for (k in a..z) s += raw[k] - noiseDbArr[k]
                s / (z - a + 1)
            }
            var gi = 0
            var worstLo = Double.MAX_VALUE; var worstHi = 0.0; var bad = 0; var tot = 0
            while (gi < grid.size) {
                val f = grid.freqs[gi]
                if (f in 50.0..10000.0) {
                    tot++
                    if (snrAt[gi] < 6.0) { bad++; worstLo = min(worstLo, f); worstHi = max(worstHi, f) }
                }
                gi++
            }
            if (bad > grid.ppo / 2) { // wider than half an octave
                val share = bad.toDouble() / max(1, tot)
                fun hz(f: Double) = if (f >= 1000) "%.1f kHz".format(f / 1000) else "%.0f Hz".format(f)
                val msg = "The microphone barely heard the test sound between about ${hz(worstLo)} and ${hz(worstHi)} (it did not stand out from the background there)."
                if (share > 0.4) fatal = fatal ?: (msg + " Raise the volume or move the mic closer to a speaker, then measure again.")
                else warnings += "$msg Treat the response there with care."
            }
        }

        val drift = if (fatal == null && sweep.size > fs) runCatching { clockDriftPpm(hr, n, peak, sweep.size, fs) }.getOrNull() else null
        // Kept for the diagnostics log only: on real Bluetooth recordings the estimate swung from -68 to +79 ppm between
        // identical runs (the system's own group delay leaks into it), so it is not reliable enough to warn about.
        val dist = if (fatal == null && sweep.size > fs) runCatching {
            distortion(hr, n, peak, sweep.size, fs, grid, gateSamples, preSamples, ::powerDb, raw)
        }.getOrNull() else null

        return Measurement(grid, magDb, ir, peak - start, fs, latencyMs, snr, warnings, fatal, dist, fromMarker, drift)
    }

    /**
     * REW's frequency-dependent window. The window starts at the direct sound and falls away over [cycles] periods of
     * the frequency being read (up to a second, so the bass of a car test gets its full cycles instead of the short
     * gate), then that one frequency is read from it.
     */
    fun fdwDb(src: DoubleArray, start: Int, preSamples: Int, available: Int, fs: Int, grid: FreqGrid, cycles: Double): DoubleArray {
        val maxLen = min(available - preSamples, fs) // never more than a second after the direct sound
        return DoubleArray(grid.size) { gi ->
            val f = grid.freqs[gi]
            val len = min((cycles / f * fs).toInt().coerceAtLeast(8), maxLen).coerceAtLeast(8)
            var re = 0.0; var im = 0.0
            val w = 2.0 * PI * f / fs
            val cw = cos(w); val sw = kotlin.math.sin(w)
            var cr = 1.0; var ci = 0.0 // e^{-j w k} by rotation
            val end = min(preSamples + len, available)
            for (k in 0 until end) {
                val win = if (k < preSamples) 0.5 * (1.0 - cos(PI * k / preSamples)) else 0.5 * (1.0 + cos(PI * (k - preSamples) / len))
                val x = src[start + k] * win
                re += x * cr; im -= x * ci
                val nr = cr * cw + ci * sw; ci = ci * cw - cr * sw; cr = nr
            }
            10.0 * log10(re * re + im * im + 1e-30)
        }
    }

    /**
     * Clock difference between playing and recording. A recording whose clock runs fast or slow stretches the sweep, which
     * smears the whole impulse response, but within one band of output frequency the distance from the direct sound back
     * to the k-th harmonic copy is still exactly T*ln(k)/ln(1000) seconds times (1 + drift), whatever the system's own
     * delay. So the direct sound and each copy are located by their envelope in the same band and the distance compared.
     * Needs a copy that clearly stands out of the background; returns null when there is none.
     */
    private fun clockDriftPpm(hr: DoubleArray, n: Int, peak: Int, sweepLen: Int, fs: Int): Double? {
        val estimates = ArrayList<Pair<Double, Double>>() // (ppm, weight)
        val fr = DoubleArray(n); val fi = DoubleArray(n)
        System.arraycopy(hr, 0, fr, 0, n)
        Fft.transform(fr, fi)
        val half = n / 2
        for ((lo, hi) in listOf(1000.0 to 2000.0, 2000.0 to 4000.0, 4000.0 to 8000.0)) {
            val br = DoubleArray(n); val bi = DoubleArray(n)
            val k0 = (lo / fs * n).toInt().coerceAtLeast(1); val k1 = (hi / fs * n).toInt().coerceAtMost(half - 1)
            for (k in k0..k1) { br[k] = 2.0 * fr[k]; bi[k] = 2.0 * fi[k] } // positive frequencies only: analytic signal
            Fft.transform(br, bi, inverse = true)
            fun env(i: Int): Double { val j = Math.floorMod(i, n); return br[j] * br[j] + bi[j] * bi[j] }
            // direct sound in this band: near the broadband peak, which a stretched sweep smears over a few ms
            val reach = (0.004 * fs).toInt()
            var dp = peak; var dv = 0.0
            for (i in peak - reach..peak + reach) if (env(i) > dv) { dv = env(i); dp = i }
            for (k in 2..3) {
                val nominal = sweepLen * Math.log(k.toDouble()) / Math.log(1000.0)
                val centre = dp - nominal.toInt()
                val span = (nominal * 5e-4).toInt().coerceAtLeast(8) + reach
                var cp = centre; var cv = 0.0; var noise = 0.0; var cnt = 0
                for (d in -span * 3..span * 3) {
                    val v = env(centre + d)
                    if (abs(d) <= span) { if (v > cv) { cv = v; cp = centre + d } } else { noise += v; cnt++ }
                }
                if (cnt == 0 || cv < 30.0 * (noise / cnt)) continue // 30x in power = 15 dB above the surroundings
                val e0 = env(cp - 1); val e2 = env(cp + 1)
                val den = e0 - 2 * cv + e2
                val frac = if (den != 0.0) 0.5 * (e0 - e2) / den else 0.0
                val dist = dp - (cp + frac)
                estimates += ((dist / nominal - 1.0) * 1e6) to cv
            }
        }
        if (estimates.isEmpty()) return null
        val w = estimates.sumOf { it.second }
        return estimates.sumOf { it.first * it.second } / w
    }

    /**
     * Harmonic distortion from the sweep. Harmonic k of an exponential sweep shows up in the deconvolved response as a
     * copy that arrives T*ln(k)/ln(f2/f1) seconds BEFORE the direct sound. Its spectrum at k*f against the direct sound
     * at k*f gives the level of the harmonic produced while the fundamental was at f.
     */
    private fun distortion(
        hr: DoubleArray, n: Int, peak: Int, sweepLen: Int, fs: Int, grid: FreqGrid, gateSamples: Int, preSamples: Int,
        powerDb: (DoubleArray) -> DoubleArray, fundamentalDb: DoubleArray,
    ): Distortion? {
        val ratio = Math.log(1000.0)
        fun harmonic(k: Int): DoubleArray {
            val delta = (sweepLen * Math.log(k.toDouble()) / ratio).toInt()
            val from = peak - delta - preSamples
            val seg = DoubleArray(gateSamples)
            val fall = (gateSamples * 0.7).toInt()
            for (i in 0 until gateSamples) {
                var w = 1.0
                if (i < preSamples) w = 0.5 * (1.0 - cos(PI * i / preSamples))
                if (i >= fall) w = 0.5 * (1.0 + cos(PI * (i - fall) / (gateSamples - fall)))
                seg[i] = hr[Math.floorMod(from + i, n)] * w
            }
            return powerDb(seg)
        }
        // freqs from 50 Hz to 5 kHz, the range where the harmonics stay inside the sweep and the result means something
        val idx = (0 until grid.size).filter { grid.freqs[it] in 50.0..5000.0 }
        val hd = Array(2) { DoubleArray(idx.size) }
        for ((j, k) in listOf(2, 3).withIndex()) {
            val h = harmonic(k)
            val shift = Math.round(grid.ppo * Math.log(k.toDouble()) / Math.log(2.0)).toInt()
            for ((q, gi) in idx.withIndex()) {
                val up = (gi + shift).coerceAtMost(grid.size - 1)
                hd[j][q] = h[up] - fundamentalDb[up] + HD_SCALE_DB[j]
            }
        }
        val thd = DoubleArray(idx.size) { q -> 100.0 * sqrt(Math.pow(10.0, hd[0][q] / 10.0) + Math.pow(10.0, hd[1][q] / 10.0)) }
        return Distortion(DoubleArray(idx.size) { grid.freqs[idx[it]] }, hd[0], hd[1], thd)
    }

    /** Level correction (dB) for the second and third harmonic, fixed by the synthetic test in DistortionTest. */
    internal val HD_SCALE_DB = doubleArrayOf(0.0, 0.0)
}

/**
 * Checks the recording around the sweep: that it was not cut off, that every part of it rose above the background
 * noise, and whether the mic overloaded (and at what frequency). A log sweep maps time to frequency, so a problem at
 * a moment in the recording points to a frequency.
 */
object SweepCheck {
    class Result(val clipHz: Double?, val fatal: String?) {
        val clipMessage: String?
            get() = clipHz?.let {
                "The mic clipped around ${hz(it)} (too loud there). Turn the volume down a few steps and measure again."
            }
    }

    private fun hz(f: Double) = if (f >= 1000) "%.1f kHz".format(f / 1000) else "%.0f Hz".format(f)

    /** Was the whole sweep recorded, and did the mic clip while it played? */
    fun inspect(rec: FloatArray, start: Int, sweepLen: Int, fs: Int, f1: Double = 20.0, f2: Double = 20000.0): Result {
        val l = kotlin.math.ln(f2 / f1)
        fun freqAt(sample: Int) = f1 * kotlin.math.exp(((sample - start).toDouble() / sweepLen).coerceIn(0.0, 1.0) * l)
        val end = start + sweepLen
        val skip = (0.25 * fs).toInt() // the first moments of a USB mic often hold a start-up click

        // cut off at the end: the sound started later than the recording allowed for
        if (end > rec.size) {
            val late = (end - rec.size).toDouble() / fs
            return Result(null,
                "The end of the sweep was cut off (the sound came %.1f s later than expected, usually Bluetooth delay), so the high frequencies are missing. Measure again.".format(late))
        }

        // clipping: several samples at full scale while the sweep plays, not a single click
        var clips = 0
        var firstClip = -1
        val c0 = max(skip, start - fs / 10)
        val c1 = min(rec.size, end + fs / 2)
        for (i in c0 until c1) if (abs(rec[i]) >= 0.999f) { clips++; if (firstClip < 0) firstClip = i }
        return Result(if (clips >= 3) freqAt(firstClip) else null, null)
    }
}

/** Level check: background noise, the level of the test sound, and its peak. */
object LevelCheck {
    class Result(val noiseDb: Double, val soundDb: Double, val peakDb: Double) {
        val verdict: String
            get() = when {
                peakDb > -1.0 -> "Too loud: peaks reach %.0f dBFS and the mic clips. Turn the volume down a few steps.".format(peakDb)
                soundDb - noiseDb < 10.0 -> "The mic barely hears the test sound (%.0f dB above the background). Check it plays through the speakers, then turn up.".format(soundDb - noiseDb)
                soundDb - noiseDb < 25.0 -> "A bit quiet: %.0f dB above the background. Turn up a few steps if you can.".format(soundDb - noiseDb)
                peakDb > -6.0 -> "Good, but close to the limit (peaks %.0f dBFS). One step down is safer.".format(peakDb)
                else -> "Good: %.0f dB above the background, peaks %.0f dBFS.".format(soundDb - noiseDb, peakDb)
            }
    }

    /** [rec] must start with at least half a second of quiet (the pre-roll) before the test sound. */
    fun analyze(rec: FloatArray, fs: Int): Result {
        val skip = (0.25 * fs).toInt()
        val win = fs / 2
        var noise = Double.MAX_VALUE
        var sound = -200.0
        var i = skip
        while (i + win <= rec.size) {
            var s = 0.0
            for (k in i until i + win) s += rec[k].toDouble() * rec[k]
            val db = 10 * log10(s / win + 1e-24)
            noise = min(noise, db)
            sound = max(sound, db)
            i += win / 2
        }
        var peak = 0f
        for (k in skip until rec.size) peak = max(peak, abs(rec[k]))
        return Result(if (noise == Double.MAX_VALUE) -120.0 else noise, sound, 20 * log10(peak.toDouble() + 1e-12))
    }
}
