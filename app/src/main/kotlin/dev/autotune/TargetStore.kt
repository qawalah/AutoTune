package dev.autotune

import android.content.Context
import dev.autotune.dsp.Target
import java.io.File

/** The user's own target curves (imported files, or "save this response as a target"), one text file each. */
object TargetStore {
    private fun dir(ctx: Context) = File(ctx.filesDir, "targets").also { it.mkdirs() }

    private fun fileFor(ctx: Context, name: String) = File(dir(ctx), name.replace(Regex("[^A-Za-z0-9._ -]"), "_") + ".txt")

    fun load(ctx: Context): List<Target> =
        (dir(ctx).listFiles { f -> f.extension == "txt" } ?: emptyArray()).sortedBy { it.name }.mapNotNull { f ->
            runCatching {
                val lines = f.readLines()
                Target.parse(lines.first().removePrefix("# name: "), lines.drop(1).joinToString("\n"))
            }.getOrNull()
        }

    fun save(ctx: Context, t: Target) {
        fileFor(ctx, t.name).writeText("# name: ${t.name}\n" + t.toText())
    }

    fun delete(ctx: Context, name: String) {
        fileFor(ctx, name).delete()
    }
}
