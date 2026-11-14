package dev.autotune

import dev.autotune.dsp.EqFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The second audit (2.36): leftover EQs, Back, the TV or PC page and the shorter flow, as pure rules. */
class SecondAuditTest {
    private fun prof(id: String, name: String, test: String = "", testId: String = "", kept: Boolean = false, kind: String = if (test.isBlank()) "hand" else "tuning") =
        EqProfile(id, name, kind, "", test, "measured in Run 1", 0L, listOf(EqFilter(100.0, -3.0, 1.0)), testId, kept)

    @Test
    fun profilesFromEveryOlderFileLoadAndTheNewKeysRoundTrip() {
        // a 2.32 file: no testId, no kept; a kept EQ is known by the name Keep gave it
        val old = "profile\nid=a\nname=Car · tuned (Run 1)\nkind=tuning\ndevice=Phone\ntest=Car\nrun=measured in Run 1\ncreated=5\nfilter=100.0,-3.0,1.0\nend\n" +
            "profile\nid=b\nname=My EQ\nkind=hand\ndevice=\ntest=\nrun=\ncreated=6\nend\n" +
            "profile\nid=c\nname=Car EQ\nkind=tuning\ndevice=Phone\ntest=Car\nrun=after Run 1\ncreated=7\nend\n"
        val l = ProfileStore.decode(old)
        assertEquals(3, l.size)
        assertTrue(l[0].kept); assertFalse(l[1].kept); assertFalse(l[2].kept)
        assertEquals("", l[0].testId)
        assertEquals(1, l[0].filters.size)
        // and what 2.36 writes comes back the same
        val now = listOf(l[0].copy(testId = "t1", name = "Car"), l[1])
        assertEquals(now, ProfileStore.decode(ProfileStore.encode(now)))
    }

    @Test
    fun olderProfilesAreTiedToTheirTestOnceAndTheKeptOneTakesItsName() {
        val ps = listOf(prof("a", "Car · tuned (Run 1)", "Car", kept = true), prof("b", "Car EQ", "Car"), prof("c", "Mine"), prof("d", "Van · tuned (Run 2)", "Van", kept = true))
        val tied = tieProfilesToTests(ps, listOf("t1" to "Car"))
        assertEquals("t1", tied[0].testId); assertEquals("Car", tied[0].name)
        assertEquals("t1", tied[1].testId); assertEquals("Car EQ", tied[1].name)
        assertEquals("", tied[2].testId)
        // its test is gone: left alone
        assertEquals("", tied[3].testId); assertEquals("Van · tuned (Run 2)", tied[3].name)
        // ids never change; running it again changes nothing
        assertEquals(ps.map { it.id }, tied.map { it.id })
        assertEquals(tied, tieProfilesToTests(tied, listOf("t1" to "Car")))
        // a saved EQ already called "Car": the kept one is numbered
        val clash = tieProfilesToTests(listOf(prof("x", "Car"), prof("a", "Car · tuned (Run 1)", "Car", kept = true)), listOf("t1" to "Car"))
        assertEquals("Car 2", clash[1].name)
    }

    @Test
    fun deletingATestTakesItsKeptEqOnly() {
        val ps = listOf(prof("a", "Car", "Car", "t1", kept = true), prof("b", "Car EQ", "Car", "t1"), prof("c", "Mine"), prof("d", "Van", "Van", "t2", kept = true))
        val (left, gone) = profilesAfterTestDelete(ps, "t1")
        assertEquals(setOf("a"), gone)
        assertEquals(listOf("b", "c", "d"), left.map { it.id })
        assertEquals("d", keptEqOf(ps, "t2")?.id)
        assertNull(keptEqOf(ps, "t3"))
    }

    @Test
    fun theEqInUseIsKnownToComeFromTheDeletedTest() {
        assertTrue(pickFromTest("run:t1:2", "t1", emptySet()))
        assertTrue(pickFromTest("suggest:t1", "t1", emptySet()))
        assertTrue(pickFromTest("ai:t1", "t1", emptySet()))
        assertTrue(pickFromTest("profile:a", "t1", setOf("a")))
        assertFalse(pickFromTest("profile:b", "t1", setOf("a")))
        assertFalse(pickFromTest("run:t2:1", "t1", emptySet()))
        assertFalse(pickFromTest("own", "t1", emptySet()))
        assertFalse(pickFromTest("", "t1", emptySet()))
    }

