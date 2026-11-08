package dev.autotune.dsp

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The REW-style additions to the fitter: fine-tuning all filters together, variable smoothing, Q caps. */
class RefineTest {
    private val g = FreqGrid()
    private val old = FitConfig(refine = false, variableSmoothing = false, maxCutQTop = 4.0, maxBoostRingS = 1e9)

    private fun shaped(vararg f: EqFilter) = DoubleArray(g.size) { i -> f.sumOf { it.magnitudeDb(g.freqs[i]) } }
    private fun after(mag: DoubleArray, fl: List<EqFilter>) = DoubleArray(g.size) { i -> mag[i] + fl.sumOf { it.magnitudeDb(g.freqs[i]) } }
    private fun score(mag: DoubleArray, fl: List<EqFilter>, t: Target = Target.FLAT) = EqFitter.score(after(mag, fl), g, t).first

    /** Two room modes close together: placed one at a time, the second filter is fitted to the first one's skirt. */
    private val twoModes = shaped(EqFilter(58.0, 9.0, 6.0), EqFilter(74.0, 7.0, 5.0), EqFilter(140.0, 5.0, 3.0), EqFilter(2500.0, 4.0, 2.0))

    @Test
    fun capsFollowREW() {
        val c = FitConfig()
        assertEquals(8.0, c.cutQCap(500.0), 1e-9)
        assertEquals(4.0, c.cutQCap(1000.0), 1e-9)
        assertEquals(3.0, c.cutQCap(10000.0), 1e-9)
        assertTrue(c.cutQCap(3000.0) in 3.0..4.0)
        assertEquals(2.0, FitConfig(maxCutQ = 2.0, maxBoostQ = 1.4).cutQCap(5000.0), 1e-9) // the phone's system EQ caps stay
        assertEquals(2.5, c.boostQCap(30.0, 3.0), 1e-9) // the usual cap is already safe at 30 Hz
        assertTrue(c.boostQCap(5.0, 6.0) < 1.0) // a boost low enough to ring for half a second is held wider
        assertEquals(48.0, FreqGrid.variableFraction(60.0), 1e-9)
        assertEquals(6.0, FreqGrid.variableFraction(1000.0), 1e-9)
        assertEquals(3.0, FreqGrid.variableFraction(15000.0), 1e-9)
    }

    @Test
    fun fineTuningNeverMakesTheFitWorse() {
        for (mag in listOf(twoModes, shaped(EqFilter(40.0, -9.0, 1.0), EqFilter(300.0, 6.0, 4.0), EqFilter(6000.0, -5.0, 1.0)))) {
            for (cfg in listOf(FitConfig(), FitConfig(maxCutQ = 2.0, maxBoostQ = 1.4))) {
                val without = EqFitter.fit(mag, g, Target.FLAT, cfg.copy(refine = false))
                val with = EqFitter.fit(mag, g, Target.FLAT, cfg)
                assertTrue(with.rmsAfter <= without.rmsAfter + 1e-9, "refined ${with.rmsAfter} vs ${without.rmsAfter}")
            }
        }
    }

    @Test
    fun closeModesAreMatchedBetter() {
        val before = score(twoModes, EqFitter.fit(twoModes, g, Target.FLAT, old).filters)
        val now = score(twoModes, EqFitter.fit(twoModes, g, Target.FLAT).filters)
        println("two close modes: old fitter %.2f dB rms, new %.2f".format(before, now))
        assertTrue(now < before, "new $now vs old $before")
    }

    @Test
    fun limitsStillHold() {
        val mag = shaped(EqFilter(36.0, -10.0, 1.2), EqFilter(55.0, 8.0, 7.0), EqFilter(4000.0, 6.0, 6.0), EqFilter(9000.0, -6.0, 2.0))
        for (cfg in listOf(FitConfig(), FitConfig(maxCutQ = 2.0, maxBoostQ = 1.4), FitConfig(boostMinHz = 45.0))) {
            val fl = EqFitter.fit(mag, g, Target.FLAT, cfg).filters
            assertTrue(fl.size <= cfg.maxFilters)
            for (f in fl) {
                assertTrue(f.gainDb in -cfg.maxCutDb..cfg.maxBoostDb, "gain $f")
                assertTrue(f.freqHz in cfg.fMinHz / 1.0001..cfg.fMaxHz * 1.0001, "band $f")
                val cap = if (f.gainDb < 0) cfg.cutQCap(f.freqHz) else cfg.boostQCap(f.freqHz, f.gainDb)
                assertTrue(f.q <= cap + 1e-9, "Q $f over $cap")
                if (f.gainDb > 0) assertTrue(f.freqHz >= cfg.boostMinHz, "boost below the floor: $f")
            }
            // fine-tuning may not push the summed boost past the limit any further than the search already had it
            fun peak(x: List<EqFilter>) = g.freqs.maxOf { fr -> x.sumOf { it.magnitudeDb(fr) } }
            val search = EqFitter.fit(mag, g, Target.FLAT, cfg.copy(refine = false)).filters
            println("total boost: search %.2f, tuned %.2f".format(peak(search), peak(fl)))
            assertTrue(peak(fl) <= maxOf(peak(search), cfg.maxBoostDb) + 0.05, "total boost ${peak(fl)} vs search ${peak(search)}")
        }
    }

