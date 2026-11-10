package dev.autotune.link

import dev.autotune.dsp.EqChain
import dev.autotune.dsp.EqFilter
import dev.autotune.dsp.Speaker
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/**
 * The phone and a TV or PC talk over the home Wi-Fi: one TCP connection, one line of text per message,
 * `type key=value key=value`, values URL-encoded. The TV or PC announces itself with DNS-SD ([NSD_TYPE]) and shows a
 * four-digit code; the phone must send it in [Msg.hello] before anything else is accepted. Nothing leaves the network.
 * Unknown keys are ignored, so a newer side can add keys without breaking an older one.
 */
const val LINK_VERSION = 1
const val LINK_PORT = 47810
const val NSD_TYPE = "_autotune._tcp."

class LinkMsg(val type: String, val args: Map<String, String> = emptyMap()) {
    operator fun get(key: String): String? = args[key]

    fun encode(): String = buildString {
        append(type)
        for ((k, v) in args) append(' ').append(k).append('=').append(URLEncoder.encode(v, "UTF-8"))
    }

    override fun toString(): String = encode()

    companion object {
        fun parse(line: String): LinkMsg? {
            val parts = line.trim().split(' ').filter { it.isNotEmpty() }
            val type = parts.firstOrNull()?.takeIf { t -> t.all { it.isLetter() } } ?: return null
            val args = LinkedHashMap<String, String>()
            for (p in parts.drop(1)) {
                val eq = p.indexOf('=')
                if (eq <= 0) continue
                args[p.substring(0, eq)] = runCatching { URLDecoder.decode(p.substring(eq + 1), "UTF-8") }.getOrDefault("")
            }
            return LinkMsg(type, args)
        }
    }
}

/** A row of the result as the TV shows it under each speaker. */
class ShownRow(val speaker: Speaker, val trim: String, val distance: String, val heard: Boolean)

/** Every message, phone to TV or PC and back. */
object Msg {
    // phone to TV
    const val HELLO = "hello"
    const val PLAY = "play"
    const val SHOW = "show"
    const val EQ = "eq"
    const val EQ_OFF = "eqoff"
    const val BYE = "bye"
    /** Pink noise on one speaker, looping until [QUIET], for setting its level (2.15). */
    const val NOISE = "noise"
    const val QUIET = "quiet"
    /** The whole system test in one clip (2.15): `playing` names each speaker as its sweep starts, then `all`. */
    const val PASS = "pass"
    /** In a welcome: what a TV or PC can do beyond playing one speaker's clip (2.16: three separate tests). */
    const val CAPS = "noise,distances,eqtest,live"
    /** The phone's live view while it records (level, waveform, status), a few times a second (2.16). */
    const val LIVE = "live"
    const val ALL = "all"
    // TV to phone
    const val WELCOME = "welcome"
    const val DENIED = "denied"
    const val PLAYING = "playing"
    const val DONE = "done"
    const val FAILED = "failed"
    const val EQ_STATE = "eqstate"

    fun hello(code: String, phone: String) = LinkMsg(HELLO, mapOf("code" to code, "name" to phone, "v" to "$LINK_VERSION"))
    /** What answers the phone: a TV ([KIND_TV]) or a Windows PC ([KIND_PC]). A welcome without a kind is from a TV. */
    const val KIND_TV = "tv"
    const val KIND_PC = "pc"

    /**
     * [caps]: the extra tests it plays ([CAPS]). [pretend]: a phone or tablet standing in for a bigger system, with
     * [real] saying what it really is ("tablet speakers · 2.0").
     */
    fun welcome(
        tv: String, method: String, eqOn: Boolean, output: String, kind: String = KIND_TV, caps: String? = CAPS,
        pretend: Boolean = false, real: String? = null,
    ) = LinkMsg(
        WELCOME,
        buildMap {
            put("name", tv); put("method", method); put("eq", if (eqOn) "1" else "0"); put("out", output); put("v", "$LINK_VERSION"); put("kind", kind)
            caps?.let { put("caps", it) }
            if (pretend) put("pretend", "1")
            real?.let { put("real", it) }
        },
    )

    fun noise(s: Speaker, layout: String) = LinkMsg(NOISE, mapOf("speaker" to s.code, "layout" to layout))
    fun quiet() = LinkMsg(QUIET)
    /** [hold]: AutoTune's own EQ on the TV or PC stays off while it plays (the stock pass reads the system as it is). */
    fun pass(id: Int, layout: String, hold: Boolean, kind: String = "system") =
        LinkMsg(PASS, mapOf("id" to "$id", "layout" to layout, "hold" to if (hold) "1" else "0", "kind" to kind))

    /**
     * What the phone hears, live: [levelDb] the mic's level (dBFS), [wave] the last moment as up to 48 heights 0..35
     * (one base-36 digit each), [status] in a few words, [change] the level test's reading (+ = turn up).
     */
    fun live(levelDb: Float, wave: String, status: String?, change: Double?) = LinkMsg(
        LIVE,
        buildMap {
            put("lv", String.format(Locale.US, "%.1f", levelDb)); put("w", wave)
            status?.let { put("st", it) }
            change?.let { put("ch", String.format(Locale.US, "%.1f", it)) }
        },
    )

