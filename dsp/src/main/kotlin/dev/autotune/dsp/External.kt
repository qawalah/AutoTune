package dev.autotune.dsp

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Test signals the user plays on ANOTHER device (PC, smart TV, media player) when the phone cannot play them. */
object TestSignals {
    const val SWEEP_SECONDS = 6.0

    const val LEAD_SECONDS = 1.0
    const val TAIL_SECONDS = 1.5

    /** 1 s silence + start marker (three rising beeps) + 1 s + the exact sweep the analyser expects + 1.5 s silence. */
    fun sweepFile(fs: Int): FloatArray {
        val sweep = Sweep.generate(fs, SWEEP_SECONDS)
        val marker = Marker.waveform(fs)
        val lead = (LEAD_SECONDS * fs).toInt()
        val tail = (TAIL_SECONDS * fs).toInt()
        val sweepAt = lead + (Marker.SWEEP_OFFSET_SEC * fs).toInt()
        val out = FloatArray(sweepAt + sweep.size + tail)
        System.arraycopy(marker, 0, out, lead, marker.size)
        System.arraycopy(sweep, 0, out, sweepAt, sweep.size)
        return out
    }

    const val COMBINED_GAP_SECONDS = 2.0
    const val COMBINED_PINK_SECONDS = 10.0

    /** 1 s silence + marker + 1 s + sweep + 2 s gap + 10 s pink noise + 1 s silence: both analyses from one recording. */
    fun combinedFile(fs: Int): FloatArray {
        val sweep = Sweep.generate(fs, SWEEP_SECONDS)
        val pink = pinkFile(fs, COMBINED_PINK_SECONDS)
        val marker = Marker.waveform(fs)
        val lead = (LEAD_SECONDS * fs).toInt()
        val sweepAt = lead + (Marker.SWEEP_OFFSET_SEC * fs).toInt()
        val gap = (COMBINED_GAP_SECONDS * fs).toInt()
        val tail = fs
        val out = FloatArray(sweepAt + sweep.size + gap + pink.size + tail)
        System.arraycopy(marker, 0, out, lead, marker.size)
        System.arraycopy(sweep, 0, out, sweepAt, sweep.size)
        System.arraycopy(pink, 0, out, sweepAt + sweep.size + gap, pink.size)
        return out
    }

    /** Pink noise with short fades, for players that can loop a file but not a sweep. */
    fun pinkFile(fs: Int, seconds: Double = 60.0): FloatArray {
        val n = Sweep.pinkNoise(fs, seconds, amplitude = 0.2)
        val fade = fs / 2
        for (i in 0 until fade) {
            val g = (0.5 * (1.0 - cos(PI * i / fade))).toFloat()
            n[i] *= g
            n[n.size - 1 - i] *= g
        }
        return n
    }
}

object Wav {
    /** 16-bit PCM WAV. [mono] is duplicated to [channels] channels. */
    fun encodePcm16(mono: FloatArray, fs: Int, channels: Int = 2, onlyChannel: Int = -1): ByteArray {
        val dataBytes = mono.size * channels * 2
        val bb = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()).putInt(36 + dataBytes).put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(channels.toShort())
            .putInt(fs).putInt(fs * channels * 2).putShort((channels * 2).toShort()).putShort(16)
        bb.put("data".toByteArray()).putInt(dataBytes)
        for (x in mono) {
            val v = (x.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            for (c in 0 until channels) bb.putShort(if (onlyChannel < 0 || c == onlyChannel) v else 0)
        }
        return bb.array()
    }
}

/**
 * The start marker at the front of the test files: three short rising beeps, then a fixed 1 s pause, then the sweep.
 * The app finds the marker by correlation (works under noise, ignores clicks and talking), so it knows exactly where
 * the sweep starts and can stop recording as soon as the sweep has been captured.
 */
object Marker {
    private const val BEEP = 0.12
    private const val GAP = 0.12
    private const val COUNT = 3
    const val LENGTH_SEC = COUNT * BEEP + (COUNT - 1) * GAP
    /** From the first sample of the marker to the first sample of the sweep. */
    const val SWEEP_OFFSET_SEC = LENGTH_SEC + 1.0

    fun waveform(fs: Int): FloatArray {
        val beep = (BEEP * fs).toInt()
        val gap = (GAP * fs).toInt()
        val out = FloatArray(COUNT * beep + (COUNT - 1) * gap)
        val fade = (0.005 * fs).toInt()
        for (c in 0 until COUNT) {
            val o = c * (beep + gap)
            for (i in 0 until beep) {
                val t = i.toDouble() / fs
                val f = 1500.0 + (3000.0 - 1500.0) * (t / BEEP) / 2.0  // 1.5 -> 3 kHz inside one beep
                var g = 0.5
                if (i < fade) g *= 0.5 * (1 - cos(PI * i / fade))
                if (i >= beep - fade) g *= 0.5 * (1 - cos(PI * (beep - 1 - i) / fade))
                out[o + i] = (g * kotlin.math.sin(2.0 * PI * f * t)).toFloat()
            }
        }
        return out
    }

