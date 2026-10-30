package dev.autotune

import dev.autotune.dsp.EqChain
import dev.autotune.dsp.EqFilter
import dev.autotune.dsp.FreqGrid
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TuningLogicTest {
    private fun run(label: String, rms: Double, filters: List<EqFilter> = emptyList(), applied: List<EqFilter> = emptyList(), complete: Boolean = true, cal: String = "none") =
        SavedRun(1L, rms, DoubleArray(4), filters, label, applied = applied, calibration = cal, complete = complete)

    /** Three boosts that overlap: the EQ shown is capped, and exactly that list must be what the next run records as applied. */
    @Test
    fun appliedIsExactlyWhatWasShown() {
        val raw = listOf(EqFilter(900.0, 3.0, 1.0), EqFilter(1000.0, 3.0, 1.0), EqFilter(1120.0, 3.0, 1.0), EqFilter(63.0, -5.0, 4.0))
        val prev = run("Run 1", 4.0, raw)
        val shown = suggestedFor(prev)
        val grid = FreqGrid()
        val peak = grid.freqs.maxOf { f -> shown.filters.sumOf { it.magnitudeDb(f) } }
        assertTrue("shown boost $peak dB must be capped", peak <= 4.05)
        val applied = appliedFor(ApplyWay.EQUIPMENT, prev, inEffectAfter(prev, null, emptyList()), null, null)
        assertEquals(shown.filters, applied)
        // the raw fit would have recorded the full 9 dB stack; the old behaviour
        val rawPeak = grid.freqs.maxOf { f -> raw.sumOf { it.magnitudeDb(f) } }
        assertTrue(rawPeak > peak + 2.0)
    }

    @Test
    fun sliderChangesAreAppliedToo() {
        val prev = run("Run 1", 4.0, listOf(EqFilter(63.0, -4.0, 3.0)))
        val layer = listOf(EqFilter(4000.0, 1.5, 1.4))
        val shown = inEffectAfter(prev, null, layer)
        assertEquals(2, shown.filters.size)
        assertEquals(shown.filters, appliedFor(ApplyWay.EQUIPMENT, prev, shown, null, null))
    }

    @Test
    fun appliedFollowsTheRoute() {
        val prev = run("Run 1", 3.0, listOf(EqFilter(63.0, -4.0, 3.0)), applied = listOf(EqFilter(125.0, -2.0, 2.0)))
        val shown = suggestedFor(prev)
        val inApp = EqChain(listOf(EqFilter(1000.0, 1.0, 1.0)), 0.0)
        val bands = listOf(EqFilter(3150.0, -1.3, 4.32))
        assertEquals(inApp.filters, appliedFor(ApplyWay.IN_APP, prev, shown, inApp, null))
        assertEquals(bands, appliedFor(ApplyWay.AUTOTUNE_EQ, prev, shown, null, bands))
        assertEquals(prev.applied, appliedFor(ApplyWay.NONE, prev, shown, null, null))
    }

    @Test
    fun runsSavedBeforeTheClosedLoopStillMakeTheSameSuggestion() {
        val old = run("Run 2", 3.0, filters = listOf(EqFilter(1000.0, -2.0, 2.0)), applied = listOf(EqFilter(1000.0, -3.0, 2.0)), complete = false)
        val s = suggestedFor(old)
        assertEquals(1, s.filters.size)
        assertEquals(-5.0, s.filters[0].gainDb, 0.01)
    }

    @Test
    fun aWorseRunGoesBackToTheBestEq() {
        val best = listOf(EqFilter(63.0, -4.0, 3.0))
        val prior = listOf(run("Stock (no EQ)", 6.0), run("Run 1", 3.0, applied = best), run("Run 2", 3.4, applied = best + EqFilter(2000.0, 2.0, 1.0)))
        val n = nextSuggestion(prior, 4.2, "Run 3", listOf(EqFilter(1.0, 1.0, 1.0)), { error("not needed") })
        assertEquals(best, n.filters)
        assertTrue(n.note!!.contains("Run 1"))
    }

    @Test
    fun aSlightlyWorseOrBetterRunKeepsItsOwnFit() {
        val prior = listOf(run("Stock (no EQ)", 6.0), run("Run 1", 3.0, applied = listOf(EqFilter(63.0, -4.0, 3.0))))
        val fit = listOf(EqFilter(100.0, -1.0, 2.0))
        assertEquals(fit, nextSuggestion(prior, 3.2, "Run 2", fit, { error("no") }).filters)
        assertEquals(fit, nextSuggestion(prior, 2.0, "Run 2", fit, { error("no") }).filters)
        assertNull(nextSuggestion(prior, 2.0, "Run 2", fit, { error("no") }).note)
    }

    @Test
    fun whenStockWasBestTheNextStepIsGentler() {
        val prior = listOf(run("Stock (no EQ)", 3.0), run("Run 1", 4.0, applied = listOf(EqFilter(63.0, -4.0, 3.0))))
        val gentle = listOf(EqFilter(63.0, -2.0, 3.0))
        val n = nextSuggestion(prior, 4.5, "Run 2", listOf(EqFilter(63.0, -8.0, 3.0)), { gentle })
        assertEquals(gentle, n.filters)
        assertNotNull(n.note)
    }

    @Test
    fun mixedCalibrationsAreFlagged() {
        val a = run("Stock (no EQ)", 5.0, cal = "umik.txt")
        val b = run("Run 1", 4.0, cal = "none")
        val c = run("Run 2", 4.0, cal = UNKNOWN_CAL)
        assertNotNull(mixedCalibrationWarning(listOf(a, b)))
        assertNull(mixedCalibrationWarning(listOf(a, a)))
        assertNull(mixedCalibrationWarning(listOf(a, c)))
        assertTrue(resultWarnings(listOf(a, b), 1).isNotEmpty())
    }

    @Test
    fun aCollapsedTrebleIsRecognisedButAVolumeChangeIsNot() {
        val f = FreqGrid().freqs
        val stock = DoubleArray(f.size) { 0.0 }
        val quieter = DoubleArray(f.size) { -8.0 }
        val noTreble = DoubleArray(f.size) { if (f[it] in 2000.0..8000.0) -20.0 else 0.0 }
        assertFalse(trebleCollapsed(stock, quieter, f))
        assertTrue(trebleCollapsed(stock, noTreble, f))
    }

    /** The loop must stay closed: measureRun fits against the stock response and the EQ really applied, never open loop. */
    @Test
    fun measureRunFitsThroughTheClosedLoopFunction() {
        val src = listOf("src/main/kotlin/dev/autotune/TuneViewModel.kt", "app/src/main/kotlin/dev/autotune/TuneViewModel.kt")
            .map(::File).first { it.exists() }.readText()
        val body = src.substringAfter("fun measureRun()").substringBefore("// ----------------------------------------------------------------")
        assertTrue(body.contains("EqFitter.fitFromStock("))
        assertFalse("measureRun must not fit the run on its own", body.contains("EqFitter.fit(m.magDb"))
        assertFalse(src.contains("TuningSession"))
    }
}

