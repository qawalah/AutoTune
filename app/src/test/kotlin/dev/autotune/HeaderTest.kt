package dev.autotune

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the tall header shows (2.2.0), and the cue on the slider row. */
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
}
