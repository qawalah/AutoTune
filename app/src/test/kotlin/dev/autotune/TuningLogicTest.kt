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

    @Test
    fun navBarHasFiveTabsWithTuneInTheCentre() {
        assertEquals(5, NAV_ORDER.size)
        assertEquals("MEASURE", NAV_ORDER[NAV_ORDER.size / 2])
        assertTrue(NAV_ORDER.containsAll(listOf("TESTS", "EQ", "LIVE", "SETTINGS")))
    }

    @Test
    fun tabChangesSlideTowardsTheChosenTab() {
        assertEquals(1, navSlide("TESTS", "MEASURE"))
        assertEquals(-1, navSlide("SETTINGS", "EQ"))
        assertEquals(0, navSlide("LIVE", "LIVE"))
        // Export is a page, not on the bar
        assertEquals(0, navSlide("TESTS", "EXPORT"))
    }

    @Test
    fun tuneStepsSlideForwardAndBack() {
        assertEquals(1, stepSlide(TuneStep.STOCK, TuneStep.APPLY))
        assertEquals(-1, stepSlide(TuneStep.VERIFY, TuneStep.APPLY))
        assertEquals(0, stepSlide(TuneStep.HOME, TuneStep.HOME))
    }

    @Test
    fun widgetRequestsOpenTheRightTab() {
        assertEquals("EQ", requestedTab("eq"))
        assertEquals("LIVE", requestedTab("live"))
        assertEquals("SETTINGS", requestedTab("Settings"))
        assertEquals("MEASURE", requestedTab("tune"))
        assertEquals("MEASURE", requestedTab(""))
    }

    @Test
    fun automaticTestNamesShortenToTheDate() {
        assertEquals("5 Nov 01:52" to "Car, Harman-style", shortTestName("Car, Harman-style (+8 dB bass) test, 5 Nov 01:52", "Car, Harman-style (+8 dB bass)"))
        assertEquals("30 Oct 05:32 (2)" to "Car", shortTestName("Car (+5 dB bass) test, 30 Oct 05:32 (2)", "Car (+5 dB bass)"))
        // a name the user typed stays as it is, with the short target under it
        assertEquals("F-150 driver seat" to "Car", shortTestName("F-150 driver seat", "Car (+5 dB bass)"))
        assertEquals("LG (3)" to "Home theatre", shortTestName("LG (3)", "Home theatre"))
        assertEquals("Flat", shortTarget("Flat"))
    }

    @Test
    fun alertsCountReadsAsOneShortLabel() {
        assertEquals("1 note", alertsCount(1))
        assertEquals("2 notes", alertsCount(2))
        assertEquals("12 notes", alertsCount(12))
    }

    @Test
    fun resultHeadlineSaysHowFarItCameInOneLine() {
        assertEquals("3.0 dB closer to the target than stock.", resultHeadline(23.1, 20.1))
        assertEquals("3.0 dB closer to the target than stock. Run 1 is still the best.", resultHeadline(23.1, 20.1, 17.9, "Run 1"))
        assertEquals("1.0 dB further from the target than stock.", resultHeadline(10.0, 11.0))
        assertEquals("About the same as stock.", resultHeadline(10.0, 10.2, 10.1, "Run 2"))
    }

    @Test
    fun tuningScaleEndsAboveTheLargestReading() {
        assertEquals(8.0, scaleMax(listOf(6.2, 2.1)), 0.0)
        assertEquals(24.0, scaleMax(listOf(23.1, 20.1, 17.9)), 0.0)
        assertEquals(4.0, scaleMax(listOf(1.0)), 0.0)
        assertEquals(4.0, scaleMax(emptyList()), 0.0)
        assertEquals(40.0, scaleMax(listOf(35.0)), 0.0)
    }

    @Test
    fun accentsReadOnTheirOwnColourAndAgainstTheBody() {
        assertEquals(Accent.LOOK, Accent.of(null))
        assertEquals(Accent.LOOK, Accent.of("PURPLE"))
        assertEquals(Accent.YELLOW, Accent.of("YELLOW"))
        val bodyLight = 0xFFF3F2EEL; val bodyDark = 0xFF1A1A19L
        Accent.entries.filter { it != Accent.LOOK }.forEach { a ->
            assertTrue("${a.name} text on light", contrast(a.light, a.onLight) >= 4.5)
            assertTrue("${a.name} text on dark", contrast(a.dark, a.onDark) >= 4.5)
            // the needle and pilot light must stand out from the body
            assertTrue("${a.name} on the light body", contrast(a.light, bodyLight) >= 3.0)
            assertTrue("${a.name} on the dark body", contrast(a.dark, bodyDark) >= 3.0)
        }
    }

    @Test
    fun aRemovedLookOpensTheDefault() {
        val kept = listOf("BRAUN", "BRAUN_GRAPHITE", "BRAUN_BLACK", "WHITE", "GRADIENT")
        RETIRED_LOOKS.forEach { assertEquals(it, "BRAUN", resolveLookName(it, kept, "BRAUN")) }
        assertEquals("BRAUN", resolveLookName(null, kept, "BRAUN"))
        assertEquals("BRAUN", resolveLookName("", kept, "BRAUN"))
        assertEquals("BRAUN", resolveLookName("braun_black", kept, "BRAUN"))
        kept.forEach { assertEquals(it, resolveLookName(it, kept, "BRAUN")) }
        // a retired name is never a kept one
        assertTrue(RETIRED_LOOKS.none { it in kept })
    }

    @Test
    fun hexColoursParseOnlyWhenValid() {
        assertEquals(0xFFBF3C18L, parseHexColour("#BF3C18"))
        assertEquals(0xFFBF3C18L, parseHexColour("bf3c18"))
        assertEquals(0xFFBF3C18L, parseHexColour("  #bF3c18 "))
        assertEquals(0xFFFFAA00L, parseHexColour("#fa0"))
        assertEquals(0xFF000000L, parseHexColour("000"))
        listOf(null, "", "#", "#12", "#1234", "#12345", "#1234567", "#GG0000", "red", "#12 345", "0x123456").forEach {
            assertNull("'$it'", parseHexColour(it))
        }
        assertEquals("#BF3C18", hexColour(0xFFBF3C18L))
        assertEquals("#00000A", hexColour(0x0AL))
        // round trip
        PALETTE_NEUTRALS.plus(PALETTE_ACCENTS).forEach { assertEquals(it.argb, parseHexColour(hexColour(it.argb))) }
    }

    @Test
    fun paletteKeepsOnlyValidOpaqueColours() {
        val stored = mapOf("palBody" to "#FFFFFF", "palInk" to "nope", "palAccent" to "#f60", "palRule" to "")
        val p = paletteFrom { stored[it] }
        assertEquals(0xFFFFFFFFL, p[PaletteSlot.BODY])
        assertNull(p[PaletteSlot.INK])
        assertNull(p[PaletteSlot.DECK])
        assertNull(p[PaletteSlot.RULE])
        assertEquals(0xFFFF6600L, p[PaletteSlot.ACCENT])
        assertTrue(paletteFrom { null }.isDefault)
        // set, replace, clear; alpha is always opaque
        val q = Palette().with(PaletteSlot.DECK, 0x00123456L)
        assertEquals(0xFF123456L, q[PaletteSlot.DECK])
        assertTrue(q.with(PaletteSlot.DECK, null).isDefault)
        // every slot has its own key
        assertEquals(PaletteSlot.entries.size, PaletteSlot.entries.map { it.key }.toSet().size)
        // presets are distinct per list
        assertEquals(PALETTE_NEUTRALS.size, PALETTE_NEUTRALS.map { it.argb }.toSet().size)
        assertEquals(PALETTE_ACCENTS.size, PALETTE_ACCENTS.map { it.argb }.toSet().size)
    }

    @Test
    fun paletteInkAlwaysReads() {
        val white = 0xFFFFFFFFL; val black = 0xFF121211L
        // a wanted ink that reads is used
        assertEquals(black, readableInk(black, white, listOf(white)))
        // one that does not falls back to the look's ink, then to near-black or near-white
        assertEquals(0xFF232321L, readableInk(0xFFF3F2EEL, 0xFF232321L, listOf(white)))
        val fallback = readableInk(0xFFF3F2EEL, 0xFFECEAE4L, listOf(white))
        assertTrue(contrast(fallback, white) >= 4.5)
        val onBlack = readableInk(0xFF1C1C1BL, 0xFF232321L, listOf(black))
        assertTrue(contrast(onBlack, black) >= 4.5)
        // muted labels read too, on every ground
        listOf(white, 0xFFE9E7E1L, 0xFF2B2B29L, black, 0xFF85857FL).forEach { g ->
            val ink = readableInk(null, 0xFF777777L, listOf(g))
            assertTrue(contrast(mutedInk(ink, listOf(g)), g) >= 4.5)
        }
        // text on an accent key
        PALETTE_ACCENTS.forEach { assertTrue(it.name, contrast(onAccent(it.argb), it.argb) >= 4.5) }
        assertEquals(0xFF808080L, mixArgb(0xFF000000L, 0xFFFFFFFFL, 0.5))
        assertEquals(0xFF000000L, mixArgb(0xFF000000L, 0xFFFFFFFFL, 0.0))
        assertEquals(0xFFFFFFFFL, mixArgb(0xFF000000L, 0xFFFFFFFFL, 1.0))
    }

    @Test
    fun anyRunButTheStockCanBeDeleted() {
        assertFalse(canDeleteRun(0, 4))
        assertTrue(canDeleteRun(2, 4))
        assertFalse(canDeleteRun(4, 4))
        // deleting run 1 of 4: run 3 moves to 2, and the latest (now 2) stays shown
        assertEquals(setOf(0, 2), selectionAfterDelete(setOf(0, 1, 3), removed = 1, newCount = 3))
        assertEquals(setOf(0, 1), selectionAfterDelete(setOf(0), removed = 2, newCount = 2))
    }

    @Test
    fun anotherRoundSkipsTheApplyStepOnlyWhereTheWayIsKnown() {
        assertTrue(refineGoesStraightToMeasure(true, ApplyWay.AUTOTUNE_EQ, false))
        assertTrue(refineGoesStraightToMeasure(true, ApplyWay.IN_APP, false))
        // the phone's EQ is known to damage the sound: the choice is shown again
        assertFalse(refineGoesStraightToMeasure(true, ApplyWay.AUTOTUNE_EQ, true))
        assertTrue(refineGoesStraightToMeasure(true, ApplyWay.IN_APP, true))
        // bands to set by hand, or another device plays the sweep: the Apply step stays
        assertFalse(refineGoesStraightToMeasure(true, ApplyWay.EQUIPMENT, false))
        assertFalse(refineGoesStraightToMeasure(false, ApplyWay.AUTOTUNE_EQ, false))
    }

    @Test
    fun playbackStartedOnlyWhenMorePlayersAreActive() {
        assertTrue(playbackStarted(0, 1))
        assertFalse(playbackStarted(1, 1))
        assertFalse(playbackStarted(1, 0))
    }
}
