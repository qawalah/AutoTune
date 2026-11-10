package dev.autotune.theatre

import dev.autotune.dsp.FreqGrid
import dev.autotune.dsp.Layout
import dev.autotune.dsp.PassClip
import dev.autotune.dsp.PassReading
import dev.autotune.dsp.PassReport
import dev.autotune.dsp.PlayMethod
import dev.autotune.dsp.Speaker
import dev.autotune.dsp.SpeakerReading
import dev.autotune.dsp.TheatreMove
import dev.autotune.dsp.TheatreReport
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

/**
 * One test of every speaker of a home theatre. [system] is the whole system playing together, from a one-pass system
 * test (2.15); [pretend] marks a phone or tablet standing in for a bigger system, and [real] says what it really is.
 */
class TheatreSession(
    val whenMillis: Long,
    val layout: Layout,
    val method: PlayMethod,
    val tv: String,
    val readings: List<SpeakerReading>,
    val system: SpeakerReading? = null,
    val pretend: Boolean = false,
    val real: String? = null,
) {
    val complete: Boolean get() = layout.speakers.all { s -> readings.any { it.speaker == s } }

    /** [r] in place of this speaker's earlier reading. */
    fun with(r: SpeakerReading, at: Long = whenMillis): TheatreSession =
        TheatreSession(at, layout, method, tv, readings.filter { it.speaker != r.speaker } + r, system, pretend, real)

    val pass: PassReading get() = PassReading(readings, system)
}

/**
 * Home-theatre sessions in `filesDir/theatre/`: `stock.txt` is the first full test (every EQ is fitted from it, so
 * EQs never stack) and `latest.txt` the newest. A versioned text format; every older version keeps loading.
 */
object TheatreStore {
    /** 2 (2.15): the whole system's reading (`r=ALL`, `m=ALL`), `pretend` and `real`. */
    const val VERSION = 2
    private const val HEAD = "autotune-theatre"
    private const val ALL = "ALL"

    fun encode(s: TheatreSession): String = buildString {
        appendLine("$HEAD $VERSION")
        appendLine("when=${s.whenMillis}")
        appendLine("layout=${s.layout.name}")
        appendLine("method=${s.method.name}")
        appendLine("tv=${enc(s.tv)}")
        if (s.pretend) appendLine("pretend=1")
        s.real?.let { appendLine("real=${enc(it)}") }
        for ((code, r) in s.readings.map { it.speaker.code to it } + listOfNotNull(s.system?.let { ALL to it })) {
            append("r=$code")
            r.deltaMs?.let { append(";delta=${f(it, 3)}") }
            r.snrDb?.let { append(";snr=${f(it, 1)}") }
            r.fatal?.let { append(";fatal=${enc(it)}") }
            appendLine()
            r.magDb?.let { m -> appendLine("m=$code;" + m.joinToString(",") { f(it, 2) }) }
        }
    }

    fun decode(text: String, grid: FreqGrid = FreqGrid()): TheatreSession? {
        val lines = text.lines()
        val head = lines.firstOrNull()?.split(' ') ?: return null
        if (head.getOrNull(0) != HEAD || (head.getOrNull(1)?.toIntOrNull() ?: return null) < 1) return null
        var whenMs = 0L; var layout: Layout? = null; var method: PlayMethod? = null; var tv = ""
        var pretend = false; var real: String? = null
        val meta = LinkedHashMap<Speaker, Map<String, String>>()
        val mags = HashMap<Speaker, DoubleArray>()
        var sysMeta: Map<String, String>? = null; var sysMag: DoubleArray? = null
        for (l in lines.drop(1)) {
            val eq = l.indexOf('=')
            if (eq <= 0) continue
            val k = l.substring(0, eq); val v = l.substring(eq + 1)
            when (k) {
                "when" -> whenMs = v.toLongOrNull() ?: 0L
                "layout" -> layout = Layout.of(v)
                "method" -> method = PlayMethod.of(v)
                "tv" -> tv = dec(v)
                "pretend" -> pretend = v == "1"
                "real" -> real = dec(v)
                "r" -> {
                    val parts = v.split(';')
                    val kv = parts.drop(1).mapNotNull { p -> p.indexOf('=').takeIf { it > 0 }?.let { p.substring(0, it) to p.substring(it + 1) } }.toMap()
                    if (parts[0] == ALL) { sysMeta = kv; continue }
                    val s = Speaker.of(parts[0]) ?: continue
                    meta[s] = kv
                }
                "m" -> {
                    val sc = v.indexOf(';')
                    val code = v.substring(0, sc.coerceAtLeast(0))
                    val vals = v.substring(sc + 1).split(',').mapNotNull { it.toDoubleOrNull() }
                    if (vals.size != grid.size) continue
                    if (code == ALL) { sysMag = vals.toDoubleArray(); continue }
                    val s = Speaker.of(code) ?: continue
                    mags[s] = vals.toDoubleArray()
                }
            }
        }
        val readings = meta.map { (s, m) ->
            SpeakerReading(s, mags[s], m["delta"]?.toDoubleOrNull(), m["snr"]?.toDoubleOrNull(), m["fatal"]?.let(::dec))
        }
        val system = sysMeta?.let { m ->
            SpeakerReading(dev.autotune.dsp.ChannelClip.REFERENCE, sysMag, m["delta"]?.toDoubleOrNull(), m["snr"]?.toDoubleOrNull(), m["fatal"]?.let(::dec))
        }
        return TheatreSession(whenMs, layout ?: return null, method ?: PlayMethod.DOLBY_51, tv, readings, system, pretend, real)
    }

