package dev.autotune.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The two home-theatre tests (2.15): levels first, with band-limited pink noise and the phone as a live meter, then the
 * system, in one pass. Calibration practice sets levels with pink noise of about 500 Hz to 2 kHz at the seat, the sub
 * with its own low noise; distances need a timed signal (a sweep); the EQ is read with every speaker playing.
 */

/** Turns per-speaker planes into the interleaved 16-bit channels of a [PlayMethod]. */
object Channels {
    /**
     * [mixDown]: a speaker [method] has no channel for plays from the front pair at −3 dB instead of being left out
     * (a phone or tablet pretending to be a bigger system, which has only its own two speakers).
     */
    fun interleave16(planes: Map<Speaker, FloatArray>, method: PlayMethod, mixDown: Boolean = false): ShortArray {
        val slots = method.slots
        val n = planes.values.maxOf { it.size }
        val mix = Array(slots.size) { FloatArray(n) }
        val half = (1 / sqrt(2.0)).toFloat()
        for ((s, plane) in planes) {
            val k = slots.indexOf(s)
            when {
                k >= 0 -> for (i in plane.indices) mix[k][i] += plane[i]
                mixDown -> listOf(Speaker.FL, Speaker.FR).map { slots.indexOf(it) }.filter { it >= 0 }.forEach { j ->
                    for (i in plane.indices) mix[j][i] += plane[i] * half
                }
            }
        }
        val out = ShortArray(n * slots.size)
        for (k in slots.indices) for (i in 0 until n) out[i * slots.size + k] = (mix[k][i].coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
        return out
    }
}

/** Pink noise for setting levels, on one speaker at a time, looping until the phone says stop. */
object LevelNoise {
    const val FS = 48000
    /** One loop: 2^17 samples (2.7 s). Three loops are exactly 256 Dolby Digital frames, so a Dolby clip loops cleanly. */
    const val LOOP = 1 shl 17
    val MAIN_BAND = 500.0 to 2000.0
    val SUB_BAND = 40.0 to 100.0
    const val RMS_DBFS = -20.0

    fun band(s: Speaker): Pair<Double, Double> = if (s.sub) SUB_BAND else MAIN_BAND

    /** One period of pink noise limited to the speaker's band: built from whole cycles, so it loops without a click. */
    fun loop(sub: Boolean, fs: Int = FS, seed: Long = 11): FloatArray {
        val n = LOOP
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        val rnd = java.util.Random(seed + if (sub) 1 else 0)
        val (lo, hi) = if (sub) SUB_BAND else MAIN_BAND
        for (k in 1 until n / 2) {
            val f = k.toDouble() * fs / n
            if (f < lo || f > hi) continue
            val amp = 1.0 / sqrt(f)
            val ph = rnd.nextDouble() * 2 * PI
            re[k] = amp * cos(ph); im[k] = amp * sin(ph)
            re[n - k] = re[k]; im[n - k] = -im[k]
        }
        Fft.transform(re, im, inverse = true)
        val rms = sqrt(re.sumOf { it * it } / n)
        val g = 10.0.pow(RMS_DBFS / 20) / rms
        return FloatArray(n) { (re[it] * g).toFloat() }
    }

    /** [speaker]'s noise; the LFE channel 10 dB lower, as every receiver plays it 10 dB louder. */
    fun planes(speaker: Speaker, fs: Int = FS): Map<Speaker, FloatArray> {
        val x = loop(speaker.sub, fs)
        if (speaker.sub) { val k = 10.0.pow(ChannelClip.LFE_TRIM_DB / 20).toFloat(); for (i in x.indices) x[i] *= k }
        return mapOf(speaker to x)
    }
}

/** Reads a speaker's level live from the mic while its noise plays, and says how far to turn it. */
object LevelMeter {
    /** Level steps a receiver offers; a change below half a step is not worth making. */
    const val STEP_DB = 0.5
    /** Held within this of the target for [STEADY_SECONDS], a speaker is done and the next one starts. */
    const val OK_DB = 0.5
    const val STEADY_SECONDS = 3

    /**
     * The level of [x][from, to) in [band], in dB, with the mic's calibration taken off: the power of the spectrum
     * in the band, averaged over Hann windows of 2^14 samples (a third of a second) half overlapped.
     */
    fun bandDb(x: FloatArray, from: Int, to: Int, fs: Int, band: Pair<Double, Double>, cal: Calibration?): Double {
        val n = 1 shl 14
        var sum = 0.0
        var windows = 0
        var start = max(0, from)
        while (start + n <= min(to, x.size)) {
            val re = DoubleArray(n) { i -> x[start + i] * (0.5 - 0.5 * cos(2 * PI * i / (n - 1))) }
            val im = DoubleArray(n)
            Fft.transform(re, im)
            for (k in 1 until n / 2) {
                val f = k.toDouble() * fs / n
                if (f < band.first || f > band.second) continue
                val corr = cal?.gainDbAt(f) ?: 0.0
                sum += (re[k] * re[k] + im[k] * im[k]) / 10.0.pow(corr / 10)
            }
            windows++
            start += n / 2
        }
        return if (windows == 0 || sum <= 0.0) -200.0 else 10 * log10(sum / windows)
    }

