package dev.autotune

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the tall header shows (2.2.0), how it folds into the slim bar (2.3.0), and the cue on the slider row. */
class HeaderTest {
    private val min = 60_000L

    @Test
    fun coldStartIsFresh() {
        assertTrue(freshOpen(null, 0L))
        assertTrue(freshOpen(null, 123_456_789L))
    }

    @Test
    fun fifteenMinutesInTheBackgroundIsFresh() {
        val t = 1_000_000L
        assertFalse(freshOpen(t, t))
        assertFalse(freshOpen(t, t + 14 * min))
        assertFalse(freshOpen(t, t + 15 * min - 1))
        assertTrue(freshOpen(t, t + 15 * min))
        assertTrue(freshOpen(t, t + 3 * 60 * min))
        assertEquals(15 * min, FRESH_OPEN_AFTER_MS)
    }

    @Test
    fun clockGoingBackCountsAsFresh() = assertTrue(freshOpen(5_000_000L, 1_000L))

    @Test
    fun thresholdCanBeGiven() {
        assertFalse(freshOpen(0L, 59_999L, thresholdMs = 60_000L))
        assertTrue(freshOpen(0L, 60_000L, thresholdMs = 60_000L))
    }

    @Test
    fun everyTabIsTallAgainOnAFreshOpen() {
        val all = NAV_ORDER.toSet()
        // used up Tests and EQ, back after 20 minutes: all five are tall again
        val used = tallAfterLeaving(tallAfterLeaving(all, "TESTS"), "EQ")
        assertEquals(setOf("MEASURE", "LIVE", "SETTINGS"), used)
        assertEquals(all, tallOnStart(used, all, 0L, 20 * min))
        // back after 2 minutes: nothing changes
        assertEquals(used, tallOnStart(used, all, 0L, 2 * min))
        // a cold start
        assertEquals(all, tallOnStart(emptySet(), all, null, 0L))
    }

    @Test
    fun leavingATabMakesItCompact() {
        val all = NAV_ORDER.toSet()
        val after = tallAfterLeaving(all, "EQ")
        assertFalse("EQ" in after)
        assertTrue("TESTS" in after)
        // leaving a screen that was already compact changes nothing
        assertEquals(after, tallAfterLeaving(after, "EQ"))
    }

    @Test
    fun noCueWhenEverythingFits() {
        val c = scrollCue(0, 0, 900)
        assertFalse(c.shown); assertFalse(c.moreLeft); assertFalse(c.moreRight)
        assertFalse(scrollCue(0, 2, 900).shown)
    }

    @Test
    fun cueFollowsTheScroll() {
        val start = scrollCue(0, 300, 900)
        assertFalse(start.moreLeft); assertTrue(start.moreRight)
        assertEquals(0.75f, start.thumbWidth, 1e-4f)
        assertEquals(0f, start.thumbStart, 1e-4f)
        val mid = scrollCue(150, 300, 900)
        assertTrue(mid.moreLeft); assertTrue(mid.moreRight)
        assertEquals(0.125f, mid.thumbStart, 1e-4f)
        val end = scrollCue(300, 300, 900)
        assertTrue(end.moreLeft); assertFalse(end.moreRight)
        assertEquals(0.25f, end.thumbStart, 1e-4f)
        // a pixel short of the end counts as the end
        assertFalse(scrollCue(299, 300, 900).moreRight)
        // out of range is held to the row
        assertFalse(scrollCue(-40, 300, 900).moreLeft)
    }

    // ---- 2.3.0: the header folds into the slim bar with the scroll

    @Test
    fun foldFollowsTheScroll() {
        assertEquals(0f, headerProgress(0f, 400f), 1e-6f)
        assertEquals(0.25f, headerProgress(100f, 400f), 1e-6f)
        assertEquals(1f, headerProgress(400f, 400f), 1e-6f)
        // past the header and overscroll are held
        assertEquals(1f, headerProgress(5000f, 400f), 1e-6f)
        assertEquals(0f, headerProgress(-30f, 400f), 1e-6f)
        // no header to fold is folded
        assertEquals(1f, headerProgress(0f, 0f), 1e-6f)
    }

