package dev.autotune.dsp

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TasteTest {
    @Test
    fun bassAndTrebleShelvesSitWhereTheyShould() {
        val t = Target.FLAT.withTaste(6.0, -3.0)
        assertTrue(abs(t.dbAt(30.0) - 6.0) < 0.6, "deep bass gets nearly all of it: ${t.dbAt(30.0)}")
        assertTrue(abs(t.dbAt(110.0) - 3.0) < 0.3, "half at 110 Hz: ${t.dbAt(110.0)}")
        assertTrue(abs(t.dbAt(1000.0)) < 0.3, "mids untouched: ${t.dbAt(1000.0)}")
        assertTrue(abs(t.dbAt(4000.0) + 1.5) < 0.3, "half the treble change at 4 kHz: ${t.dbAt(4000.0)}")
        assertTrue(t.dbAt(16000.0) < -2.5)
        assertEquals("Flat · bass +6 dB · treble -3 dB", t.name)
        assertSame(Target.CAR, Target.CAR.withTaste(0.0, 0.0))
    }

    @Test
    fun harmanStyleCurvesLiftTheBassMoreThanTheOldOnes() {
        assertTrue(Target.HARMAN_CAR.dbAt(40.0) > Target.CAR.dbAt(40.0) + 2.0)
        assertTrue(Target.HARMAN_ROOM.dbAt(40.0) > Target.HOME_THEATER.dbAt(40.0) + 1.0)
        assertEquals(0.0, Target.HARMAN_CAR.dbAt(1000.0), 1e-9)
        // the old built-ins keep their place, so tests stored by index still find theirs
        assertEquals(listOf(Target.CAR, Target.HOME_THEATER, Target.FLAT), Target.BUILT_IN.take(3))
    }

    @Test
    fun moreBassInTheTargetMeansTheFitCutsLessBass() {
        val grid = FreqGrid()
        // a cabin with 12 dB of bass gain over the mids
        val mag = DoubleArray(grid.size) { i -> 12.0 * Target.bassWeight(grid.freqs[i]) }
        fun bassCut(t: Target): Double {
            val f = EqFitter.fit(mag, grid, t).filters
            return grid.freqs.filter { it in 30.0..80.0 }.minOf { fr -> f.sumOf { it.magnitudeDb(fr) } }
        }
        val plain = bassCut(Target.CAR)
        val lifted = bassCut(Target.CAR.withTaste(6.0, 0.0))
        assertTrue(lifted > plain + 3.0, "bass cut with taste $lifted vs $plain")
    }
}
