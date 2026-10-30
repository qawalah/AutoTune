package dev.autotune.dsp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportTest {
    @Test
    fun graphicEqLineHas127PointsAndNoCommaDecimals() {
        val chain = EqChain.withSafePreamp(listOf(EqFilter(100.0, -4.0, 2.0), EqFilter(3000.0, 2.0, 1.0)))
        val line = Export.graphicEqLine(chain)
        assertTrue(line.startsWith("GraphicEQ: 20 "))
        assertEquals(127, line.removePrefix("GraphicEQ: ").split("; ").size)
        assertTrue(!line.contains(","))
    }

    @Test
    fun combineMergesNearbyFiltersAndDropsTinyOnes() {
        val out = Export.combine(listOf(EqFilter(100.0, -3.0, 2.0), EqFilter(104.0, -1.5, 2.0), EqFilter(1000.0, 0.1, 1.0), EqFilter(5000.0, -2.0, 1.0)))
        assertEquals(2, out.size)
        assertEquals(-4.5, out[0].gainDb, 1e-9)
    }
}

class FitterStackingTest {
    @Test
    fun deepWideDipNeverStacksBoostsBeyondLimit() {
        val g = FreqGrid()
        // flat response with a deep, wide hole at 36 Hz
        val mag = DoubleArray(g.size) { i -> val f = g.freqs[i]; if (f < 60) -12.0 else 0.0 }
        val fit = EqFitter.fit(mag, g, Target.FLAT)
        val total = g.freqs.maxOf { f -> fit.filters.sumOf { it.magnitudeDb(f) } }
        assertTrue(total <= 4.5, "total boost $total dB")
    }
}