    @Test
    fun aSavedEqReadsAsScoreAndDate() {
        assertTrue(savedEqLine(2.14, "14 Nov 10:20", 8).startsWith("2.1 dB from target · "))
        assertEquals("8 bands · 14 Nov", savedEqLine(null, "14 Nov", 8))
        assertEquals("1 band · 14 Nov", savedEqLine(null, "14 Nov", 1))
    }

    @Test
    fun theEqStatusLineShowsProblemsOnly() {
        assertNull(eqStatusShown("EQ updated: 8 bands, preamp -3.3 dB."))
        assertNull(eqStatusShown("On: Car · Kept EQ · 8 bands, preamp -3.3 dB."))
        assertNull(eqStatusShown("On, flat. Pick an EQ in “EQ currently in use” and it plays at once."))
        assertNull(eqStatusShown("EQ off."))
        assertNull(eqStatusShown(null))
        assertEquals("This phone does not allow the EQ.", eqStatusShown("This phone does not allow the EQ."))
    }

    @Test
    fun backOnTheMeasuringScreenAsksBeforeDroppingSpots() {
        // spot 1 measuring: nothing to lose, Back stops as before
        assertEquals(MeasureBack.STOP, measureBack(MeasureStage.MEASURING, busy = true, spot = 1))
        // spot 2 measuring: spot 1 would be lost
        assertEquals(MeasureBack.ASK, measureBack(MeasureStage.MEASURING, busy = true, spot = 2))
        assertEquals(1, spotsMeasured(MeasureStage.MEASURING, 2))
        // the countdown between spots: spot 1 is done
        assertEquals(MeasureBack.ASK, measureBack(MeasureStage.SPOT_DONE, busy = false, spot = 1))
        assertEquals(1, spotsMeasured(MeasureStage.SPOT_DONE, 1))
        // saved or failed: nothing left to lose
        assertEquals(MeasureBack.CLOSE, measureBack(MeasureStage.SAVED, busy = false, spot = 3))
        assertEquals(MeasureBack.CLOSE, measureBack(MeasureStage.FAILED, busy = false, spot = 2))
        assertEquals("Spot 1 is measured and is dropped.", dropSpotsText(1))
        assertEquals("Spots 1-2 are measured and are dropped.", dropSpotsText(2))
    }

    @Test
    fun backGoesOneStepBackAndEveryStepCanBeLeft() {
        assertEquals(TuneStep.APPLY, previousStep(TuneStep.RESULT, 3))
        assertEquals(TuneStep.HOME, previousStep(TuneStep.APPLY, 3))
        assertEquals(TuneStep.HOME, previousStep(TuneStep.DONE, 3))
        assertEquals(TuneStep.APPLY, previousStep(TuneStep.VERIFY, 1))
        assertEquals(TuneStep.SETUP, previousStep(TuneStep.STOCK, 0))
        // a cancelled reading returns to the screen its key was on
        assertEquals(TuneStep.SETUP, returnAfterMeasuring(TuneStep.SETUP, 0))
        assertEquals(TuneStep.RESULT, returnAfterMeasuring(TuneStep.RESULT, 2))
        for (n in 0..4) TuneStep.entries.forEach { st -> var x = st; repeat(4) { x = previousStep(x, n) }; assertEquals("$st $n", TuneStep.HOME, x) }
    }

    @Test
    fun setUpSkipsWhatIsRemembered() {
        // used before, measured by the phone: straight to the sound screen
        assertTrue(setupStartsAtSound(usedBefore = true, e = Equipment.CAR, installed = false))
        assertTrue(setupStartsAtSound(true, Equipment.HOME, installed = false))
        // first time, or AutoTune on the TV or PC: asks as before
        assertFalse(setupStartsAtSound(false, Equipment.CAR, false))
        assertFalse(setupStartsAtSound(true, Equipment.PC, installed = true))
        assertEquals("car · as last time", asLastTime(Equipment.CAR))
        // the AutoTune question skipped for a TV remembered with it: Simple connects from the first screen
        assertEquals(listOf(SetupPage.EQUIPMENT), setupPages(Equipment.HOME, viaCompanion = true, askNames = false, askCompanion = false))
        assertNull(nextSetupPage(Equipment.HOME, SetupPage.EQUIPMENT, true, askNames = false, askCompanion = false))
        assertEquals(SetupPage.NAMES, nextSetupPage(Equipment.PC, SetupPage.EQUIPMENT, true, askNames = true, askCompanion = false))
        assertEquals(SetupPage.EQUIPMENT, previousSetupPage(Equipment.PC, SetupPage.NAMES, true, askNames = true, askCompanion = false))
        // a car never has the question
        assertEquals(setupPages(Equipment.CAR), setupPages(Equipment.CAR, askCompanion = false))
    }

