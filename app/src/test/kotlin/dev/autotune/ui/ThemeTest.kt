package dev.autotune.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Braun's dark body with light decks by day and dark decks at night (2.4.0), and that text reads on both. */
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
}
