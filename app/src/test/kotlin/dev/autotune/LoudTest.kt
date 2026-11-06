package dev.autotune

import dev.autotune.dsp.EqChain
import dev.autotune.dsp.EqFilter
import dev.autotune.dsp.FreqGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The loud EQ: cuts from distortion, how it follows the volume, and the volume-key switch. */
class LoudTest {
    private val g = FreqGrid()

    @Test
    fun cutsOnlyWhereItDistorts() {
        // 1% everywhere, 20% around 63 Hz (woofer out of travel), 6% around 4 kHz (harsh tweeter)
        val thd = DoubleArray(g.size) { i ->
            val f = g.freqs[i]
            when {
                f in 45.0..89.0 -> 20.0
                f in 2900.0..5600.0 -> 6.0
                else -> 1.0
            }
        }
        val cuts = loudCutsFromDistortion(g.freqs, thd)
        assertEquals(listOf(63.0, 4000.0), cuts.map { it.freqHz })
        assertTrue(cuts.all { it.gainDb < 0.0 && it.gainDb >= -6.0 })
        // 20% against a 6% limit is 10.5 dB over: cut 2/3 of it, then capped at 6
        assertEquals(-6.0, cuts[0].gainDb, 0.0)
        // 6% against 2% is 9.5 dB over: 2/3 of it is 6.4, capped at 6
        assertEquals(-6.0, cuts[1].gainDb, 0.0)
        // a clean system gets no cuts
        assertTrue(loudCutsFromDistortion(g.freqs, DoubleArray(g.size) { 0.5 }).isEmpty())
        // mismatched input is ignored
        assertTrue(loudCutsFromDistortion(g.freqs, DoubleArray(3)).isEmpty())
    }

    @Test
    fun deltaOnlyCutsAndTakesBackBoosts() {
        val play = listOf(EqFilter(50.0, 5.0, 0.7), EqFilter(1000.0, 3.0, 1.0), EqFilter(10000.0, 4.0, 0.8), EqFilter(300.0, -4.0, 2.0))
        val d = loudDelta(play, emptyList())
        assertTrue(d.all { it.gainDb < 0.0 })
        // the bass and treble boosts are taken back by 60%; the mid boost and the cut are left alone
        assertEquals(listOf(50.0, 10000.0), d.map { it.freqHz })
        assertEquals(-3.0, d[0].gainDb, 1e-9)
        assertEquals(-2.4, d[1].gainDb, 1e-9)
        // no reading: the safe default cuts are used
        val def = loudDelta(emptyList(), null)
        assertEquals(LOUD_DEFAULT_CUTS.map { it.freqHz }, def.map { it.freqHz })
        // cuts are kept within 6 dB
        assertEquals(-6.0, loudDelta(emptyList(), listOf(EqFilter(60.0, -12.0, 1.0)))[0].gainDb, 0.0)
    }

    @Test
    fun amountFollowsVolume() {
        assertEquals(0.0, loudAmount(0.5, 0.7), 0.0)
        assertEquals(0.0, loudAmount(0.7, 0.7), 0.0)
        assertEquals(1.0, loudAmount(1.0, 0.7), 1e-9)
        assertEquals(0.5, loudAmount(0.85, 0.7), 1e-9)
        // it only grows as the volume goes up
        var last = 0.0
        for (v in 70..100) { val a = loudAmount(v / 100.0, 0.7); assertTrue(a >= last); last = a }
    }

    @Test
    fun loudKeepsPreampAndNeverAddsLevel() {
        val base = playChain(listOf(EqFilter(60.0, 6.0, 0.7)), Flavour.NONE, PreampMode.SAFE, 0.0, g)
        val delta = loudDelta(base.filters, null)
        val loud = withLoud(base, delta, 1.0)
        assertEquals(base.preampDb, loud.preampDb, 0.0)
        for (f in g.freqs) assertTrue("at $f", loud.responseDb(f) <= base.responseDb(f) + 1e-9)
        // half way is half the change at the cut's centre
        val half = withLoud(base, delta, 0.5)
        val full = loud.responseDb(40.0) - base.responseDb(40.0)
        assertEquals(full / 2, half.responseDb(40.0) - base.responseDb(40.0), 0.3)
        // none: the same chain
        assertTrue(withLoud(base, delta, 0.0) === base)
    }

    @Test
    fun filtersText() {
        val f = listOf(EqFilter(63.0, -4.5, 1.4), EqFilter(4000.0, -2.0, 1.4))
        assertEquals(f, filtersFromText(filtersToText(f)))
        assertEquals(emptyList<EqFilter>(), filtersFromText(""))
        assertNull(filtersFromText(null))
        assertNull(filtersFromText("nonsense"))
    }

    @Test
    fun volumeKeyPattern() {
        val g = VolumeGesture(2500)
        assertFalse(g.step(0, -1)); assertFalse(g.step(300, 1)); assertFalse(g.step(600, -1))
        assertTrue(g.step(900, 1))
        // starts over after a match
        assertFalse(g.step(1200, -1))
        // holding a key (same direction) never counts
        val h = VolumeGesture(2500)
        assertFalse((0 until 10).any { h.step(it * 100L, 1) })
        // too slow
        val s = VolumeGesture(2500)
        assertFalse(s.step(0, 1)); assertFalse(s.step(1000, -1)); assertFalse(s.step(2000, 1)); assertFalse(s.step(3000, -1))
        // a held press then the pattern still works
        val m = VolumeGesture(2500)
        m.step(0, 1); m.step(100, 1); m.step(200, 1)
        assertFalse(m.step(500, -1)); assertFalse(m.step(800, 1))
        assertTrue(m.step(1100, -1))
    }

    @Test
    fun volumeCheck() {
        assertTrue(volumeFollows(listOf(5, 6, 7, 6)))
        assertFalse(volumeFollows(listOf(5, 5, 5)))
        assertFalse(volumeFollows(listOf(5, 6)))
        assertFalse(volumeFollows(emptyList()))
    }

    @Test
    fun modesRead() {
        assertEquals(LoudMode.OFF, LoudMode.of(null))
        assertEquals(LoudMode.VOLUME, LoudMode.of("VOLUME"))
        assertTrue(EqChain(emptyList()).filters.isEmpty())
    }
}
