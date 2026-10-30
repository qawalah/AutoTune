package dev.autotune.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class ResonanceKind { LOW_FREQ_MODE, NARROW_PEAK }

data class Resonance(
    val freqHz: Double,
    /** Height of the peak above the local (1-octave median) response, dB. */
    val prominenceDb: Double,
    val q: Double,
    /** Estimated 60 dB decay time from the impulse response. */
    val t60Ms: Double?,
    /** True if the decay was still ringing at the end of the usable gate (real T60 is longer). */
    val t60IsLowerBound: Boolean,
    val kind: ResonanceKind,
    val suggestion: String,
)

object ResonanceDetector {

    fun detect(
        m: Measurement,
        minProminenceDb: Double = 4.0,
        minQ: Double = 1.5,
        fMinHz: Double = 25.0,
        fMaxHz: Double = 12000.0,
    ): List<Resonance> {
        val g = m.grid
        val s = g.smoothOctave(m.magDb, 24.0)
        val n = s.size

        // Robust baseline: 1-octave running median.
        val w = g.ppo / 2
        val base = DoubleArray(n) { i ->
            val lo = max(0, i - w)
            val hi = min(n - 1, i + w)
            s.copyOfRange(lo, hi + 1).sorted().let { it[it.size / 2] }
        }

        val lo = g.indexAtOrAbove(fMinHz)
        val hi = g.indexAtOrBelow(fMaxHz)
        val nb = g.ppo / 12
        val found = ArrayList<Resonance>()

        for (i in lo..hi) {
            val prom = s[i] - base[i]
            if (prom < minProminenceDb) continue
            var isMax = true
            for (k in max(0, i - nb)..min(n - 1, i + nb)) if (s[k] > s[i]) { isMax = false; break }
            if (!isMax) continue
            // skip plateaus: only first index of an equal run
            if (i > 0 && s[i - 1] == s[i]) continue

            val halfLevel = base[i] + prom / 2.0
            var l = i
            while (l > 0 && s[l] > halfLevel) l--
            var r = i
            while (r < n - 1 && s[r] > halfLevel) r++
            val bw = g.freqs[r] - g.freqs[l]
            val q = g.freqs[i] / max(bw, 1e-6)
            if (q < minQ) continue

            val decay = t60(m, g.freqs[i])
            found += Resonance(
                freqHz = g.freqs[i],
                prominenceDb = prom,
                q = q,
                t60Ms = decay?.first,
                t60IsLowerBound = decay?.second ?: false,
                kind = if (g.freqs[i] < 300.0) ResonanceKind.LOW_FREQ_MODE else ResonanceKind.NARROW_PEAK,
                suggestion = suggest(g.freqs[i], prom, q, decay),
            )
        }
        return found.sortedByDescending { it.prominenceDb }
    }

    /** Rebuilds a Resonance from saved numbers (used when a stored test is reopened). */
    fun rebuild(f: Double, prom: Double, q: Double, t60Ms: Double?, lowerBound: Boolean) = Resonance(
        f, prom, q, t60Ms, lowerBound,
        if (f < 300.0) ResonanceKind.LOW_FREQ_MODE else ResonanceKind.NARROW_PEAK,
        suggest(f, prom, q, t60Ms?.let { it to lowerBound }),
    )

    fun suggest(f: Double, prom: Double, q: Double, decay: Pair<Double, Boolean>?): String {
        val cut = "Cut ~%.1f dB at %.0f Hz, Q ~%.1f".format(prom.coerceAtMost(12.0), f, q.coerceIn(0.7, 8.0))
        val ringing = decay != null && (decay.first > 350.0 || (f > 300.0 && decay.first > 120.0))
        if (!ringing) return cut
        val sign = if (decay!!.second) ">" else "~"
        val t = "%.0f".format(decay.first)
        return "$cut. It also rings (T60 $sign$t ms): EQ lowers the level but cannot shorten the decay - " +
            "reposition speakers/sub or add damping/absorption."
    }

    /**
     * Cumulative-spectral-decay estimate: level of a Hann-tapered tail of the impulse response at [f0],
     * measured at successive start times. T60 is extrapolated (x3) from the time to fall 20 dB.
     * Returns (t60Ms, lowerBound) or null if the IR is too short.
     */
    fun t60(m: Measurement, f0: Double): Pair<Double, Boolean>? {
        val ir = m.ir
        val fs = m.fs
        val usable = (ir.size * 0.7).toInt()
        val t0 = m.irPeakIndex
        val tailLen = usable - t0
        if (tailLen < fs / 50) return null

        val step = max((0.002 * fs).toInt(), (fs / f0 * 0.5).toInt()).coerceAtLeast(1)
        val minTail = (tailLen * 0.3).toInt()
        val levels = ArrayList<Pair<Int, Double>>()
        var s = t0
        while (usable - s >= minTail) {
            levels += (s - t0) to tailLevelDb(ir, fs, f0, s, usable)
            s += step
        }
        if (levels.size < 3) return null
        val ref = levels[0].second
        for (k in 1 until levels.size) {
            if (levels[k].second <= ref - 20.0) {
                val (t1, l1) = levels[k - 1]
                val (t2, l2) = levels[k]
                val frac = if (l1 == l2) 0.0 else (ref - 20.0 - l1) / (l2 - l1)
                val sec = (t1 + frac * (t2 - t1)) / fs
                return sec * 3.0 * 1000.0 to false
            }
        }
        // Still within 20 dB at the end of the usable window: report a lower bound.
        val sec = levels.last().first.toDouble() / fs
        val drop = ref - levels.last().second
        val est = if (drop > 3.0) sec * (20.0 / drop) else sec * 4.0
        return est * 3.0 * 1000.0 to true
    }

    private fun tailLevelDb(ir: DoubleArray, fs: Int, f0: Double, start: Int, end: Int): Double {
        val n = end - start
        var re = 0.0
        var im = 0.0
        val w0 = 2.0 * PI * f0 / fs
        for (k in 0 until n) {
            val w = 0.5 * (1.0 + cos(PI * k / n))
            val a = w0 * (start + k)
            val v = ir[start + k] * w
            re += v * cos(a)
            im -= v * sin(a)
        }
        return 20.0 * log10(hypot(re, im) + 1e-20)
    }
}
