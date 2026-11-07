package dev.autotune

import dev.autotune.ui.GEAR_BODY_R
import dev.autotune.ui.Ic
import dev.autotune.ui.KNOB_CX
import dev.autotune.ui.KNOB_CY
import dev.autotune.ui.KNOB_POINTER_DEG
import dev.autotune.ui.KNOB_R
import dev.autotune.ui.Tab
import dev.autotune.ui.gearTeeth
import dev.autotune.ui.knobEdge
import dev.autotune.ui.knobTicks
import dev.autotune.ui.flavourIcon
import dev.autotune.ui.loudModeIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The icon set: older entries keep their place, each flavour says what it is, Tune is a knob and Settings a gear (2.3.0). */
class IconTest {
    @Test
    fun olderIconsKeepTheirOrder() {
        val before = "TESTS MEASURE EQ LIVE EXPORT GEAR PLUS INFO CHEVRON CHEVRON_DOWN CLOSE CHECK BACK ALERT MORE MENU " +
            "TUNE MIC SPEAKER PLAY STOP SHARE SAVE AGAIN TRASH POWER UNDO EDIT DIAG DONE BLUETOOTH NEXT FILE " +
            "CAR HOME FLAT CURVE LOCK UNLOCK AI SEND EXPAND"
        assertEquals(before.split(' '), Ic.entries.take(42).map { it.name })
    }

    @Test
    fun everyFlavourHasItsOwnIcon() {
        val icons = Flavour.entries.map { flavourIcon(it) }
        assertEquals(Flavour.entries.size, icons.toSet().size)
        // none of them is the generic curve or speaker they used to share
        icons.forEach { assertNotEquals(Ic.CURVE, it); assertNotEquals(Ic.SPEAKER, it) }
    }

    @Test
    fun everyDynamicModeHasItsOwnIcon() = assertEquals(LoudMode.entries.size, LoudMode.entries.map { loudModeIcon(it) }.toSet().size)

    @Test
    fun the22IconsKeepTheirPlaceToo() {
        val added = "BASS TREBLE GAIN VOLUME NIGHT SPEECH DRUM VOCAL GUITAR SYNTH"
        assertEquals(added.split(' '), Ic.entries.drop(42).take(10).map { it.name })
    }

    @Test
    fun tuneIsTheKnobAndSettingsTheGear() {
        assertEquals(Ic.TUNE, Tab.MEASURE.icon)
        assertEquals(Ic.GEAR, Tab.SETTINGS.icon)
        assertNotEquals(Tab.MEASURE.icon, Tab.SETTINGS.icon)
    }

    @Test
    fun theKnobsScaleSitsOverItsUpperHalf() {
        val ticks = knobTicks()
        assertEquals(5, ticks.size)
        ticks.forEach { d -> assertTrue("tick at $d is not above the centre", Math.sin(Math.toRadians(d.toDouble())) < 0) }
        // evenly spaced and symmetric round the top
        val steps = ticks.zipWithNext { a, b -> b - a }
        steps.forEach { assertEquals(steps[0], it, 1e-4f) }
        assertEquals(-90f, (ticks.first() + ticks.last()) / 2, 1e-4f)
        // the pointer points at one of the ticks
        assertTrue(ticks.any { Math.abs(it - KNOB_POINTER_DEG) < 1e-3f })
    }

    @Test
    fun theKnobIsRidgedButRound() {
        val edge = knobEdge(KNOB_CX, KNOB_CY, KNOB_R)
        val radii = edge.map { (x, y) -> Math.hypot((x - KNOB_CX).toDouble(), (y - KNOB_CY).toDouble()) }
        // shallow ridges: the outline stays within a quarter unit of the circle, so it never reads as a gear
        assertTrue(radii.all { Math.abs(it - KNOB_R) <= 0.23 + 1e-6 })
        assertTrue(radii.max() - radii.min() > 0.25)
        // all of it inside the 24-unit square, with the scale
        edge.forEach { (x, y) -> assertTrue(x in 0f..24f && y in 0f..24f) }
        assertTrue(KNOB_CY - 10.8f - 0.9f >= 0f)
    }

    @Test
    fun theGearHasEightEvenSquareTeeth() {
        val teeth = gearTeeth()
        assertEquals(8, teeth.size)
        val angles = teeth.map { t ->
            val mx = t.map { it.first }.average(); val my = t.map { it.second }.average()
            Math.toDegrees(Math.atan2(my - 12, mx - 12))
        }
        angles.zipWithNext { a, b -> ((b - a + 360) % 360) }.forEach { assertEquals(45.0, it, 1e-3) }
        teeth.forEach { t ->
            assertEquals(4, t.size)
            // square ends: the two sides of a tooth are parallel and as long as each other
            val side1 = Math.hypot((t[1].first - t[0].first).toDouble(), (t[1].second - t[0].second).toDouble())
            val side2 = Math.hypot((t[2].first - t[3].first).toDouble(), (t[2].second - t[3].second).toDouble())
            assertEquals(side1, side2, 1e-4)
            // they reach past the hub and stay inside the square with the stroke
            t.forEach { (x, y) -> assertTrue(Math.hypot(x - 12.0, y - 12.0) > GEAR_BODY_R - 1f); assertTrue(x in 1f..23f && y in 1f..23f) }
        }
    }
}
