package dev.autotune.dsp

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.sinh

class Target(val name: String, val points: List<Pair<Double, Double>>) {
    fun toText(): String = points.joinToString("\n") { (f, d) -> String.format(java.util.Locale.US, "%.1f,%.2f", f, d) }

    /**
     * This curve with the user's taste on top: [bassDb] as a low shelf (half its value at about 110 Hz, all of it
     * below 50 Hz) and [trebleDb] as a high shelf (half at about 4 kHz). Zero for both returns the curve itself.
     */
    fun withTaste(bassDb: Double, trebleDb: Double): Target {
        if (abs(bassDb) < 0.05 && abs(trebleDb) < 0.05) return this
        val pts = (0..60).map { i ->
            val f = 20.0 * Math.pow(2.0, i / 6.0)
            f to dbAt(f) + bassDb * bassWeight(f) + trebleDb * trebleWeight(f)
        }.filter { it.first <= 20000.0 }
        return Target(name + tasteSuffix(bassDb, trebleDb), pts)
    }

    fun dbAt(f: Double): Double {
        if (f <= points.first().first) return points.first().second
        if (f >= points.last().first) return points.last().second
        for (i in 1 until points.size) {
            if (f <= points[i].first) {
                val (f0, d0) = points[i - 1]
                val (f1, d1) = points[i]
                return d0 + (d1 - d0) * ln(f / f0) / ln(f1 / f0)
            }
        }
        return points.last().second
    }

    companion object {
        fun bassWeight(f: Double) = 1.0 / (1.0 + (f / 110.0) * (f / 110.0))
        fun trebleWeight(f: Double) = 1.0 / (1.0 + (4000.0 / f) * (4000.0 / f))

        fun tasteSuffix(bassDb: Double, trebleDb: Double): String = buildString {
            if (abs(bassDb) >= 0.05) append(String.format(java.util.Locale.US, " · bass %+.0f dB", bassDb))
            if (abs(trebleDb) >= 0.05) append(String.format(java.util.Locale.US, " · treble %+.0f dB", trebleDb))
        }

        val FLAT = Target("Flat", listOf(20.0 to 0.0, 20000.0 to 0.0))

        /** Gentle bass lift and treble roll-off, typical for a calibrated home-theatre room. */
        val HOME_THEATER = Target(
            "Home theatre",
            listOf(20.0 to 4.0, 50.0 to 3.5, 100.0 to 2.0, 200.0 to 0.5, 500.0 to 0.0, 2000.0 to 0.0,
                5000.0 to -1.0, 10000.0 to -2.5, 20000.0 to -5.0),
        )

        /** Car cabin: stronger bass lift to beat road noise, treble tilted down. */
        val CAR = Target(
            "Car",
            listOf(20.0 to 5.0, 40.0 to 5.0, 80.0 to 3.5, 160.0 to 1.5, 400.0 to 0.0, 2000.0 to 0.0,
                6000.0 to -1.5, 12000.0 to -3.5, 20000.0 to -6.0),
        )

        /**
         * Harman-style in-car curve: the shape of the listener-preference research (a bass shelf below about 100 Hz,
         * flat mids, treble falling towards 20 kHz) with about +8 dB of bass, the usual amount for a car.
         * Approximated from the published shape, not Harman's own data points.
         */
        val HARMAN_CAR = Target(
            "Car, Harman-style (+8 dB bass)",
            listOf(20.0 to 8.0, 40.0 to 8.0, 63.0 to 7.4, 100.0 to 5.0, 160.0 to 2.2, 250.0 to 0.7, 400.0 to 0.0, 1000.0 to 0.0,
                2000.0 to -0.5, 5000.0 to -1.8, 10000.0 to -3.5, 20000.0 to -6.0),
        )

        /** Harman-style in-room curve with +6 dB of bass, the middle of the +4/+6/+8 dB versions used for rooms. */
        val HARMAN_ROOM = Target(
            "Room, Harman-style (+6 dB bass)",
            listOf(20.0 to 6.0, 40.0 to 6.0, 63.0 to 5.5, 100.0 to 3.8, 160.0 to 1.7, 250.0 to 0.5, 400.0 to 0.0, 1000.0 to 0.0,
                2000.0 to -0.4, 5000.0 to -1.4, 10000.0 to -2.6, 20000.0 to -4.5),
        )

        // stored tests refer to targets by name; older ones by index, so new built-ins only ever go at the end
        val BUILT_IN = listOf(CAR, HOME_THEATER, FLAT, HARMAN_CAR, HARMAN_ROOM)

        /** Built-in targets followed by the user's saved curves (set by the app at start-up and when curves change). */
        @Volatile
        var ALL: List<Target> = BUILT_IN

        /** Reads "frequency, dB" lines (comma, space, tab or semicolon separated; # and * start comments). */
        fun parse(name: String, text: String): Target? {
            val pts = text.lines().mapNotNull { l ->
                val s = l.trim()
                if (s.isEmpty() || s.startsWith("#") || s.startsWith("*") || s.startsWith("\"")) return@mapNotNull null
                val p = s.split(Regex("[,;\\s]+")).filter { it.isNotEmpty() }
                val f = p.getOrNull(0)?.toDoubleOrNull()
                val d = p.getOrNull(1)?.toDoubleOrNull()
                if (f != null && d != null && f > 0) f to d else null
            }.sortedBy { it.first }.distinctBy { it.first }
            return if (pts.size >= 4 && pts.first().first <= 200 && pts.last().first >= 5000) Target(name, pts) else null
        }
    }
}

