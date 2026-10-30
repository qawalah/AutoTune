package dev.autotune.dsp

import kotlin.math.ln

/**
 * Microphone calibration (e.g. miniDSP UMIK-1 "xxxxxxx.txt"): lines of "frequency  deviation_dB",
 * optionally preceded by a header line like: "Sens Factor =-1.378dB, SERNO: 7012345".
 * gainDb is how much the mic reads ABOVE flat at that frequency, so we subtract it from measurements.
 */
class Calibration(
    val freqs: DoubleArray,
    val gainsDb: DoubleArray,
    val sensFactorDb: Double?,
) {
    val pointCount: Int get() = freqs.size

    fun gainDbAt(f: Double): Double {
        if (f <= freqs.first()) return gainsDb.first()
        if (f >= freqs.last()) return gainsDb.last()
        var lo = 0
        var hi = freqs.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (freqs[mid] <= f) lo = mid else hi = mid
        }
        val t = ln(f / freqs[lo]) / ln(freqs[hi] / freqs[lo])
        return gainsDb[lo] + t * (gainsDb[hi] - gainsDb[lo])
    }

    companion object {
        private val sensRegex = Regex("""sens\s*factor\s*=\s*(-?[0-9]*\.?[0-9]+)\s*db""", RegexOption.IGNORE_CASE)

        fun parse(text: String): Calibration {
            val pts = ArrayList<Pair<Double, Double>>()
            var sens: Double? = null
            for (raw in text.lineSequence()) {
                val line = raw.trim().trimStart('﻿')
                if (line.isEmpty()) continue
                if (line.startsWith("\"") || line.startsWith("*") || line.startsWith("#")) {
                    sensRegex.find(line)?.let { sens = it.groupValues[1].toDoubleOrNull() }
                    continue
                }
                val tok = line.split(Regex("""[\s,;]+""")).filter { it.isNotEmpty() }
                if (tok.size < 2) continue
                val f = tok[0].toDoubleOrNull() ?: continue
                val g = tok[1].toDoubleOrNull() ?: continue
                if (f > 0.0) pts.add(f to g)
            }
            require(pts.size >= 3) { "No calibration points found in file" }
            pts.sortBy { it.first }
            return Calibration(
                DoubleArray(pts.size) { pts[it].first },
                DoubleArray(pts.size) { pts[it].second },
                sens,
            )
        }
    }
}
