package dev.autotune.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RtaTest {
    private val fs = 48000

    @Test
    fun pinkNoiseGivesFlatBars() {
        val an = RtaAnalyzer(fs)
        val noise = Sweep.pinkNoise(fs, 12.0)
        for (i in noise.indices step 2048) an.push(noise.copyOfRange(i, minOf(i + 2048, noise.size)), minOf(2048, noise.size - i))
        val bars = an.bandsDb()
        val ref = bars[Rta.CENTERS.toList().indexOf(1000.0)]
        for (i in Rta.CENTERS.indices) {
            if (Rta.CENTERS[i] in 100.0..10000.0) {
                assertTrue(abs(bars[i] - ref) < 3.0, "band ${Rta.CENTERS[i]} off by ${bars[i] - ref} dB")
            }
        }
    }

    @Test
    fun sineLightsTheRightBar() {
        val an = RtaAnalyzer(fs)
        val x = FloatArray(fs * 2) { sin(2.0 * PI * 1000.0 * it / fs).toFloat() }
        an.push(x, x.size)
        val bars = an.bandsDb()
        val top = bars.indices.maxByOrNull { bars[it] }!!
        assertEquals(1000.0, Rta.CENTERS[top])
    }

    @Test
    fun flatResponseGivesFlatBarsAndOnTargetRegions() {
        val grid = FreqGrid()
        val flat = DoubleArray(grid.size)
        val bars = Rta.barsFromResponse(flat, grid)
        assertTrue(bars.all { abs(it) < 0.01 })
        val regions = Describe.regions(flat, grid, Target.FLAT)
        assertTrue(regions.all { it.verdict == "on target" })
    }

    @Test
    fun descriptionsAreShortAndSensible() {
        assertTrue(Describe.filter(EqFilter(62.0, -5.0, 6.0)).contains("boomy bass"))
        assertTrue(Describe.filter(EqFilter(62.0, -5.0, 6.0)).contains("resonance"))
        assertTrue(Describe.filter(EqFilter(3000.0, 2.0, 1.0)).startsWith("Restores"))
    }
}