    /** One beep on its own: the template the detector correlates with. */
    fun beepWaveform(fs: Int): FloatArray = waveform(fs).copyOf((BEEP * fs).toInt())

    /** Longest stretch of the recording correlated in one go; keeps the FFT buffers at a few MB whatever the recording length. */
    const val WINDOW_SEC = 6.0

    /** Highest-scoring candidate of one window (see [find]). */
    private class Hit(val start: Int, val score: Double)

    /**
     * First sample of the marker inside rec[from, to), or null when it is not clearly there.
     *
     * The recording is correlated with ONE beep, in windows of [WINDOW_SEC] that overlap by more than the marker length.
     * A hit needs the structure of the marker, not just a strong correlation: three equally strong peaks exactly
     * BEEP+GAP apart, and almost nothing in the gaps between them. A sweep passing through the beeps' band gives a
     * broad plateau of correlation (no gaps), noise gives no three-peak train, so neither is mistaken for the marker.
     * The previous test (one strong peak with side peaks) took the sweep's passage through the beep band for the
     * marker on a real soundbar recording; the silent gaps rule that out.
     */
    fun find(rec: FloatArray, fs: Int, from: Int = 0, to: Int = rec.size): Int? {
        val template = beepWaveform(fs)
        val full = waveform(fs)
        val len = to - from
        if (len < full.size * 2) return null
        val win = min(len, (WINDOW_SEC * fs).toInt())
        val overlap = full.size + fs / 2
        val n = Fft.nextPow2(win + template.size)
        val yr = DoubleArray(n); val yi = DoubleArray(n)
        val xr = DoubleArray(n); val xi = DoubleArray(n)
        for (i in template.indices) xr[i] = template[i].toDouble()
        Fft.transform(xr, xi)
        var best: Hit? = null
        var start = 0
        while (true) {
            val a = from + start
            val b = min(to, a + win)
            val l = b - a
            if (l >= full.size) {
                java.util.Arrays.fill(yr, 0.0); java.util.Arrays.fill(yi, 0.0)
                for (i in 0 until l) yr[i] = rec[a + i].toDouble()
                Fft.transform(yr, yi)
                for (k in 0 until n) { // Y * conj(X), in place
                    val r = yr[k] * xr[k] + yi[k] * xi[k]
                    val im = yi[k] * xr[k] - yr[k] * xi[k]
                    yr[k] = r; yi[k] = im
                }
                Fft.transform(yr, yi, inverse = true)
                hitIn(yr, l - full.size, fs)?.let { h -> if (best == null || h.score > best!!.score) best = Hit(a + h.start, h.score) }
            }
            if (b >= to) break
            start += win - overlap
        }
        return best?.start
    }

    /** The marker's structure inside one window of single-beep correlation c[0..last]; null when it is not there. */
    private fun hitIn(c: DoubleArray, last: Int, fs: Int): Hit? {
        if (last < 0) return null
        val period = ((BEEP + GAP) * fs).toInt()
        val tol = (0.003 * fs).toInt()           // timing slack per beep
        fun peakNear(i: Int): Double {
            var m = 0.0
            for (k in max(0, i - tol)..min(last, i + tol)) m = max(m, abs(c[k]))
            return m
        }
        // the gap after a beep: the template sits exactly on silence, so a real marker correlates with nothing there
        fun gapAfter(i: Int): Double {
            val g0 = i + (BEEP * fs).toInt() - (0.01 * fs).toInt()
            val g1 = i + (BEEP * fs).toInt() + (0.01 * fs).toInt()
            var m = 0.0
            for (k in max(0, g0)..min(last, g1)) m = max(m, abs(c[k]))
            return m
        }
        var p = 0
        var pv = 0.0
        for (i in 0..last) { val v = abs(c[i]); if (v > pv) { pv = v; p = i } }
        if (pv <= 0.0) return null
        var best: Hit? = null
        // the strongest peak may be any of the three beeps
        for (k in 0 until COUNT) {
            val s = p - k * period
            if (s < 0 || s + (COUNT - 1) * period > last) continue
            val heights = DoubleArray(COUNT) { peakNear(s + it * period) }
            val top = heights.max()
            val low = heights.min()
            if (low < 0.5 * top) continue
            val gaps = (0 until COUNT - 1).maxOf { gapAfter(s + it * period) }
            if (gaps > 0.35 * low) continue
            // refine: the exact first-beep lag is the strongest sample near s
            var s0 = s; var s0v = 0.0
            for (i in max(0, s - tol)..min(last, s + tol)) if (abs(c[i]) > s0v) { s0v = abs(c[i]); s0 = i }
            val score = (low / top) * (1.0 - gaps / low) * top
            if (best == null || score > best!!.score) best = Hit(s0, score)
        }
        return best
    }
}

/**
 * Finds the sweep inside a long recording whose start time is unknown (user pressed play on another device),
 * and cuts a window around it that is small enough to deconvolve on a phone.
 */
object SweepTrim {
    /** Sample where the sweep starts: from the start marker when it is heard, otherwise from the first sustained sound. */
    fun locate(rec: FloatArray, fs: Int): Int? =
        Marker.find(rec, fs)?.let { it + (Marker.SWEEP_OFFSET_SEC * fs).toInt() } ?: onset(rec, fs)

