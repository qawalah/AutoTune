package dev.autotune

import dev.autotune.ui.Ic
import dev.autotune.ui.flavourIcon
import dev.autotune.ui.loudModeIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The icon set of 2.2.0: older entries keep their place, each flavour says what it is. */
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
}
