package dev.autotune.dsp

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Android's DynamicsProcessing applies the EQ per FFT bin; the default 10 ms frame lumped the whole bass into two bins. */
class DpBinsTest {
    private val fs = 48000
    private val fine = DoubleArray(61) { 20.0 * Math.pow(2.0, it / 6.0) }.filter { it <= 20000.0 }.toDoubleArray()
    private val fineEdges = DoubleArray(fine.size) { minOf(fine[it] * Math.pow(2.0, 1.0 / 12.0), 21000.0) }
    private val coarseEdges = DoubleArray(Rta.CENTERS.size) { minOf(Rta.CENTERS[it] * Rta.EDGE, 21000.0) }
    /** The F-150's Run 4 EQ (11 Nov 2026), preamp -3.3 dB. */
    private val run4 = EqChain(
        listOf(
            EqFilter(30.4, 2.5, 2.22), EqFilter(46.5, -7.48, 0.86), EqFilter(204.5, 3.0, 2.5), EqFilter(246.8, -6.11, 4.0),
            EqFilter(472.6, 3.0, 2.5), EqFilter(652.8, -8.51, 2.75), EqFilter(1483.0, 3.0, 2.5), EqFilter(9476.0, 3.0, 0.88),
        ),
        -3.3,
    )

    @Test
    fun blockSizes() {
        assertEquals(4096, DpBins.block(48000))
        assertEquals(4096, DpBins.block(44100))
        assertEquals(512, DpBins.block(48000, 10f), "Android's default frame")
        assertEquals(8, DpBins.block(8000, 0.1f))
        assertEquals(16384, DpBins.block(192000, 200f))
    }

    @Test
    fun theDefaultFrameGaveTheWholeBassToTheFortyFiveHertzBand() {
        val own = DpBins.owners(fineEdges, 512, fs)
        val at45 = fine.indexOfFirst { abs(it - 44.9) < 0.1 }
        assertEquals(1..1, own[at45], "the 45 Hz band sets the bin at 94 Hz")
        // no band between 50 and 140 Hz sets anything
        assertTrue(fine.indices.filter { fine[it] in 50.0..135.0 }.all { own[it] == null })
    }

    @Test
    fun fourThousandSamplesGiveTheBassItsBands() {
        val coarse = DpBins.owners(coarseEdges, 4096, fs)
        assertTrue(Rta.CENTERS.indices.filter { Rta.CENTERS[it] >= 50.0 }.all { coarse[it] != null }, "every 31-band band from 50 Hz")
        val f = DpBins.owners(fineEdges, 4096, fs)
        assertTrue(fine.indices.filter { fine[it] >= 100.0 }.all { f[it] != null }, "every 61-band band from 100 Hz")
        assertNotNull(f.last())
        assertNull(DpBins.owners(doubleArrayOf(10.0, 11.0), 4096, fs)[1], "a band inside the bin before it sets nothing")
    }

    private fun worstError(block: Int, lo: Double, hi: Double): Double {
        val g = DpBins.bandGains(fineEdges, fine, block, fs) { run4.responseDb(it) }
        val bins = DpBins.binGains(fineEdges, g, block, fs)
        val f = FreqGrid().freqs.filter { it in lo..hi }
        val worst = f.maxBy { abs(DpBins.realisedDb(bins, block, fs, it) - run4.responseDb(it)) }
        return abs(DpBins.realisedDb(bins, block, fs, worst) - run4.responseDb(worst))
    }

    @Test
    fun theBassIsAppliedAsWantedWithLongerBlocks() {
        assertTrue(worstError(4096, 50.0, 200.0) < 1.0, "4096 bass: worst ${worstError(4096, 50.0, 200.0)} dB")
        // elsewhere one gain per band is the limit (a steep cut inside a 1/6-octave band), not the bins
        assertTrue(worstError(4096, 50.0, 16000.0) < 2.0, "4096: worst ${worstError(4096, 50.0, 16000.0)} dB")
        assertTrue(worstError(512, 50.0, 200.0) > 2.0, "512 was off in the bass: worst ${worstError(512, 50.0, 200.0)} dB")
    }

    @Test
    fun realisedIsInterpolatedBetweenBins() {
        val bins = doubleArrayOf(0.0, -6.0, 0.0)
        assertEquals(-3.0, DpBins.realisedDb(bins, 4, 4, 0.5), 1e-9)
        assertEquals(-6.0, DpBins.realisedDb(bins, 4, 4, 1.0), 1e-9)
        assertEquals(0.0, DpBins.realisedDb(bins, 4, 4, 100.0), 1e-9)
    }
}
