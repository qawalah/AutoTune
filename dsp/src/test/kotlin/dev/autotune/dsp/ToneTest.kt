package dev.autotune.dsp

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToneTest {
    private val fs = 48000

    @Test
    fun sliderScaleIsLogarithmicAndRoundTrips() {
        assertEquals(20.0, Tone.freqAt(0.0), 1e-9)
        assertEquals(20000.0, Tone.freqAt(1.0), 1e-6)
        // the middle of a 20 Hz - 20 kHz log slider is 632 Hz
        assertEquals(632.4, Tone.freqAt(0.5), 0.5)
        for (hz in listOf(20.0, 63.0, 440.0, 1000.0, 12500.0, 20000.0)) assertEquals(hz, Tone.freqAt(Tone.fractionOf(hz)), hz * 1e-9)
        // out-of-range values are held at the ends
        assertEquals(0.0, Tone.fractionOf(5.0), 1e-12)
        assertEquals(1.0, Tone.fractionOf(40000.0), 1e-12)
    }

    @Test
    fun toneLandsInTheNearestBar() {
        assertEquals(Rta.CENTERS.toList().indexOf(1000.0), Tone.bandIndex(1000.0))
        assertEquals(Rta.CENTERS.toList().indexOf(63.0), Tone.bandIndex(64.0))
        assertEquals(0, Tone.bandIndex(20.0))
        assertEquals(Rta.CENTERS.size - 1, Tone.bandIndex(20000.0))
    }

    @Test
    fun frequencyReadsInHzOrKhz() {
        assertEquals("63 Hz", Tone.text(63.2))
        assertEquals("1.25 kHz", Tone.text(1250.0))
    }

    @Test
    fun synthFollowsTheFrequencyWithoutJumps() {
        val s = ToneSynth(fs, level = 0.1)
        s.targetHz = 1000.0
        val a = FloatArray(fs)
        s.fill(a, a.size)
        // after the glide it is a clean 1 kHz at the asked level: RMS 0.1 / sqrt(2)
        val tail = a.copyOfRange(fs / 2, fs)
        val rms = Math.sqrt(tail.sumOf { it.toDouble() * it } / tail.size)
        assertEquals(0.1 / Math.sqrt(2.0), rms, 0.003)
        var crossings = 0
        for (i in 1 until tail.size) if (tail[i - 1] < 0f && tail[i] >= 0f) crossings++
        assertTrue(abs(crossings - 500) <= 3, "$crossings crossings in half a second of 1 kHz")
        // a big move of the slider never jumps more than the wave's own biggest step at the new pitch
        // (an 8 kHz sine of amplitude 0.1 at 48 kHz moves 0.2 * sin(pi / 6) = 0.1 per sample; a click would be up to 0.2)
        s.targetHz = 8000.0
        val b = FloatArray(fs / 4)
        s.fill(b, b.size)
        var worst = abs(b[0] - a.last())
        for (i in 1 until b.size) worst = maxOf(worst, abs(b[i] - b[i - 1]))
        assertTrue(worst < 0.105f, "step of $worst between samples")
    }

    @Test
    fun synthFadesInAndStaysWithinItsLevel() {
        val s = ToneSynth(fs, level = 0.1)
        val a = FloatArray(fs / 10)
        s.fill(a, a.size)
        assertTrue(abs(a[0]) < 0.001f)
        assertTrue(a.all { abs(it) <= 0.1f + 1e-6f })
    }

    @Test
    fun synthClampsTheTargetToTheAudibleRange() {
        val s = ToneSynth(fs)
        s.targetHz = 5.0
        assertEquals(20.0, s.targetHz)
        s.targetHz = 90000.0
        assertEquals(20000.0, s.targetHz)
    }

    @Test
    fun peakHoldKeepsTheHighestUntilReset() {
        val h = PeakHold(3)
        assertNull(h.snapshot())
        h.update(doubleArrayOf(1.0, -5.0, 2.0))
        h.update(doubleArrayOf(0.0, -2.0, 4.0))
        h.update(doubleArrayOf(-3.0, -9.0, 1.0))
        assertEquals(listOf(1.0, -2.0, 4.0), h.snapshot()!!.toList())
        h.reset()
        assertNull(h.snapshot())
        h.update(doubleArrayOf(-20.0, -20.0, -20.0))
        // after a reset the old, higher peaks are gone
        assertEquals(listOf(-20.0, -20.0, -20.0), assertNotNull(h.snapshot()).toList())
    }
}
