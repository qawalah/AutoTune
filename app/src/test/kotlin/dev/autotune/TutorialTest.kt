package dev.autotune

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The first-start tutorial (2.9.0): its cards, the keys round them, how a card moves as you swipe, and its pictures. */
class TutorialTest {
    @Test
    fun fiveCardsAnOpeningThenTheFourSteps() {
        assertEquals(listOf("hello", "measure", "eq", "verify", "keep"), TUTORIAL_CARDS.map { it.key })
        assertEquals(listOf(0, 1, 2, 3, 4), TUTORIAL_CARDS.map { it.step })
        assertEquals(4, TUTORIAL_STEPS)
        TUTORIAL_CARDS.forEach {
            assertTrue(it.title.isNotBlank())
            // plain and short: one or two sentences under the title
            assertTrue("${it.key} is too long", it.text.length <= 110)
        }
    }

    @Test
    fun stepLabelsAreSmallAndLowercase() {
        assertNull(tutorialStepLabel(TUTORIAL_CARDS[0]))
        assertEquals("step 1 of 4", tutorialStepLabel(TUTORIAL_CARDS[1]))
        assertEquals("step 4 of 4", tutorialStepLabel(TUTORIAL_CARDS[4]))
    }

    @Test
    fun theKeyReadsNextUntilTheLastCard() {
        assertEquals("Next", tutorialKeyLabel(0))
        assertEquals("Next", tutorialKeyLabel(3))
        assertEquals("Get started", tutorialKeyLabel(4))
    }

    @Test
    fun skipShowsOnEveryCardButTheLast() {
        (0..3).forEach { assertTrue(tutorialShowsSkip(it)) }
        assertFalse(tutorialShowsSkip(4))
    }

    @Test
    fun backGoesToThePreviousCardAndClosesFromTheFirst() {
        assertEquals(2, tutorialBack(3))
        assertEquals(0, tutorialBack(1))
        assertNull(tutorialBack(0))
    }

    @Test
    fun aCardsPlaceIsItsDistanceFromTheSwipe() {
        assertEquals(0f, tutorialPos(page = 2, currentPage = 2, offsetFraction = 0f), 1e-6f)
        // half way from card 2 to card 3: card 3 is half a page to the end, card 2 half a page to the start
        assertEquals(0.5f, tutorialPos(page = 3, currentPage = 2, offsetFraction = 0.5f), 1e-6f)
        assertEquals(-0.5f, tutorialPos(page = 2, currentPage = 2, offsetFraction = 0.5f), 1e-6f)
    }

    @Test
    fun theCurrentCardStandsStill() {
        val m = tutorialMotion(0f)
        assertEquals(0f, m.artShift, 1e-6f)
        assertEquals(0f, m.textShift, 1e-6f)
        assertEquals(1f, m.alpha, 1e-6f)
        assertEquals(1f, m.artScale, 1e-6f)
    }

    @Test
    fun thePictureTravelsFurtherThanTheWords() {
        for (pos in listOf(-1f, -0.6f, -0.2f, 0.3f, 0.7f, 1f)) {
            val m = tutorialMotion(pos)
            assertTrue(abs(m.artShift) > abs(m.textShift))
            // both drift the way the card is going
            assertTrue(m.artShift * pos > 0 && m.textShift * pos > 0)
        }
        assertEquals(TUTORIAL_ART_SHIFT, tutorialMotion(1f).artShift, 1e-6f)
        assertEquals(-TUTORIAL_TEXT_SHIFT, tutorialMotion(-1f).textShift, 1e-6f)
    }

    @Test
    fun aCardFadesAndShrinksAsItLeaves() {
        assertEquals(0.5f, tutorialMotion(0.5f).alpha, 1e-6f)
        assertEquals(0f, tutorialMotion(1f).alpha, 1e-6f)
        assertEquals(TUTORIAL_ART_MIN_SCALE, tutorialMotion(-1f).artScale, 1e-6f)
        // further away than one page changes nothing more
        assertEquals(tutorialMotion(1f), tutorialMotion(2.5f))
        assertEquals(tutorialMotion(-1f), tutorialMotion(-4f))
    }

    @Test
    fun aPartOfThePicturePlaysInItsOwnWindow() {
        assertEquals(0f, playPhase(tMs = 0f, delayMs = 200, durationMs = 400), 1e-6f)
        assertEquals(0f, playPhase(tMs = 200f, delayMs = 200, durationMs = 400), 1e-6f)
        assertEquals(0.5f, playPhase(tMs = 400f, delayMs = 200, durationMs = 400), 1e-6f)
        assertEquals(1f, playPhase(tMs = 600f, delayMs = 200, durationMs = 400), 1e-6f)
        assertEquals(1f, playPhase(tMs = 5000f, delayMs = 200, durationMs = 400), 1e-6f)
        // a part with no length is there or not
        assertEquals(1f, playPhase(tMs = 300f, delayMs = 300, durationMs = 0), 1e-6f)
    }

    @Test
    fun everyPictureFinishesWithinItsPlayTime() {
        TUTORIAL_CARDS.forEach { assertTrue(it.key, tutorialPlayMs(it.key) in 800..2400) }
    }

    @Test
    fun theDemoSystemIsUnevenBeforeAndCloseAfter() {
        val xs = (0..60).map { it / 60f }
        val stock = xs.map { tutorialCurveDb(it, tuned = 0f) }
        val tuned = xs.map { tutorialCurveDb(it, tuned = 1f) }
        // the stock has a bass hump and a dip, as a car usually does
        assertTrue(stock.max() > 4f)
        assertTrue(stock.min() < -4f)
        // the EQ brings every point closer, never further
        xs.indices.forEach { assertTrue(abs(tuned[it]) <= abs(stock[it]) + 1e-6f) }
        assertTrue(tuned.maxOf { abs(it) } < 2.5f)
    }

    @Test
    fun theReadingCountsDownFromStockToTuned() {
        assertEquals(4.1, tutorialReadingDb(0f), 1e-9)
        assertEquals(1.2, tutorialReadingDb(1f), 1e-9)
        val steps = (0..10).map { tutorialReadingDb(it / 10f) }
        steps.zipWithNext().forEach { (a, b) -> assertTrue(b <= a) }
        // shown to a tenth of a dB
        steps.forEach { assertEquals(it, Math.round(it * 10) / 10.0, 1e-9) }
    }

    @Test
    fun theFadersCutTheHumpAndBoostGently() {
        val gains = (0 until TUTORIAL_FADERS).map { tutorialFaderDb(it) }
        assertEquals(TUTORIAL_FADERS, gains.size)
        gains.forEach { assertTrue(it in -6f..3f) }
        // the loudest band of the stock gets the deepest cut
        val centres = (0 until TUTORIAL_FADERS).map { tutorialFaderX(it) }
        val loudest = centres.indices.maxBy { tutorialCurveDb(centres[it], 0f) }
        assertEquals(loudest, gains.indices.minBy { gains[it] })
        assertTrue(gains[loudest] < 0f)
        // centres run from bass to treble across the strip
        centres.zipWithNext().forEach { (a, b) -> assertTrue(b > a) }
        assertTrue(centres.first() > 0f && centres.last() < 1f)
    }
}
