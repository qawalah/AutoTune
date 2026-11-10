package dev.autotune.theatre

import dev.autotune.dsp.ChannelClip
import dev.autotune.dsp.FreqGrid
import dev.autotune.dsp.Layout
import dev.autotune.dsp.Speaker
import dev.autotune.dsp.SpeakerReading
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/** The three home-theatre tests (2.16), and the speaker-by-speaker test of older TVs and PCs. */
enum class RunKind(val code: String, val title: String) {
    LEVELS("levels", "Levels"), DISTANCES("distances", "Distances"), EQ("eq", "EQ"), SPEAKERS("speakers", "Speakers");

    companion object {
        fun of(code: String?): RunKind? = entries.firstOrNull { it.code == code }
    }
}

/**
 * One run of a home-theatre test. [levels]: the change left on each speaker when the level test ended (+ = up).
 * [readings]: each speaker on its own (distances, or the old per-speaker test). [system]: every speaker together
 * at the seat, averaged over [seats]. [eqInUse]: where an EQ was on while it played ("on the TV"), null when none.
 */
class TheatreRun(
    val kind: RunKind,
    val whenMillis: Long,
    val layout: Layout,
    val device: String,
    val pretend: Boolean = false,
    val real: String? = null,
    val levels: Map<Speaker, Double> = emptyMap(),
    val readings: List<SpeakerReading> = emptyList(),
    val system: SpeakerReading? = null,
    val seats: Int = 1,
    val eqInUse: String? = null,
    val note: String = "",
) {
    fun withNote(n: String) = TheatreRun(kind, whenMillis, layout, device, pretend, real, levels, readings, system, seats, eqInUse, n)
}

/** A named home theatre ("Living room 5.1") and every run made on it, oldest first. */
class TheatreTest(val id: String, val name: String, val createdMillis: Long, val runs: List<TheatreRun>) {
    fun with(r: TheatreRun) = TheatreTest(id, name, createdMillis, runs + r)
    fun renamed(n: String) = TheatreTest(id, n.trim().ifEmpty { name }, createdMillis, runs)
    fun replaceLast(r: TheatreRun) = TheatreTest(id, name, createdMillis, runs.dropLast(1) + r)

    fun last(kind: RunKind): TheatreRun? = runs.lastOrNull { it.kind == kind }

    /** The EQ run every EQ is fitted from: the first whose system was heard, never a pretend one. */
    val eqStock: TheatreRun? get() = runs.firstOrNull { it.kind == RunKind.EQ && !it.pretend && it.system?.heard == true }
}

/**
 * Home-theatre tests in `filesDir/theatre/tests/<id>.txt`: a versioned text format, every older version loads.
 * The 2.13-2.15 `stock.txt` and `latest.txt` become the runs of a first test ([migrate]).
 */
object TheatreTests {
    const val VERSION = 1
    private const val HEAD = "autotune-theatre-test"
    private const val ALL = "ALL"

    fun dir(base: File) = File(base, "theatre/tests")

    fun encode(t: TheatreTest): String = buildString {
        appendLine("$HEAD $VERSION")
        appendLine("name=${enc(t.name)}")
        appendLine("created=${t.createdMillis}")
        for (r in t.runs) {
            appendLine(
                "run=${r.kind.code};when=${r.whenMillis};layout=${r.layout.name};device=${enc(r.device)};pretend=${if (r.pretend) 1 else 0}" +
                    ";seats=${r.seats}" + (r.real?.let { ";real=${enc(it)}" } ?: "") + (r.eqInUse?.let { ";eqinuse=${enc(it)}" } ?: "") +
                    (if (r.note.isNotEmpty()) ";note=${enc(r.note)}" else ""),
            )
            if (r.levels.isNotEmpty()) appendLine("lv=" + r.levels.entries.joinToString(",") { "${it.key.code}:${f(it.value, 1)}" })
            for ((code, rd) in r.readings.map { it.speaker.code to it } + listOfNotNull(r.system?.let { ALL to it })) {
                append("r=$code")
                rd.deltaMs?.let { append(";delta=${f(it, 3)}") }
                rd.snrDb?.let { append(";snr=${f(it, 1)}") }
                rd.fatal?.let { append(";fatal=${enc(it)}") }
                appendLine()
                rd.magDb?.let { m -> appendLine("m=$code;" + m.joinToString(",") { f(it, 2) }) }
            }
        }
    }

