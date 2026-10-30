package dev.autotune.dsp

import kotlin.math.abs

/** One tonal-balance region and how far it sits from the target (positive = too loud). */
class Region(val name: String, val loHz: Double, val hiHz: Double, val errDb: Double) {
    val verdict: String
        get() = when {
            abs(errDb) < 1.5 -> "on target"
            errDb > 0 -> "%.1f dB too loud".format(errDb)
            else -> "%.1f dB too quiet".format(-errDb)
        }
}

/** Plain-language descriptions of what a recommendation does, in a few words. */
object Describe {
    private class Zone(val hiHz: Double, val name: String, val cut: String, val boost: String)

    private val ZONES = listOf(
        Zone(40.0, "Sub-bass", "sub-bass rumble", "deep sub-bass"),
        Zone(80.0, "Bass", "boomy bass", "bass punch"),
        Zone(200.0, "Upper bass", "bass bloat", "bass warmth"),
        Zone(500.0, "Low mids", "muddy low-mids", "body and warmth"),
        Zone(2000.0, "Mids", "boxy or honky mids", "vocal body"),
        Zone(5000.0, "Presence", "harsh, shouty presence", "vocal clarity"),
        Zone(10000.0, "Treble", "sharp, sibilant treble", "detail and bite"),
        Zone(Double.MAX_VALUE, "Air", "excess hiss and sizzle", "air and sparkle"),
    )

    fun zoneName(freqHz: Double): String = ZONES.first { freqHz < it.hiHz }.name

    /** e.g. "Tames boomy bass (narrow cut) - a resonance". */
    fun filter(f: EqFilter): String {
        val z = ZONES.first { f.freqHz < it.hiHz }
        val width = when {
            f.q >= 4.0 -> "narrow"
            f.q >= 1.5 -> "medium"
            else -> "wide"
        }
        return if (f.gainDb < 0) {
            "Tames ${z.cut} ($width cut)" + if (f.q >= 4.0) " - a resonance" else ""
        } else {
            "Restores ${z.boost} ($width boost)"
        }
    }

    private val REGIONS = listOf(
        Triple("Bass", 30.0, 120.0),
        Triple("Low mids", 120.0, 500.0),
        Triple("Mids", 500.0, 2000.0),
        Triple("Presence", 2000.0, 6000.0),
        Triple("Treble", 6000.0, 12000.0),
    )

    fun regions(magDb: DoubleArray, grid: FreqGrid, target: Target, cfg: FitConfig = FitConfig()): List<Region> {
        val s = grid.smoothOctave(magDb, 6.0)
        val off = EqFitter.offsetDb(magDb, grid, target, cfg)
        return REGIONS.map { (name, lo, hi) ->
            var sum = 0.0
            var n = 0
            for (i in grid.freqs.indices) {
                val f = grid.freqs[i]
                if (f in lo..hi) { sum += s[i] - target.dbAt(f) - off; n++ }
            }
            Region(name, lo, hi, if (n > 0) sum / n else 0.0)
        }
    }
}
