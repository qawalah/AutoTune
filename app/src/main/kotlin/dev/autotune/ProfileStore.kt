package dev.autotune

import android.content.Context
import dev.autotune.dsp.EqFilter
import java.io.File

/**
 * A saved EQ. [kind] is "hand" (built or saved by the user) or "tuning" (came out of a tuning run: [test], [run] and
 * [device] say which one and where it was measured). [testId] ties it to its test (2.36; empty in older files until
 * [tieProfilesToTests] runs once). [kept]: made by Keep at the end of a tune (one per test), not saved by hand.
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
    val testId: String = "",
    val kept: Boolean = false,
) {
    val fromTuning: Boolean get() = kind == "tuning"
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
        return decode(f.readText())
    }

    @Synchronized
    fun save(ctx: Context, list: List<EqProfile>) {
        profilesFile(ctx).writeText(encode(list))
    }

    /**
     * One profile from its stored keys. Files before 2.36 have no `testId` or `kept`: a kept EQ is then known by the
     * name Keep gave it ("Car · tuned (Run 1)"); older versions ignore the new keys.
     */
    fun profileFrom(id: String, m: Map<String, String>, filters: List<EqFilter>): EqProfile {
        val kind = m["kind"] ?: "hand"
        val name = m["name"].orEmpty()
        val test = m["test"].orEmpty()
        val kept = m["kept"]?.let { it == "1" } ?: (kind == "tuning" && test.isNotBlank() && name.startsWith("$test · tuned"))
        return EqProfile(
            id, name, kind, m["device"].orEmpty(), test, m["run"].orEmpty(), m["created"]?.toLongOrNull() ?: 0L, filters,
            m["testId"].orEmpty(), kept,
        )
    }

    /** The text of profiles.txt (pure, for the tests). */
    fun encode(list: List<EqProfile>): String = buildString {
        list.forEach { p ->
            appendLine("profile")
            appendLine("id=${p.id}"); appendLine("name=${clean(p.name)}"); appendLine("kind=${p.kind}")
            appendLine("device=${clean(p.device)}"); appendLine("test=${clean(p.test)}"); appendLine("run=${clean(p.run)}")
            appendLine("created=${p.created}")
            if (p.testId.isNotBlank()) appendLine("testId=${clean(p.testId)}")
            appendLine("kept=${if (p.kept) "1" else "0"}")
            p.filters.forEach { appendLine("filter=${it.freqHz},${it.gainDb},${it.q}") }
            appendLine("end")
        }
    }

    /** profiles.txt read back (pure, for the tests): every older file loads. */
    fun decode(text: String): List<EqProfile> {
        val out = ArrayList<EqProfile>()
        var m = HashMap<String, String>()
        var filters = ArrayList<EqFilter>()
        text.lines().forEach { line ->
            when {
                line == "profile" -> { m = HashMap(); filters = ArrayList() }
                line == "end" -> m["id"]?.let { out += profileFrom(it, m, filters) }
                line.startsWith("filter=") -> line.removePrefix("filter=").split(',').let { p ->
                    if (p.size == 3) runCatching { filters += EqFilter(p[0].toDouble(), p[1].toDouble(), p[2].toDouble()) }
                }
                "=" in line -> m[line.substringBefore('=')] = line.substringAfter('=')
            }
        }
        return out
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
