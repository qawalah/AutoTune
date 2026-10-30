package dev.autotune.dsp

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** Log-spaced frequency grid (default 48 points per octave, 20 Hz - 20 kHz). */
class FreqGrid(
    val fMin: Double = 20.0,
    val fMax: Double = 20000.0,
    val ppo: Int = 48,
) {
    val freqs: DoubleArray
    val size: Int get() = freqs.size

    init {
        val octaves = kotlin.math.ln(fMax / fMin) / kotlin.math.ln(2.0)
        val n = (octaves * ppo).toInt() + 1
        freqs = DoubleArray(n) { fMin * 2.0.pow(it.toDouble() / ppo) }
    }

    fun indexAtOrAbove(f: Double): Int = freqs.indexOfFirst { it >= f }.let { if (it < 0) size - 1 else it }
    fun indexAtOrBelow(f: Double): Int = freqs.indexOfLast { it <= f }.let { if (it < 0) 0 else it }

    /** Fractional-octave smoothing (power average) of a dB curve. fraction=6 means 1/6 octave. */
    fun smoothOctave(db: DoubleArray, fraction: Double): DoubleArray {
        val half = (ppo / fraction / 2.0).roundToInt().coerceAtLeast(0)
        if (half == 0) return db.copyOf()
        val prefix = DoubleArray(db.size + 1)
        for (i in db.indices) prefix[i + 1] = prefix[i] + 10.0.pow(db[i] / 10.0)
        return DoubleArray(db.size) { i ->
            val lo = (i - half).coerceAtLeast(0)
            val hi = (i + half).coerceAtMost(db.size - 1)
            10.0 * log10((prefix[hi + 1] - prefix[lo]) / (hi - lo + 1) + 1e-30)
        }
    }
}
