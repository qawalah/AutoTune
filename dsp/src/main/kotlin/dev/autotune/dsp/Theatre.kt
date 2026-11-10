package dev.autotune.dsp

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * One speaker of a home theatre and where it sits on the room map: [x] from -1 (left wall) to 1 (right wall),
 * [y] from 0 (the screen) to 1 (the back wall). New entries are appended: saved sessions refer to speakers by [code].
 */
enum class Speaker(val code: String, val title: String, val short: String, val x: Float, val y: Float, val sub: Boolean = false) {
    FL("FL", "Front left", "L", -0.5f, 0.1f),
    FR("FR", "Front right", "R", 0.5f, 0.1f),
    C("C", "Centre", "C", 0f, 0.05f),
    SUB("SW", "Subwoofer", "SW", 0.92f, 0.1f, sub = true),
    SL("SL", "Surround left", "SL", -0.92f, 0.62f),
    SR("SR", "Surround right", "SR", 0.92f, 0.62f),
    BL("BL", "Back left", "BL", -0.45f, 0.86f),
    BR("BR", "Back right", "BR", 0.45f, 0.86f),
    ;

    companion object {
        fun of(code: String?): Speaker? = entries.firstOrNull { it.code == code }
    }
}

/** The speakers a home theatre has. New entries are appended. */
enum class Layout(val title: String, val speakers: List<Speaker>) {
    L20("2.0", listOf(Speaker.FL, Speaker.FR)),
    L21("2.1", listOf(Speaker.FL, Speaker.FR, Speaker.SUB)),
    L31("3.1", listOf(Speaker.FL, Speaker.C, Speaker.FR, Speaker.SUB)),
    L51("5.1", listOf(Speaker.FL, Speaker.C, Speaker.FR, Speaker.SR, Speaker.SL, Speaker.SUB)),
    L71("7.1", listOf(Speaker.FL, Speaker.C, Speaker.FR, Speaker.SR, Speaker.BR, Speaker.BL, Speaker.SL, Speaker.SUB)),
    ;

    companion object {
        fun of(name: String?): Layout? = entries.firstOrNull { it.name == name }
    }
}

/**
 * How the TV sends one speaker's test sound to the home theatre. HDMI ARC carries only two raw channels, but it does
 * carry Dolby Digital 5.1, so that is how a speaker is reached over ARC. eARC, or a box plugged into the receiver,
 * can carry raw multichannel sound. New entries are appended.
 */
enum class PlayMethod(val title: String, val layouts: List<Layout>) {
    DOLBY_51("Dolby Digital 5.1", listOf(Layout.L51, Layout.L31, Layout.L21, Layout.L20)),
    PCM_71("Multichannel 7.1", listOf(Layout.L71, Layout.L51, Layout.L31, Layout.L21, Layout.L20)),
    PCM_51("Multichannel 5.1", listOf(Layout.L51, Layout.L31, Layout.L21, Layout.L20)),
    STEREO("Stereo only", listOf(Layout.L20)),
    ;

    /** The output channels a clip has, each holding one speaker (null = a channel no layout uses). */
    val slots: List<Speaker?>
        get() = when (this) {
            // Android's 5.1 order is FL FR FC LFE BL BR; a 5.1 system's surrounds sit in the two back channels
            DOLBY_51, PCM_51 -> listOf(Speaker.FL, Speaker.FR, Speaker.C, Speaker.SUB, Speaker.SL, Speaker.SR)
            // Android's 7.1 order is FL FR FC LFE BL BR SL SR
            PCM_71 -> listOf(Speaker.FL, Speaker.FR, Speaker.C, Speaker.SUB, Speaker.BL, Speaker.BR, Speaker.SL, Speaker.SR)
            STEREO -> listOf(Speaker.FL, Speaker.FR)
        }

    fun slotOf(s: Speaker): Int? = slots.indexOf(s).takeIf { it >= 0 }

