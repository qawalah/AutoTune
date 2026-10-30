package dev.autotune

import android.content.Context
import android.os.Build
import dev.autotune.dsp.EqChain
import dev.autotune.dsp.EqFilter
import dev.autotune.dsp.Wav
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Diagnostics the user can send back: a running log of every measurement, EQ change and failure (one JSON object per
 * line), the last recordings as WAV files, and the saved tests. Nothing leaves the phone unless the user shares the zip.
 */
object Diag {
    private const val MAX_RECORDINGS = 12
    private const val MAX_LOG_BYTES = 4_000_000L

    private fun dir(ctx: Context) = File(ctx.filesDir, "diag").also { it.mkdirs() }
    private fun recDir(ctx: Context) = File(dir(ctx), "recordings").also { it.mkdirs() }
    private fun logFile(ctx: Context) = File(dir(ctx), "log.jsonl")

    /** Appends one event. Never throws: diagnostics must not break a measurement. */
    @Synchronized
    fun log(ctx: Context, type: String, fields: Map<String, Any?> = emptyMap()) {
        runCatching {
            val f = logFile(ctx)
            if (f.length() > MAX_LOG_BYTES) { // keep the newer half
                val lines = f.readLines()
                f.writeText(lines.drop(lines.size / 2).joinToString("\n", postfix = "\n"))
            }
            val all = linkedMapOf<String, Any?>("t" to stamp(System.currentTimeMillis()), "type" to type)
            all.putAll(fields)
            f.appendText(json(all) + "\n")
        }
    }

    /** Saves a recording as 16-bit WAV and keeps only the newest few. Returns the file name, or null. */
    fun keepRecording(ctx: Context, rec: FloatArray, fs: Int, tag: String): String? = runCatching {
        val name = "rec-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}-${tag.replace(Regex("[^A-Za-z0-9]+"), "_")}.wav"
        File(recDir(ctx), name).writeBytes(Wav.encodePcm16(rec, fs, 1))
        val files = recDir(ctx).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        files.drop(MAX_RECORDINGS).forEach { it.delete() }
        name
    }.getOrNull()

    fun clear(ctx: Context) {
        dir(ctx).deleteRecursively()
    }

    /** Everything in one zip in the cache folder (shareable through the app's file provider). */
    fun export(ctx: Context, info: String): File {
        val out = File(ctx.cacheDir, "autotune-diagnostics-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.zip")
        ZipOutputStream(out.outputStream().buffered()).use { z ->
            fun put(name: String, bytes: ByteArray) { z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() }
            put("info.txt", info.toByteArray())
            logFile(ctx).takeIf { it.exists() }?.let { put("log.jsonl", it.readBytes()) }
            File(ctx.filesDir, "tests").listFiles()?.forEach { put("tests/${it.name}", it.readBytes()) }
            File(ctx.filesDir, "calibration.txt").takeIf { it.exists() }?.let { put("calibration.txt", it.readBytes()) }
            recDir(ctx).listFiles()?.sortedBy { it.name }?.forEach { put("recordings/${it.name}", it.readBytes()) }
        }
        return out
    }

    fun deviceLine(): String = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    /** Peak and RMS of a recording in dBFS. */
    fun levels(rec: FloatArray): Pair<Double, Double> {
        var peak = 0f; var sq = 0.0
        for (x in rec) { val a = abs(x); if (a > peak) peak = a; sq += x.toDouble() * x }
        val rms = sqrt(sq / rec.size.coerceAtLeast(1))
        return 20 * log10(peak.toDouble() + 1e-12) to 20 * log10(rms + 1e-12)
    }

    fun bands(f: List<EqFilter>?): List<List<Double>>? = f?.map { listOf(r(it.freqHz, 1), r(it.gainDb, 2), r(it.q, 2)) }
    fun chain(c: EqChain?): Map<String, Any?>? = c?.let { mapOf("preampDb" to r(it.preampDb, 2), "bands" to bands(it.filters)) }

    private fun r(v: Double, d: Int): Double { val k = Math.pow(10.0, d.toDouble()); return Math.round(v * k) / k }
    private fun stamp(ms: Long) = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date(ms))

    /** Minimal JSON writer for maps, lists, strings, numbers and booleans. */
    fun json(v: Any?): String = when (v) {
        null -> "null"
        is String -> buildString {
            append('"')
            for (ch in v) when {
                ch == '\\' -> append("\\\\")
                ch == '"' -> append("\\\"")
                ch == '\n' -> append("\\n")
                ch == '\r' -> {}
                ch == '\t' -> append(' ')
                ch < ' ' -> append("\\u%04x".format(ch.code)) // any other control character would make the JSON invalid
                else -> append(ch)
            }
            append('"')
        }
        is Double -> if (v.isNaN() || v.isInfinite()) "null" else String.format(Locale.US, "%.4g", v).let { if ('e' in it) v.toString() else it }
        is Float -> json(v.toDouble())
        is Number, is Boolean -> v.toString()
        is Map<*, *> -> v.entries.joinToString(",", "{", "}") { (k, x) -> json(k.toString()) + ":" + json(x) }
        is Iterable<*> -> v.joinToString(",", "[", "]") { json(it) }
        is DoubleArray -> v.joinToString(",", "[", "]") { json(it) }
        is Enum<*> -> json(v.name)
        else -> json(v.toString())
    }
}
