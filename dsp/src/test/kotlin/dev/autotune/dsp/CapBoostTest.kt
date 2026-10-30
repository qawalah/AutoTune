package dev.autotune.dsp

import kotlin.test.Test
import kotlin.test.assertTrue

class CapBoostTest {
    @Test
    fun overlappingBoostsAreCappedAndCutsKept() {
        val grid = FreqGrid()
        val f = listOf(EqFilter(100.0, 3.0, 1.0), EqFilter(120.0, 3.0, 1.0), EqFilter(140.0, 3.0, 1.0), EqFilter(1000.0, -6.0, 1.0))
        val raw = grid.freqs.maxOf { fr -> f.sumOf { it.magnitudeDb(fr) } }
        assertTrue(raw > 6.0)
        val capped = EqChain.capBoost(f)
        val peak = grid.freqs.maxOf { fr -> capped.sumOf { it.magnitudeDb(fr) } }
        assertTrue(peak <= 4.1, "peak $peak")
        assertTrue(capped.last().gainDb == -6.0)
        assertTrue(EqChain.withSafePreamp(capped).preampDb >= -4.5)
    }
}