    companion object {
        fun of(name: String?): PlayMethod? = entries.firstOrNull { it.name == name }

        /**
         * The best way the TV's output offers: raw multichannel first (nothing lost, and 7.1), then Dolby Digital
         * (what ARC carries), then stereo.
         */
        fun pick(dolby: Boolean, rawChannels: Int): PlayMethod = when {
            rawChannels >= 8 -> PCM_71
            dolby -> DOLBY_51
            rawChannels >= 6 -> PCM_51
            else -> STEREO
        }
    }
}

/**
 * One speaker's test clip: silence while the home theatre switches to the format, the start beeps on the reference
 * speaker (front left), then the sweep on the speaker under test. The beeps give a common start for every speaker, so
 * the delay between the beeps and the sweep tells how much farther or nearer the speaker is than front left.
 */
object ChannelClip {
    const val FS = 48000
    const val SWEEP_SECONDS = 6.0
    /** A receiver takes a second or two to lock on to a new format, and mutes meanwhile. */
    const val LEAD_SECONDS = 2.5
    const val TAIL_SECONDS = 1.5
    /** The LFE channel is played 10 dB louder by every receiver, so its sweep is 10 dB lower to land level. */
    const val LFE_TRIM_DB = -10.0
    val REFERENCE = Speaker.FL

    val seconds: Double get() = LEAD_SECONDS + Marker.SWEEP_OFFSET_SEC + SWEEP_SECONDS + TAIL_SECONDS

    /** The sweep the analyser deconvolves with (the same for every speaker, so their levels compare). */
    fun sweep(fs: Int = FS): FloatArray = Sweep.generate(fs, SWEEP_SECONDS)

    /** One mono plane per speaker that sounds in [speaker]'s clip. */
    fun planes(speaker: Speaker, fs: Int = FS): Map<Speaker, FloatArray> {
        val n = (seconds * fs).toInt()
        val lead = (LEAD_SECONDS * fs).toInt()
        val marker = Marker.waveform(fs)
        val sweep = sweep(fs)
        val sweepAt = lead + (Marker.SWEEP_OFFSET_SEC * fs).toInt()
        val ref = FloatArray(n)
        System.arraycopy(marker, 0, ref, lead, marker.size)
        val test = if (speaker == REFERENCE) ref else FloatArray(n)
        val g = if (speaker.sub) Math.pow(10.0, LFE_TRIM_DB / 20.0).toFloat() else 1f
        for (i in sweep.indices) test[sweepAt + i] = sweep[i] * g
        return if (speaker == REFERENCE) mapOf(REFERENCE to ref) else mapOf(REFERENCE to ref, speaker to test)
    }

