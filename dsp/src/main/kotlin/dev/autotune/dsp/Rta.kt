package dev.autotune.dsp

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * RTA = Real-Time Analyzer: the row of 1/3-octave bars car-audio tuners watch while pink noise plays.
 * With pink noise every bar carries equal energy, so a flat system shows level bars.
 */
object Rta {
    /** ISO 1/3-octave centres, 20 Hz - 20 kHz (31 bars). */
    val CENTERS = doubleArrayOf(
        20.0, 25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0, 125.0, 160.0, 200.0, 250.0, 315.0, 400.0, 500.0, 630.0,
        800.0, 1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3150.0, 4000.0, 5000.0, 6300.0, 8000.0, 10000.0,
        12500.0, 16000.0, 20000.0,
    )

    val EDGE: Double = 2.0.pow(1.0 / 6.0)

    /** Equivalent RTA bars of a measured response (equal weight per log-frequency, like pink noise). */
    fun barsFromResponse(magDb: DoubleArray, grid: FreqGrid): DoubleArray = DoubleArray(CENTERS.size) { b ->
        val lo = CENTERS[b] / EDGE
        val hi = CENTERS[b] * EDGE
        var s = 0.0
        var n = 0
        for (i in grid.freqs.indices) {
            val f = grid.freqs[i]
            if (f >= lo && f < hi) { s += 10.0.pow(magDb[i] / 10.0); n++ }
        }
        if (n == 0) magDb[grid.indexAtOrBelow(CENTERS[b])] else 10.0 * log10(s / n)
    }

    /** Shifts bars so their level over the reference band matches the target (target bars stay where they are). */
    fun relativeToTarget(bars: DoubleArray, target: DoubleArray, refLowHz: Double = 250.0, refHighHz: Double = 2500.0): DoubleArray {
        var sum = 0.0
        var n = 0
        for (i in CENTERS.indices) if (CENTERS[i] in refLowHz..refHighHz) { sum += bars[i] - target[i]; n++ }
        val off = if (n > 0) sum / n else 0.0
        return DoubleArray(bars.size) { bars[it] - off }
    }

    fun targetBars(target: Target): DoubleArray = DoubleArray(CENTERS.size) { target.dbAt(CENTERS[it]) }
}

/** Streaming RTA: feed microphone samples, read smoothed 1/3-octave band levels (dB, arbitrary reference). */
class RtaAnalyzer(
    private val fs: Int,
    private val cal: Calibration? = null,
    private val fftSize: Int = 16384,
    private val smoothing: Double = 0.7,
) {
    private val ring = FloatArray(fftSize)
    private var pos = 0
    private var filled = 0
    private var sinceLast = 0
    private val hop = fftSize / 4
    private val window = DoubleArray(fftSize) { 0.5 * (1.0 - cos(2.0 * PI * it / (fftSize - 1))) }
    private val re = DoubleArray(fftSize)
    private val im = DoubleArray(fftSize)
    private val power = DoubleArray(Rta.CENTERS.size)
    private var frames = 0

    /** Returns true when a new analysis frame was produced. */
    fun push(x: FloatArray, n: Int): Boolean {
        var updated = false
        for (i in 0 until n) {
            ring[pos] = x[i]
            pos = (pos + 1) % fftSize
            if (filled < fftSize) filled++
            if (++sinceLast >= hop && filled == fftSize) {
                sinceLast = 0
                compute()
                updated = true
            }
        }
        return updated
    }

    private fun compute() {
        for (k in 0 until fftSize) {
            re[k] = ring[(pos + k) % fftSize] * window[k]
            im[k] = 0.0
        }
        Fft.transform(re, im)
        val binHz = fs.toDouble() / fftSize
        val half = fftSize / 2
        for (b in Rta.CENTERS.indices) {
            val lo = Rta.CENTERS[b] / Rta.EDGE / binHz
            val hi = Rta.CENTERS[b] * Rta.EDGE / binHz
            var s = 0.0
            val k0 = max(1, floor(lo + 0.5).toInt())
            val k1 = min(half - 1, ceil(hi - 0.5).toInt())
            for (k in k0..k1) {
                val overlap = min(hi, k + 0.5) - max(lo, k - 0.5)
                if (overlap > 0.0) s += (re[k] * re[k] + im[k] * im[k]) * overlap
            }
            power[b] = if (frames == 0) s else power[b] * smoothing + s * (1.0 - smoothing)
        }
        frames++
    }

    fun bandsDb(): DoubleArray = DoubleArray(Rta.CENTERS.size) {
        10.0 * log10(power[it] + 1e-30) - (cal?.gainDbAt(Rta.CENTERS[it]) ?: 0.0)
    }
}