    fun load(dir: File, name: String): TheatreSession? =
        runCatching { File(dir, "$name.txt").takeIf { it.exists() }?.readText()?.let { decode(it) } }.getOrNull()

    fun save(dir: File, name: String, s: TheatreSession) {
        dir.mkdirs()
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(encode(s))
        tmp.renameTo(File(dir, "$name.txt"))
    }

    fun delete(dir: File, name: String) { File(dir, "$name.txt").delete() }

    private fun f(x: Double, d: Int) = String.format(Locale.US, "%.${d}f", x)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
}

/** The pinned key after a home-theatre test, and its second line. [device] is what plays: "TV" or "PC". */
fun theatreKey(r: TheatreReport, device: String = "TV"): Pair<String, String> = when (r.move) {
    TheatreMove.CHECK_SPEAKER -> "Test ${r.moveSpeaker?.title?.lowercase() ?: "it"} again" to "after checking its wiring and setting"
    TheatreMove.SET_LEVELS -> "Test again" to "after setting the levels"
    TheatreMove.SET_DISTANCES -> "Test again" to "after setting the distances"
    TheatreMove.USE_EQ -> "Use the EQ on the $device" to "fitted per speaker"
    TheatreMove.DONE -> "Test again" to "every speaker is set"
}

/** The speakers the next test plays: one when the move is about one speaker, else the whole layout. */
fun theatreNextSpeakers(r: TheatreReport?, layout: Layout): List<Speaker> =
    if (r?.move == TheatreMove.CHECK_SPEAKER && r.moveSpeaker != null && r.layout == layout) listOf(r.moveSpeaker!!) else layout.speakers

/** What the pinned key does next in the two tests. */
enum class SystemStep { LEVELS, SYSTEM, USE_EQ }

/** Levels first, then the system; after a system test, what its result asks for. */
fun systemNext(r: PassReport?, levelsDone: Boolean): SystemStep = when {
    r == null -> if (levelsDone) SystemStep.SYSTEM else SystemStep.LEVELS
    r.move == TheatreMove.SET_LEVELS -> SystemStep.LEVELS
    r.move == TheatreMove.USE_EQ -> SystemStep.USE_EQ
    else -> SystemStep.SYSTEM
}

/** The pinned key in the two tests, and its second line. [device] is what plays: "TV" or "PC". */
fun systemKey(r: PassReport?, levelsDone: Boolean, layout: Layout, device: String = "TV"): Pair<String, String> {
    val secs = PassClip.seconds(layout).roundToInt()
    return when (systemNext(r, levelsDone)) {
        SystemStep.LEVELS -> "Set the levels" to if (r == null) "pink noise, one speaker at a time" else "they differ by up to ${String.format(Locale.US, "%.1f", r.speakers.levelSpreadDb)} dB"
        SystemStep.USE_EQ -> "Use the EQ on the $device" to "one EQ for every speaker"
        SystemStep.SYSTEM -> when (r?.move) {
            null -> "Test the system" to "one pass, about $secs s"
            TheatreMove.CHECK_SPEAKER -> "Test the system again" to "after checking ${r.speakers.moveSpeaker?.title?.lowercase() ?: "the speakers"}"
            TheatreMove.SET_DISTANCES -> "Test the system again" to "after setting the distances"
            else -> "Test the system again" to "every speaker is set"
        }
    }
}
