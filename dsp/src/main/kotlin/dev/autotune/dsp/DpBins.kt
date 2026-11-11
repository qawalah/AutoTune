package dev.autotune.dsp

import kotlin.math.max
import kotlin.math.min

/**
 * How Android's DynamicsProcessing (the frequency-domain variant, AOSP `DPFrequency`) really applies its pre-EQ bands.
 * It works in blocks of [block] samples with one gain per FFT bin, [fs]/[block] Hz apart. Bin k takes the gain of the
 * FIRST band whose cutoff, rounded to a bin, reaches k; a band narrower than a bin gets no bin of its own.
 *
 * With Android's default 10 ms frame (512 samples at 48 kHz, bins 94 Hz apart) everything below about 140 Hz was two
 * bins: the 45 Hz band's gain ran from 47 to 140 Hz (seen in the owner's F-150 runs, 2.19.2), and the coarse bins
 * smeared the bass. AutoTune asks for [FRAME_MS] (4096 samples, bins about 12 Hz apart).
 */
object DpBins {
    /** Preferred frame duration AutoTune gives the effect: 4096-sample blocks at 44.1 and 48 kHz (about 85 ms of delay). */
    const val FRAME_MS = 80f

    /** The block (FFT) size the effect picks for [frameMs] at [fs]: rounded up to a power of two, 8 to 16384. */
    fun block(fs: Int, frameMs: Float = FRAME_MS): Int {
        var b = (frameMs * fs / 1000f).toInt()
        if (b < 8) return 8
        if (b and (b - 1) != 0) b = Integer.highestOneBit(b) shl 1
        return min(b, 16384)
    }

    /** The bins each band sets, low to high, or null for a band that sets none. [edges] are the bands' cutoffs (Hz). */
    fun owners(edges: DoubleArray, block: Int, fs: Int): List<IntRange?> {
        val half = block / 2 + 1
        var next = 0
        return edges.map { e ->
            val stop = (0.5 + e * block / fs).toInt()
            val r = if (stop >= next && next < half) next..min(stop, half - 1) else null
            next = stop + 1
            r
        }
    }

    /**
     * The gain to give each band so its bins carry [responseDb] (the EQ wanted): the average over the bins it sets, or
     * the value at its centre when it sets none (then it does nothing anyway).
     */
    fun bandGains(edges: DoubleArray, centres: DoubleArray, block: Int, fs: Int, responseDb: (Double) -> Double): DoubleArray {
        val own = owners(edges, block, fs)
        val binHz = fs.toDouble() / block
        return DoubleArray(edges.size) { b ->
            val r = own[b]
            if (r == null) responseDb(centres[b]) else r.map { k -> responseDb(max(k * binHz, 5.0)) }.average()
        }
    }

    /** The gain (dB) of every bin, 0 to block/2, for bands set to [gains]; bins above the last band stay at 0 dB. */
    fun binGains(edges: DoubleArray, gains: DoubleArray, block: Int, fs: Int): DoubleArray {
        val out = DoubleArray(block / 2 + 1)
        owners(edges, block, fs).forEachIndexed { b, r -> r?.forEach { out[it] = gains[b] } }
        return out
    }

    /** What the bins do at [f] Hz: the two nearest bins, interpolated in dB. */
    fun realisedDb(bins: DoubleArray, block: Int, fs: Int, f: Double): Double {
        val pos = (f * block / fs).coerceIn(0.0, (bins.size - 1).toDouble())
        val k = pos.toInt().coerceAtMost(bins.size - 2)
        val t = pos - k
        return bins[k] * (1 - t) + bins[k + 1] * t
    }
}
