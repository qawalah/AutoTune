package dev.autotune

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 2.32 audit of every screen and key: the pure rules behind the changes. */
class AuditScreensTest {
    @Test
    fun simpleSetUpAsksNoNames() {
        assertEquals(listOf(SetupPage.EQUIPMENT, SetupPage.SOUND), setupPages(Equipment.CAR, askNames = false))
        assertEquals(SetupPage.SOUND, nextSetupPage(Equipment.CAR, SetupPage.EQUIPMENT, askNames = false))
        // with AutoTune on the TV and no names, the question is the last screen: its yes connects
        assertEquals(listOf(SetupPage.EQUIPMENT, SetupPage.COMPANION), setupPages(Equipment.HOME, viaCompanion = true, askNames = false))
        assertNull(nextSetupPage(Equipment.HOME, SetupPage.COMPANION, viaCompanion = true, askNames = false))
        assertEquals(SetupPage.COMPANION, previousSetupPage(Equipment.PC, SetupPage.SOUND, askNames = false))
        // a names screen left open when Simple is picked shows the screen before it
        assertEquals(SetupPage.EQUIPMENT, shownSetupPage(Equipment.CAR, SetupPage.NAMES, askNames = false))
        assertEquals(SetupPage.NAMES, shownSetupPage(Equipment.CAR, SetupPage.NAMES, askNames = true))
        assertNull(nextSetupPage(Equipment.CAR, SetupPage.NAMES, askNames = false))
    }

    @Test
    fun tuneHomeContinueIsTheMainKeyUntilFinished() {
        assertEquals(TuneHome.NEW_TEST, tuneHome(0, false))
        assertEquals(TuneHome.CONTINUE, tuneHome(2, false))
        assertEquals(TuneHome.TUNED, tuneHome(3, true))
    }

    @Test
    fun theOpenTestIsKeptAcrossARestart() {
        val m = OpenTestMemory("abc", TuneStep.RESULT, 3, 2, "p1", true)
        assertEquals(m, OpenTestMemory.fromText(m.toText()))
        val plain = OpenTestMemory("abc", null, 0, -1, null, false)
        assertEquals(plain, OpenTestMemory.fromText(plain.toText()))
        // Set up is not a place to come back to; nonsense reads as nothing
        assertNull(OpenTestMemory.fromText("1;abc;SETUP;0;-1;;0")?.resumeStep)
        assertNull(OpenTestMemory.fromText("2;abc"))
        assertNull(OpenTestMemory.fromText(null))
        assertNull(OpenTestMemory.fromText("1;;;0;-1;;0"))
    }

    @Test
    fun countsAndNotesReadRight() {
        assertEquals("1 system", countText(1, "system"))
        assertEquals("2 readings", countText(2, "reading"))
        assertTrue(isInfoNote("Averaged 3 mic positions."))
        assertFalse(isInfoNote("The mic clipped around 2 kHz."))
        assertEquals("Bass fades out below 100 Hz.", sentence("Bass fades out below 100 Hz"))
        assertEquals("Done.", sentence("Done."))
        val warns = listOf("Averaged 3 mic positions.", "Road noise is high.")
        val a = analyzeStock(5.0, 2.0, 6, emptyList(), emptyList(), warns, null)
        // the fact is not a finding; the warning is listed once
        assertTrue(a.findings.none { it.detail.startsWith("Averaged") })
        val notes = stockNotes(resultWarnings(listOf(SavedRun(1L, 5.0, DoubleArray(4), emptyList(), "Stock", warnings = warns, complete = true)), 0), a.findings)
        assertEquals(1, notes.count { it.contains("Road noise") })
        assertTrue(notes.none { it.startsWith("Averaged") })
    }

    @Test
    fun moreBelowOnlyForRealContent() {
        // an empty list: content shorter than the screen, only the header's room to scroll
        assertFalse(showsMoreBelow(0, 400, 1200, 1800, 100))
        assertTrue(showsMoreBelow(0, 1400, 3200, 1800, 100))
        assertFalse(showsMoreBelow(1350, 1400, 3200, 1800, 100))
        // sizes unknown: the scroll left decides, as before
        assertTrue(showsMoreBelow(0, 400, -1, -1, 100))
    }

