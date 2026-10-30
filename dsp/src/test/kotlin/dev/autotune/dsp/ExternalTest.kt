package dev.autotune.dsp

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExternalTest {
    private val fs = 48000

    private fun relative(m: Measurement): DoubleArray {
        val s = m.grid.smoothOctave(m.magDb, 6.0)
        val off = EqFitter.offsetDb(m.magDb, m.grid, Target.FLAT)
        return DoubleArray(s.size) { s[it] - off }
    }

    private fun rmsDiff(a: DoubleArray, b: DoubleArray, grid: FreqGrid, lo: Double, hi: Double): Double {
        var sum = 0.0
        var n = 0
        for (i in grid.freqs.indices) if (grid.freqs[i] in lo..hi) { val d = a[i] - b[i]; sum += d * d; n++ }
        return sqrt(sum / n)
    }

    @Test
    fun wavHeaderIsValid() {
        val bytes = Wav.encodePcm16(FloatArray(480), fs, 2)
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals("WAVE", String(bytes, 8, 4))
        assertEquals(44 + 480 * 2 * 2, bytes.size)
    }

    @Test
    fun sweepIsFoundWhenPlayedAtAnUnknownTimeAmongClicksAndNoise() {
        val room = SimRoom(fs)
        val cal = Calibration.parse(room.calibrationFile())
        val file = TestSignals.sweepFile(fs)
        val played = room.record(file) // already includes 0.5 s lead + 2 s tail

        // Reference: analysed directly against the plain sweep (what the in-app flow does).
        val sweep = Sweep.generate(fs, TestSignals.SWEEP_SECONDS)
        val direct = Analyzer.analyze(room.record(sweep), sweep, fs, cal, gateMs = 500.0)

        // Unknown start: 9 s of low background noise with a click at 3 s, then the playback.
        val rnd = java.util.Random(5)
        val lead = FloatArray(9 * fs) { (rnd.nextGaussian() * 2e-4).toFloat() }
        lead[3 * fs] = 0.4f
        val long = FloatArray(lead.size + played.size + 5 * fs)
        System.arraycopy(lead, 0, long, 0, lead.size)
        System.arraycopy(played, 0, long, lead.size, played.size)

        val trimmed = assertNotNull(SweepTrim.trim(long, fs, TestSignals.SWEEP_SECONDS), "sweep should be found")
        assertTrue(trimmed.size < long.size / 2)
        val viaFile = Analyzer.analyze(trimmed, sweep, fs, cal, gateMs = 500.0)

        val d = rmsDiff(relative(direct), relative(viaFile), direct.grid, 40.0, 12000.0)
        println("trim: direct vs unknown-start rms diff = %.3f dB".format(d))
        assertTrue(d < 0.5, "file route should match the direct route (was $d dB)")
    }

    @Test
    fun silenceIsReportedAsNoSweep() {
        assertEquals(null, SweepTrim.trim(FloatArray(20 * fs), fs, 6.0))
    }

    @Test
    fun pinkNoiseRouteAgreesWithSweepRoute() {
        val room = SimRoom(fs)
        val cal = Calibration.parse(room.calibrationFile())
        val sweep = Sweep.generate(fs, TestSignals.SWEEP_SECONDS)
        val viaSweep = Analyzer.analyze(room.record(sweep), sweep, fs, cal, gateMs = 500.0)

        val noise = room.record(Sweep.pinkNoise(fs, 25.0, amplitude = 0.2))
        val viaNoise = NoiseAnalyzer.analyze(noise.copyOfRange(fs, noise.size), fs, cal)

        val d = rmsDiff(relative(viaSweep), relative(viaNoise), viaSweep.grid, 50.0, 10000.0)
        println("noise: sweep vs pink-noise rms diff = %.3f dB".format(d))
        assertTrue(d < 2.0, "pink-noise route should agree with the sweep route within 2 dB RMS (was $d)")

        val res = ResonanceDetector.detect(viaNoise)
        assertTrue(res.any { abs(it.freqHz / 62.0 - 1.0) < 0.1 }, "noise route should still flag the 62 Hz peak; found ${res.map { it.freqHz.toInt() }}")
        assertTrue(res.all { it.t60Ms == null }, "no impulse response, so no decay times")
    }
}

class InvalidMeasurementTest {
    private val fs = 48000

    @Test
    fun recordingWithNoSweepIsRejected() {
        val sweep = Sweep.generate(fs, 6.0)
        val rnd = java.util.Random(3)
        val noiseOnly = FloatArray(10 * fs) { (rnd.nextGaussian() * 1e-3).toFloat() }
        val m = Analyzer.analyze(noiseOnly, sweep, fs, null, gateMs = 80.0)
        assertNotNull(m.fatal, "pure noise must not be accepted as a measurement")
    }

    @Test
    fun silentPinkNoiseCaptureIsRejected() {
        val m = NoiseAnalyzer.analyze(FloatArray(20 * fs), fs, null)
        assertNotNull(m.fatal)
    }