    /**
     * What [s]'s level should be: front left's ([ref]) for every main speaker, and the target's bass lift above it for
     * the sub (as [TheatreJudge] sets it), in steps of [STEP_DB]. The change is that less the reading: + means turn up.
     */
    fun change(level: Double, ref: Double, s: Speaker, target: Target): Double {
        val want = if (s.sub) ref + target.dbAt(60.0) - target.dbAt(1000.0) else ref
        return TheatreJudge.roundHalf((want - level).coerceIn(-12.0, 12.0))
    }

    fun steady(change: Double): Boolean = abs(change) < OK_DB
}

/**
 * Which sweeps a pass plays after its one set of start beeps (2.16): each speaker on its own for the distances, every
 * speaker together for the EQ, or both (the system test of 2.15). The name is what the link and the Dolby files use.
 */
enum class PassKind(val code: String) {
    SYSTEM("system"), DISTANCES("distances"), EQ("eq");

    companion object {
        fun of(code: String?): PassKind = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

/** A test as one clip: the start beeps once, then the sweeps of its [PassKind]. */
object PassClip {
    const val FS = 48000
    /** After each sweep the room's tail dies away before the next one. */
    const val GAP_SECONDS = 1.0

    /** Who plays each sweep, in order: each speaker of [layout], then all of them together (null). */
    fun slots(layout: Layout, kind: PassKind = PassKind.SYSTEM): List<Speaker?> = when (kind) {
        PassKind.SYSTEM -> layout.speakers + listOf<Speaker?>(null)
        PassKind.DISTANCES -> layout.speakers
        PassKind.EQ -> listOf(null)
    }

    /** Seconds from the first beep to the start of sweep [k]. */
    fun sweepAfterMarker(k: Int): Double = Marker.SWEEP_OFFSET_SEC + k * (ChannelClip.SWEEP_SECONDS + GAP_SECONDS)

    fun seconds(layout: Layout, kind: PassKind = PassKind.SYSTEM): Double =
        ChannelClip.LEAD_SECONDS + sweepAfterMarker(slots(layout, kind).size) - GAP_SECONDS + ChannelClip.TAIL_SECONDS

    /** Seconds from the first beep to the end of the last sweep and its tail: how long the phone listens once it hears them. */
    fun afterMarkerSeconds(layout: Layout, kind: PassKind = PassKind.SYSTEM): Double = sweepAfterMarker(slots(layout, kind).size) + 0.2

    fun planes(layout: Layout, fs: Int = FS, kind: PassKind = PassKind.SYSTEM): Map<Speaker, FloatArray> {
        val n = (seconds(layout, kind) * fs).toInt()
        val lead = (ChannelClip.LEAD_SECONDS * fs).toInt()
        val marker = Marker.waveform(fs)
        val sweep = ChannelClip.sweep(fs)
        val lfe = 10.0.pow(ChannelClip.LFE_TRIM_DB / 20).toFloat()
        val out = layout.speakers.associateWith { FloatArray(n) }
        System.arraycopy(marker, 0, out.getValue(ChannelClip.REFERENCE), lead, marker.size)
        slots(layout, kind).forEachIndexed { k, who ->
            val at = lead + (sweepAfterMarker(k) * fs).toInt()
            for (s in who?.let { listOf(it) } ?: layout.speakers) {
                val plane = out.getValue(s)
                val g = if (s.sub) lfe else 1f
                for (i in sweep.indices) plane[at + i] += sweep[i] * g
            }
        }
        return out
    }

    /** What plays [t] seconds into the clip: the slot's index and how far into its sweep, or null between sweeps. */
    fun nowPlaying(layout: Layout, t: Double, kind: PassKind = PassKind.SYSTEM): Pair<Int, Double>? {
        val sinceMarker = t - ChannelClip.LEAD_SECONDS
        for (k in slots(layout, kind).indices) {
            val into = sinceMarker - sweepAfterMarker(k)
            if (into >= 0 && into <= ChannelClip.SWEEP_SECONDS) return k to into
        }
        return null
    }
}

/** What one pass heard: each speaker on its own, and the whole system playing together. */
class PassReading(val speakers: List<SpeakerReading>, val system: SpeakerReading?) {
    val complete: Boolean get() = system?.heard == true
}

object PassAnalysis {
    /** Reads every sweep of a [PassClip] recording; [markerAt] is the first sample of the beeps when already found. */
    fun read(rec: FloatArray, fs: Int, layout: Layout, cal: Calibration?, markerAt: Int? = null, kind: PassKind = PassKind.SYSTEM): PassReading {
        val slots = PassClip.slots(layout, kind)
        val m0 = markerAt ?: Marker.find(rec, fs) ?: run {
            val none = "The start beeps were not heard: turn the volume up and check front left plays."
            return PassReading(layout.speakers.map { SpeakerReading(it, null, null, null, none) }, SpeakerReading(ChannelClip.REFERENCE, null, null, null, none))
        }
        val readings = slots.mapIndexed { k, who ->
            ChannelAnalysis.read(
                rec, fs, who ?: ChannelClip.REFERENCE, cal, m0, sweepAfterSec = PassClip.sweepAfterMarker(k),
                beforeSec = 0.5, afterSec = PassClip.GAP_SECONDS - 0.1,
            )
        }
        val system = readings.lastOrNull()?.takeIf { slots.last() == null }
        return PassReading(if (system != null) readings.dropLast(1) else readings, system)
    }

    /** The whole system as heard over several seats: the readings' power average (null when none was heard). */
    fun averageSeats(seats: List<SpeakerReading?>): SpeakerReading? {
        val heard = seats.filterNotNull().filter { it.heard }
        if (heard.isEmpty()) return seats.filterNotNull().firstOrNull()
        val n = heard.first().magDb!!.size
        val avg = DoubleArray(n) { i -> 10 * log10(heard.sumOf { 10.0.pow(it.magDb!![i] / 10) } / heard.size) }
        return SpeakerReading(heard.first().speaker, avg, heard.first().deltaMs, heard.mapNotNull { it.snrDb }.minOrNull())
    }
}

/** The distances test: each speaker against front left, the sub's measured delay included. */
class DistanceReport(val speakers: List<SpeakerResult>, val notes: List<String>, val headline: String, val ok: Boolean) {
    val spreadM: Double get() = speakers.mapNotNull { it.distanceM }.maxOfOrNull { abs(it) } ?: 0.0
    val heardCount: Int get() = speakers.count { it.heard }
}

object DistanceJudge {
    fun judge(layout: Layout, readings: List<SpeakerReading>, target: Target, grid: FreqGrid = FreqGrid()): DistanceReport {
        val r = TheatreJudge.judge(layout, readings, target, null, grid)
        val missing = r.speakers.filter { !it.heard }
        val off = r.distanceSpreadM > TheatreJudge.DISTANCE_OK_M
        val headline = when {
            missing.isNotEmpty() -> "No sound from ${missing.first().speaker.title.lowercase()}"
            off -> "Distances are off by up to ${String.format(java.util.Locale.US, "%.2f", r.distanceSpreadM)} m"
            else -> "Every speaker is in time"
        }
        return DistanceReport(r.speakers, r.notes, headline, missing.isEmpty() && !off)
    }
}

/** The EQ test: the whole system at the seat(s), and one EQ for every speaker. */
class EqReport(
    val systemDb: DoubleArray?,
    val rmsDb: Double?,
    /** The stock EQ test's, to compare with (null on the stock itself). */
    val stockRmsDb: Double?,
    val eq: List<EqFilter>,
    val seats: Int,
    val headline: String,
    val worth: Boolean,
)

object EqJudge {
    val FIT = PassJudge.FIT

    /** [stock]: the test's first EQ run, which every EQ is fitted from so they never stack. */
    fun judge(system: SpeakerReading?, seats: Int, target: Target, stock: SpeakerReading?, grid: FreqGrid = FreqGrid()): EqReport {
        val sys = system?.takeIf { it.heard }?.magDb
        val base = stock?.takeIf { it.heard }?.magDb ?: sys
        val rms = sys?.let { EqFitter.score(it, grid, target, FIT).first }
        val stockRms = stock?.takeIf { it.heard && it !== system }?.magDb?.let { EqFitter.score(it, grid, target, FIT).first }
        val eq = base?.let { EqChain.capBoost(EqFitter.fit(it, grid, target, FIT).filters) }.orEmpty()
        val worth = (rms ?: 0.0) > TheatreJudge.EQ_WORTH_DB && eq.isNotEmpty()
        val headline = when {
            sys == null -> "The system was not heard: ${system?.fatal ?: "turn the volume up"}"
            stockRms != null && rms != null && stockRms - rms >= 0.5 -> "${String.format(java.util.Locale.US, "%.1f", stockRms - rms)} dB closer to the target than at the start"
            worth -> "${String.format(java.util.Locale.US, "%.1f", rms)} dB from the target: an EQ will help"
            else -> "Close to the target"
        }
        return EqReport(sys, rms, stockRms, eq, seats, headline, worth)
    }

    /**
     * Whether an EQ reaches the speakers: [off] and [on] are the system with the EQ held and on, and [eq] what was
     * set. The measured change against the EQ's own curve, over 60 Hz to 8 kHz, both level-matched: null when either
     * was not heard; otherwise the share of the EQ's shape that arrived (1 = all of it, 0 = none).
     */
    fun reached(off: SpeakerReading?, on: SpeakerReading?, eq: EqChain, grid: FreqGrid = FreqGrid()): Double? {
        val a = off?.takeIf { it.heard }?.magDb ?: return null
        val b = on?.takeIf { it.heard }?.magDb ?: return null
        val sa = grid.smoothOctave(a, 3.0); val sb = grid.smoothOctave(b, 3.0)
        val lo = grid.indexAtOrAbove(60.0); val hi = grid.indexAtOrBelow(8000.0)
        val want = DoubleArray(hi - lo + 1) { eq.responseDb(grid.freqs[lo + it]) - eq.preampDb }
        val got = DoubleArray(hi - lo + 1) { sb[lo + it] - sa[lo + it] }
        val wm = want.average(); val gm = got.average()
        var num = 0.0; var den = 0.0
        for (i in want.indices) { num += (want[i] - wm) * (got[i] - gm); den += (want[i] - wm) * (want[i] - wm) }
        return if (den < 1e-6) null else (num / den).coerceIn(0.0, 1.5)
    }
}

/** The result of a system pass: levels and distances per speaker, and one EQ for the whole system. */
class PassReport(
    val speakers: TheatreReport,
    /** The whole system at the seat (null when it was not heard). */
    val systemDb: DoubleArray?,
    /** dB RMS from the target, the whole system, now and in the stock pass. */
    val rmsDb: Double?,
    val stockRmsDb: Double?,
    /** One EQ for every speaker, fitted from the stock pass so EQs never stack. */
    val eq: List<EqFilter>,
    val headline: String,
    val move: TheatreMove,
)

object PassJudge {
    /** The whole system's fit: every speaker playing, sub included, so it reaches down to 25 Hz. */
    val FIT = FitConfig(maxFilters = 10, fMinHz = 25.0, fMaxHz = 12000.0)

    fun judge(layout: Layout, pass: PassReading, target: Target, stock: PassReading?, grid: FreqGrid = FreqGrid()): PassReport {
        val each = TheatreJudge.judge(layout, pass.speakers, target, stock?.speakers, grid)
        val sys = pass.system?.takeIf { it.heard }?.magDb
        val base = stock?.system?.takeIf { it.heard }?.magDb ?: sys
        val rms = sys?.let { EqFitter.score(it, grid, target, FIT).first }
        val stockRms = stock?.system?.takeIf { it.heard }?.magDb?.let { EqFitter.score(it, grid, target, FIT).first }
        val eq = base?.let { EqChain.capBoost(EqFitter.fit(it, grid, target, FIT).filters) }.orEmpty()
        val move = when {
            each.move == TheatreMove.CHECK_SPEAKER -> TheatreMove.CHECK_SPEAKER
            each.move == TheatreMove.SET_LEVELS -> TheatreMove.SET_LEVELS
            each.move == TheatreMove.SET_DISTANCES -> TheatreMove.SET_DISTANCES
            sys == null -> TheatreMove.CHECK_SPEAKER
            (rms ?: 0.0) > TheatreJudge.EQ_WORTH_DB && eq.isNotEmpty() -> TheatreMove.USE_EQ
            else -> TheatreMove.DONE
        }
        val headline = when {
            move == TheatreMove.USE_EQ -> "Levels and distances match: the EQ is next"
            move == TheatreMove.DONE && stockRms != null && rms != null && stockRms - rms >= 0.5 ->
                "${fmt1(stockRms - rms)} dB closer to the target than at the start"
            move == TheatreMove.DONE -> "Every speaker is level, in time and close to the target"
            sys == null && each.move != TheatreMove.CHECK_SPEAKER -> "The whole system was not heard"
            else -> each.headline
        }
        return PassReport(each, sys, rms, stockRms, eq, headline, move)
    }

    private fun fmt1(x: Double) = String.format(java.util.Locale.US, "%.1f", x)
}
