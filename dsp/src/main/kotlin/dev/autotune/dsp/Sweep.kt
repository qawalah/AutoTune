package dev.autotune.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/** Exponential (log) sine sweep, the standard excitation for impulse-response measurement. */
object Sweep {
    fun generate(
        fs: Int,
        seconds: Double,
        f1: Double = 20.0,
        f2: Double = 20000.0,
        fadeMs: Double = 20.0,
        amplitude: Double = 0.5,
    ): FloatArray {
        val n = (fs * seconds).toInt()
        val l = ln(f2 / f1)
        val fade = (fs * fadeMs / 1000.0).toInt().coerceAtLeast(1)
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / fs
            val phase = 2.0 * PI * f1 * seconds / l * (exp(t / seconds * l) - 1.0)
            var a = amplitude
            if (i < fade) a *= 0.5 * (1.0 - cos(PI * i / fade))
            if (i >= n - fade) a *= 0.5 * (1.0 - cos(PI * (n - 1 - i) / fade))
            out[i] = (a * sin(phase)).toFloat()
        }
        return out
    }

    /** Pink noise (Voss-McCartney style via filtered white noise), deterministic seed. */
    fun pinkNoise(fs: Int, seconds: Double, amplitude: Double = 0.25, seed: Long = 7): FloatArray {
        val rnd = java.util.Random(seed)
        val n = (fs * seconds).toInt()
        val out = FloatArray(n)
        var b0 = 0.0; var b1 = 0.0; var b2 = 0.0; var b3 = 0.0; var b4 = 0.0; var b5 = 0.0; var b6 = 0.0
        for (i in 0 until n) {
            val w = rnd.nextGaussian()
            b0 = 0.99886 * b0 + w * 0.0555179
            b1 = 0.99332 * b1 + w * 0.0750759
            b2 = 0.96900 * b2 + w * 0.1538520
            b3 = 0.86650 * b3 + w * 0.3104856
            b4 = 0.55000 * b4 + w * 0.5329522
            b5 = -0.7616 * b5 - w * 0.0168980
            val p = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362
            b6 = w * 0.115926
            out[i] = (p * 0.11 * amplitude * 4.0).toFloat().coerceIn(-1f, 1f)
        }
        return out
    }
}
