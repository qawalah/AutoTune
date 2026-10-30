package dev.autotune

import android.content.Context
import dev.autotune.dsp.EqFilter
import dev.autotune.dsp.Target
import java.io.File

/** Calibration name stored with runs measured before it was recorded. */
const val UNKNOWN_CAL = "unknown"

/**
 * One measurement of a test. Run 0 is always the stock system (no EQ) and stays as the reference.
 * [filters] = the EQ AutoTune recommends after looking at this run: the complete EQ for the stock system when
 * [complete] is set (1.9.1+), only the further tweaks on top of [applied] for older runs.
 * [applied] = the EQ that was in effect when this run was measured (what was done for that tune).
 */
class SavedRun(
    val whenMillis: Long,
    val rmsDb: Double,
    val magDb: DoubleArray,
    val filters: List<EqFilter> = emptyList(),
    val label: String = "",
    val note: String = "",
    val applied: List<EqFilter> = emptyList(),
    /** Measurement warnings (low SNR, mic source, ...) kept with the run so they survive reopening the test. */
    val warnings: List<String> = emptyList(),
    /** File name of the mic calibration used, "none" without one, "unknown" for runs saved before 1.9.1. */
    val calibration: String = UNKNOWN_CAL,
    /**
     * True when [filters] is the complete EQ for the stock system (1.9.1 and later). Older runs stored only the further
     * tweaks on top of [applied], so their complete EQ is applied + filters.
     */
    val complete: Boolean = false,
    /**
     * The parametric EQ the user set or was given (1.11+). With AutoTune EQ on, [applied] is what its 31 fixed bands
     * really did (what the mic heard); this is the EQ behind it, which is what gets kept, exported and shown.
     * Null when it is the same as [applied].
     */
    val appliedParam: List<EqFilter>? = null,
    /** After this run no better EQ was found: the suggestion is the best run's EQ again (1.11+). */
    val converged: Boolean = false,
) {
    /** The EQ to show, keep and export for this run. */
    val eq: List<EqFilter> get() = appliedParam ?: applied
}

/** A named test (e.g. "Camry front stage"): its settings and every run, stock first. Source and target never change. */
data class SavedTest(
    val id: String,
    val name: String,
    val created: Long,
    val targetIdx: Int,
    val sourceIdx: Int,
    val gateMs: Int,
    val runs: List<SavedRun>,
    /** [freqHz, prominenceDb, q, t60Ms or NaN, lowerBound 0/1] */
    val resonances: List<DoubleArray> = emptyList(),
    /** Free text about the setup (seat position, tyre pressure, amp settings...). */
    val notes: String = "",
    /** Taste on top of the target curve (1.16+): a bass shelf and a treble shelf, in dB. */
    val bassDb: Double = 0.0,
    val trebleDb: Double = 0.0,
    /** No boosts below this frequency: the speakers were measured not to follow them (1.18+). 0 = none. */
    val boostFloorHz: Double = 0.0,
)

/** One text file per test in filesDir/tests, so tests can never overwrite or leak into each other. */
object TestStore {
    private fun dir(ctx: Context) = File(ctx.filesDir, "tests").also { it.mkdirs() }

    fun save(ctx: Context, t: SavedTest) {
        File(dir(ctx), "${t.id}.txt").writeText(encode(t))
    }

    fun delete(ctx: Context, id: String) {
        File(dir(ctx), "$id.txt").delete()
    }

