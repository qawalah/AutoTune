package dev.autotune.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Braun's dark body with light decks by day and dark decks at night (2.4.0), and that text reads on both; the 2.8 looks and the palette. */
class ThemeTest {
    private fun contrast(a: Color, b: Color): Double {
        val l1 = a.luminance() + 0.05; val l2 = b.luminance() + 0.05
        return (maxOf(l1, l2) / minOf(l1, l2)).toDouble()
    }

    @Test
    fun dayHasDarkBodyAndLightDecks() {
        val day = lookColors(Look.BRAUN, dark = false)
        assertNotNull(day.deck)
        val deck = day.deck!!
        assertTrue(day.bg.luminance() < 0.1f)
        assertTrue(deck.panel.luminance() > 0.8f)
    }

    @Test
    fun nightDecksStayDark() {
        val night = lookColors(Look.BRAUN, dark = true)
        assertTrue(night.deck!!.panel.luminance() < 0.1f)
        // the deck stands off the body
        assertTrue(night.deck!!.panel.luminance() > night.bg.luminance())
    }

    @Test
    fun textReadsOnBodyAndDeck() {
        for (dark in listOf(false, true)) {
            val c = lookColors(Look.BRAUN, dark)
            val d = c.deck!!
            assertTrue(contrast(c.text, c.bg) >= 4.5)
            assertTrue(contrast(c.muted, c.bg) >= 4.5)
            assertTrue(contrast(d.text, d.panel) >= 4.5)
            assertTrue(contrast(d.muted, d.panel) >= 4.5)
            assertTrue(contrast(d.muted, d.panelHigh) >= 4.5)
        }
    }

    @Test
    fun accentReachesTheDeck() {
        val c = lookColors(Look.BRAUN, false).withAccent(dev.autotune.Accent.entries.last())
        assertTrue(c.deck!!.signal != lookColors(Look.BRAUN, false).deck!!.signal || dev.autotune.Accent.entries.last() == dev.autotune.Accent.LOOK)
    }

    /** The surfaces text sits on in a set of colours: the deck's, or the body's and its plates for a look without decks. */
    private fun readsEverywhere(c: AtColors, what: String) {
        val d = c.deck
        if (d != null) {
            assertTrue("$what body text", contrast(c.text, c.bg) >= 4.5)
            assertTrue("$what body muted", contrast(c.muted, c.bg) >= 4.5)
        }
        val plate = d ?: c
        for (g in listOf(plate.panel, plate.panelHigh) + (if (d == null) listOf(c.bg) else emptyList())) {
            assertTrue("$what text", contrast(plate.text, g) >= 4.5)
            assertTrue("$what muted", contrast(plate.muted, g) >= 4.5)
        }
    }

    @Test
    fun everyLookReadsByDayAndNight() {
        for (l in Look.entries) for (dark in listOf(false, true)) readsEverywhere(lookColors(l, dark), "${l.name} dark=$dark")
    }

    @Test
    fun removedLooksOpenClassic() {
        for (old in dev.autotune.RETIRED_LOOKS) assertEquals(Look.BRAUN, Look.of(old))
        assertEquals(Look.BRAUN, Look.of(null))
        assertEquals(Look.BRAUN, Look.of("SOMETHING_ELSE"))
        for (l in Look.entries) assertEquals(l, Look.of(l.name))
        // the stored names of the Braun looks are unchanged, so a saved choice survives the rename
        assertEquals(listOf("BRAUN", "BRAUN_GRAPHITE", "BRAUN_BLACK"), Look.entries.take(3).map { it.name })
    }

    @Test
    fun whiteLookIsWhiteWithVisiblePlates() {
        for (dark in listOf(false, true)) {
            val c = lookColors(Look.WHITE, dark)
            assertEquals(Color.White, c.bg)
            assertNull(c.deck)
            // the plate differs from the body and its hairline stands off both
            assertTrue(c.panel != c.bg)
            assertTrue(contrast(c.line, c.bg) >= 1.5)
            assertTrue(contrast(c.line, c.panel) >= 1.4)
        }
    }

    @Test
    fun gradientLookShadesTheDeckEdgeDarkToLight() {
        for (dark in listOf(false, true)) {
            val c = lookColors(Look.GRADIENT, dark)
            val e = c.deck!!.edge!!
            assertEquals(2, e.size)
            assertTrue(e.first().luminance() < e.last().luminance())
            // the colours otherwise are Classic's
            assertEquals(lookColors(Look.BRAUN, dark).deck!!.panel, c.deck!!.panel)
        }
        // only the Gradient edge look has a gradient outline
        for (l in Look.entries.filter { it != Look.GRADIENT }) for (dark in listOf(false, true)) {
            val c = lookColors(l, dark)
            assertNull(c.edge); assertNull(c.deck?.edge)
        }
    }

    @Test
    fun paletteRecoloursBodyDeckInkAccentAndHairline() {
        val p = dev.autotune.Palette()
            .with(dev.autotune.PaletteSlot.BODY, 0xFFFFFFFFL)
            .with(dev.autotune.PaletteSlot.DECK, 0xFFE9E7E1L)
            .with(dev.autotune.PaletteSlot.INK, 0xFF121211L)
            .with(dev.autotune.PaletteSlot.ACCENT, 0xFF2456A6L)
            .with(dev.autotune.PaletteSlot.RULE, 0xFF85857FL)
        for (l in Look.entries) for (dark in listOf(false, true)) {
            val c = themeColors(l, dark, dev.autotune.Accent.LOOK, p)
            val plate = c.deck ?: c
            assertEquals(Color.White, c.bg)
            assertEquals(Color(0xFFE9E7E1), plate.panel)
            assertEquals(Color(0xFF121211), plate.text)
            assertEquals(Color(0xFF2456A6), plate.signal)
            assertEquals(Color(0xFF2456A6), c.signal)
            assertEquals(Color(0xFF85857F), plate.line)
            readsEverywhere(c, "${l.name} dark=$dark")
        }
        // no palette changes nothing
        assertTrue(themeColors(Look.BRAUN, false, dev.autotune.Accent.LOOK, dev.autotune.Palette()) === lookColors(Look.BRAUN, false))
    }

