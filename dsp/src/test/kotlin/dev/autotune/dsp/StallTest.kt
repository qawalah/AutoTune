package dev.autotune.dsp

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Bluetooth pauses in a car (2.19.0 diagnostics, F-150): found in the sweep and in the start beeps. */
class StallTest {
    private val fs = 48000
    private val sweep = Sweep.generate(fs, 10.0)
    private val pre = 2 * fs

    /** The phone's 10 s sweep through a small car-like system (direct sound, two reflections), [edit] applied to what played. */
    private fun record(edit: (FloatArray) -> FloatArray = { it }): FloatArray {
        val played = edit(sweep.copyOf())
        val rnd = java.util.Random(11)
        val rec = FloatArray(pre + played.size + 2 * fs) { (rnd.nextGaussian() * 2e-4).toFloat() }
        val taps = listOf(0 to 0.3f, (0.003 * fs).toInt() to 0.12f, (0.011 * fs).toInt() to 0.07f)
        for ((d, g) in taps) for (i in played.indices) rec[pre + d + i] += played[i] * g
        return rec
    }

    /** [ms] of silence inserted [atSec] into the sweep (the sound paused), or removed when negative (it skipped). */
    private fun pause(atSec: Double, ms: Double): (FloatArray) -> FloatArray = { s ->
        val at = (atSec * fs).toInt(); val n = (abs(ms) * fs / 1000).toInt()
        if (ms > 0) s.copyOfRange(0, at) + FloatArray(n) + s.copyOfRange(at, s.size)
        else s.copyOfRange(0, at) + s.copyOfRange(at + n, s.size)
    }

    @Test
    fun aCleanSweepHasNoStall() {
        assertNull(SweepCheck.stall(record(), pre, sweep, fs))
    }

    @Test
    fun aPauseIsFoundWithItsLengthAndPlace() {
        for ((at, ms) in listOf(7.0 to 20.0, 4.5 to 21.0, 3.2 to 24.0, 6.6 to 10.0)) {
            val st = assertNotNull(SweepCheck.stall(record(pause(at, ms)), pre, sweep, fs), "pause of $ms ms at $at s")
            assertTrue(abs(st.ms - ms) < 2.0, "measured ${st.ms} ms for $ms")
            assertTrue(abs(st.atSec - at) <= SweepCheck.CHUNK_SEC, "found at ${st.atSec} s for $at")
            assertTrue(st.message.contains("paused"))
        }
    }

    @Test
    fun aSkipIsFoundToo() {
        val st = assertNotNull(SweepCheck.stall(record(pause(5.0, -20.0)), pre, sweep, fs))
        assertTrue(abs(st.ms + 20.0) < 2.0, "measured ${st.ms} ms")
        assertTrue(st.message.contains("skipped"))
    }

    @Test
    fun aPauseBeforeTheSweepMovesEverythingAndIsHarmless() {
        // the whole sweep late: the analyser finds it as one, so it is not a stall
        assertNull(SweepCheck.stall(record { FloatArray((0.024 * fs).toInt()) + it }, pre, sweep, fs))
    }

    @Test
    fun chunkLagsFromTheRealCarRuns() {
        // per-chunk lags (ms, 0.5 s chunks from 2 s) read from the 11 Nov 2026 F-150 recordings; a late subwoofer gives
        // the single 13-14 ms chunk at 3.5 s in every run, the tweeters some scatter at the end
        val clean = listOf(
            doubleArrayOf(3.0, -0.0, 1.0, 14.0, 3.0, 4.0, 0.0, 1.0, 2.0, 0.0, 0.0, 1.0, 1.0, 0.0, 9.0, 6.0),
            doubleArrayOf(1.0, -1.0, 0.0, 13.0, 2.0, 3.0, 4.0, 0.0, -0.0, -0.0, -0.0, 0.0, 0.0, -0.0, -0.0, 6.0),
            doubleArrayOf(3.0, 0.0, 1.0, 13.0, 2.0, 4.0, 4.0, 1.0, 6.0, 0.0, 0.0, 1.0, -0.0, 7.0, 0.0, 0.0),
        )
        for (l in clean) assertNull(SweepCheck.stallIn(l), l.joinToString())
        val stalled = listOf(
            4.5 to doubleArrayOf(2.0, 1.0, 1.0, 14.0, 7.0, 24.0, 20.0, 21.0, 22.0, 22.0, 20.0, 21.0, 21.0, 26.0, 24.0, 26.0),
            7.0 to doubleArrayOf(2.0, -1.0, 0.0, 13.0, 2.0, 3.0, -0.0, 0.0, 2.0, 2.0, 14.0, 15.0, 15.0, 16.0, 18.0, 20.0),
            3.5 to doubleArrayOf(1.0, -1.0, 13.0, 35.0, 24.0, 25.0, 22.0, 22.0, 21.0, 22.0, 21.0, 22.0, 22.0, 24.0, 28.0, 28.0),
            7.0 to doubleArrayOf(5.0, 2.0, 4.0, 15.0, 4.0, 6.0, 7.0, 3.0, 10.0, 3.0, 15.0, 16.0, 14.0, 15.0, 15.0, 15.0),
        )
        for ((at, l) in stalled) {
            val st = assertNotNull(SweepCheck.stallIn(l), l.joinToString())
            assertTrue(st.ms >= 9.0, "step ${st.ms}")
            assertTrue(abs(st.atSec - at) <= 1.0, "at ${st.atSec}, expected about $at")
        }
    }