    /** The first builds kept a single anonymous Before run. Keep it, but as a named test the user can see and delete. */
    fun migrateLegacy(ctx: Context, targetNames: List<String>) {
        val legacy = RunStore.load(ctx) ?: return
        val idx = legacy.targetIdx.coerceIn(0, targetNames.size - 1)
        val when_ = java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(legacy.whenMillis))
        save(
            ctx,
            SavedTest(
                id = java.util.UUID.randomUUID().toString(),
                name = "Earlier test (${targetNames[idx]}, $when_)",
                created = legacy.whenMillis,
                targetIdx = idx,
                sourceIdx = legacy.sourceIdx,
                gateMs = legacy.gateMs,
                runs = listOf(SavedRun(legacy.whenMillis, legacy.rmsDb, legacy.magDb, legacy.filters, STOCK_LABEL, complete = true)),
            ),
        )
        RunStore.clear(ctx)
    }

    const val STOCK_LABEL = "Stock (no EQ)"

    private fun enc(f: EqFilter) = "${f.freqHz},${f.gainDb},${f.q}"

    internal fun encode(t: SavedTest): String = buildString {
        appendLine("v6")
        appendLine("name=${t.name.replace('\n', ' ')}")
        appendLine("created=${t.created}")
        appendLine("target=${t.targetIdx}")
        appendLine("targetname=${Target.ALL.getOrNull(t.targetIdx)?.name.orEmpty().replace('\n', ' ')}")
        appendLine("notes=${t.notes.replace('\n', ' ')}")
        appendLine("source=${t.sourceIdx}")
        appendLine("gate=${t.gateMs}")
        if (t.bassDb != 0.0 || t.trebleDb != 0.0) appendLine("taste=${t.bassDb},${t.trebleDb}")
        if (t.boostFloorHz > 0.0) appendLine("boostfloor=${t.boostFloorHz}")
        t.runs.forEachIndexed { i, r ->
            appendLine("run.$i.when=${r.whenMillis}")
            appendLine("run.$i.label=${r.label.replace('\n', ' ')}")
            appendLine("run.$i.note=${r.note.replace('\n', ' ')}")
            appendLine("run.$i.rms=${r.rmsDb}")
            appendLine("run.$i.mag=${r.magDb.joinToString(",")}")
            r.filters.forEach { appendLine("run.$i.filter=${enc(it)}") }
            r.applied.forEach { appendLine("run.$i.applied=${enc(it)}") }
            appendLine("run.$i.cal=${r.calibration.replace('\n', ' ')}")
            appendLine("run.$i.complete=${if (r.complete) 1 else 0}")
            r.warnings.forEach { appendLine("run.$i.warn=${it.replace('\n', ' ')}") }
            r.appliedParam?.let { p -> appendLine("run.$i.hasparam=1"); p.forEach { appendLine("run.$i.param=${enc(it)}") } }
            if (r.converged) appendLine("run.$i.converged=1")
        }
        t.resonances.forEach { appendLine("res=${it.joinToString(",")}") }
    }

    internal fun decode(text: String): SavedTest {
        val kv = text.lines().filter { it.contains('=') }.map { it.substringBefore('=') to it.substringAfter('=') }
        fun one(k: String) = kv.firstOrNull { it.first == k }?.second
        fun all(k: String) = kv.filter { it.first == k }.map { it.second }
        fun nums(s: String) = s.split(",").map { it.toDouble() }
        fun filt(s: String) = nums(s).let { p -> EqFilter(p[0], p[1], p[2]) }

        val runs = ArrayList<SavedRun>()
        var i = 0
        while (one("run.$i.mag") != null) {
            runs += SavedRun(
                one("run.$i.when")!!.toLong(), one("run.$i.rms")!!.toDouble(), nums(one("run.$i.mag")!!).toDoubleArray(),
                all("run.$i.filter").map(::filt),
                one("run.$i.label").orEmpty().ifBlank { if (i == 0) STOCK_LABEL else "Run $i" },
                one("run.$i.note").orEmpty(),
                all("run.$i.applied").map(::filt),
                all("run.$i.warn"),
                one("run.$i.cal").orEmpty().ifBlank { UNKNOWN_CAL },
                one("run.$i.complete") == "1" || i == 0,
                if (one("run.$i.hasparam") == "1") all("run.$i.param").map(::filt) else null,
                one("run.$i.converged") == "1",
            )
            i++
        }
        if (runs.isEmpty()) { // v3: a Before run and at most one After run
            one("before.mag")?.let {
                val b = SavedRun(
                    one("before.when")!!.toLong(), one("before.rms")!!.toDouble(), nums(it).toDoubleArray(),
                    all("filter").map(::filt), STOCK_LABEL,
                )
                runs += b
                one("after.mag")?.let { am ->
                    runs += SavedRun(one("after.when")!!.toLong(), one("after.rms")!!.toDouble(), nums(am).toDoubleArray(), emptyList(), "Run 1", "", b.filters)
                }
            }
        }
        return SavedTest(
            id = "",
            name = one("name") ?: "Test",
            created = one("created")!!.toLong(),
            targetIdx = resolveTarget(one("targetname"), one("target")!!.toInt()),
            sourceIdx = one("source")!!.toInt(),
            gateMs = one("gate")!!.toInt(),
            runs = runs,
            resonances = all("res").map { r -> r.split(",").map { it.toDouble() }.toDoubleArray() },
            notes = one("notes").orEmpty(),
            bassDb = one("taste")?.split(",")?.getOrNull(0)?.toDoubleOrNull() ?: 0.0,
            trebleDb = one("taste")?.split(",")?.getOrNull(1)?.toDoubleOrNull() ?: 0.0,
            boostFloorHz = one("boostfloor")?.toDoubleOrNull() ?: 0.0,
        )
    }

    /** Targets are stored by name, so deleting a custom curve cannot shift the others. A missing curve falls back to Flat. */
    private fun resolveTarget(name: String?, idx: Int): Int {
        if (!name.isNullOrBlank()) {
            val i = Target.ALL.indexOfFirst { it.name == name }
            return if (i >= 0) i else Target.ALL.indexOf(Target.FLAT)
        }
        return idx.coerceIn(0, Target.BUILT_IN.size - 1)
    }

    /** Newest first. The id is the file name (it is not stored inside the file). */
    fun list(ctx: Context): List<SavedTest> =
        (dir(ctx).listFiles { f -> f.extension == "txt" } ?: emptyArray())
            .mapNotNull { f -> runCatching { decode(f.readText()).copy(id = f.nameWithoutExtension) }.getOrNull() }
            .sortedByDescending { it.created }
}