    @Test
    fun paletteInkThatWouldVanishFallsBack() {
        // a white ink on the white look's plates: text stays readable
        val p = dev.autotune.Palette().with(dev.autotune.PaletteSlot.INK, 0xFFFFFFFFL)
        val c = themeColors(Look.WHITE, false, dev.autotune.Accent.LOOK, p)
        assertTrue(c.text != Color.White)
        readsEverywhere(c, "white ink on White")
        // a black body under Classic's light decks: the body's text turns light, the decks keep theirs
        val q = dev.autotune.Palette().with(dev.autotune.PaletteSlot.BODY, 0xFF000000L)
        readsEverywhere(themeColors(Look.BRAUN, false, dev.autotune.Accent.LOOK, q), "black body")
        val r = dev.autotune.Palette().with(dev.autotune.PaletteSlot.BODY, 0xFFFFFFFFL)
        readsEverywhere(themeColors(Look.BRAUN, false, dev.autotune.Accent.LOOK, r), "white body under Classic")
    }

    /** The bug reported after 2.8.1: a dark ink picked at night changed nothing, because no night surface could carry it. */
    @Test
    fun darkInkTakesEffectAtNight() {
        val darkInks = dev.autotune.PALETTE_NEUTRALS.filter { it.name in setOf("Slate", "Graphite", "Charcoal", "Near black", "Black") }
        for (l in Look.entries) for (ink in darkInks) {
            val p = dev.autotune.Palette().with(dev.autotune.PaletteSlot.INK, ink.argb)
            val night = themeColors(l, true, dev.autotune.Accent.LOOK, p)
            val plate = night.deck ?: night
            // the ink is used where text sits on a plate, so the pick is seen; everything still reads. Slate is too
            // light for the grey plates of Graphite and Black (as by day), and the field says so
            val reads = (if (night.deck != null) listOf(plate.panel) else listOf(night.panel, night.bg)).all { contrast(Color(ink.argb), it) >= 4.5 }
            if (ink.name != "Slate") assertTrue("${l.name} ${ink.name} reads", reads)
            if (reads) assertEquals("${l.name} ${ink.name}", Color(ink.argb), plate.text)
            else assertEquals("doesn't read on these colours", paletteInkNote(l, true, p))
            readsEverywhere(night, "${l.name} night, ${ink.name} ink")
            // a look with decks keeps its night body, so titles stay light on it
            if (lookColors(l, true).deck != null) assertEquals(lookColors(l, true).bg, night.bg)
        }
        // by day nothing changes about the surfaces
        val p = dev.autotune.Palette().with(dev.autotune.PaletteSlot.INK, 0xFF121211L)
        for (l in Look.entries) assertTrue(!usesDaySurfaces(l, false, p))
        // a light ink at night keeps the night look
        val light = dev.autotune.Palette().with(dev.autotune.PaletteSlot.INK, 0xFFFFFFFFL)
        assertEquals(lookColors(Look.BRAUN, true).deck!!.panel, themeColors(Look.BRAUN, true, dev.autotune.Accent.LOOK, light).deck!!.panel)
    }

    @Test
    fun inkNoteShowsWhenTheInkNeedsIt() {
        val dark = dev.autotune.Palette().with(dev.autotune.PaletteSlot.INK, 0xFF121211L)
        assertEquals("on light decks, so it reads", paletteInkNote(Look.BRAUN, true, dark))
        assertNull(paletteInkNote(Look.BRAUN, false, dark))
        assertNull(paletteInkNote(Look.BRAUN, true, dev.autotune.Palette()))
        // with a dark deck of the user's own the ink cannot read anywhere, and the field says so
        val both = dark.with(dev.autotune.PaletteSlot.DECK, 0xFF1C1C1BL).with(dev.autotune.PaletteSlot.BODY, 0xFF121211L)
        assertEquals("doesn't read on these colours", paletteInkNote(Look.BRAUN, true, both))
        readsEverywhere(themeColors(Look.BRAUN, true, dev.autotune.Accent.LOOK, both), "dark ink on dark own surfaces")
    }

    @Test
    fun tuneDialIsLightWithDarkDotsInEveryLook() {
        val palettes = listOf(
            dev.autotune.Palette(),
            dev.autotune.Palette().with(dev.autotune.PaletteSlot.BODY, 0xFF121211L).with(dev.autotune.PaletteSlot.DECK, 0xFF1C1C1BL),
            dev.autotune.Palette().with(dev.autotune.PaletteSlot.INK, 0xFF121211L),
        )
        for (l in Look.entries) for (dark in listOf(false, true)) for (p in palettes) {
            val (face, dots) = dialColours(themeColors(l, dark, dev.autotune.Accent.LOOK, p))
            assertTrue("${l.name} dark=$dark face", face.luminance() > 0.5f)
            assertTrue("${l.name} dark=$dark dots", dots.luminance() < 0.1f)
            assertTrue("${l.name} dark=$dark reads", contrast(dots, face) >= 4.5)
        }
    }
}