    /** The clip for [method]'s channels, interleaved, as 16-bit samples. */
    fun interleaved16(speaker: Speaker, method: PlayMethod, fs: Int = FS): ShortArray {
        val p = planes(speaker, fs)
        val slots = method.slots
        val n = p.values.first().size
        val out = ShortArray(n * slots.size)
        for ((k, s) in slots.withIndex()) {
            val plane = p[s] ?: continue
            for (i in 0 until n) out[i * slots.size + k] = (plane[i].coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
        }
        return out
    }

    /** Seconds from the first beep to the end of the sweep: how long the phone keeps listening once it hears them. */
    val afterMarkerSeconds: Double get() = Marker.SWEEP_OFFSET_SEC + SWEEP_SECONDS + 1.2

    /** What plays at [t] seconds into a clip, for the screens: null before the beeps, "beeps", or the sweep's Hz. */
    fun nowPlaying(t: Double): Double? {
        val sweepT = t - LEAD_SECONDS - Marker.SWEEP_OFFSET_SEC
        if (sweepT < 0 || sweepT > SWEEP_SECONDS) return null
        return 20.0 * Math.pow(1000.0, sweepT / SWEEP_SECONDS)
    }
}

/** What the phone heard from one speaker. [deltaMs] is the sweep's arrival after the beeps, less the planned gap. */
class SpeakerReading(
    val speaker: Speaker,
    /** Calibrated response on the default [FreqGrid] (null when nothing usable was heard). */
    val magDb: DoubleArray?,
    val deltaMs: Double?,
    val snrDb: Double?,
    val fatal: String? = null,
) {
    val heard: Boolean get() = magDb != null && fatal == null
}

/** One speaker's line of the result. Changes are what to add to the receiver's current setting. */
class SpeakerResult(
    val speaker: Speaker,
    val heard: Boolean,
    val levelDb: Double?,
    /** Raise (+) or lower (−) this speaker's level by this much, in 0.5 dB steps. */
    val trimDb: Double?,
    /** Set this speaker's distance this much farther (+) or nearer (−) than now. */
    val distanceM: Double?,
    val delayMs: Double?,
    /** dB RMS from the target, 1/6-octave smoothed, over the speaker's own range. */
    val rmsDb: Double?,
    /** The EQ for this speaker, fitted from its stock reading. */
    val eq: List<EqFilter>,
)

enum class TheatreMove { CHECK_SPEAKER, SET_LEVELS, SET_DISTANCES, USE_EQ, DONE }

class TheatreReport(
    val layout: Layout,
    val speakers: List<SpeakerResult>,
    /** Notes, one line each (a speaker that was not heard, one that seems to play from another). */
    val notes: List<String>,
    val headline: String,
    val move: TheatreMove,
    /** The speaker the move is about (CHECK_SPEAKER). */
    val moveSpeaker: Speaker?,
) {
    val levelSpreadDb: Double get() = speakers.mapNotNull { it.trimDb }.maxOfOrNull { abs(it) } ?: 0.0
    val distanceSpreadM: Double get() = speakers.mapNotNull { it.distanceM }.maxOfOrNull { abs(it) } ?: 0.0
    val heardCount: Int get() = speakers.count { it.heard }
}

object TheatreJudge {
    const val SOUND_MPS = 343.0
    /** Receivers set levels in 0.5 dB steps; smaller changes are not worth making. */
    const val LEVEL_OK_DB = 0.75
    const val DISTANCE_OK_M = 0.15
    const val EQ_WORTH_DB = 2.0
    const val QUIET_DB = 25.0

    private val MAIN_BAND = 500.0 to 2000.0
    private val SUB_BAND = 40.0 to 100.0

    fun fitConfig(s: Speaker): FitConfig =
        if (s.sub) FitConfig(maxFilters = 4, fMinHz = 20.0, fMaxHz = 120.0, refLowHz = SUB_BAND.first, refHighHz = SUB_BAND.second)
        else FitConfig(maxFilters = 8, fMinHz = 60.0, fMaxHz = 12000.0)

    fun level(m: DoubleArray, s: Speaker, grid: FreqGrid = FreqGrid()): Double {
        val (lo, hi) = if (s.sub) SUB_BAND else MAIN_BAND
        return bandPowerDb(m, grid, lo, hi)
    }

