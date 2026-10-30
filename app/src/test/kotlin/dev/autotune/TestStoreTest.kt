package dev.autotune

import dev.autotune.dsp.EqFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestStoreTest {
    private fun run(label: String, n: Int, applied: List<EqFilter> = emptyList(), note: String = "") =
        SavedRun(1_000L * n, 3.0 - n * 0.5, DoubleArray(5) { it + n.toDouble() / 10 }, listOf(EqFilter(100.0 * (n + 1), -2.5, 3.0)), label, note, applied)

    /** The user's complaint: after the second run the data was gone. Every run must survive a save/load cycle. */
    @Test
    fun everyRunSurvivesSaveAndLoad() {
        val eq = listOf(EqFilter(63.0, -3.0, 4.0), EqFilter(2500.0, 2.0, 1.2))
        val t = SavedTest(
            "id", "Camry = front stage", 5L, 0, 0, 80,
            listOf(run("Stock (no EQ)", 0), run("Run 1", 1, eq, "applied = 5 bands"), run("Run 2", 2, eq + EqFilter(125.0, 1.0, 2.0))),
            notes = "seat = driver",
        )
        val back = TestStore.decode(TestStore.encode(t))
        assertEquals(3, back.runs.size)
        assertEquals(listOf("Stock (no EQ)", "Run 1", "Run 2"), back.runs.map { it.label })
        assertEquals("applied = 5 bands", back.runs[1].note)
        assertEquals(3, back.runs[2].applied.size)
        assertEquals(t.runs[2].magDb.toList(), back.runs[2].magDb.toList())
        assertEquals("Camry = front stage", back.name)
        assertEquals("seat = driver", back.notes)
        assertTrue(back.runs[2].filters.isNotEmpty())
    }

    @Test
    fun warningsCalibrationAndCompletenessSurviveSaveAndLoad() {
        val t = SavedTest(
            "id", "Test", 5L, 0, 0, 80,
            listOf(
                SavedRun(1L, 3.0, DoubleArray(3) { it.toDouble() }, listOf(EqFilter(100.0, -2.0, 3.0)), "Stock (no EQ)", complete = true, calibration = "umik.txt",
                    warnings = listOf("Low signal-to-noise (30 dB)", "Mic source: MIC")),
                SavedRun(2L, 2.0, DoubleArray(3), emptyList(), "Run 1 (repeat)", complete = true, calibration = "none"),
            ),
        )
        val back = TestStore.decode(TestStore.encode(t))
        assertEquals(listOf("Low signal-to-noise (30 dB)", "Mic source: MIC"), back.runs[0].warnings)
        assertEquals("umik.txt", back.runs[0].calibration)
        assertEquals("none", back.runs[1].calibration)
        assertEquals("Run 1 (repeat)", back.runs[1].label)
        assertTrue(back.runs[1].complete)
    }

    /** Files written by 1.9.0 and before have none of the new fields. */
    @Test
    fun versionFourFilesStillLoad() {
        val v4 = """
            v4
            name=Old
            created=5
            target=0
            notes=
            source=0
            gate=80
            run.0.when=1
            run.0.label=Stock (no EQ)
            run.0.note=
            run.0.rms=3.0
            run.0.mag=1.0,2.0,3.0
            run.0.filter=100.0,-2.0,3.0
            run.1.when=2
            run.1.label=Run 1
            run.1.note=
            run.1.rms=2.5
            run.1.mag=1.0,2.0,3.0
            run.1.filter=1000.0,1.0,1.0
            run.1.applied=100.0,-2.0,3.0
        """.trimIndent()
        val t = TestStore.decode(v4)
        assertEquals(2, t.runs.size)
        assertEquals(UNKNOWN_CAL, t.runs[1].calibration)
        assertTrue(t.runs[1].warnings.isEmpty())
        assertTrue(!t.runs[1].complete) // its filters are the further tweaks on top of what was applied
        assertTrue(t.runs[0].complete)
    }
}