    @Test
    fun keepingIsDone() {
        assertEquals("Done" to "keeps this EQ", keepKey(NextMove.KEEP_LATEST, "Run 2"))
        assertEquals("Done" to "keeps the EQ of Run 1", keepKey(NextMove.KEEP_BEST, "Run 1"))
        assertNull(keepKey(NextMove.REFINE, null))
        assertNull(keepKey(null, null))
    }

    @Test
    fun theConnectPageTicksItsThreeSteps() {
        assertEquals("the PC", connectDevice(null, Equipment.PC))
        assertEquals("the TV", connectDevice(null, Equipment.HOME))
        assertEquals("the TV or PC", connectDevice(null, null))
        assertEquals("Living room TV", connectDevice("Living room TV", Equipment.HOME))
        val none = connectSteps("the TV", found = false, codeLength = 2)
        assertEquals(listOf("AutoTune open on the TV", "found on this Wi-Fi", "code entered"), none.map { it.first })
        assertTrue(none.none { it.second })
        assertEquals(listOf(true, true, true), connectSteps("the TV", true, 4).map { it.second })
    }

    @Test
    fun theAddressIsOfferedAfterTheWait() {
        assertFalse(offersAddress(false, true, OFFER_ADDRESS_AFTER_MS - 1000))
        assertTrue(offersAddress(false, true, OFFER_ADDRESS_AFTER_MS))
    }

    private val list = listOf(
        TheatreEntry("1", "Living room", "LG TV"), TheatreEntry("2", "Desk", "My PC"),
        TheatreEntry("3", "Bedroom", "lg tv"), TheatreEntry("4", "New one", null),
    )

    @Test
    fun theListShowsTheConnectedDevicesOnlyElseGroupsThem() {
        val tv = theatreGroups(list, "LG TV")
        assertEquals(1, tv.size)
        assertEquals(listOf("1", "3"), tv[0].second.map { it.id })
        val all = theatreGroups(list, null)
        assertEquals(listOf("LG TV", "My PC", null), all.map { it.first })
        assertEquals(listOf("1", "3"), all[0].second.map { it.id })
        assertTrue(theatreMayOpen("LG TV", "lg tv"))
        assertFalse(theatreMayOpen("My PC", "LG TV"))
        assertTrue(theatreMayOpen("My PC", null))
        assertEquals("PC speakers", theatreListTitle(true))
        assertEquals("Home theatres", theatreListTitle(false))
    }

    @Test
    fun deletingAHomeTheatreOpensAnotherOfTheSameDeviceOnly() {
        val rest = list.filter { it.id != "1" }
        assertEquals("3", theatreAfterDelete(rest, "LG TV")?.id)
        assertNull(theatreAfterDelete(rest.filter { it.id != "3" }, "LG TV"))
        assertNull(theatreAfterDelete(rest, null))
        // its EQ is the one last sent: by the id kept with it, else (sent before 2.36) by the remembered device
        assertTrue(clearsCompanionEq("1", "LG TV", "1", "LG TV", hasBase = true))
        assertFalse(clearsCompanionEq("1", "LG TV", "3", "LG TV", hasBase = true))
        assertTrue(clearsCompanionEq("1", "LG TV", null, "lg tv", hasBase = true))
        assertFalse(clearsCompanionEq("2", "My PC", null, "LG TV", hasBase = true))
        assertFalse(clearsCompanionEq("1", "LG TV", "1", "LG TV", hasBase = false))
    }

    @Test
    fun fromTuneTheRememberedDevicesHomeTheatreOpens() {
        assertEquals("1", theatreFromTune(list, "LG TV", rememberedPc = false, pickedPc = false)?.id)
        // PC speakers picked while a TV is remembered: a new one
        assertNull(theatreFromTune(list, "LG TV", rememberedPc = false, pickedPc = true))
        assertNull(theatreFromTune(list, null, false, false))
        assertNull(theatreFromTune(list, "Other TV", false, false))
        assertTrue(theatreShowsEq(hasEqRun = true, eqOn = false))
        assertTrue(theatreShowsEq(false, true))
        assertFalse(theatreShowsEq(false, false))
    }
}
