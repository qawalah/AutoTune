package dev.autotune.dsp

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

class DistortionTest {
    private val fs = 48000

    private fun record(a2: Double, a3: Double, reflectMs: Double = 0.0, reflect: Double = 0.0): Pair<FloatArray, FloatArray> {
        val sweep = Sweep.generate(fs, 10.0)
        val delay = 3000
        val rec = FloatArray(sweep.size + 4 * fs)
        val lag = (reflectMs * fs / 1000).toInt()
        for (i in sweep.indices) {
            val x = sweep[i].toDouble()
            val y = (x + a2 * x * x + a3 * x * x * x).toFloat()
            rec[delay + fs + i] += y
            if (lag > 0) rec[delay + fs + i + lag] += (reflect * y).toFloat()
        }
        return rec to sweep
    }

    @Test
    fun secondAndThirdHarmonicLevelsAreRead() {
        val (rec, sweep) = record(0.2, 0.0)
        val d = Analyzer.analyze(rec, sweep, fs, null, gateMs = 80.0).distortion!!
        val i = d.freqs.indexOfFirst { it >= 500.0 }
        assertTrue(abs(d.hd2Db[i] - 20 * log10(0.2 * 0.5 / 2)) < 1.0, "HD2 ${d.hd2Db[i]}")
        assertTrue(d.hd3Db[i] < -60.0)
        val (rec3, sweep3) = record(0.0, 0.4)
        val d3 = Analyzer.analyze(rec3, sweep3, fs, null, gateMs = 80.0).distortion!!
        assertTrue(abs(d3.hd3Db[i] - 20 * log10(0.4 * 0.25 * 0.25)) < 1.5, "HD3 ${d3.hd3Db[i]}")
        assertTrue(d.thdPct[i] in 4.0..6.0, "THD ${d.thdPct[i]}")
    }

    @Test
    fun cleanSystemReadsAsAlmostNoDistortion() {
        val (rec, sweep) = record(0.0, 0.0)
        val d = Analyzer.analyze(rec, sweep, fs, null, gateMs = 80.0).distortion!!
        assertTrue(d.averagePct(100.0, 5000.0) < 0.1, "THD ${d.averagePct(100.0, 5000.0)}")
    }

    @Test
    fun frequencyDependentWindowCalmsReflectionRippleInTheTreble() {
        val (rec, sweep) = record(0.0, 0.0, reflectMs = 5.0, reflect = 0.7)
        fun ripple(m: Measurement): Double {
            val v = m.grid.freqs.indices.filter { m.grid.freqs[it] in 2000.0..8000.0 }.map { m.magDb[it] }
            val mean = v.average()
            return sqrt(v.sumOf { (it - mean) * (it - mean) } / v.size)
        }
        val fixed = Analyzer.analyze(rec, sweep, fs, null, gateMs = 80.0)
        val fdw = Analyzer.analyze(rec, sweep, fs, null, gateMs = 80.0, fdwCycles = 6.0)
        assertTrue(ripple(fdw) < ripple(fixed) * 0.5, "fdw ${ripple(fdw)} fixed ${ripple(fixed)}")
    }
}
