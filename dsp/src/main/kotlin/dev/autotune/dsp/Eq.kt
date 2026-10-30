package dev.autotune.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

/** One parametric (peaking) EQ band, RBJ cookbook. Negative gain = cut. */
data class EqFilter(val freqHz: Double, val gainDb: Double, val q: Double) {

    /** Normalised coefficients [b0, b1, b2, a1, a2]. */
    fun coefficients(fs: Double): DoubleArray {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freqHz / fs
        val alpha = sin(w0) / (2.0 * q)
        val cw = cos(w0)
        val a0 = 1.0 + alpha / a
        return doubleArrayOf(
            (1.0 + alpha * a) / a0,
            (-2.0 * cw) / a0,
            (1.0 - alpha * a) / a0,
            (-2.0 * cw) / a0,
            (1.0 - alpha / a) / a0,
        )
    }

    fun magnitudeDb(f: Double, fs: Double = 48000.0): Double {
        val c = coefficients(fs)
        val w = 2.0 * PI * f / fs
        val nr = c[0] + c[1] * cos(w) + c[2] * cos(2 * w)
        val ni = -(c[1] * sin(w) + c[2] * sin(2 * w))
        val dr = 1.0 + c[3] * cos(w) + c[4] * cos(2 * w)
        val di = -(c[3] * sin(w) + c[4] * sin(2 * w))
        return 10.0 * log10((nr * nr + ni * ni) / (dr * dr + di * di) + 1e-30)
    }
}

class EqChain(val filters: List<EqFilter>, val preampDb: Double = 0.0) {
    fun responseDb(f: Double, fs: Double = 48000.0): Double =
        preampDb + filters.sumOf { it.magnitudeDb(f, fs) }

    /**
     * What a fixed-band equaliser (the phone's 31-band system EQ) really does with this chain: one peaking filter per
     * band whose summed response, with neighbours overlapping, matches this chain's response (without the preamp) at
     * every band centre. Narrow cuts lose depth here, which is the point: this is the EQ the measurement will see.
     */
    fun sampledTo(centres: List<Double> = Rta.CENTERS.toList(), q: Double = 4.32): List<EqFilter> {
        val want = DoubleArray(centres.size) { i -> filters.sumOf { it.magnitudeDb(centres[i]) } }
        val g = want.copyOf()
        repeat(6) {
            val fl = centres.indices.map { EqFilter(centres[it], g[it], q) }
            for (i in centres.indices) g[i] += want[i] - fl.sumOf { it.magnitudeDb(centres[i]) }
        }
        return centres.indices.map { EqFilter(centres[it], g[it].coerceIn(-30.0, 15.0), q) }.filter { abs(it.gainDb) >= 0.2 }
    }

    companion object {
        val EMPTY = EqChain(emptyList(), 0.0)

        /** Chain with a preamp that prevents clipping (compensates the largest boost). */
        fun withSafePreamp(filters: List<EqFilter>, grid: FreqGrid = FreqGrid()): EqChain {
            val sorted = filters.sortedBy { it.freqHz } // always listed low to high (order does not change the sound)
            return withSafePreampSorted(sorted, grid)
        }

        /**
         * Keeps the largest boost of the whole chain at or below [maxPeakDb] by scaling the boosting bands down (cuts stay).
         * Boosts that add up from run to run otherwise ask for a deep negative preamp, which makes the next sweep too quiet.
         */
        fun capBoost(filters: List<EqFilter>, maxPeakDb: Double = 3.0, grid: FreqGrid = FreqGrid()): List<EqFilter> {
            var f = filters
            repeat(5) {
                val peak = grid.freqs.maxOfOrNull { fr -> f.sumOf { it.magnitudeDb(fr) } } ?: 0.0
                if (peak <= maxPeakDb + 0.05) return f
                val k = maxPeakDb / peak
                f = f.map { if (it.gainDb > 0) it.copy(gainDb = it.gainDb * k) else it }
            }
            return f
        }

        private fun withSafePreampSorted(filters: List<EqFilter>, grid: FreqGrid): EqChain {
            val peak = grid.freqs.maxOfOrNull { f -> filters.sumOf { it.magnitudeDb(f) } } ?: 0.0
            return EqChain(filters, if (peak > 0.0) -(peak + 0.3) else 0.0)
        }
    }
}

/** Real-time mono processor for a chain (direct form II transposed). Create one per channel. */
class EqProcessor(chain: EqChain, fs: Int) {
    private val gain = 10.0.pow(chain.preampDb / 20.0)
    private val coefs = chain.filters.map { it.coefficients(fs.toDouble()) }
    private val z1 = DoubleArray(coefs.size)
    private val z2 = DoubleArray(coefs.size)

    fun process(x: Float): Float {
        var v = x * gain
        for (i in coefs.indices) {
            val c = coefs[i]
            val y = c[0] * v + z1[i]
            z1[i] = c[1] * v - c[3] * y + z2[i]
            z2[i] = c[2] * v - c[4] * y
            v = y
        }
        return v.toFloat()
    }

    fun process(buf: FloatArray) {
        for (i in buf.indices) buf[i] = process(buf[i])
    }
}