data class FitConfig(
    val maxFilters: Int = 10,
    val maxBoostDb: Double = 3.0,
    val maxCutDb: Double = 12.0,
    val fMinHz: Double = 30.0,
    val fMaxHz: Double = 12000.0,
    /** Level reference band used to remove overall volume difference between measurement and target. */
    val refLowHz: Double = 250.0,
    val refHighHz: Double = 2500.0,
    val toleranceDb: Double = 1.0,
    val maxCutQ: Double = 8.0,
    val maxBoostQ: Double = 2.5,
    /** Above this frequency cuts are fitted to a 1/3 octave smoothing (in a car the treble comb moves with the mic). */
    val smoothAboveHz: Double = 500.0,
    /** Highest Q for a cut above 1 kHz. */
    val maxCutQHigh: Double = 4.0,
    /** No boosts below this frequency (the speakers were measured not to follow boosts down there). 0 = no floor. */
    val boostMinHz: Double = 0.0,
)

class FitResult(
    val filters: List<EqFilter>,
    /** RMS of the (1/6 oct smoothed) error inside the fit band BEFORE these filters, dB. */
    val rmsBefore: Double,
    /** Predicted RMS after applying [filters]. */
    val rmsAfter: Double,
    val maxAfter: Double,
    /** Error curve (measured - target - offset) on the grid before fitting. */
    val errorDb: DoubleArray,
)

object EqFitter {

