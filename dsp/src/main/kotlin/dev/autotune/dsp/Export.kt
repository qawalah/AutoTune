package dev.autotune.dsp

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

object Export {
    /** Equalizer APO / AutoEq / many Android parametric-EQ apps import this text format. */
    fun parametricText(chain: EqChain): String = buildString {
        appendLine(String.format(Locale.US, "Preamp: %.1f dB", chain.preampDb))
        chain.filters.forEachIndexed { i, f ->
            appendLine(String.format(Locale.US, "Filter %d: ON PK Fc %.0f Hz Gain %.1f dB Q %.2f", i + 1, f.freqHz, f.gainDb, f.q))
        }
    }

    /**
     * AutoEq / Equalizer APO "GraphicEQ" line: 127 log-spaced points 20 Hz - 20 kHz, with the preamp baked in
     * (so nothing is boosted above 0 dB and the signal cannot clip). Wavelet imports this format.
     */
    fun graphicEqLine(chain: EqChain, points: Int = 127): String {
        val items = (0 until points).map { i ->
            val f = Math.round(20.0 * 1000.0.pow(i / (points - 1.0)))
            String.format(Locale.US, "%d %.1f", f, chain.responseDb(f.toDouble()))
        }
        return "GraphicEQ: " + items.joinToString("; ")
    }

    /**
     * The one merge for EQ filter lists. Filters within 1/6 octave of each other with a similar Q become one (gains add),
     * every band is held to the fit's own limits, near-zero bands are dropped, and the list is cut to the strongest
     * [FitConfig.maxFilters] bands, so repeated merging can never grow without bound.
     */
    fun combine(filters: List<EqFilter>, cfg: FitConfig = FitConfig()): List<EqFilter> {
        val out = ArrayList<EqFilter>()
        for (f in filters.sortedBy { it.freqHz }) {
            val i = out.indexOfLast { o -> f.freqHz / o.freqHz < 2.0.pow(1.0 / 6.0) && maxOf(f.q, o.q) / minOf(f.q, o.q) < 1.6 }
            if (i < 0) { out += f; continue }
            val o = out[i]
            val w = abs(o.gainDb) + abs(f.gainDb) + 1e-9
            val freq = (o.freqHz * abs(o.gainDb) + f.freqHz * abs(f.gainDb)) / w
            out[i] = EqFilter(freq, o.gainDb + f.gainDb, (o.q * abs(o.gainDb) + f.q * abs(f.gainDb)) / w)
        }
        val clamped = out.map { it.copy(gainDb = it.gainDb.coerceIn(-cfg.maxCutDb, cfg.maxBoostDb)) }.filter { abs(it.gainDb) >= 0.3 }
        val kept = if (clamped.size > cfg.maxFilters) clamped.sortedByDescending { abs(it.gainDb) }.take(cfg.maxFilters) else clamped
        return kept.sortedBy { it.freqHz }
    }

    /** Optional: band layouts of common graphic equalizers (head unit, phone/player EQ). */
    class BandSet(val name: String, val centers: List<Double>)

    val BAND_SETS = listOf(
        BandSet("31-band (1/3 oct)", Rta.CENTERS.toList()),
        BandSet("15-band", listOf(25.0, 40.0, 63.0, 100.0, 160.0, 250.0, 400.0, 630.0, 1000.0, 1600.0, 2500.0, 4000.0, 6300.0, 10000.0, 16000.0)),
        BandSet("10-band", listOf(31.5, 63.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)),
        BandSet("7-band", listOf(63.0, 160.0, 400.0, 1000.0, 2500.0, 6300.0, 16000.0)),
        BandSet("5-band (Android default)", listOf(60.0, 230.0, 910.0, 3600.0, 14000.0)),
    )

    val THIRD_OCTAVE_CENTERS = listOf(
        25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0, 125.0, 160.0, 200.0, 250.0, 315.0, 400.0, 500.0, 630.0, 800.0,
        1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3150.0, 4000.0, 5000.0, 6300.0, 8000.0, 10000.0, 12500.0,
    )

    /**
     * Converts the chain to gains for a fixed-band graphic EQ (car head unit, Samsung/Android slider EQ, AVR).
     * Pass the band centres of the target device. Gains are rounded to [stepDb].
     */
    fun graphicBands(chain: EqChain, centers: List<Double>, stepDb: Double = 0.5): List<Pair<Double, Double>> =
        centers.map { f ->
            val raw = chain.responseDb(f) - chain.preampDb
            f to Math.round(raw / stepDb) * stepDb
        }

    fun graphicText(chain: EqChain, centers: List<Double> = THIRD_OCTAVE_CENTERS): String =
        graphicBands(chain, centers).joinToString("\n") { (f, g) ->
            "%7s Hz  %+5.1f dB".format(if (f < 1000) "%.1f".format(f).removeSuffix(".0") else "%.0f".format(f), g)
        }
}
