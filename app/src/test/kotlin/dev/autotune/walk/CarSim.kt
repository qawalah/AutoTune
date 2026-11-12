package dev.autotune.walk

import dev.autotune.audio.AudioSimulator
import dev.autotune.audio.OutChannel
import dev.autotune.audio.SystemEq
import dev.autotune.dsp.EqChain
import dev.autotune.dsp.EqFilter
import dev.autotune.dsp.EqProcessor
import dev.autotune.dsp.Fft
import dev.autotune.dsp.Sweep
import dev.autotune.dsp.TestSignals
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow

/**
 * A pretend F-150: a speaker with a boomy 62 Hz and a harsh 3 kHz, a dip in the mids, a rolled-off top, a cabin with
 * reflections, 120 ms of Bluetooth delay, road noise, and a phone mic that is weak in the bass and bright near 12 kHz.
 * Whatever the app has switched on as its system EQ reaches the mic, as it does on a real phone.
 */
class CarSim(
    private val fs: Int = 48000,
    private val btDelaySec: Double = 0.12,
    private val roadNoiseDb: Double = -52.0,
    /** Level loss between the phone's output and the mic, in dB (the mic is a metre from the speaker). */
    private val pathLossDb: Double = 14.0,
) : AudioSimulator {
    private val modes = listOf(Triple(62.0, 0.60, 2.8), Triple(118.0, 0.45, 2.2), Triple(3000.0, 0.05, 2.5))
    private val speaker = EqChain(listOf(EqFilter(50.0, 3.0, 0.7), EqFilter(8000.0, -5.0, 0.6), EqFilter(400.0, -3.0, 1.2), EqFilter(250.0, 2.0, 0.9)))
    private val phoneMic = EqChain(listOf(EqFilter(90.0, -6.0, 0.7), EqFilter(12000.0, 4.0, 0.7)))
    private val reflections = listOf(40 to 0.30, 90 to 0.25, 170 to 0.20, 260 to 0.15)

    private val ir: DoubleArray = run {
        val n = fs
        val h = DoubleArray(n)
        val d = (0.006 * fs).toInt()
        h[d] = 1.0
        for ((k, a) in reflections) h[d + k] += a
        val p = EqProcessor(speaker, fs)
        for (i in h.indices) h[i] = p.process(h[i].toFloat()).toDouble()
        for ((f, t60, g) in modes) {
            val tau = t60 / 6.908
            val a = 2.0 * (g - 1.0) / tau
            for (i in d until n) {
                val t = (i - d).toDouble() / fs
                h[i] += a * exp(-t / tau) * cos(2.0 * PI * f * t) / fs
            }
        }
        h
    }

    private var seed = 1L
    private val loss = 10.0.pow(-pathLossDb / 20.0)

    private fun convolve(played: FloatArray): DoubleArray {
        val n = Fft.nextPow2(played.size + ir.size)
        val ar = DoubleArray(n); val ai = DoubleArray(n); val br = DoubleArray(n); val bi = DoubleArray(n)
        for (i in played.indices) ar[i] = played[i].toDouble()
        for (i in ir.indices) br[i] = ir[i]
        Fft.transform(ar, ai); Fft.transform(br, bi)
        for (k in 0 until n) { val r = ar[k] * br[k] - ai[k] * bi[k]; val i = ar[k] * bi[k] + ai[k] * br[k]; ar[k] = r; ai[k] = i }
        Fft.transform(ar, ai, inverse = true)
        return ar
    }

    private fun noise(n: Int): FloatArray {
        val rnd = java.util.Random(seed++)
        val out = FloatArray(n)
        // road noise is mostly low rumble: a one-pole low-pass of white noise
        var y = 0.0
        val amp = 10.0.pow(roadNoiseDb / 20.0)
        for (i in 0 until n) { y += 0.04 * (rnd.nextGaussian() - y); out[i] = (y * 3.0 * amp + rnd.nextGaussian() * 1e-5).toFloat() }
        return out
    }

    override fun hear(played: FloatArray, fs: Int, channel: OutChannel): FloatArray {
        require(fs == this.fs)
        val x = played.copyOf()
        // the system EQ the app switched on sits between the phone and the speaker
        SystemEq.chain?.let { EqProcessor(it, fs).process(x) }
        val side = when (channel) { OutChannel.BOTH -> 1.0; OutChannel.LEFT -> 0.8; OutChannel.RIGHT -> 0.7 }
        val wet = convolve(x)
        val lead = (0.4 * fs).toInt()
        val bt = (btDelaySec * fs).toInt()
        val total = lead + bt + x.size + fs
        val rec = noise(total)
        for (i in 0 until total) {
            val j = i - lead - bt
            if (j in wet.indices) rec[i] += (wet[j] * loss * side).toFloat()
        }
        EqProcessor(phoneMic, fs).process(rec)
        return rec
    }

    private var musicAt = 0
    private val music: FloatArray by lazy { hear(TestSignals.pinkFile(fs, 6.0).also { for (i in it.indices) it[i] *= 0.6f }, fs, OutChannel.BOTH) }

    /**
     * Nothing is playing from the phone. Short blocks (the live spectrum) hear the truck's stereo playing music; a
     * long recording hears the head unit play the test file the way the app asked for it, a few seconds in (the user
     * pressed play): 55 s = sweep + pink file, 40 s = sweep file, 22 s = pink noise.
     */
    override fun listen(seconds: Double, fs: Int): FloatArray {
        val n = (seconds * fs).toInt().coerceAtLeast(1)
        if (seconds < 1.0) {
            val out = noise(n)
            for (i in 0 until n) out[i] += music[(musicAt + i) % music.size]
            musicAt = (musicAt + n) % music.size
            return out
        }
        val file: FloatArray? = when (seconds.toInt()) {
            55 -> TestSignals.combinedFile(fs)
            40 -> TestSignals.sweepFile(fs)
            22 -> TestSignals.pinkFile(fs, 14.0)
            else -> null
        }
        if (file == null) return noise(n)
        val heard = hear(file, fs, OutChannel.BOTH)
        val out = noise(n)
        val start = 3 * fs
        for (i in heard.indices) if (start + i < n) out[start + i] += heard[i]
        return out
    }

    /** The head unit plays a file we hand it (pink noise or a sweep): used by the "not connected" routes. */
    fun hearFile(file: FloatArray, fs: Int): FloatArray = hear(file, fs, OutChannel.BOTH)

    companion object { fun pink(fs: Int, s: Double) = Sweep.pinkNoise(fs, s) }
}