    /** Greedy peak-picking parametric EQ fit. Cuts follow narrow peaks, boosts only fill wide dips. */
    fun fit(
        magDb: DoubleArray,
        grid: FreqGrid,
        target: Target,
        cfg: FitConfig = FitConfig(),
        stepScale: Double = 1.0,
    ): FitResult {
        val smoothCut = cutSmoothing(magDb, grid, cfg)
        val smoothBoost = grid.smoothOctave(magDb, 3.0)
        val tgt = DoubleArray(grid.size) { target.dbAt(grid.freqs[it]) }

        val rl = grid.indexAtOrAbove(cfg.refLowHz)
        val rh = grid.indexAtOrBelow(cfg.refHighHz)
        var off = 0.0
        for (i in rl..rh) off += smoothCut[i] - tgt[i]
        off /= (rh - rl + 1)

        val errCut = DoubleArray(grid.size) { smoothCut[it] - tgt[it] - off }
        val errBoost = DoubleArray(grid.size) { smoothBoost[it] - tgt[it] - off }
        // Working error: positive = too loud (cut), negative = too quiet (boost only if it is a wide dip).
        val e = DoubleArray(grid.size) { if (errCut[it] > 0.0) errCut[it] else max(errCut[it], errBoost[it]) }

        val lo = grid.indexAtOrAbove(cfg.fMinHz)
        val hi = grid.indexAtOrBelow(cfg.fMaxHz)
        val rmsBefore = rms(e, lo, hi)

        val out = ArrayList<EqFilter>()
        // Total response of the filters chosen so far. The per-filter limits alone let the fitter stack several +3 dB
        // boosts on the same dip (36 Hz three times = +9 dB), so the limits are applied to this running total.
        val acc = DoubleArray(grid.size)
        val real = e.copyOf() // true remaining error (e has unfixable lobes zeroed so the search moves on)
        var guard = 0
        while (out.size < cfg.maxFilters && guard++ < 200) {
            var bi = lo
            for (i in lo..hi) if (abs(e[i]) > abs(e[bi])) bi = i
            val v = e[bi]
            if (abs(v) < cfg.toleranceDb) break

            val cut = v > 0
            if (!cut && grid.freqs[bi] < cfg.boostMinHz) { // the speakers cannot play this: leave the dip alone
                var l0 = bi; while (l0 > 0 && e[l0] < 0) l0--
                var r0 = bi; while (r0 < grid.size - 1 && e[r0] < 0) r0++
                for (i in l0..r0) e[i] = 0.0
                continue
            }
            var gain = (-v * stepScale).coerceIn(-cfg.maxCutDb, cfg.maxBoostDb)
            // Never let the running total at this frequency exceed the limits.
            gain = if (cut) max(gain, -cfg.maxCutDb - acc[bi]).coerceAtMost(0.0) else min(gain, cfg.maxBoostDb - acc[bi]).coerceAtLeast(0.0)

            // Half-height width of this error lobe -> Q.
            val half = abs(v) / 2.0
            var l = bi
            while (l > 0 && e[l] * v > 0 && abs(e[l]) > half) l--
            var r = bi
            while (r < grid.size - 1 && e[r] * v > 0 && abs(e[r]) > half) r++
            if (abs(gain) < 0.3) { // limit already reached here: leave this lobe alone and look at the next one
                for (i in l..r) e[i] = 0.0
                continue
            }
            val bwOct = max((r - l).toDouble() / grid.ppo, 2.0 / grid.ppo)
            val q = (1.0 / (2.0 * sinh(ln(2.0) / 2.0 * bwOct)))
                .coerceIn(0.5, if (cut) (if (grid.freqs[bi] >= 1000.0) min(cfg.maxCutQ, cfg.maxCutQHigh) else cfg.maxCutQ) else cfg.maxBoostQ)

            val f = EqFilter(grid.freqs[bi], gain, q)
            out += f
            for (i in e.indices) {
                val m = f.magnitudeDb(grid.freqs[i])
                e[i] += m; real[i] += m; acc[i] += m
            }
        }

        return FitResult(out.sortedBy { it.freqHz }, rmsBefore, rms(real, lo, hi), maxAbs(real, lo, hi), errCut)
    }

    private fun cutSmoothing(magDb: DoubleArray, grid: FreqGrid, cfg: FitConfig): DoubleArray {
        val narrow = grid.smoothOctave(magDb, 6.0)
        val wide = grid.smoothOctave(magDb, 3.0)
        return DoubleArray(grid.size) { if (grid.freqs[it] < cfg.smoothAboveHz) narrow[it] else wide[it] }
    }

    class ClosedLoopResult(
        /** The complete EQ (not a change to what was applied). */
        val fit: FitResult,
        /** How much of the applied EQ showed up in the measurement, 1 = all of it. Null when too little was applied to tell. */
        val effectiveness: Double?,
        /** The applied EQ did not change what the mic hears (ignored by the phone, not set on the head unit, wrong device). */
        val ineffective: Boolean,
    )