    /**
     * Levels, distances and EQ for every speaker of [layout] from [latest]. The EQ is fitted from [stock] (the first
     * full test) when there is one, so applying it and testing again never stacks a second EQ on top.
     */
    fun judge(layout: Layout, latest: List<SpeakerReading>, target: Target, stock: List<SpeakerReading>? = null, grid: FreqGrid = FreqGrid()): TheatreReport {
        val all = latest.associateBy { it.speaker }
        val mainLevels = layout.speakers.filter { !it.sub }.mapNotNull { s -> all[s]?.takeIf { it.heard }?.let { level(it.magDb!!, s, grid) } }.sorted()
        val median = mainLevels.getOrNull(mainLevels.size / 2)
        // far quieter than the rest (25 dB) is only the room's noise or another speaker's echo: not heard
        val by = all.mapValues { (s, r) ->
            if (r.heard && median != null && level(r.magDb!!, s, grid) < median - QUIET_DB) SpeakerReading(s, null, null, r.snrDb, "Too quiet to read.") else r
        }
        val heard = layout.speakers.mapNotNull { s -> by[s]?.takeIf { it.heard } }
        val mains = heard.filter { !it.speaker.sub }
        val ref = if (mains.isEmpty()) null else mains.map { level(it.magDb!!, it.speaker, grid) }.average()
        // the sub sits where the target puts the bass, against the mains
        val subLift = target.dbAt(60.0) - target.dbAt(1000.0)
        val refDelta = by[ChannelClip.REFERENCE]?.takeIf { it.heard }?.deltaMs ?: 0.0
        val results = layout.speakers.map { s ->
            val r = by[s]?.takeIf { it.heard }
            if (r == null) SpeakerResult(s, false, null, null, null, null, null, emptyList())
            else {
                val lv = level(r.magDb!!, s, grid)
                val want = ref?.let { if (s.sub) it + subLift else it }
                val trim = want?.let { roundHalf((it - lv).coerceIn(-12.0, 12.0)) }
                // the beeps' own detection offset cancels: every delay is taken against front left's
                val dMs = r.deltaMs?.let { it - refDelta }
                val dM = dMs?.let { roundCm(it / 1000.0 * SOUND_MPS) }
                val cfg = fitConfig(s)
                val rms = EqFitter.score(r.magDb, grid, target, cfg).first
                val base = stock?.firstOrNull { it.speaker == s && it.heard }?.magDb ?: r.magDb
                val eq = EqChain.capBoost(EqFitter.fit(base, grid, target, cfg).filters)
                SpeakerResult(s, true, lv, trim, dM, dMs, rms, eq)
            }
        }
        val notes = ArrayList<String>()
        val missing = results.filter { !it.heard }.map { it.speaker }
        missing.forEach { notes += "Nothing heard from ${it.title.lowercase()}." }
        val mixed = downmixed(layout, by, grid)
        mixed.forEach { (s, from) -> notes += "${s.title} sounds like ${from.title.lowercase()}: check the home theatre has ${s.title.lowercase()} switched on." }

        val levels = results.mapNotNull { it.trimDb }.maxOfOrNull { abs(it) } ?: 0.0
        val dists = results.mapNotNull { it.distanceM }.maxOfOrNull { abs(it) } ?: 0.0
        val worstEq = results.mapNotNull { it.rmsDb }.maxOrNull() ?: 0.0
        val (move, about) = when {
            missing.isNotEmpty() -> TheatreMove.CHECK_SPEAKER to missing.first()
            mixed.isNotEmpty() -> TheatreMove.CHECK_SPEAKER to mixed.first().first
            levels > LEVEL_OK_DB -> TheatreMove.SET_LEVELS to null
            dists > DISTANCE_OK_M -> TheatreMove.SET_DISTANCES to null
            worstEq > EQ_WORTH_DB -> TheatreMove.USE_EQ to null
            else -> TheatreMove.DONE to null
        }
        val headline = when (move) {
            TheatreMove.CHECK_SPEAKER -> if (about in missing) "No sound from ${about!!.title.lowercase()}" else "${about!!.title} plays from another speaker"
            TheatreMove.SET_LEVELS -> "Levels differ by up to ${fmt1(levels)} dB"
            TheatreMove.SET_DISTANCES -> "Levels match; distances are off by up to ${fmt2(dists)} m"
            TheatreMove.USE_EQ -> "Levels and distances match"
            TheatreMove.DONE -> "Every speaker is level and in time"
        }
        return TheatreReport(layout, results, notes, headline, move, about)
    }

    /**
     * Speakers that seem to play from another one: the same arrival and the same sound as a front speaker. A home
     * theatre set to fewer speakers than the layout folds the missing ones into the fronts.
     */
    fun downmixed(layout: Layout, by: Map<Speaker, SpeakerReading>, grid: FreqGrid = FreqGrid()): List<Pair<Speaker, Speaker>> {
        val fronts = listOf(Speaker.FL, Speaker.FR).mapNotNull { by[it]?.takeIf { r -> r.heard } }
        val out = ArrayList<Pair<Speaker, Speaker>>()
        for (s in layout.speakers) {
            if (s == Speaker.FL || s == Speaker.FR || s.sub) continue
            val r = by[s]?.takeIf { it.heard } ?: continue
            val match = fronts.firstOrNull { f ->
                val dt = if (r.deltaMs != null && f.deltaMs != null) abs(r.deltaMs - f.deltaMs) else Double.MAX_VALUE
                dt < 0.25 && shapeDifferenceDb(r.magDb!!, f.magDb!!, grid) < 0.6
            }
            if (match != null) out += s to match.speaker
        }
        return out
    }

