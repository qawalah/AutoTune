package dev.autotune.dsp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The failures seen in a real car: a sweep that arrived late and was cut off, a dropout, a start-up click. */
class SweepCheckTest {
    private val fs = 48000
    private val sweep = Sweep.generate(fs, 6.0)

    /** Background noise, then the sweep [delaySec] in, recording [totalSec] long. */
    private fun record(delaySec: Double, totalSec: Double, gain: Float = 0.3f, noise: Double = 1e-3, edit: (FloatArray, Int) -> Unit = { _, _ -> }): FloatArray {
        val rnd = java.util.Random(3)
        val rec = FloatArray((totalSec * fs).toInt()) { (rnd.nextGaussian() * noise).toFloat() }
        val d = (delaySec * fs).toInt()
        for (i in sweep.indices) if (d + i < rec.size) rec[d + i] += sweep[i] * gain
        edit(rec, d)
        return rec
    }

    @Test
    fun goodRecordingPasses() {
        val m = Analyzer.analyze(record(1.8, 11.0), sweep, fs, null, 80.0)
        assertNull(m.fatal, m.fatal)
        assertTrue(m.warnings.none { "clipped" in it })
    }

    @Test
    fun startupClickIsNotClipping() {
        val rec = record(1.8, 11.0) { r, _ -> for (i in 100 until 140) r[i] = 1f }
        val m = Analyzer.analyze(rec, sweep, fs, null, 80.0)
        assertTrue(m.warnings.none { "clipped" in it }, m.warnings.toString())
        assertNull(m.fatal)
    }

    @Test
    fun realClippingNamesTheFrequency() {
        // overload during the first second of the sweep = the bass
        val rec = record(1.8, 11.0) { r, d -> for (i in d + fs / 2 until d + fs / 2 + 200) r[i] = if (i % 2 == 0) 1f else -1f }
        val m = Analyzer.analyze(rec, sweep, fs, null, 80.0)
        val w = m.warnings.firstOrNull { "clipped" in it }
        assertNotNull(w)
        assertTrue(w.contains(" Hz"), w)
    }

    @Test
    fun lateSweepThatGetsCutOffIsRejected() {
        // Bluetooth woke up late: the recording ends 2 s before the sweep does (the run that read 31 dB from target)
        val m = Analyzer.analyze(record(2.5, 6.5), sweep, fs, null, 80.0)
        assertNotNull(m.fatal)
        assertTrue(m.fatal!!.contains("cut off"), m.fatal)
    }

    @Test
    fun dropoutIsRejected() {
        // the sound stopped reaching the mic half way through (wrong output, or the stream dropped)
        val rec = record(1.8, 11.0) { r, d -> for (i in d + 3 * fs until d + 6 * fs) r[i] = (r[i] - sweep[i - d] * 0.3f) }
        val m = Analyzer.analyze(rec, sweep, fs, null, 80.0)
        val msg = m.fatal ?: m.warnings.firstOrNull { "barely heard" in it }
        assertNotNull(msg)
        assertTrue(msg.contains("barely heard"), msg)
    }

    @Test
    fun levelCheckReadsSoundAndBackground() {
        val rnd = java.util.Random(1)
        val rec = FloatArray(6 * fs) { i -> (rnd.nextGaussian() * 1e-3).toFloat() + if (i > 2 * fs) Sweep.pinkNoise(1, 0.0).let { 0f } else 0f }
        val pink = Sweep.pinkNoise(fs, 3.5)
        for (i in pink.indices) rec[2 * fs + i] += pink[i] * 0.5f
        val r = LevelCheck.analyze(rec, fs)
        assertTrue(r.soundDb - r.noiseDb > 25, "${r.soundDb} ${r.noiseDb}")
        assertTrue(r.verdict.startsWith("Good"), r.verdict)
        assertEquals(true, r.peakDb < -1)
    }

    @Test
    fun loudMidsAgainstBassHeavyNoiseIsNotADropout() {
        // car-like background: strong low-frequency rumble. The sweep is clearly heard but its mids carry less
        // total energy than the rumble; a broadband comparison used to call that a dropout.
        val rnd = java.util.Random(7)
        var lp = 0.0
        val rec = record(1.8, 11.0) { r, _ ->
            for (i in r.indices) { lp = 0.995 * lp + rnd.nextGaussian() * 0.004; r[i] += lp.toFloat() }
        }
        val m = Analyzer.analyze(rec, sweep, fs, null, 80.0)
        assertTrue(m.warnings.none { "barely heard" in it }, m.warnings.toString())
        assertEquals(null, m.fatal)
    }

    @Test
    fun wrongExpectedStartIsCorrectedByTheGlobalPeak() {
        val room = SimRoom(fs)
        val rec = room.record(sweep)
        val good = (0.5 * fs).toInt() + (0.005 * fs).toInt()
        val ref = Analyzer.analyze(rec, sweep, fs, null, 80.0, expectedStart = good)
        assertTrue(ref.peakFromMarker)
        assertTrue(ref.warnings.none { "start beeps" in it })
        for (off in listOf(0.25, 1.0)) {
            val m = Analyzer.analyze(rec, sweep, fs, null, 80.0, expectedStart = good + (off * fs).toInt())
            var sum = 0.0; var c = 0
            for (i in m.grid.freqs.indices) if (m.grid.freqs[i] in 40.0..12000.0) { val d = m.magDb[i] - ref.magDb[i]; sum += d * d; c++ }
            val rms = kotlin.math.sqrt(sum / c)
            assertTrue(rms < 0.5, "offset $off s gave $rms dB RMS")
            assertTrue(m.warnings.any { "start beeps" in it }, m.warnings.toString())
            assertTrue(!m.peakFromMarker)
        }
    }
}
