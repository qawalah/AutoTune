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
        // the default is Gradient edge (asked 2026-11-09), for a missing, retired or unknown setting
        assertEquals("GRADIENT", DEFAULT_LOOK)
        assertTrue(DEFAULT_LOOK in kept)
        RETIRED_LOOKS.forEach { assertEquals(it, "GRADIENT", resolveLookName(it, kept)) }
        assertEquals("GRADIENT", resolveLookName(null, kept))
        assertEquals("GRADIENT", resolveLookName("", kept))
        assertEquals("GRADIENT", resolveLookName("braun_black", kept))
        assertEquals("GRADIENT", resolveLookName("SOMETHING_ELSE", kept))
        // a look saved by name is kept: Classic stays Classic
        assertEquals("BRAUN", resolveLookName("BRAUN", kept))
        kept.forEach { assertEquals(it, resolveLookName(it, kept)) }
        // an explicit fallback is still honoured
        assertEquals("BRAUN", resolveLookName(null, kept, "BRAUN"))
        // a retired name is never a kept one
        assertTrue(RETIRED_LOOKS.none { it in kept })
    }

    /** The owner's Gradient edge palette (asked 2026-11-09), from the drop-downs' own presets. */
    @Test
    fun gradientDefaultPaletteIsTheOwnersPresets() {
        val p = lookDefaultPalette("GRADIENT")
        assertEquals(GRADIENT_DEFAULT_PALETTE, p)
        assertEquals(0xFFF3F2EEL, p[PaletteSlot.BODY])
        assertEquals(0xFFFFFFFFL, p[PaletteSlot.DECK])
        assertEquals(0xFF5E5C57L, p[PaletteSlot.INK])
        assertEquals(0xFFBF3C18L, p[PaletteSlot.ACCENT])
        assertEquals(0xFFB9BAB8L, p[PaletteSlot.RULE])
        // the same values as the presets of those names
        fun preset(slot: PaletteSlot, name: String) = paletteChoices(slot).single { it.name == name }.argb
        assertEquals(preset(PaletteSlot.BODY, "Warm white"), p[PaletteSlot.BODY])
        assertEquals(preset(PaletteSlot.DECK, "White"), p[PaletteSlot.DECK])
        assertEquals(preset(PaletteSlot.INK, "Slate"), p[PaletteSlot.INK])
        assertEquals(preset(PaletteSlot.ACCENT, "Braun red"), p[PaletteSlot.ACCENT])
        assertEquals(preset(PaletteSlot.RULE, "Aluminium"), p[PaletteSlot.RULE])
        // the other looks start from their own colours
        listOf("BRAUN", "BRAUN_GRAPHITE", "BRAUN_BLACK", "WHITE", "NAVY", "").forEach { assertTrue(it, lookDefaultPalette(it).isDefault) }
    }

    /** The preferences as the view model writes and reads them: set, "As the look", reset, and switching looks. */
    @Test
    fun gradientPaletteDefaultsResetAndOverrides() {
        val prefs = HashMap<String, String>()
        val gradient = lookDefaultPalette("GRADIENT")
        val none = lookDefaultPalette("BRAUN")
        fun read(d: Palette) = paletteFrom(d) { prefs[it] }
        fun set(slot: PaletteSlot, argb: Long?, d: Palette) {
            val v = paletteStoredValue(slot, argb, d)
            if (v != null) prefs[slot.key] = v else prefs.remove(slot.key)
        }
        // nothing saved: Gradient edge shows its five colours, Classic its own
        assertEquals(gradient, read(gradient))
        assertTrue(read(none).isDefault)
        // a saved colour overrides the default, the others stay
        set(PaletteSlot.BODY, 0xFF121211L, gradient)
        set(PaletteSlot.INK, 0xFFF2F1ECL, gradient)
        val own = read(gradient)
        assertEquals(0xFF121211L, own[PaletteSlot.BODY])
        assertEquals(0xFFF2F1ECL, own[PaletteSlot.INK])
        assertEquals(gradient[PaletteSlot.DECK], own[PaletteSlot.DECK])
        // "As the look" on Gradient edge is the look's own colour there, not the default, and it survives a restart
        set(PaletteSlot.RULE, null, gradient)
        assertEquals(PALETTE_AS_LOOK, prefs[PaletteSlot.RULE.key])
        assertNull(read(gradient)[PaletteSlot.RULE])
        assertEquals(0, paletteMenu(PaletteSlot.RULE, read(gradient)[PaletteSlot.RULE]).selected)
        // on Classic the same choice just removes the key, and the marker reads as not set
        set(PaletteSlot.ACCENT, 0xFF2456A6L, none)
        set(PaletteSlot.ACCENT, null, none)
        assertNull(prefs[PaletteSlot.ACCENT.key])
        assertNull(read(none)[PaletteSlot.RULE])
        // Classic with the same saved colours: only the saved ones, no Gradient defaults
        assertEquals(Palette().with(PaletteSlot.BODY, 0xFF121211L).with(PaletteSlot.INK, 0xFFF2F1ECL), read(none))
        // a bad stored value takes the default
        prefs[PaletteSlot.DECK.key] = "nope"
        assertEquals(gradient[PaletteSlot.DECK], read(gradient)[PaletteSlot.DECK])
        assertNull(read(none)[PaletteSlot.DECK])
        // Reset palette removes every key: Gradient edge returns to its five colours, Classic to its own
        PaletteSlot.entries.forEach { prefs.remove(it.key) }
        assertEquals(gradient, read(gradient))
        assertTrue(read(none).isDefault)
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

    /** 2.19.1: a new song clicked because every player start rebuilt the EQ; now only after an output change. */
    @Test
    fun aNewSongDoesNotRebuildTheEqUnlessTheOutputChanged() {
        assertFalse(rebuildOnPlayback(started = true, routeChangedSinceBuild = false, effectRunning = true))
        assertTrue(rebuildOnPlayback(started = true, routeChangedSinceBuild = true, effectRunning = true))
        assertTrue(rebuildOnPlayback(started = true, routeChangedSinceBuild = false, effectRunning = false))
        assertFalse(rebuildOnPlayback(started = false, routeChangedSinceBuild = true, effectRunning = false))
        assertEquals(2, SWEEP_ATTEMPTS)
    }

    /**
     * Reported again after 2.9.0 (by day): a dark ink never reached Classic's dark body, and on its light decks it was
     * as dark as the ink already there, so nothing changed. Each surface now takes the version the ink reads on.
     */
    @Test
    fun aDarkInkBringsTheLightSurfaceItNeeds() {
        val dayBody = 0xFF2B2B29L; val nightBody = 0xFF121211L; val lightBody = 0xFFE9E7E1L
        val dayDeck = 0xFFF3F2EEL; val nightDeck = 0xFF232321L
        val darkInks = PALETTE_NEUTRALS.filter { it.name in setOf("Slate", "Graphite", "Charcoal", "Near black", "Black") }
        assertEquals(5, darkInks.size)
        darkInks.forEach { ink ->
            assertTrue(ink.name, isDarkInk(ink.argb))
            // the cause: on Classic's dark body none of them reads, so readableInk kept the light ink there, silently
            assertEquals(ink.name, InkReach.NONE, inkReach(ink.argb, listOf(dayBody, nightBody)))
            assertEquals(0xFFECEAE4L, readableInk(ink.argb, 0xFFECEAE4L, listOf(dayBody)))
            // the fix: the dark body takes its light version, by day and by night; night decks take the day decks
            assertEquals(ink.name, InkSurface.LIGHT, inkSurface(ink.argb, listOf(dayBody), listOf(lightBody), listOf(nightBody)))
            assertEquals(ink.name, InkSurface.LIGHT, inkSurface(ink.argb, listOf(nightBody), listOf(lightBody), listOf(nightBody)))
            assertEquals(ink.name, InkSurface.LIGHT, inkSurface(ink.argb, listOf(nightDeck), listOf(dayDeck), listOf(nightDeck)))
            // a light deck already carries it
            assertEquals(ink.name, InkSurface.OWN, inkSurface(ink.argb, listOf(dayDeck), listOf(dayDeck), listOf(nightDeck)))
            // a surface the user set stays as set
            assertEquals(ink.name, InkSurface.OWN, inkSurface(ink.argb, listOf(nightBody), listOf(lightBody), listOf(nightBody), fixed = true))
        }
        // a light ink on light decks takes the dark ones; no ink changes nothing
        assertFalse(isDarkInk(0xFFFFFFFFL))
        assertEquals(InkSurface.DARK, inkSurface(0xFFFFFFFFL, listOf(dayDeck), listOf(dayDeck), listOf(nightDeck)))
        assertEquals(InkSurface.OWN, inkSurface(null, listOf(dayBody), listOf(lightBody), listOf(nightBody)))
        // an ink that reads on neither version keeps the surface (the readable-ink fallback and the note take over)
        assertEquals(InkSurface.OWN, inkSurface(0xFF85857FL, listOf(dayDeck), listOf(dayDeck), listOf(nightDeck)))
        assertEquals(InkSurface.OWN, inkSurface(0xFF121211L, listOf(dayBody), null, listOf(nightBody)))
    }

    @Test
    fun inkNoteSaysWhatTheInkDid() {
        val black = 0xFF121211L
        assertNull(inkNote(null, listOf(1L), listOf(1L)))
        // used everywhere and plainly different: nothing to say
        assertNull(inkNote(0xFF5E5C57L, listOf(0xFF5E5C57L, 0xFF5E5C57L), listOf(0xFFECEAE4L, 0xFF232321L)))
        // used everywhere, but as dark as the ink it replaced: say so, instead of silently looking the same
        assertEquals("about the same as the look's ink", inkNote(black, listOf(black, black), listOf(0xFF1B1B1AL, 0xFF232321L)))
        // it brought light or dark surfaces with it
        assertEquals("on light surfaces, so it reads", inkNote(black, listOf(black, black), listOf(0xFFECEAE4L, 0xFF232321L), InkSurface.LIGHT))
        assertEquals("on dark surfaces, so it reads", inkNote(0xFFFFFFFFL, listOf(0xFFFFFFFFL, 0xFFFFFFFFL), listOf(0xFFECEAE4L, 0xFF232321L), InkSurface.DARK))
        // only some, or none, of the surfaces carry it
        assertEquals("only where it reads", inkNote(black, listOf(0xFFECEAE4L, black), listOf(0xFFECEAE4L, 0xFF232321L)))
        assertEquals("doesn't read on these colours", inkNote(black, listOf(0xFFECEAE4L, 0xFFECEAE4L), listOf(0xFFECEAE4L, 0xFFECEAE4L)))
        // the note is one short line
        listOf("about the same as the look's ink", "on light surfaces, so it reads", "doesn't read on these colours").forEach { assertTrue(it.length <= 40) }
    }

    /** The palette's drop-down shows what was stored, and every preset is written and read back as itself. */
    @Test
    fun paletteMenuShowsTheStoredColour() {
        for (slot in PaletteSlot.entries) {
            val presets = paletteChoices(slot)
            val empty = paletteMenu(slot, null)
            assertEquals(0, empty.selected)
            assertEquals("As the look", empty.names[0])
            assertEquals("Custom…", empty.names[empty.customIdx])
            assertNull(empty.colourAt(0))
            for ((i, preset) in presets.withIndex()) {
                // pick option i+1: the value written to the preferences, read back on the next start
                val picked = empty.colourAt(i + 1)!!
                assertEquals(preset.argb, picked)
                val stored = hexColour(Palette().with(slot, picked)[slot]!!)
                val back = paletteFrom { k -> if (k == slot.key) stored else null }[slot]
                assertEquals(preset.argb, back)
                // the field shows that preset, by name, with its swatch
                val m = paletteMenu(slot, back)
                assertEquals("${slot.name} ${preset.name}", i + 1, m.selected)
                assertEquals(preset.name, m.names[m.selected])
                assertEquals(preset.argb, m.colours[m.selected])
            }
            // a typed colour is listed as itself and selected
            val own = paletteMenu(slot, 0xFF123456L)
            assertEquals("#123456", own.names[own.selected])
            assertEquals(0xFF123456L, own.colourAt(own.selected))
            assertEquals(own.names.size - 2, own.selected)
        }
    }

    @Test
    fun tuneDialIsLightWithDarkDots() {
        // a light candidate is used as it is; with none, a light face anyway
        assertEquals(0xFFF3F2EEL, dialFace(listOf(0xFFF3F2EEL, 0xFF353533L), night = false))
        assertEquals(0xFFE9E7E1L, dialFace(listOf(0xFF232321L, 0xFFE9E7E1L), night = true))
        assertEquals(0xFFD5D5D2L, dialFace(listOf(0xFF232321L, 0xFF121211L), night = true))
        assertEquals(0xFFF3F2EEL, dialFace(emptyList(), night = false))
        for (face in listOf(0xFFF3F2EEL, 0xFFD5D5D2L, 0xFFFFFFFFL)) {
            // a light ink is never a dot; a dark one that reads is
            val dots = dialDots(face, listOf(0xFFECEAE4L, 0xFF353533L))
            assertEquals(0xFF353533L, dots)
            assertTrue(contrast(dialDots(face, listOf(0xFFECEAE4L)), face) >= 4.5)
            assertTrue(contrast(dialDots(face, listOf(0xFFECEAE4L)), 0xFF000000L) < contrast(dialDots(face, listOf(0xFFECEAE4L)), 0xFFFFFFFFL))
        }
    }

    @Test
    fun scoreIsOnlyShownWhereItIsKnown() {
        // the focus run's report
        assertEquals(72, knownScore(3, 3, 72))
        // a compared run
        assertEquals(64, knownScore(0, 3, 72, listOf(0, 3), listOf(64, 72)))
        // nothing known: nothing shown, nothing computed
        assertNull(knownScore(1, 3, 72, listOf(0, 3), listOf(64, 72)))
        assertNull(knownScore(3, 3, null))
        assertNull(knownScore(0, null, null, listOf(0), emptyList()))
        // the stock review's report card: the stock's score is known even when the stock is not ticked
        assertEquals(39, knownScore(0, 3, 52, listOf(3), listOf(52), stockScore = 39))
        assertNull(knownScore(2, 3, 52, listOf(3), listOf(52), stockScore = 39))
        // a compared stock keeps the compared score (the same as the runs list shows)
        assertEquals(41, knownScore(0, 3, 52, listOf(0, 3), listOf(41, 52), stockScore = 39))
        // the results page's key: the move in lower case after "next:"
        assertEquals("next: keep this EQ and finish", nextLine(moveLabel(NextMove.KEEP_LATEST, null)))
        assertNull(nextLine(null))
    }

    @Test
    fun graphicsPlayTwiceThenStop() {
        assertEquals(2, GRAPHIC_PLAYS)
        // from rest: forward, a quick return, forward again: two whole movements, ending settled
        assertEquals(listOf(1f, 0f, 1f), arrivalLegs(0f))
        // a replay of a settled graphic returns first, so it still plays two whole movements
        assertEquals(listOf(0f, 1f, 0f, 1f), arrivalLegs(1f))
        assertEquals(listOf(0f, 1f, 0f, 1f), arrivalLegs(0.4f))
        assertEquals(2, arrivalLegs(0f).count { it == 1f })
        assertEquals(1f, arrivalLegs(0f).last())
        assertEquals(listOf(1f), arrivalLegs(0f, plays = 1))
        // the return is quick, and none with animations off
        assertEquals(300, returnMs(900))
        assertEquals(180, returnMs(300))
        assertEquals(0, returnMs(0))
    }

    @Test
    fun aGraphicPlaysWhenItCanBeSeen() {
        assertFalse(shownOnScreen(0f, 400f))
        assertFalse(shownOnScreen(10f, 0f))
        // half of a small graphic, or enough of a tall one
        assertTrue(shownOnScreen(100f, 200f))
        assertFalse(shownOnScreen(90f, 200f))
        assertTrue(shownOnScreen(160f, 2000f))
        assertFalse(shownOnScreen(120f, 2000f))
    }

    @Test
    fun aPullPastTheTopReplaysOncePerGesture() {
        val p = PullReplay(100f)
        // scrolling in the middle of the page never counts
        assertFalse(p.drag(80f, atTop = false))
        assertFalse(p.drag(80f, atTop = false))
        // at the top: the pull adds up and fires once past the threshold
        assertFalse(p.drag(60f, atTop = true))
        assertTrue(p.drag(60f, atTop = true))
        assertFalse(p.drag(200f, atTop = true))
        // a new gesture can fire again
        p.end()
        assertFalse(p.drag(50f, atTop = true))
        // dragging back up starts the count over
        assertFalse(p.drag(-10f, atTop = true))
        assertFalse(p.drag(60f, atTop = true))
        assertTrue(p.drag(60f, atTop = true))
    }

    @Test
    fun theDialPulsesFromTheCentreOut() {
        val rim = 100f
        // the front starts at the centre and moves out past the rim, never back
        assertEquals(0f, pulseFront(0f, rim), 1e-6f)
        val fronts = (0..10).map { pulseFront(it / 10f, rim) }
        assertTrue(fronts.zipWithNext().all { (a, b) -> b > a })
        assertTrue(pulseFront(1f, rim) > rim)
        // a hole is lit when the front passes it: inner holes first, outer holes later
        val band = 16f
        assertEquals(1f, pulseLight(40f, 40f, band), 1e-6f)
        assertEquals(0f, pulseLight(80f, 40f, band), 1e-6f)
        assertTrue(pulseLight(45f, 40f, band) in 0.5f..1f)
        val inner = (0..20).map { pulseLight(45f, pulseFront(it / 20f, rim), band) }
        val outer = (0..20).map { pulseLight(85f, pulseFront(it / 20f, rim), band) }
        assertTrue(inner.indexOf(inner.max()) < outer.indexOf(outer.max()))
        // nothing lit before it starts
        assertEquals(0f, pulseLight(40f, 0f, band), 1e-6f)
    }

    @Test
    fun readingsTakeASecondLineRatherThanSqueezeAScore() {
        // the main reading and two others with score boxes are too wide for a phone: two lines
        assertFalse(readingsFitOneLine(listOf(150, 126, 126), 12, 347))
        // the stock with "with the eq" fits on one
        assertTrue(readingsFitOneLine(listOf(150, 80), 12, 347))
        assertTrue(readingsFitOneLine(emptyList(), 12, 100))
    }

    @Test
    fun stockAnalysisKeepsTheReportScoreItWasGiven() {
        assertNull(analyzeStock(4.0, 2.0, 6, emptyList(), emptyList(), emptyList(), null).score)
        // the stock review's report card is already worked out; its score is kept for the display, not recomputed
        val report = dev.autotune.dsp.SystemReport(39, "Fair", "", emptyList(), 55)
        assertEquals(39, analyzeStock(6.6, 4.1, 6, emptyList(), emptyList(), emptyList(), report).score)
    }

    @Test
    fun eqsAreNamedAfterTheirSystem() {
        // the device, without its kind, and never the target
        org.junit.Assert.assertEquals("F-150", eqTitle("Car (Harman-style) test, 11 Nov 17:44", "Bluetooth: F-150", "Car (Harman-style)", false, "11 Nov 17:44"))
        // no device: the test's own name
        org.junit.Assert.assertEquals("Camry front stage", eqTitle("Camry front stage", "", "Car", false, ""))
        // two tests on one device: the date tells them apart
        org.junit.Assert.assertEquals("F-150 · 9 Nov 10:02", eqTitle("x", "Bluetooth: F-150", "Car", true, "9 Nov 10:02"))
        // under it: the note first, else the score and date
        org.junit.Assert.assertEquals("windows down", eqSubline(" windows down ", 3.3, "11 Nov 17:44", "Run 4"))
        org.junit.Assert.assertEquals("3.3 dB from target · 11 Nov 17:44", eqSubline("", 3.3, "11 Nov 17:44", "Run 4"))
        org.junit.Assert.assertEquals("Suggested EQ", eqSubline(null, null, null, "Suggested EQ"))
    }

    @Test
    fun everyRunHasItsScore() {
        // the focus run and compared runs keep theirs; every other run falls back to its own score
        org.junit.Assert.assertEquals(55, knownScore(7, 7, 55, allScores = List(8) { 40 + it }))
        org.junit.Assert.assertEquals(44, knownScore(4, 7, 55, allScores = List(8) { 40 + it }))
        org.junit.Assert.assertEquals(41, knownScore(0, 7, 55, stockScore = 41, allScores = List(8) { 40 + it }))
        // a run that could not be scored shows none
        org.junit.Assert.assertNull(knownScore(2, 7, 55, allScores = listOf(40, 41, -1)))
        org.junit.Assert.assertEquals("Refine", STEP_NAMES[2])
    }
}
