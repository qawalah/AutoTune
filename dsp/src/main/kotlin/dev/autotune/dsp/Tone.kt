package dev.autotune.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/** The Live tab's tone generator: the slider's log frequency scale and a click-free sine source. */
object Tone {
    const val MIN_HZ = 20.0
    const val MAX_HZ = 20000.0
    const val DEFAULT_HZ = 1000.0

    /** Slider position 0..1 to frequency, logarithmic, 20 Hz to 20 kHz. */
    fun freqAt(fraction: Double): Double = MIN_HZ * (MAX_HZ / MIN_HZ).pow(fraction.coerceIn(0.0, 1.0))

    /** Frequency to slider position 0..1. */
    fun fractionOf(hz: Double): Double = (ln(hz.coerceIn(MIN_HZ, MAX_HZ) / MIN_HZ) / ln(MAX_HZ / MIN_HZ))

    /** Index of the RTA bar a tone of [hz] lands in. */
    fun bandIndex(hz: Double): Int {
        var best = 0
        var bestD = Double.MAX_VALUE
        for (i in Rta.CENTERS.indices) {
            val d = abs(ln(hz / Rta.CENTERS[i]))
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    /** "63 Hz", "1.25 kHz". */
    fun text(hz: Double): String =
        if (hz >= 1000.0) String.format(java.util.Locale.US, "%.2f kHz", hz / 1000.0)
        else String.format(java.util.Locale.US, "%.0f Hz", hz)
}

/**
 * A sine that follows a changing frequency without clicks: the phase carries on, and the frequency glides
 * to its target a little each sample, so dragging the slider is smooth to hear.
 */
class ToneSynth(private val fs: Int, private val level: Double = 0.1) {
    private var phase = 0.0
    private var freq = Tone.DEFAULT_HZ
    private var fade = 0.0

    @Volatile var targetHz: Double = Tone.DEFAULT_HZ
        set(v) { field = v.coerceIn(Tone.MIN_HZ, Tone.MAX_HZ) }

    /** Fills [out] (mono) with [n] samples; the first ~20 ms fade in. */
    fun fill(out: FloatArray, n: Int) {
        val target = targetHz
        val glide = 1.0 - 0.0015
        val fadeStep = 1.0 / (0.02 * fs)
        for (i in 0 until n) {
            freq = target + (freq - target) * glide
            phase += 2.0 * PI * freq / fs
            if (phase > 2.0 * PI) phase -= 2.0 * PI
            if (fade < 1.0) fade = (fade + fadeStep).coerceAtMost(1.0)
            out[i] = (sin(phase) * level * fade).toFloat()
        }
    }
}

/** Keeps the highest value each bar has reached since the last reset. */
class PeakHold(private val size: Int) {
    private val held = DoubleArray(size) { Double.NEGATIVE_INFINITY }
    private var any = false

    fun update(bars: DoubleArray) {
        for (i in 0 until minOf(size, bars.size)) if (bars[i] > held[i]) held[i] = bars[i]
        any = true
    }

    /** The held bars, or null before the first reading. */
    fun snapshot(): DoubleArray? = if (any) held.copyOf() else null

    fun reset() {
        held.fill(Double.NEGATIVE_INFINITY)
        any = false
    }
}

/** Compares two held-peak recordings of the Live spectrum, A (kept) and B (the one being held now). */
object PeakCompare {
    /**
     * The bar where [b] differs most from [a], as its index and the difference B - A in dB; null when nothing differs by
     * at least [minDb] (or there is nothing to compare). Bars that are not finite in both are left out.
     */
    fun largest(a: DoubleArray, b: DoubleArray, minDb: Double = 0.5): Pair<Int, Double>? {
        var best = -1
        var bestD = 0.0
        for (i in 0 until minOf(a.size, b.size)) {
            if (!a[i].isFinite() || !b[i].isFinite()) continue
            val d = b[i] - a[i]
            if (abs(d) > abs(bestD)) { best = i; bestD = d }
        }
        return if (best >= 0 && abs(bestD) >= minDb) best to bestD else null
    }
}