    @Test
    fun withoutAnimationsTheHeaderSwitches() {
        assertEquals(0f, headerPhase(0f, 400f, animate = false), 0f)
        assertEquals(0f, headerPhase(399f, 400f, animate = false), 0f)
        assertEquals(1f, headerPhase(400f, 400f, animate = false), 0f)
        assertEquals(0.5f, headerPhase(200f, 400f, animate = true), 1e-6f)
    }

    @Test
    fun theFullHeaderComesBackOnlyAtTheTop() {
        assertTrue(headerFull(0))
        assertFalse(headerFull(1))
        assertFalse(headerFull(300))
        // part-way up the page the header is still the slim bar
        assertEquals(1f, headerProgress(2000f - 1500f, 400f), 1e-6f)
        // it only starts to open in the last stretch of the way to the top
        assertTrue(headerProgress(399f, 400f) < 1f)
    }

    @Test
    fun theHeaderEdgeFollowsTheContent() {
        assertEquals(900f, headerBottomPx(0f, 900f, 170f), 1e-4f)
        assertEquals(600f, headerBottomPx(300f, 900f, 170f), 1e-4f)
        assertEquals(170f, headerBottomPx(800f, 900f, 170f), 1e-4f)
        assertEquals(170f, headerBottomPx(99_999f, 900f, 170f), 1e-4f)
        assertEquals(900f, headerBottomPx(-50f, 900f, 170f), 1e-4f)
    }

    @Test
    fun rampsFadeOnTheirOwnStretch() {
        assertEquals(0f, ramp(0.2f, 0.35f, 0.7f), 0f)
        assertEquals(0.5f, ramp(0.525f, 0.35f, 0.7f), 1e-5f)
        assertEquals(1f, ramp(0.9f, 0.35f, 0.7f), 0f)
        // an empty stretch is a switch
        assertEquals(0f, ramp(0.49f, 0.5f, 0.5f), 0f)
        assertEquals(1f, ramp(0.5f, 0.5f, 0.5f), 0f)
    }

    @Test
    fun theGraphicGlidesIntoTheRightCorner() {
        val tall = HeaderRect(520f, 330f, 450f, 300f)
        val slim = slimArtRect(tall, rowEndX = 1032f, rowCentreY = 84f, slimH = 78f)
        // a small version of itself: same proportions, right edge on the bar's end, centred on the bar
        assertEquals(tall.w / tall.h, slim.w / slim.h, 1e-4f)
        assertEquals(1032f, slim.right, 1e-3f)
        assertEquals(84f, slim.y + slim.h / 2, 1e-3f)
        assertEquals(tall, artAt(0f, tall, slim))
        assertEquals(slim, artAt(1f, tall, slim))
        val mid = artAt(0.5f, tall, slim)
        assertTrue(mid.h < tall.h && mid.h > slim.h)
        assertTrue(mid.y < tall.y && mid.y > slim.y)
        // its proportions hold all the way
        assertEquals(tall.w / tall.h, mid.w / mid.h, 1e-3f)
        assertEquals(slim, artAt(3f, tall, slim))
    }

    @Test
    fun aCompactScreenStartsWithTheHeaderFolded() {
        // left at the top: it comes back folded, content high
        assertEquals(400, compactOpenScroll(0, 400))
        // left part-way: folded the rest of the way
        assertEquals(400, compactOpenScroll(120, 400))
        // left further down: it keeps its place
        assertEquals(1800, compactOpenScroll(1800, 400))
        // a Tune step has no header and keeps its scroll
        assertEquals(0, compactOpenScroll(0, 400, hasHeader = false))
    }

    @Test
    fun aLongNameShrinksToFitItsLongestWord() {
        assertEquals(90f, fitTitlePx(90f, 300f, 400f, 60f), 1e-4f)
        assertEquals(72f, fitTitlePx(90f, 500f, 400f, 60f), 1e-4f)
        // never below the floor: it then ellipsises
        assertEquals(60f, fitTitlePx(90f, 2000f, 400f, 60f), 1e-4f)
    }
}
