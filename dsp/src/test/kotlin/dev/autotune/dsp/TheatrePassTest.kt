package dev.autotune.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TheatrePassTest {
    private val fs = 48000

    private class Sim(val distanceM: Double, val gainDb: Double, val subLowpassHz: Double? = null)

    private val room51 = mapOf(
        Speaker.FL to Sim(3.0, 0.0),
        Speaker.FR to Sim(3.0, -1.0),
        Speaker.C to Sim(2.8, 2.0),
        Speaker.SL to Sim(2.0, -3.0),
        Speaker.SR to Sim(2.2, -3.0),
        Speaker.SUB to Sim(3.4, 0.0, subLowpassHz = 90.0),
    )

    /** Plays [planes] through the simulated room's speakers and returns what the mic at the seat records. */
    private fun record(planes: Map<Speaker, FloatArray>, room: Map<Speaker, Sim>, seed: Long = 5): FloatArray {
        val pad = 2 * fs
        val out = FloatArray(planes.values.maxOf { it.size } + 2 * pad)
        for ((ch, plane) in planes) {
            val sim = room[ch] ?: continue
            var x = plane.copyOf()
            sim.subLowpassHz?.let { fc ->
                val a = exp(-2.0 * Math.PI * fc / fs).toFloat()
                repeat(2) { var y = 0f; for (i in x.indices) { y = (1 - a) * x[i] + a * y; x[i] = y } }
                val k = Math.pow(10.0, -ChannelClip.LFE_TRIM_DB / 20.0).toFloat()
                x = FloatArray(x.size) { x[it] * k }
            }
            val g = Math.pow(10.0, sim.gainDb / 20.0).toFloat()
            val d = (sim.distanceM / TheatreJudge.SOUND_MPS * fs).toInt()
            val refl = d + ((3 + ch.ordinal) * 0.001 * fs).toInt()
            for (i in x.indices) {
                out[pad + d + i] += g * x[i]
                if (pad + refl + i < out.size) out[pad + refl + i] += 0.4f * g * x[i]
            }
        }
        val rnd = java.util.Random(seed)
        for (i in out.indices) out[i] += (rnd.nextGaussian() * 1e-4).toFloat()
        return out
    }

    @Test
    fun levelNoiseLoopsCleanlyAndStaysInItsBand() {
        val x = LevelNoise.loop(sub = false)
        assertEquals(LevelNoise.LOOP, x.size)
        val rms = sqrt(x.sumOf { it.toDouble() * it } / x.size)
        assertEquals(LevelNoise.RMS_DBFS, 20 * log10(rms), 0.1)
        // whole cycles only: the jump from the last sample to the first is no bigger than any step inside
        val maxStep = (1 until x.size).maxOf { abs(x[it] - x[it - 1]) }
        assertTrue(abs(x[0] - x[x.size - 1]) <= maxStep)
        // almost all of its power is between 500 Hz and 2 kHz
        val inBand = LevelMeter.bandDb(x, 0, x.size, fs, LevelNoise.MAIN_BAND, null)
        val below = LevelMeter.bandDb(x, 0, x.size, fs, 20.0 to 400.0, null)
        val above = LevelMeter.bandDb(x, 0, x.size, fs, 2500.0 to 20000.0, null)
        assertTrue(inBand - below > 30 && inBand - above > 30, "in $inBand, below $below, above $above")
    }

    @Test
    fun theMeterReadsEachSpeakersLevelAgainstFrontLeft() {
        // each speaker's noise as the mic hears it; the last second is what the phone reads
        fun level(s: Speaker): Double {
            val loop = LevelNoise.planes(s, fs).getValue(s)
            val two = FloatArray(loop.size * 2) { loop[it % loop.size] }
            val rec = record(mapOf(s to two), room51)
            val end = rec.size - 2 * fs
            return LevelMeter.bandDb(rec, end - fs, end, fs, LevelNoise.band(s), null)
        }
        val ref = level(Speaker.FL)
        val target = Target.HOME_THEATER
        // the centre plays 2 dB louder (a little less at 2.8 m than front left's 3 m): turn it down about 2.5 dB
        val c = LevelMeter.change(level(Speaker.C), ref, Speaker.C, target)
        assertTrue(c in -3.0..-2.0, "centre $c")
        // the surrounds play 3 dB quieter (the simulated room delays a near speaker but does not make it louder)
        val sl = LevelMeter.change(level(Speaker.SL), ref, Speaker.SL, target)
        assertTrue(sl in 2.5..3.5, "surround left $sl")
        // front left against itself needs nothing
        assertTrue(LevelMeter.steady(LevelMeter.change(ref, ref, Speaker.FL, target)))
        // the sub is set to the target's bass lift over the mains, read over its own band
        val sw = LevelMeter.change(level(Speaker.SUB), ref, Speaker.SUB, target)
        assertTrue(abs(sw) < 12, "sub $sw")
    }

    @Test
    fun onePassReadsEverySpeakerAndTheWholeSystem() {
        val planes = PassClip.planes(Layout.L51, fs)
        // the start beeps sound once, on front left only
        val markerLen = Marker.waveform(fs).size
        val lead = (ChannelClip.LEAD_SECONDS * fs).toInt()
        assertTrue(planes.filterKeys { it != Speaker.FL }.values.all { p -> (lead until lead + markerLen).all { p[it] == 0f } })
        val rec = record(planes, room51)
        val pass = PassAnalysis.read(rec, fs, Layout.L51, null)
        assertTrue(pass.speakers.all { it.heard }, pass.speakers.joinToString { "${it.speaker}: ${it.fatal}" })
        assertNotNull(pass.system?.magDb)
        val sys = PassJudge.judge(Layout.L51, pass, Target.HOME_THEATER, null)
        val by = sys.speakers.speakers.associateBy { it.speaker }
        // distances against front left (3.0 m): the centre 0.2 m nearer, the surrounds about a metre nearer
        assertEquals(-0.2, by.getValue(Speaker.C).distanceM!!, 0.05)
        assertEquals(-1.0, by.getValue(Speaker.SL).distanceM!!, 0.05)
        assertTrue(by.getValue(Speaker.C).trimDb!! < -1.5)
        // levels differ, so setting them comes before the EQ; the one EQ is already worked out
        assertEquals(TheatreMove.SET_LEVELS, sys.move)
        assertNotNull(sys.rmsDb)
        assertTrue(sys.eq.isNotEmpty())
    }

    @Test
    fun aLevelRoomLinesUpAndTheSecondPassKeepsTheSameEq() {
        // every speaker at 3 m and level, the sub reaching up past the band it is read in, against a flat target
        val even = Layout.L51.speakers.associateWith { s -> if (s.sub) Sim(3.0, 0.0, subLowpassHz = 250.0) else Sim(3.0, 0.0) }
        val pass = PassAnalysis.read(record(PassClip.planes(Layout.L51, fs), even), fs, Layout.L51, null)
        val sys = PassJudge.judge(Layout.L51, pass, Target.FLAT, null)
        // the mains line up (the sub's level is the room's at low bass, which its echo raises, so it is measured)
        val mains = sys.speakers.speakers.filter { !it.speaker.sub }.maxOf { abs(it.trimDb!!) }
        assertTrue(mains <= 1.0, "mains $mains")
        assertTrue(sys.move != TheatreMove.CHECK_SPEAKER, "${sys.move}: ${sys.headline}")
        assertNotNull(sys.systemDb)
        // the second pass with nothing changed is judged against the first as the stock
        val again = PassJudge.judge(Layout.L51, pass, Target.FLAT, pass)
        assertEquals(sys.eq.size, again.eq.size)
    }

    @Test
    fun aSpeakerWithNoChannelPlaysFromTheFrontPair() {
        val c = FloatArray(4) { 0.5f }
        val st = Channels.interleave16(mapOf(Speaker.C to c), PlayMethod.STEREO, mixDown = true)
        assertEquals(8, st.size)
        val half = (0.5f / sqrt(2f) * 32767f).toInt()
        assertTrue(abs(st[0] - half) <= 1 && abs(st[1] - half) <= 1)
        // without mixing down it is left out, as before
        assertTrue(Channels.interleave16(mapOf(Speaker.C to c), PlayMethod.STEREO).all { it == 0.toShort() })
    }

    @Test
    fun theClipSaysWhatIsPlaying() {
        val first = ChannelClip.LEAD_SECONDS + Marker.SWEEP_OFFSET_SEC + 1.0
        assertEquals(0, PassClip.nowPlaying(Layout.L51, first)!!.first)
        val all = ChannelClip.LEAD_SECONDS + PassClip.sweepAfterMarker(6) + 2.0
        assertEquals(6, PassClip.nowPlaying(Layout.L51, all)!!.first)
        assertEquals(null, PassClip.nowPlaying(Layout.L51, 0.5))
    }
}