    /**
     * A car's upper bass: narrow peaks at 115, 150 and 250 Hz with a dip at 200 Hz between them, fitted for the phone's
     * system EQ (Q <= 2). Wide cuts either side dig into the dip; boosts stacked to refill it used to be merged away
     * afterwards (Export.combine), leaving 200 Hz far under the target. What plays is the merged EQ, so that is checked.
     */
    @Test
    fun aDipBetweenCutsIsNotDugOutAfterMerging() {
        val mag = shaped(
            EqFilter(38.0, 12.0, 1.0), EqFilter(70.0, 10.0, 2.5), EqFilter(115.0, 8.0, 5.0), EqFilter(150.0, 9.0, 4.0),
            EqFilter(200.0, -6.0, 4.0), EqFilter(250.0, 10.0, 5.0), EqFilter(1250.0, 3.0, 3.0),
        )
        val sys = FitConfig(maxCutQ = 2.0, maxBoostQ = 1.4)
        val t = Target.HARMAN_CAR
        val played = Export.combine(EqFitter.fit(mag, g, t, sys).filters, sys)
        val s = g.smoothOctave(mag, 6.0)
        val off = EqFitter.offsetDb(mag, g, t)
        fun errAfter(f: Double): Double { val i = g.indexAtOrAbove(f); return s[i] - off - t.dbAt(f) + played.sumOf { it.magnitudeDb(f) } }
        val upper = g.freqs.indices.filter { g.freqs[it] in 120.0..250.0 }.map { errAfter(g.freqs[it]) }.average()
        println("dip between cuts: 200 Hz %+.1f dB, 120-250 Hz %+.1f dB after the merged EQ: %s".format(errAfter(200.0), upper, played))
        assertTrue(upper > -2.5, "upper bass hollowed out: $upper dB")
        assertTrue(errAfter(200.0) > -8.0, "200 Hz dug to ${errAfter(200.0)} dB")
        // the fit already is what plays: merging it again changes nothing
        assertEquals(played.size, Export.combine(played, sys).size)
    }

    @Test
    fun aPartialStepStaysPartial() {
        val half = EqFitter.fit(twoModes, g, Target.FLAT, FitConfig(), stepScale = 0.5).filters
        val plain = EqFitter.fit(twoModes, g, Target.FLAT, FitConfig(refine = false), stepScale = 0.5).filters
        assertEquals(plain, half)
    }

    /** Before/after on the simulated rooms the loop tests use. Prints only; the asserts above carry the rules. */
    @Test
    fun benchmark() {
        val fs = 48000
        val sweep = Sweep.generate(fs, 6.0)
        val rooms = listOf(
            "room" to SimRoom(fs),
            "car comb" to SimRoom(fs, listOf(40 to 0.35, 90 to 0.3, 170 to 0.25, 260 to 0.2)),
        )
        val variants = listOf(
            "old" to old,
            "+refine" to old.copy(refine = true),
            "+variable" to old.copy(variableSmoothing = true),
            "+caps" to old.copy(maxCutQTop = 3.0, maxBoostRingS = 0.5),
            "new" to FitConfig(),
            "new+variable" to FitConfig(variableSmoothing = true),
        )
        for ((name, room) in rooms) {
            val m = Analyzer.analyze(room.record(sweep), sweep, fs, null, gateMs = 500.0)
            for (t in listOf(Target.HOME_THEATER, Target.CAR)) {
                for ((route, base) in listOf("app" to FitConfig(), "systemEq" to FitConfig(maxCutQ = 2.0, maxBoostQ = 1.4))) {
                    val line = variants.joinToString("  ") { (vn, v) ->
                        val cfg = v.copy(maxCutQ = base.maxCutQ, maxBoostQ = base.maxBoostQ)
                        val fl = EqChain.capBoost(EqFitter.fit(m.magDb, m.grid, t, cfg).filters)
                        "%s %.2f".format(vn, EqFitter.score(after(m.magDb, fl), g, t).first)
                    }
                    println("bench %-8s %-12s %-8s stock %.2f | %s".format(name, t.name.take(12), route, EqFitter.score(m.magDb, g, t).first, line))
                }
            }
        }
        val synth = (1..20).map { seed ->
            val r = java.util.Random(seed.toLong())
            val fl = (0 until 7).map {
                val f = 25.0 * Math.pow(2.0, r.nextDouble() * 8.5)
                EqFilter(f, (r.nextDouble() * 16 - 7), 0.7 + r.nextDouble() * (if (f < 300) 9.0 else 3.0))
            }
            DoubleArray(g.size) { i -> fl.sumOf { it.magnitudeDb(g.freqs[i]) } + r.nextGaussian() * 0.3 }
        }
        for ((vn, v) in variants) {
            val avg = synth.map { score(it, EqChain.capBoost(EqFitter.fit(it, g, Target.FLAT, v).filters)) }.average()
            println("bench synthetic(20) %-10s mean rms %.3f".format(vn, avg))
        }
        assertTrue(abs(score(twoModes, emptyList())) > 0)
    }
}
