package dev.autotune.dsp

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The loop the app runs: stock response + what was applied + what was measured -> a complete EQ. */
class ClosedLoopTest {
    private val fs = 48000
    private val sweep = Sweep.generate(fs, 6.0)
    private val target = Target.HOME_THEATER
    private val cfg = FitConfig()

    private class Round(val rms: Double, val filters: List<EqFilter>, val ineffective: Boolean)

    private fun run(room: SimRoom, rounds: Int, applyReachesMic: Boolean, roomFor: (Int) -> SimRoom = { room }): List<Round> {
        val stock = Analyzer.analyze(room.record(sweep), sweep, fs, null, gateMs = 500.0)
        var applied: List<EqFilter> = emptyList()
        val out = ArrayList<Round>()
        for (r in 0 until rounds) {
            val played = sweep.copyOf()
            if (applyReachesMic) EqProcessor(EqChain.withSafePreamp(applied), fs).process(played)
            val m = Analyzer.analyze(roomFor(r).record(played, seed = r.toLong()), sweep, fs, null, gateMs = 500.0)
            val res = EqFitter.fitFromStock(stock.magDb, applied, m.magDb, m.grid, target, cfg)
            applied = Export.combine(res.fit.filters, cfg)
            out += Round(EqFitter.score(m.magDb, m.grid, target, cfg).first, applied, res.ineffective)
        }
        return out
    }

    @Test
    fun appliedInTheRoomConvergesAndStaysWithinLimits() {
        val rounds = run(SimRoom(fs), 5, applyReachesMic = true)
        rounds.forEachIndexed { i, r -> println("round $i rms %.2f bands %d".format(r.rms, r.filters.size)) }
        for (i in 2 until rounds.size) assertTrue(rounds[i].rms <= rounds[i - 1].rms + 0.15, "round $i rms ${rounds[i].rms} after ${rounds[i - 1].rms}")
        assertTrue(rounds.last().rms < rounds.first().rms * 0.8 || rounds.last().rms < 1.5, "no real improvement: ${rounds.map { it.rms }}")
        rounds.forEach { r ->
            assertTrue(r.filters.size <= cfg.maxFilters)
            assertTrue(r.filters.all { it.gainDb >= -cfg.maxCutDb - 1e-9 && it.gainDb <= cfg.maxBoostDb + 1e-9 })
        }
    }

    @Test
    fun anEqThatNeverReachesTheMicDoesNotDoubleTheCorrection() {
        val rounds = run(SimRoom(fs), 4, applyReachesMic = false)
        fun deepest(r: Round) = r.filters.minOf { it.gainDb }
        println("deepest cuts: " + rounds.map { "%.1f".format(deepest(it)) })
        assertTrue(abs(deepest(rounds[3]) - deepest(rounds[0])) < 1.0, "cut grew from ${deepest(rounds[0])} to ${deepest(rounds[3])}")
        assertTrue(rounds.drop(1).all { it.ineffective })
    }

    @Test
    fun aMovingMicDoesNotGrowTheEq() {
        val a = SimRoom(fs, listOf(40 to 0.35, 90 to 0.3, 170 to 0.25, 260 to 0.2))
        val b = SimRoom(fs, listOf(46 to 0.35, 96 to 0.3, 176 to 0.25, 266 to 0.2)) // each reflection 6 samples later
        val rounds = run(a, 4, applyReachesMic = true, roomFor = { if (it % 2 == 0) a else b })
        rounds.forEachIndexed { i, r -> println("comb round $i rms %.2f bands %d".format(r.rms, r.filters.size)) }
        assertTrue(rounds.last().filters.size <= 12, "bands: ${rounds.map { it.filters.size }}")
        val stockRms = EqFitter.score(Analyzer.analyze(a.record(sweep), sweep, fs, null, gateMs = 500.0).let { it.magDb }, FreqGrid(), target, cfg).first
        assertTrue(rounds.last().rms < stockRms, "rms ${rounds.last().rms} vs stock $stockRms")
    }

    @Test
    fun systemEqSamplingKeepsWhatABandCanRealise() {
        val chain = EqChain(listOf(EqFilter(2994.0, -4.3, 6.9), EqFilter(62.0, -5.6, 6.3), EqFilter(120.0, 3.0, 2.0)))
        val s = chain.sampledTo()
        val sampled = EqChain(s)
        for (c in listOf(62.5, 125.0, 3150.0)) {
            val want = chain.filters.sumOf { it.magnitudeDb(c) }
            assertEquals(want, sampled.responseDb(c), 0.5, "at $c Hz")
        }
        assertTrue(s.size <= 31)
    }

    @Test
    fun clockDriftIsEstimatedFromTheHarmonicCopies() {
        val seconds = 10.0
        val sw = Sweep.generate(fs, seconds)
        for (ppm in listOf(0.0, 100.0, -150.0)) {
            val d = ppm * 1e-6
            val len = (sw.size * (1 + d)).toInt()
            val rec = FloatArray(len + 5 * fs)
            for (i in 0 until len) {
                val pos = i / (1 + d)
                val i0 = pos.toInt().coerceIn(0, sw.size - 2)
                val fr = pos - i0
                val x = sw[i0] * (1 - fr) + sw[i0 + 1] * fr
                val xx = x.toDouble()
                rec[fs + 4000 + i] = (xx + 0.15 * xx * xx + 0.1 * xx * xx * xx).toFloat()
            }
            val m = Analyzer.analyze(rec, sw, fs, null, gateMs = 80.0)
            val est = assertNotNull(m.driftPpm, "drift for $ppm ppm")
            assertTrue(abs(est - ppm) < 30.0, "estimated $est for $ppm ppm")
        }
    }
}