    /**
     * Closed-loop fit. [stockMagDb] is the system without EQ, [applied] the filters that were really in effect when
     * [measuredMagDb] was taken. The result is a complete EQ for the stock system: the stock response corrected by
     * whatever the applied EQ did not do as predicted (mic moved, plant not matching), so it does not grow when the same
     * correction is measured again. When the applied EQ visibly did nothing the correction is left out, and the
     * suggestion stays what the stock response alone asks for instead of doubling.
     */
    fun fitFromStock(
        stockMagDb: DoubleArray, applied: List<EqFilter>, measuredMagDb: DoubleArray,
        grid: FreqGrid, target: Target, cfg: FitConfig = FitConfig(), stepScale: Double = 1.0,
    ): ClosedLoopResult {
        if (applied.isEmpty()) return ClosedLoopResult(fit(measuredMagDb, grid, target, cfg, stepScale), null, false)
        val rl = grid.indexAtOrAbove(cfg.refLowHz); val rh = grid.indexAtOrBelow(cfg.refHighHz)
        val lo = grid.indexAtOrAbove(cfg.fMinHz); val hi = grid.indexAtOrBelow(cfg.fMaxHz)
        val st = grid.smoothOctave(stockMagDb, 6.0)
        val me = grid.smoothOctave(measuredMagDb, 6.0)
        val pred = DoubleArray(grid.size) { i -> applied.sumOf { it.magnitudeDb(grid.freqs[i]) } }
        val delta = DoubleArray(grid.size) { me[it] - st[it] }
        var mean = 0.0
        for (i in rl..rh) mean += delta[i]
        mean /= (rh - rl + 1)
        for (i in delta.indices) delta[i] -= mean // overall volume differences are not EQ
        var num = 0.0; var den = 0.0
        for (i in lo..hi) { num += delta[i] * pred[i]; den += pred[i] * pred[i] }
        val predRms = sqrt(den / (hi - lo + 1))
        if (predRms < 1.0) return ClosedLoopResult(fit(stockMagDb, grid, target, cfg, stepScale), null, false)
        val k = num / den
        if (k < 0.3) return ClosedLoopResult(fit(stockMagDb, grid, target, cfg, stepScale), k, true)
        val kk = k.coerceIn(0.3, 1.2)
        // what the applied EQ failed to do (or overdid), smoothed and limited, moves the base
        val r = grid.smoothOctave(DoubleArray(grid.size) { delta[it] - kk * pred[it] + 40.0 }, 3.0)
        val base = DoubleArray(grid.size) { i -> stockMagDb[i] + (r[i] - 40.0).coerceIn(-4.0, 4.0) }
        return ClosedLoopResult(fit(base, grid, target, cfg, stepScale), k, false)
    }

    /** Level offset (dB) between a measurement and the target over the reference band; subtract to plot vs target. */
    fun offsetDb(magDb: DoubleArray, grid: FreqGrid, target: Target, cfg: FitConfig = FitConfig()): Double {
        val s = grid.smoothOctave(magDb, 6.0)
        val rl = grid.indexAtOrAbove(cfg.refLowHz)
        val rh = grid.indexAtOrBelow(cfg.refHighHz)
        var off = 0.0
        for (i in rl..rh) off += s[i] - target.dbAt(grid.freqs[i])
        return off / (rh - rl + 1)
    }

    /** RMS error of a measurement vs target inside the fit band (what the loop tries to minimise). */
    fun score(magDb: DoubleArray, grid: FreqGrid, target: Target, cfg: FitConfig = FitConfig()): Pair<Double, Double> {
        val s = grid.smoothOctave(magDb, 6.0)
        val rl = grid.indexAtOrAbove(cfg.refLowHz)
        val rh = grid.indexAtOrBelow(cfg.refHighHz)
        var off = 0.0
        for (i in rl..rh) off += s[i] - target.dbAt(grid.freqs[i])
        off /= (rh - rl + 1)
        val err = DoubleArray(grid.size) { s[it] - target.dbAt(grid.freqs[it]) - off }
        val lo = grid.indexAtOrAbove(cfg.fMinHz)
        val hi = grid.indexAtOrBelow(cfg.fMaxHz)
        return rms(err, lo, hi) to maxAbs(err, lo, hi)
    }

    private fun rms(a: DoubleArray, lo: Int, hi: Int): Double {
        var s = 0.0
        for (i in lo..hi) s += a[i] * a[i]
        return sqrt(s / (hi - lo + 1))
    }

    private fun maxAbs(a: DoubleArray, lo: Int, hi: Int): Double {
        var m = 0.0
        for (i in lo..hi) m = max(m, abs(a[i]))
        return m
    }
}