    /** RMS difference of two responses (1/3-octave smoothed, 200 Hz to 8 kHz) once their levels are matched. */
    fun shapeDifferenceDb(a: DoubleArray, b: DoubleArray, grid: FreqGrid = FreqGrid()): Double {
        val sa = grid.smoothOctave(a, 3.0); val sb = grid.smoothOctave(b, 3.0)
        val lo = grid.indexAtOrAbove(200.0); val hi = grid.indexAtOrBelow(8000.0)
        var off = 0.0
        for (i in lo..hi) off += sa[i] - sb[i]
        off /= (hi - lo + 1)
        var s = 0.0
        for (i in lo..hi) { val d = sa[i] - sb[i] - off; s += d * d }
        return sqrt(s / (hi - lo + 1))
    }

    fun roundHalf(x: Double): Double = (x * 2.0).roundToInt() / 2.0 + 0.0
    private fun roundCm(x: Double): Double = (x * 100.0).roundToInt() / 100.0 + 0.0
    private fun fmt1(x: Double) = String.format(java.util.Locale.US, "%.1f", x)
    private fun fmt2(x: Double) = String.format(java.util.Locale.US, "%.2f", x)

    /** "+1.5 dB", "−2.0 dB", or "level" when no change is needed. */
    fun trimText(db: Double?): String = when {
        db == null -> "—"
        abs(db) < LEVEL_OK_DB -> "level"
        else -> (if (db > 0) "+" else "−") + fmt1(abs(db)) + " dB"
    }

    /** "+0.40 m", "−0.25 m", or "in time" when no change is needed. */
    fun distanceText(m: Double?): String = when {
        m == null -> "—"
        abs(m) < DISTANCE_OK_M -> "in time"
        else -> (if (m > 0) "+" else "−") + fmt2(abs(m)) + " m"
    }
}

/** Reads one speaker from a recording of its clip. */
object ChannelAnalysis {
    /**
     * [markerAt] is the first sample of the start beeps when the recorder already found them. The sweep is read from
     * the window the beeps point at; its arrival after them, less the planned gap, is [SpeakerReading.deltaMs].
     */
    fun read(
        rec: FloatArray, fs: Int, speaker: Speaker, cal: Calibration?, markerAt: Int? = null, gateMs: Double = 300.0,
        /** From the first beep to this sweep: a one-pass clip ([PassClip]) has several sweeps after one set of beeps. */
        sweepAfterSec: Double = Marker.SWEEP_OFFSET_SEC,
        /** How much of the recording around the sweep is read: in a pass the neighbouring sweeps stay out. */
        beforeSec: Double? = null, afterSec: Double = 3.0,
    ): SpeakerReading {
        val m0 = markerAt ?: Marker.find(rec, fs)
            ?: return SpeakerReading(speaker, null, null, null, "The start beeps were not heard: turn the volume up and check front left plays.")
        val lead = (sweepAfterSec * fs).toInt()
        val sweep = ChannelClip.sweep(fs)
        if (m0 + lead + sweep.size > rec.size) return SpeakerReading(speaker, null, null, null, "The recording ended before the sweep did.")
        val from = max(0, if (beforeSec == null) m0 - fs else m0 + lead - (beforeSec * fs).toInt())
        val to = min(rec.size, m0 + lead + sweep.size + (afterSec * fs).toInt())
        val expected = m0 - from + lead
        val m = Analyzer.analyze(rec.copyOfRange(from, to), sweep, fs, cal, gateMs, expectedStart = expected)
        if (m.fatal != null) return SpeakerReading(speaker, null, null, m.snrDb, m.fatal)
        return SpeakerReading(speaker, m.magDb, m.latencyMs - expected * 1000.0 / fs, m.snrDb)
    }
}