    fun decode(id: String, text: String, grid: FreqGrid = FreqGrid()): TheatreTest? {
        val lines = text.lines()
        val head = lines.firstOrNull()?.split(' ') ?: return null
        if (head.getOrNull(0) != HEAD || (head.getOrNull(1)?.toIntOrNull() ?: return null) < 1) return null
        var name = "Home theatre"; var created = 0L
        val runs = ArrayList<TheatreRun>()
        // the run being read: its fields, then its readings as they come
        var meta: Map<String, String>? = null
        var levels: Map<Speaker, Double> = emptyMap()
        val rMeta = LinkedHashMap<String, Map<String, String>>()
        val mags = HashMap<String, DoubleArray>()
        fun close() {
            val m = meta ?: return
            val kind = RunKind.of(m["run"]); val layout = Layout.of(m["layout"])
            if (kind != null && layout != null) {
                fun reading(code: String, s: Speaker) = rMeta[code]?.let { q ->
                    SpeakerReading(s, mags[code], q["delta"]?.toDoubleOrNull(), q["snr"]?.toDoubleOrNull(), q["fatal"]?.let(::dec))
                }
                runs += TheatreRun(
                    kind, m["when"]?.toLongOrNull() ?: 0L, layout, m["device"]?.let(::dec).orEmpty(), m["pretend"] == "1", m["real"]?.let(::dec),
                    levels, rMeta.keys.filter { it != ALL }.mapNotNull { c -> Speaker.of(c)?.let { reading(c, it) } },
                    reading(ALL, ChannelClip.REFERENCE), m["seats"]?.toIntOrNull() ?: 1, m["eqinuse"]?.let(::dec), m["note"]?.let(::dec).orEmpty(),
                )
            }
            meta = null; levels = emptyMap(); rMeta.clear(); mags.clear()
        }
        for (l in lines.drop(1)) {
            val eq = l.indexOf('=')
            if (eq <= 0) continue
            val k = l.substring(0, eq); val v = l.substring(eq + 1)
            when (k) {
                "name" -> name = dec(v)
                "created" -> created = v.toLongOrNull() ?: 0L
                "run" -> {
                    close()
                    val parts = v.split(';')
                    meta = mapOf("run" to parts[0]) + parts.drop(1).mapNotNull { p -> p.indexOf('=').takeIf { it > 0 }?.let { p.substring(0, it) to p.substring(it + 1) } }
                }
                "lv" -> levels = v.split(',').mapNotNull { p ->
                    val c = p.indexOf(':'); if (c <= 0) null else Speaker.of(p.substring(0, c))?.let { s -> p.substring(c + 1).toDoubleOrNull()?.let { s to it } }
                }.toMap()
                "r" -> {
                    val parts = v.split(';')
                    rMeta[parts[0]] = parts.drop(1).mapNotNull { p -> p.indexOf('=').takeIf { it > 0 }?.let { p.substring(0, it) to p.substring(it + 1) } }.toMap()
                }
                "m" -> {
                    val sc = v.indexOf(';')
                    val vals = v.substring(sc + 1).split(',').mapNotNull { it.toDoubleOrNull() }
                    if (sc > 0 && vals.size == grid.size) mags[v.substring(0, sc)] = vals.toDoubleArray()
                }
            }
        }
        close()
        return TheatreTest(id, name, created, runs)
    }

    fun list(base: File): List<TheatreTest> =
        dir(base).listFiles { f -> f.name.endsWith(".txt") }.orEmpty().mapNotNull { f -> runCatching { decode(f.nameWithoutExtension, f.readText()) }.getOrNull() }
            .sortedByDescending { t -> t.runs.lastOrNull()?.whenMillis ?: t.createdMillis }

    fun load(base: File, id: String): TheatreTest? =
        runCatching { File(dir(base), "$id.txt").takeIf { it.exists() }?.readText()?.let { decode(id, it) } }.getOrNull()

    fun save(base: File, t: TheatreTest) {
        val d = dir(base).apply { mkdirs() }
        val tmp = File(d, "${t.id}.tmp")
        tmp.writeText(encode(t))
        tmp.renameTo(File(d, "${t.id}.txt"))
    }

    fun delete(base: File, id: String) { File(dir(base), "$id.txt").delete() }

    fun newId(): String = "ht" + System.currentTimeMillis().toString(36)

    /** The runs of 2.13-2.15 (stock and latest) as a first test, once; true when there was something to bring over. */
    fun migrate(base: File): TheatreTest? {
        if (dir(base).listFiles()?.isNotEmpty() == true) return null
        val old = File(base, "theatre")
        val sessions = listOfNotNull(TheatreStore.load(old, "stock"), TheatreStore.load(old, "latest")).distinctBy { it.whenMillis }
        if (sessions.isEmpty()) return null
        val runs = sessions.sortedBy { it.whenMillis }.flatMap { s -> fromSession(s) }
        val t = TheatreTest(newId(), "Home theatre", sessions.minOf { it.whenMillis }, runs)
        save(base, t)
        return t
    }

    /** A 2.13-2.15 session as runs: its distances and its system (2.15), or its speaker-by-speaker readings. */
    fun fromSession(s: TheatreSession): List<TheatreRun> =
        if (s.system != null) listOf(
            TheatreRun(RunKind.DISTANCES, s.whenMillis, s.layout, s.tv, s.pretend, s.real, readings = s.readings),
            TheatreRun(RunKind.EQ, s.whenMillis, s.layout, s.tv, s.pretend, s.real, system = s.system),
        )
        else listOf(TheatreRun(RunKind.SPEAKERS, s.whenMillis, s.layout, s.tv, s.pretend, s.real, readings = s.readings))

    private fun f(x: Double, d: Int) = String.format(Locale.US, "%.${d}f", x)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
}
