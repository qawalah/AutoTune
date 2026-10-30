package dev.autotune

import android.content.Context
import dev.autotune.dsp.EqFilter
import java.io.File

/** The single anonymous "Before" run of the first builds. Only read once, to turn it into a named test (TestStore.migrateLegacy). */
class StoredRun(
    val whenMillis: Long,
    val targetIdx: Int,
    val gateMs: Int,
    /** Source ordinal: 0 phone, 1 test file, 2 YouTube pink noise. */
    val sourceIdx: Int,
    val rmsDb: Double,
    val magDb: DoubleArray,
    val filters: List<EqFilter>,
)

object RunStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "before_run.txt")

    fun clear(ctx: Context) { file(ctx).delete() }

    fun load(ctx: Context): StoredRun? = runCatching {
        val all = file(ctx).readLines().filter { it.isNotBlank() }
        val v2 = all.first() == "v2"
        val lines = if (v2) all.drop(1) else all
        val o = if (v2) 1 else 0 // v1 files (earlier build) have no source line
        StoredRun(
            whenMillis = lines[0].toLong(),
            targetIdx = lines[1].toInt(),
            gateMs = lines[2].toInt(),
            sourceIdx = if (v2) lines[3].toInt() else 0,
            rmsDb = lines[3 + o].toDouble(),
            magDb = lines[4 + o].split(",").map { it.toDouble() }.toDoubleArray(),
            filters = lines.drop(5 + o).map { l ->
                val p = l.split(",").map { it.toDouble() }
                EqFilter(p[0], p[1], p[2])
            },
        )
    }.getOrNull()
}