    fun trim(rec: FloatArray, fs: Int, sweepSeconds: Double, preSec: Double = 0.8, postSec: Double = 3.5): FloatArray? {
        val onsetSample = locate(rec, fs) ?: return null
        return trimAt(rec, fs, onsetSample, sweepSeconds, preSec, postSec)
    }

    /** Cuts [onsetSample] - [preSec] .. + sweep + [postSec] out of the recording, so the analysis never sees the rest. */
    fun trimAt(rec: FloatArray, fs: Int, onsetSample: Int, sweepSeconds: Double, preSec: Double = 0.8, postSec: Double = 3.5): FloatArray {
        val start = max(0, onsetSample - (preSec * fs).toInt())
        val end = min(rec.size, onsetSample + ((sweepSeconds + postSec) * fs).toInt())
        return rec.copyOfRange(start, max(start, end))
    }

    /** Sample index where sustained sound (the sweep) starts, or null if nothing audible. Accurate to about 10 ms. */
    fun onset(rec: FloatArray, fs: Int): Int? {
        val win = fs / 100 // 10 ms
        val n = rec.size / win
        if (n < 100) return null
        val env = DoubleArray(n) { i ->
            var s = 0.0
            for (k in i * win until (i + 1) * win) s += rec[k].toDouble() * rec[k]
            sqrt(s / win)
        }
        val peak = env.max()
        if (peak < 1e-4) return null // nothing audible
        val thr = peak * 0.03 // -30 dB
        // Onset = first window above threshold that is followed by sustained sound (rejects clicks and bumps).
        var onset = -1
        for (i in 0 until n - 50) {
            if (env[i] < thr) continue
            var above = 0
            for (k in i until i + 50) if (env[k] > thr * 0.5) above++
            if (above >= 40) { onset = i; break }
        }
        if (onset < 0) return null
        return onset * win
    }
}

/**
 * Magnitude response from a recording of PINK NOISE played on another device (e.g. a YouTube pink-noise video).
 * No reference signal or timing is needed: the averaged spectrum of pink noise is already the system response
 * (power per Hz falls as 1/f, so we multiply by f). Gives no impulse response, so no decay times.
 */
object NoiseAnalyzer {
    fun analyze(rec: FloatArray, fs: Int, cal: Calibration?, grid: FreqGrid = FreqGrid(), fftSize: Int = 16384): Measurement {
        val warnings = ArrayList<String>()
        require(rec.size >= fftSize * 4) { "Recording too short - capture at least 6 seconds of pink noise" }
        run {
            var clips = 0
            for (i in (fs / 4) until rec.size) if (abs(rec[i]) >= 0.999f) clips++
            if (clips >= 3) warnings += "The mic clipped (too loud). Turn the volume down a few steps and capture again."
        }
        var sq = 0.0
        for (x in rec) sq += x.toDouble() * x
        val rms = sqrt(sq / rec.size)
        val fatal = if (rms < 1e-4) "No pink noise heard - raise the volume on the other device, move the mic closer, and make sure it is playing." else null
        if (rms < 1e-3) warnings += "Very quiet signal (%.0f dBFS) - raise the volume or move the mic closer.".format(20 * log10(rms + 1e-12))

        val hop = fftSize / 2
        val window = DoubleArray(fftSize) { 0.5 * (1.0 - cos(2.0 * PI * it / (fftSize - 1))) }
        val power = DoubleArray(fftSize / 2 + 1)
        val re = DoubleArray(fftSize)
        val im = DoubleArray(fftSize)
        var frames = 0
        var pos = 0
        while (pos + fftSize <= rec.size) {
            for (k in 0 until fftSize) { re[k] = rec[pos + k] * window[k]; im[k] = 0.0 }
            Fft.transform(re, im)
            for (k in 0..fftSize / 2) power[k] += re[k] * re[k] + im[k] * im[k]
            frames++
            pos += hop
        }
        for (k in power.indices) power[k] /= frames

        val binHz = fs.toDouble() / fftSize
        val half = 2.0.pow(1.0 / (2.0 * grid.ppo))
        val magDb = DoubleArray(grid.size) { gi ->
            val f = grid.freqs[gi]
            val b0 = Math.ceil(f / half / binHz).toInt().coerceAtLeast(1)
            val b1 = Math.floor(f * half / binHz).toInt().coerceAtMost(fftSize / 2 - 1)
            val p: Double = if (b1 >= b0) {
                var s = 0.0
                for (b in b0..b1) s += power[b]
                s / (b1 - b0 + 1)
            } else {
                val bin = f / binHz
                val lo = bin.toInt().coerceIn(1, fftSize / 2 - 2)
                power[lo] + (bin - lo) * (power[lo + 1] - power[lo])
            }
            10.0 * log10(p * f + 1e-30) - (cal?.gainDbAt(f) ?: 0.0)
        }
        return Measurement(grid, magDb, DoubleArray(0), 0, fs, 0.0, null, warnings, fatal)
    }
}