class DiagJsonTest {
    @Test
    fun writesValidLookingJson() {
        val j = Diag.json(mapOf("a" to "x\"y\nz", "n" to 1.5, "nan" to Double.NaN, "l" to listOf(1, 2), "b" to true, "e" to TuneStep.APPLY))
        assertEquals("{\"a\":\"x\\\"y\\nz\",\"n\":1.500,\"nan\":null,\"l\":[1,2],\"b\":true,\"e\":\"APPLY\"}", j)
    }
}

class ConvergenceTest {
    private fun run(label: String, rms: Double, eq: List<EqFilter>, param: List<EqFilter>? = null) =
        SavedRun(1L, rms, DoubleArray(4), emptyList(), label, applied = eq, complete = true, appliedParam = param)

    private val a = listOf(EqFilter(52.0, -12.0, 2.0), EqFilter(185.0, -11.0, 2.0))
    private val b = listOf(EqFilter(50.0, -8.0, 4.0), EqFilter(200.0, -6.0, 4.0))

    /** The swing seen on a real home theatre: B measured best, A worse; the fit after re-measuring B is A again. */
    @Test
    fun aSuggestionThatAlreadyMeasuredWorseIsNotOfferedAgain() {
        val prior = listOf(run("Stock (no EQ)", 7.0, emptyList()), run("Run 1", 5.45, b), run("Run 2", 5.95, a), run("Run 3", 5.5, b))
        val n = nextSuggestion(prior, 5.55, "Run 4", a, { emptyList() }, newEq = b)
        assertTrue(n.converged)
        assertEquals(b, n.filters)
    }

    @Test
    fun aNewIdeaIsStillTried() {
        val prior = listOf(run("Stock (no EQ)", 7.0, emptyList()), run("Run 1", 5.45, b))
        val other = listOf(EqFilter(2500.0, 3.0, 1.0))
        val n = nextSuggestion(prior, 5.4, "Run 2", other, { emptyList() }, newEq = b)
        assertFalse(n.converged)
        assertEquals(other, n.filters)
    }

    @Test
    fun theParametricEqIsKeptBesideTheRealisedBands() {
        val realised = listOf(EqFilter(50.0, -8.7, 4.32), EqFilter(63.0, -5.3, 4.32))
        val r = run("Run 1", 5.0, realised, param = b)
        assertEquals(b, r.eq)
        val t = SavedTest("id", "LG", 1L, 1, 0, 400, listOf(run("Stock (no EQ)", 7.0, emptyList()), r.let { SavedRun(it.whenMillis, it.rmsDb, it.magDb, it.filters, it.label, applied = it.applied, complete = true, appliedParam = it.appliedParam, converged = true) }))
        val back = TestStore.decode(TestStore.encode(t))
        assertEquals(b, back.runs[1].eq)
        assertEquals(realised, back.runs[1].applied)
        assertTrue(back.runs[1].converged)
        assertNull(back.runs[0].appliedParam)
    }

    @Test
    fun twinBandsAreMergedInTheSuggestion() {
        val twins = listOf(EqFilter(38.3, 3.0, 1.4), EqFilter(38.3, 3.0, 1.4), EqFilter(38.3, 2.8, 1.4), EqFilter(185.0, -10.0, 2.0))
        val s = suggestedFor(SavedRun(1L, 5.0, DoubleArray(4), twins, "Run 1", complete = true))
        assertEquals(2, s.filters.size)
    }

    @Test
    fun systemEqFitCheckFollowsTheBandWidthLimits() {
        assertTrue(fitsSystemEq(listOf(EqFilter(100.0, -6.0, 2.0), EqFilter(2000.0, 2.0, 1.4))))
        assertFalse(fitsSystemEq(listOf(EqFilter(100.0, -6.0, 4.0))))
        assertFalse(fitsSystemEq(listOf(EqFilter(2000.0, 2.0, 2.0))))
        assertTrue(fitsSystemEq(emptyList()))
    }
}
