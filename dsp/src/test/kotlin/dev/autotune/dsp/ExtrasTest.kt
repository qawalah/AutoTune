package dev.autotune.dsp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtrasTest {
    private val fs = 48000

    @Test
    fun combinedRecordingGivesSweepResponseAndAgreesWithPinkNoise() {
        val room = SimRoom(fs)
        val cal = Calibration.parse(room.calibrationFile())
        val played = room.record(TestSignals.combinedFile(fs))
        val rnd = java.util.Random(3)
        val lead = FloatArray(7 * fs) { (rnd.nextGaussian() * 2e-4).toFloat() }
        val rec = FloatArray(lead.size + played.size + 4 * fs)
        System.arraycopy(lead, 0, rec, 0, lead.size)
        System.arraycopy(played, 0, rec, lead.size, played.size)
        val r = CombinedAnalyzer.analyze(rec, fs, cal, gateMs = 500.0)
        assertNull(r.measurement.fatal)
        val d = assertNotNull(r.agreementRmsDb)
        println("combined: sweep vs pink agreement = %.2f dB".format(d))
        assertTrue(d < 3.0, "agreement $d")
    }

    @Test
    fun averagingTwoPositionsIsBetweenThem() {
        val g = FreqGrid()
        fun m(level: Double) = Measurement(g, DoubleArray(g.size) { level }, DoubleArray(0), 0, fs, 0.0, 40.0, emptyList())
        val avg = Averaging.average(listOf(m(0.0), m(-6.0)))
        assertTrue(avg.magDb[10] in -3.0..-1.0, "avg ${avg.magDb[10]}")
    }

    @Test
    fun leftRightFindsLevelAndDelay() {
        val g = FreqGrid()
        fun m(level: Double, peak: Int) = Measurement(g, DoubleArray(g.size) { level }, DoubleArray(100), peak, fs, 0.0, 40.0, emptyList())
        val r = LeftRight.analyze(m(0.0, 10), m(3.0, 58), delayValid = true)
        assertEquals(3.0, r.levelDiffDb, 0.1)
        assertEquals(1.0, r.delayMs!!, 0.01)
    }

    @Test
    fun subHelperFindsCrossoverAndPolarity() {
        val g = FreqGrid()
        fun cur(f: (Double) -> Double) = Measurement(g, DoubleArray(g.size) { f(g.freqs[it]) }, DoubleArray(0), 0, fs, 0.0, 40.0, emptyList())
        val mains = cur { f -> if (f < 80) -12.0 * Math.log(80.0 / f) / Math.log(2.0) else 0.0 }
        val sub = cur { f -> if (f > 80) -12.0 * Math.log(f / 80.0) / Math.log(2.0) else 0.0 }
        val sumN = cur { -8.0 }
        val sumI = cur { 0.0 }
        val r = SubHelper.analyze(mains, sub, sumN, sumI, Target.FLAT)
        assertNotNull(r.crossoverHz)
        assertTrue(r.crossoverHz!! in 70.0..90.0)
        assertEquals(true, r.invertPolarity)
    }

    @Test
    fun judgeScoresFlatHighAndRoughLow() {
        val g = FreqGrid()
        val flat = DoubleArray(g.size)
        val good = SystemJudge.judge(flat, g, Target.FLAT, emptyList())
        assertTrue(good.overall >= 90, "flat ${good.overall}")
        val rough = DoubleArray(g.size) { i -> val f = g.freqs[i]; (if (f < 100) -15.0 else 0.0) + (if (f in 600.0..800.0) 10.0 else 0.0) + (if (f > 6000) -12.0 else 0.0) }
        val bad = SystemJudge.judge(rough, g, Target.FLAT, emptyList())
        println("judge: flat ${good.overall}, rough ${bad.overall}, potential ${bad.potential}: ${bad.verdict}")
        assertTrue(bad.overall < good.overall - 20)
        assertTrue(bad.potential >= bad.overall)
    }

    @Test
    fun headroomWarnsOnClipAndBigPreamp() {
        val chain = EqChain.withSafePreamp(listOf(EqFilter(50.0, 9.0, 1.0)))
        val r = Headroom.check(chain)
        assertTrue(r.peakDb <= 0.0)
        assertTrue(r.warnings.any { it.contains("boosts by") })
    }

    @Test
    fun targetCurveParsesCommonFormats() {
        val t = Target.parse("mine", "# house\nFreq(Hz), SPL(dB)\n20, 6\n100;3\n1000\t0\n5000 -1\n20000,-4\n")
        assertNotNull(t)
        assertEquals(5, t.points.size)
        assertEquals(3.0, t.dbAt(100.0), 1e-9)
        assertNull(Target.parse("bad", "1,2\n3,4"))
    }
}