    /** [wave] heights from samples: the peak of each of [n] slices, on a dB scale from −60 to 0 dBFS. */
    fun waveText(x: FloatArray, from: Int, to: Int, n: Int = 48): String = buildString {
        val len = (to - from).coerceAtLeast(n)
        for (k in 0 until n) {
            var peak = 0f
            for (i in from + k * len / n until (from + (k + 1) * len / n).coerceAtMost(x.size)) if (i >= 0) peak = maxOf(peak, kotlin.math.abs(x[i]))
            val db = if (peak <= 1e-6f) -60.0 else 20 * kotlin.math.log10(peak.toDouble())
            append(((db + 60) / 60 * 35).toInt().coerceIn(0, 35).toString(36))
        }
    }

    fun waveHeights(text: String?): List<Float> = text.orEmpty().map { (it.digitToIntOrNull(36) ?: 0) / 35f }

    /**
     * The result as the phone shows it, for the TV: [readings] (value, unit, name), the system's response and the
     * target at third-octave points (dB), the notes and the next step. Sent with [show]'s rows.
     */
    fun result(headline: String, readings: List<Triple<String, String, String>>, response: List<Double>?, target: List<Double>?, notes: List<String>, next: String?) = LinkMsg(
        "result",
        buildMap {
            put("headline", headline)
            put("readings", readings.joinToString("|") { (v, u, n) -> listOf(v, u, n).joinToString(";") { it.replace("|", "").replace(";", "") } })
            response?.let { put("resp", it.joinToString(",") { v -> String.format(Locale.US, "%.1f", v) }) }
            target?.let { put("target", it.joinToString(",") { v -> String.format(Locale.US, "%.1f", v) }) }
            if (notes.isNotEmpty()) put("notes", notes.joinToString("|") { it.replace("|", "") })
            next?.let { put("next", it) }
        },
    )
    const val RESULT = "result"

    fun readings(text: String?): List<Triple<String, String, String>> = text.orEmpty().split('|').mapNotNull { r ->
        val f = r.split(';'); if (f.size < 3) null else Triple(f[0], f[1], f[2])
    }

    fun numbers(text: String?): List<Double> = text.orEmpty().split(',').mapNotNull { it.toDoubleOrNull() }
    fun playingAll(id: Int) = LinkMsg(PLAYING, mapOf("id" to "$id", "speaker" to ALL))
    fun denied(reason: String) = LinkMsg(DENIED, mapOf("reason" to reason))
    fun play(id: Int, s: Speaker, layout: String) = LinkMsg(PLAY, mapOf("id" to "$id", "speaker" to s.code, "layout" to layout))
    fun playing(id: Int, s: Speaker) = LinkMsg(PLAYING, mapOf("id" to "$id", "speaker" to s.code))
    fun done(id: Int) = LinkMsg(DONE, mapOf("id" to "$id"))
    fun failed(id: Int, reason: String) = LinkMsg(FAILED, mapOf("id" to "$id", "reason" to reason))
    fun show(layout: String, headline: String, rows: List<ShownRow>) =
        LinkMsg(SHOW, mapOf("layout" to layout, "headline" to headline, "rows" to rowsText(rows)))
    fun eq(chains: Map<Speaker, EqChain>) = LinkMsg(EQ, mapOf("chains" to chainsText(chains)))
    fun eqOff() = LinkMsg(EQ_OFF)
    fun eqState(on: Boolean, error: String? = null) =
        LinkMsg(EQ_STATE, buildMap { put("on", if (on) "1" else "0"); error?.let { put("error", it) } })
    fun bye() = LinkMsg(BYE)

    // rows: CODE;trim;distance;heard|...
    fun rowsText(rows: List<ShownRow>): String =
        rows.joinToString("|") { listOf(it.speaker.code, it.trim, it.distance, if (it.heard) "1" else "0").joinToString(";") { f -> f.replace("|", "").replace(";", "") } }

    fun rows(text: String?): List<ShownRow> =
        text.orEmpty().split('|').mapNotNull { r ->
            val f = r.split(';')
            val s = Speaker.of(f.getOrNull(0)) ?: return@mapNotNull null
            ShownRow(s, f.getOrNull(1).orEmpty(), f.getOrNull(2).orEmpty(), f.getOrNull(3) != "0")
        }

    // chains: CODE/preamp/f,g,q;f,g,q|...
    fun chainsText(chains: Map<Speaker, EqChain>): String =
        chains.entries.joinToString("|") { (s, c) ->
            s.code + "/" + n(c.preampDb) + "/" + c.filters.joinToString(";") { "${n(it.freqHz)},${n(it.gainDb)},${n(it.q)}" }
        }

    fun chains(text: String?): Map<Speaker, EqChain> =
        text.orEmpty().split('|').mapNotNull { e ->
            val f = e.split('/')
            val s = Speaker.of(f.getOrNull(0)) ?: return@mapNotNull null
            val pre = f.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            val bands = f.getOrNull(2).orEmpty().split(';').mapNotNull { b ->
                val v = b.split(',').mapNotNull { it.toDoubleOrNull() }
                if (v.size == 3 && v[0] in 10.0..24000.0 && v[2] > 0) EqFilter(v[0], v[1].coerceIn(-15.0, 6.0), v[2].coerceIn(0.1, 20.0)) else null
            }
            s to EqChain(bands, pre.coerceIn(-15.0, 0.0))
        }.toMap()

    private fun n(x: Double) = String.format(Locale.US, "%.2f", x)
}

object Pairing {
    /** A new four-digit code, "0000" to "9999". */
    fun newCode(rnd: java.util.Random = java.security.SecureRandom()): String = String.format(Locale.US, "%04d", rnd.nextInt(10000))

    fun matches(given: String?, code: String): Boolean = given?.trim()?.filter { it.isDigit() } == code
}
