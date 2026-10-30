package dev.autotune

import android.content.Context
import dev.autotune.dsp.EqFilter
import java.io.File

/**
 * A saved EQ. [kind] is "hand" (built or saved by the user) or "tuning" (came out of a tuning run: [test], [run] and
 * [device] say which one and where it was measured).
 */
data class EqProfile(
    val id: String,
    val name: String,
    val kind: String,
    val device: String,
    val test: String,
    val run: String,
    val created: Long,
    val filters: List<EqFilter>,
) {
    val fromTuning: Boolean get() = kind == "tuning"

    /** One line saying where it came from. */
    val origin: String
        get() = if (fromTuning) listOfNotNull("Tuned", device.takeIf { it.isNotBlank() }, run.takeIf { it.isNotBlank() }).joinToString(" · ")
        else "Saved by hand"
}

/** When this Bluetooth device connects, switch to [profileId]. */
data class AutoRule(val address: String, val deviceName: String, val profileId: String)

/** Profiles and automation rules, one small text file each in filesDir. Used by the app and by the background service. */
object ProfileStore {
    private fun profilesFile(ctx: Context) = File(ctx.filesDir, "profiles.txt")
    private fun rulesFile(ctx: Context) = File(ctx.filesDir, "rules.txt")
    private fun clean(s: String) = s.replace('\n', ' ').replace('\r', ' ')

    @Synchronized
    fun load(ctx: Context): List<EqProfile> {
        val f = profilesFile(ctx)
        if (!f.exists()) return emptyList()
        val out = ArrayList<EqProfile>()
        var m = HashMap<String, String>()
        var filters = ArrayList<EqFilter>()
        fun flush() {
            val id = m["id"] ?: return
            out += EqProfile(
                id, m["name"].orEmpty(), m["kind"] ?: "hand", m["device"].orEmpty(), m["test"].orEmpty(), m["run"].orEmpty(),
                m["created"]?.toLongOrNull() ?: 0L, filters,
            )
        }
        f.readLines().forEach { line ->
            when {
                line == "profile" -> { m = HashMap(); filters = ArrayList() }
                line == "end" -> flush()
                line.startsWith("filter=") -> line.removePrefix("filter=").split(',').let { p ->
                    if (p.size == 3) runCatching { filters += EqFilter(p[0].toDouble(), p[1].toDouble(), p[2].toDouble()) }
                }
                "=" in line -> m[line.substringBefore('=')] = line.substringAfter('=')
            }
        }
        return out
    }

    @Synchronized
    fun save(ctx: Context, list: List<EqProfile>) {
        profilesFile(ctx).writeText(buildString {
            list.forEach { p ->
                appendLine("profile")
                appendLine("id=${p.id}"); appendLine("name=${clean(p.name)}"); appendLine("kind=${p.kind}")
                appendLine("device=${clean(p.device)}"); appendLine("test=${clean(p.test)}"); appendLine("run=${clean(p.run)}")
                appendLine("created=${p.created}")
                p.filters.forEach { appendLine("filter=${it.freqHz},${it.gainDb},${it.q}") }
                appendLine("end")
            }
        })
    }

    @Synchronized
    fun loadRules(ctx: Context): List<AutoRule> {
        val f = rulesFile(ctx)
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { l ->
            val p = l.split('\t')
            if (p.size == 3) AutoRule(p[0], p[1], p[2]) else null
        }
    }

    @Synchronized
    fun saveRules(ctx: Context, rules: List<AutoRule>) {
        rulesFile(ctx).writeText(rules.joinToString("\n") { "${it.address}\t${clean(it.deviceName).replace('\t', ' ')}\t${it.profileId}" })
    }
}