    @Test
    fun theMicsSwitchOnClickIsSilencedAndNothingElse() {
        val rec = FloatArray(3 * fs) { 0.1f }
        rec[100] = 1f
        val q = SweepCheck.quietStart(rec, fs)
        assertEquals(1f, rec[100], "the original is left alone")
        assertTrue((0 until (SweepCheck.MIC_SETTLE_SEC * fs).toInt()).all { q[it] == 0f })
        assertTrue(((SweepCheck.MIC_SETTLE_SEC * fs).toInt() until q.size).all { q[it] == 0.1f })
        assertEquals(0, SweepCheck.quietStart(FloatArray(0), fs).size)
    }

    @Test
    fun aLateThirdBeepStillGivesTheMarker() {
        // seen at 17:47: the third beep 19 ms late, so the strict spacing check missed the marker
        val w = Marker.waveform(fs)
        val period = ((0.12 + 0.12) * fs).toInt()
        val late = (0.019 * fs).toInt()
        val played = w.copyOfRange(0, 2 * period) + FloatArray(late) + w.copyOfRange(2 * period, w.size)
        val rnd = java.util.Random(4)
        val at = 2 * fs
        val rec = FloatArray(6 * fs) { (rnd.nextGaussian() * 1e-3).toFloat() }
        for (i in played.indices) rec[at + i] += played[i] * 0.3f
        val found = assertNotNull(Marker.find(rec, fs), "marker with a late beep")
        // given where the first beep would have been, so the sweep (which follows the last beep) is where it says
        assertTrue(abs(found - (at + late)) < fs / 500 + 2, "marker off by ${found - at - late} samples")
        val timed = assertNotNull(Marker.findTimed(rec, fs))
        assertTrue(abs(timed.lateMs - 19.0) < 1.0, "late by ${timed.lateMs} ms")
    }

    @Test
    fun anOnTimeMarkerIsUnchanged() {
        val w = Marker.waveform(fs)
        val rnd = java.util.Random(4)
        val at = 2 * fs
        val rec = FloatArray(6 * fs) { (rnd.nextGaussian() * 1e-3).toFloat() }
        for (i in w.indices) rec[at + i] += w[i] * 0.3f
        assertEquals(at, Marker.find(rec, fs))
        assertTrue(abs(assertNotNull(Marker.findTimed(rec, fs)).lateMs) < 0.2)
    }

    @Test
    fun rampFramesMoveEvenlyAndEndExactly() {
        val f = EqRamp.frames(floatArrayOf(0f, -10f), floatArrayOf(-3f, 0f))
        assertEquals(EqRamp.STEPS, f.size)
        assertEquals(-0.3f, f[0][0], 1e-5f)
        assertEquals(-9f, f[0][1], 1e-5f)
        assertTrue(f.last().contentEquals(floatArrayOf(-3f, 0f)))
        // another band count (31 to 61 bands): from flat
        val g = EqRamp.frames(FloatArray(31), FloatArray(61) { 6f }, 2)
        assertEquals(3f, g[0][60], 1e-5f)
        assertEquals(1, EqRamp.frames(FloatArray(2), FloatArray(2), 0).size)
    }
}
