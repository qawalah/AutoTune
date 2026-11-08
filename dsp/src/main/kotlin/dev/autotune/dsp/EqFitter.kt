package dev.autotune.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
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
    /** After placing the filters, adjust all of them together (REW's "optimise gains, Qs and frequencies"). */
    val refine: Boolean = true,
    /**
     * Below [smoothAboveHz] cuts follow REW's variable smoothing (finer in the bass, where room modes are narrow).
     * Off by default: on the simulated car comb and the moving-mic loop it fitted worse than the fixed 1/6 octave.
     */
    val variableSmoothing: Boolean = false,
    /** Longest 60 dB ring a boost may add, in seconds (REW holds boosts to about half a second). */
    val maxBoostRingS: Double = 0.5,
    /** Highest cut Q at 10 kHz: from 1 kHz the cap slides from [maxCutQHigh] down to this (REW: 3 at 10 kHz). */
    val maxCutQTop: Double = 3.0,
    /** A narrow dip counts as "leave alone", never as too loud, so cuts beside it cannot sink into it for free. */
    val protectDips: Boolean = true,
) {
    /** Highest Q a cut at [f] may use. */
    fun cutQCap(f: Double): Double {
        if (f < 1000.0) return maxCutQ
        val hi = min(maxCutQ, maxCutQHigh)
        val top = min(hi, maxCutQTop)
        return hi * (top / hi).pow(log10(f / 1000.0).coerceIn(0.0, 1.0))
    }

    /**
     * Highest Q a boost of [gainDb] at [f] may use: [maxBoostQ], and never so narrow that the boost itself rings longer
     * than [maxBoostRingS]. A peaking boost's poles have a Q of about A*Q (A = 10^(gain/40)) and a resonance of that Q
     * takes about 2.2*Q/f seconds to fall 60 dB.
     */
    fun boostQCap(f: Double, gainDb: Double): Double {
        val a = 10.0.pow(abs(gainDb) / 40.0)
        return min(maxBoostQ, maxBoostRingS * f / (2.2 * a))
    }
}

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
        // Working error: positive = too loud (cut), negative = too quiet (boost only if it is a wide dip). A narrow dip
        // is left alone, never read as too loud: the 1/3-octave power average fills it with the peaks beside it, and
        // reading that as "too loud" let wide cuts sink into a spot that was already low (a car's 200 Hz between peaks
        // at 125, 160 and 250 Hz ended 11 dB under the target).
        val e = DoubleArray(grid.size) {
            when {
                errCut[it] > 0.0 -> errCut[it]
                cfg.protectDips -> max(errCut[it], min(errBoost[it], 0.0))
                else -> max(errCut[it], errBoost[it])
            }
        }

        val lo = grid.indexAtOrAbove(cfg.fMinHz)
        val hi = grid.indexAtOrBelow(cfg.fMaxHz)
        val rmsBefore = rms(e, lo, hi)

        val out = ArrayList<EqFilter>()
        // Total response of the filters chosen so far. The per-filter limits alone let the fitter stack several +3 dB
        // boosts on the same dip (36 Hz three times = +9 dB), so the limits are applied to this running total.
        val acc = DoubleArray(grid.size)
        val e0 = e.copyOf() // the error before any filter
        // where the error still counts: lobes the search gives up on (below the boost floor, or at the limit already)
        // are left out of the fine-tuning too, so it does not try to fix them another way
        val weight = DoubleArray(grid.size) { 1.0 }
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
                for (i in l0..r0) { e[i] = 0.0; weight[i] = 0.0 }
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
                for (i in l..r) { e[i] = 0.0; weight[i] = 0.0 }
                continue
            }
            val bwOct = max((r - l).toDouble() / grid.ppo, 2.0 / grid.ppo)
            val fc = grid.freqs[bi]
            val q = (1.0 / (2.0 * sinh(ln(2.0) / 2.0 * bwOct)))
                .coerceIn(0.5, max(0.5, if (cut) cfg.cutQCap(fc) else cfg.boostQCap(fc, gain)))

            val f = EqFilter(fc, gain, q)
            out += f
            for (i in e.indices) {
                val m = f.magnitudeDb(grid.freqs[i])
                e[i] += m; acc[i] += m
            }
        }

        // Every suggestion goes through Export.combine, which merges bands within 1/6 octave and holds the merged band to
        // the limits. The search can stack three +3 dB boosts to refill a hole that two wide cuts dig (an F-150 at
        // 210 Hz); merged later, two thirds of that refill vanished and the hole stayed (200 Hz 11 dB under the
        // target). Merging here, before the fine-tuning, makes the tuning balance the bands that will really play.
        val merged = Export.combine(out, cfg)
        // a deliberately partial step (stepScale < 1) is left as it is: fine-tuning would turn it back into a full one
        val filters = if (cfg.refine && stepScale == 1.0 && merged.isNotEmpty()) refine(merged, e0, weight, grid, cfg, lo, hi) else merged
        val real = DoubleArray(grid.size) { i -> e0[i] + filters.sumOf { it.magnitudeDb(grid.freqs[i]) } }
        return FitResult(filters.sortedBy { it.freqHz }, rmsBefore, rms(real, lo, hi), maxAbs(real, lo, hi), errCut)
    }

    /**
     * Fine-tunes the filters together, as REW's "optimise gains, Qs and frequencies" does after it has placed them:
     * the one-at-a-time search sets each filter against the error left by the ones before it, so overlapping filters
     * overshoot. Small steps on every gain, Q and frequency are kept only when they lower the remaining error. Each
     * filter keeps its direction (a cut stays a cut), its frequency stays within 10% of where it was placed (and inside
     * the fit band, and above the boost floor), Q stays within the caps, and no step may push the running total
     * further past the boost or cut limit than it already was. Lobes the search gave up on are not chased, and the
     * error over the whole fit band never ends up higher than the search left it.
     */
    private fun refine(
        start: List<EqFilter>, e0: DoubleArray, weight: DoubleArray, grid: FreqGrid, cfg: FitConfig, lo: Int, hi: Int,
    ): List<EqFilter> {
        val n = grid.size
        val fs = 48000.0 // the rate magnitudeDb uses
        val c1 = DoubleArray(n); val s1 = DoubleArray(n); val c2 = DoubleArray(n); val s2 = DoubleArray(n)
        for (i in 0 until n) {
            val w = 2.0 * PI * grid.freqs[i] / fs
            c1[i] = cos(w); s1[i] = sin(w); c2[i] = cos(2 * w); s2[i] = sin(2 * w)
        }
        fun response(f: EqFilter): DoubleArray {
            val c = f.coefficients(fs)
            return DoubleArray(n) { i ->
                val nr = c[0] + c[1] * c1[i] + c[2] * c2[i]
                val ni = -(c[1] * s1[i] + c[2] * s2[i])
                val dr = 1.0 + c[3] * c1[i] + c[4] * c2[i]
                val di = -(c[3] * s1[i] + c[4] * s2[i])
                10.0 * log10((nr * nr + ni * ni) / (dr * dr + di * di) + 1e-30)
            }
        }
        fun cost(a: DoubleArray): Double {
            var s = 0.0
            for (i in lo..hi) { val d = e0[i] + a[i]; s += weight[i] * d * d }
            return s
        }
        fun costAll(a: DoubleArray): Double {
            var s = 0.0
            for (i in lo..hi) { val d = e0[i] + a[i]; s += d * d }
            return s
        }
        fun overLimits(a: DoubleArray): Double {
            var s = 0.0
            for (v in a) {
                if (v > cfg.maxBoostDb) s += v - cfg.maxBoostDb
                if (v < -cfg.maxCutDb) s += -cfg.maxCutDb - v
            }
            return s
        }

        val fLo = grid.freqs[lo]; val fHi = grid.freqs[hi]
        fun legal(f: EqFilter, k: Int): EqFilter {
            val cut = start[k].gainDb < 0
            val home = start[k].freqHz
            var fMin = max(home / 1.1, fLo)
            if (!cut) fMin = max(fMin, min(cfg.boostMinHz, home))
            val freq = f.freqHz.coerceIn(fMin, max(fMin, min(home * 1.1, fHi)))
            val g = if (cut) f.gainDb.coerceIn(-cfg.maxCutDb, 0.0) else f.gainDb.coerceIn(0.0, cfg.maxBoostDb)
            val cap = max(0.5, if (cut) cfg.cutQCap(freq) else cfg.boostQCap(freq, g))
            return EqFilter(freq, g, f.q.coerceIn(0.5, cap))
        }

        val fl = start.mapIndexed { k, f -> legal(f, k) }.toMutableList()
        val resp = fl.map { response(it) }.toMutableList()
        val acc = DoubleArray(n)
        for (r in resp) for (i in 0 until n) acc[i] += r[i]
        var j = cost(acc)
        var over = overLimits(acc)
        val ceiling = costAll(acc) // the whole band's error may never end up above where the search left it
        val trial = DoubleArray(n)

        var stepDb = 0.5; var stepQ = 1.2; var stepF = 1.02
        var sweeps = 0
        while (sweeps++ < 60) {
            var improved = false
            for (k in fl.indices) {
                for (move in 0 until 6) {
                    val cur = fl[k]
                    val raw = when (move) {
                        0 -> cur.copy(gainDb = cur.gainDb + stepDb)
                        1 -> cur.copy(gainDb = cur.gainDb - stepDb)
                        2 -> cur.copy(q = cur.q * stepQ)
                        3 -> cur.copy(q = cur.q / stepQ)
                        4 -> cur.copy(freqHz = cur.freqHz * stepF)
                        else -> cur.copy(freqHz = cur.freqHz / stepF)
                    }
                    val c = legal(raw, k)
                    if (c == cur) continue
                    val nr = response(c)
                    val old = resp[k]
                    for (i in 0 until n) trial[i] = acc[i] - old[i] + nr[i]
                    val jt = cost(trial)
                    val ot = overLimits(trial)
                    // never drift close enough to another band for Export.combine to merge the two afterwards
                    val apart = fl.indices.none { o -> o != k && Export.mergeable(c, fl[o]) }
                    if (apart && ot <= over + 1e-9 && jt < j - 1e-9 && costAll(trial) <= ceiling + 1e-9) {
                        fl[k] = c; resp[k] = nr
                        System.arraycopy(trial, 0, acc, 0, n)
                        j = jt; over = ot; improved = true
                    }
                }
            }
            if (!improved) {
                stepDb /= 2.0; stepQ = sqrt(stepQ); stepF = sqrt(stepF)
                if (stepDb < 0.05) break
            }
        }
        return fl.filter { abs(it.gainDb) >= 0.2 }
    }

    private fun cutSmoothing(magDb: DoubleArray, grid: FreqGrid, cfg: FitConfig): DoubleArray {
        val wide = grid.smoothOctave(magDb, 3.0)
        val narrow = if (cfg.variableSmoothing) {
            grid.smoothVariable(magDb) { f -> FreqGrid.variableFraction(f) }
        } else {
            grid.smoothOctave(magDb, 6.0)
        }
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