    @Test
    fun goodMeasurementIsAccepted() {
        val room = SimRoom(fs)
        val sweep = Sweep.generate(fs, 6.0)
        val m = Analyzer.analyze(room.record(sweep), sweep, fs, null, gateMs = 80.0)
        assertEquals(null, m.fatal)
    }

    @Test
    fun markerLocatesTheSweepStartUnderNoiseAndTalking() {
        val file = TestSignals.sweepFile(fs)
        val rnd = java.util.Random(11)
        val pre = 7 * fs + 12345
        val rec = FloatArray(pre + file.size + 3 * fs) { (rnd.nextGaussian() * 3e-3).toFloat() }
        // someone talking / a loud click before the file starts
        for (i in 2 * fs until 3 * fs) rec[i] += (0.2 * kotlin.math.sin(2 * Math.PI * 440.0 * i / fs)).toFloat()
        for (i in file.indices) rec[pre + i] += file[i] * 0.3f
        val at = assertNotNull(SweepTrim.locate(rec, fs))
        val expected = pre + (TestSignals.LEAD_SECONDS * fs).toInt() + (Marker.SWEEP_OFFSET_SEC * fs).toInt()
        assertTrue(kotlin.math.abs(at - expected) < fs / 100, "sweep start off by ${at - expected} samples")
    }

    @Test
    fun markerIsNotFoundInNoiseOrMusic() {
        val rnd = java.util.Random(2)
        val noise = FloatArray(10 * fs) { (rnd.nextGaussian() * 0.05).toFloat() }
        assertEquals(null, Marker.find(noise, fs))
    }

    @Test
    fun filesAreShorterNow() {
        assertTrue(TestSignals.sweepFile(fs).size < 11 * fs)
        assertTrue(TestSignals.combinedFile(fs).size < 23 * fs)
    }

    @Test
    fun beepLessSweepIsNotMistakenForTheMarker() {
        for (secs in listOf(6.0, 10.0)) {
            val sweep = Sweep.generate(fs, secs)
            val rnd = java.util.Random(3)
            val rec = FloatArray(6 * fs + sweep.size + 3 * fs) { (rnd.nextGaussian() * 1e-3).toFloat() }
            for (i in sweep.indices) rec[6 * fs + i] += sweep[i] * 0.3f
            assertEquals(null, Marker.find(rec, fs), "sweep of $secs s")
            val at = assertNotNull(SweepTrim.locate(rec, fs))
            assertTrue(abs(at - 6 * fs) < fs / 50, "onset fallback off by ${at - 6 * fs} samples")
        }
    }

    @Test
    fun quietBeepsAreStillFound() {
        val file = TestSignals.sweepFile(fs)
        val lead = (TestSignals.LEAD_SECONDS * fs).toInt()
        val len = Marker.waveform(fs).size
        for (i in lead until lead + len) file[i] *= 0.1f
        val room = SimRoom(fs)
        val rec = room.record(file)
        val at = assertNotNull(Marker.find(rec, fs), "quiet beeps should be found")
        val expected = (0.5 * fs).toInt() + lead + (0.005 * fs).toInt() // lead of the sim + room's direct delay
        assertTrue(abs(at - expected) < fs / 500 + 2, "marker off by ${at - expected} samples")
    }

    @Test
    fun markerSearchOnALongRecordingStaysSmall() {
        val file = TestSignals.sweepFile(fs)
        val rec = FloatArray(60 * fs)
        val pre = 40 * fs
        for (i in file.indices) rec[pre + i] = file[i] * 0.3f
        System.gc()
        val rt = Runtime.getRuntime()
        val before = rt.totalMemory() - rt.freeMemory()
        val at = Marker.find(rec, fs)
        val grown = (rt.totalMemory() - rt.freeMemory() - before) / 1_000_000.0
        assertNotNull(at)
        assertTrue(abs(at - (pre + (TestSignals.LEAD_SECONDS * fs).toInt())) < 50)
        assertTrue(grown < 40.0, "allocated about $grown MB net")
    }

    @Test
    fun markerIsFoundInAReverberantRoom() {
        // living-room echoes (RT60 about 0.5 s) fill part of the gaps between the beeps; the marker must still be found
        val w = Marker.waveform(fs)
        val rnd = java.util.Random(5)
        val pre = 2 * fs
        val rec = FloatArray(8 * fs) { (rnd.nextGaussian() * 1e-3).toFloat() }
        for (i in w.indices) rec[pre + i] += w[i] * 0.3f
        val decay = 6.9 / 0.5 // amplitude e-folding rate for 60 dB in 0.5 s
        repeat(600) {
            val d = (0.003 + rnd.nextDouble() * 0.45) * fs
            val g = (rnd.nextGaussian() * 0.08 * kotlin.math.exp(-decay * d / fs)).toFloat()
            val o = pre + d.toInt()
            for (i in w.indices) if (o + i < rec.size) rec[o + i] += w[i] * 0.3f * g
        }
        val at = assertNotNull(Marker.find(rec, fs), "marker in a reverberant room")
        assertTrue(abs(at - pre) < fs / 500 + 2, "marker off by ${at - pre} samples")
    }
}