    @Test
    fun chartsDrawOnlyWhatSaysSomething() {
        val base = doubleArrayOf(0.0, 1.0, 2.0)
        assertFalse(predictionDiffers(base, doubleArrayOf(0.1, 1.1, 2.0)))
        assertTrue(predictionDiffers(base, doubleArrayOf(0.0, 3.0, 2.0)))
        assertFalse(drawsNextEq(NextMove.KEEP_LATEST))
        assertFalse(drawsNextEq(NextMove.KEEP_BEST))
        assertTrue(drawsNextEq(NextMove.REFINE))
        assertTrue(drawsNextEq(null))
    }

    @Test
    fun refineListsTheThreeWaysRecommendedFirst() {
        assertEquals(listOf(ApplyWay.AUTOTUNE_EQ, ApplyWay.IN_APP, ApplyWay.EQUIPMENT), applyWays(true, false, false))
        assertEquals(listOf(ApplyWay.IN_APP, ApplyWay.AUTOTUNE_EQ, ApplyWay.EQUIPMENT), applyWays(true, false, true))
        assertEquals(ApplyWay.EQUIPMENT, applyWays(true, true, false).first())
        assertEquals(listOf(ApplyWay.EQUIPMENT), applyWays(false, false, false))
        assertEquals("Set it on the head unit", applyWayText(ApplyWay.EQUIPMENT, equipmentDevice(Equipment.CAR)).first)
        // each fact fits one line beside "recommended · "
        ApplyWay.entries.forEach { assertTrue(applyWayText(it, "the PC").second.length <= 30) }
    }

    @Test
    fun eqTabAndSettingsSayOnlyWhatApplies() {
        val w = listOf("If your EQ app has no preamp setting, lower its output level by 3.3 dB, or boosts can clip.", "Big boost.")
        assertEquals(listOf("Big boost."), eqTabNotes(w, forOtherEquipment = false))
        assertEquals(w, eqTabNotes(w, forOtherEquipment = true))
        assertNull(calibrationLine(phoneMic = true, name = null, info = null))
        assertEquals(false to "No calibration loaded", calibrationLine(phoneMic = false, name = null, info = null))
        assertEquals(true to "umik.txt (100 points)", calibrationLine(false, "umik.txt", "100 points"))
    }

    @Test
    fun connectOffersTheAddressAndSaysWhatItConnects() {
        assertTrue(offersAddress(advanced = true, foundNone = false, lookingMs = 0))
        assertFalse(offersAddress(advanced = false, foundNone = true, lookingMs = 2000))
        assertTrue(offersAddress(advanced = false, foundNone = true, lookingMs = OFFER_ADDRESS_AFTER_MS))
        assertFalse(offersAddress(advanced = false, foundNone = false, lookingMs = 60_000))
        assertEquals("PC speakers", connectTitle(null, Equipment.PC))
        assertEquals("Home theatre", connectTitle(null, Equipment.HOME))
        assertEquals("TV or PC", connectTitle(null, null))
        assertEquals("PC speakers", connectTitle("PC", Equipment.HOME))
        assertEquals("Home theatre", connectTitle("TV", null))
    }

    @Test
    fun theNextSpotStartsByItself() {
        assertEquals(NEXT_SPOT_SECONDS, countdownLeft(1000, 1000))
        assertEquals(NEXT_SPOT_SECONDS - 2, countdownLeft(1000, 3100))
        assertEquals(0, countdownLeft(1000, 1000 + NEXT_SPOT_SECONDS * 1000L + 5))
        assertNull(spotsLine(1))
        assertEquals("3 mic spots · each starts by itself", spotsLine(3))
        assertEquals("spot 1 of 3 done", spotDoneText(1, 3))
        assertTrue(allChecksPassed(listOf(1, 1, 1)))
        assertFalse(allChecksPassed(listOf(1, 2)))
        assertFalse(allChecksPassed(emptyList()))
    }
}
