package dev.autotune.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TheatreTest {
    private val fs = 48000

    /** A speaker in the simulated room: its distance from the mic, its level, a wall reflection, a low-pass for a sub. */
    private class Sim(val distanceM: Double, val gainDb: Double, val subLowpassHz: Double? = null)

    /** Plays [speaker]'s clip through the simulated speakers and returns what the mic records. */
    private fun record(speaker: Speaker, room: Map<Speaker, Sim>, routeTo: Map<Speaker, Speaker> = emptyMap(), seed: Long = 3): FloatArray {
        val planes = ChannelClip.planes(speaker, fs)
        val pad = 2 * fs
        val out = FloatArray(planes.values.first().size + 2 * pad)
        for ((ch, plane) in planes) {
            val phys = routeTo[ch] ?: ch
            val sim = room[phys] ?: continue
            var x = plane.copyOf()
            sim.subLowpassHz?.let { fc ->
                // two one-pole low-passes: a sub that plays only the bass
                val a = exp(-2.0 * Math.PI * fc / fs).toFloat()
                repeat(2) { var y = 0f; for (i in x.indices) { y = (1 - a) * x[i] + a * y; x[i] = y } }
                // the LFE channel is played 10 dB louder by every receiver
                val k = Math.pow(10.0, -ChannelClip.LFE_TRIM_DB / 20.0).toFloat()
                x = FloatArray(x.size) { x[it] * k }
            }
            val g = Math.pow(10.0, sim.gainDb / 20.0).toFloat()
            val d = (sim.distanceM / TheatreJudge.SOUND_MPS * fs).toInt()
            // each speaker has its own wall echo, as in a real room
            val refl = d + ((3 + phys.ordinal) * 0.001 * fs).toInt()
            for (i in x.indices) {
                out[pad + d + i] += g * x[i]
                if (pad + refl + i < out.size) out[pad + refl + i] += 0.4f * g * x[i]
            }
        }
        val rnd = java.util.Random(seed)
        for (i in out.indices) out[i] += (rnd.nextGaussian() * 1e-4).toFloat()
        return out
    }

    private val room51 = mapOf(
        Speaker.FL to Sim(3.0, 0.0),
        Speaker.FR to Sim(3.0, -1.0),
        Speaker.C to Sim(2.8, 2.0),
        Speaker.SL to Sim(2.0, -3.0),
        Speaker.SR to Sim(2.2, -3.0),
        Speaker.SUB to Sim(3.4, 0.0, subLowpassHz = 90.0),
    )

    private fun readAll(layout: Layout, room: Map<Speaker, Sim>, routeTo: Map<Speaker, Speaker> = emptyMap()) =
        layout.speakers.map { ChannelAnalysis.read(record(it, room, routeTo), fs, it, null) }

    @Test
    fun methodFollowsWhatTheOutputCarries() {
        assertEquals(PlayMethod.PCM_71, PlayMethod.pick(dolby = true, rawChannels = 8))
        assertEquals(PlayMethod.DOLBY_51, PlayMethod.pick(dolby = true, rawChannels = 2))
        assertEquals(PlayMethod.PCM_51, PlayMethod.pick(dolby = false, rawChannels = 6))
        assertEquals(PlayMethod.STEREO, PlayMethod.pick(dolby = false, rawChannels = 2))
        // ARC: 5.1 at most, and every layout it offers fits its channels
        for (m in PlayMethod.entries) for (l in m.layouts) for (s in l.speakers) assertNotNull(m.slotOf(s), "${m.name} ${l.name} ${s.name}")
        assertNull(PlayMethod.DOLBY_51.slotOf(Speaker.BL))
    }

    @Test
    fun clipPutsBeepsOnFrontLeftAndTheSweepOnTheTestedSpeaker() {
        val p = ChannelClip.planes(Speaker.SL, fs)
        assertEquals(setOf(Speaker.FL, Speaker.SL), p.keys)
        val lead = (ChannelClip.LEAD_SECONDS * fs).toInt()
        val sweepAt = lead + (Marker.SWEEP_OFFSET_SEC * fs).toInt()
        assertTrue(p[Speaker.FL]!!.copyOfRange(sweepAt, sweepAt + fs).all { it == 0f }, "no sweep on front left")
        assertTrue(p[Speaker.SL]!!.copyOfRange(lead, lead + fs / 2).all { it == 0f }, "no beeps on the tested speaker")
        // the LFE sweep is 10 dB lower
        val sub = ChannelClip.planes(Speaker.SUB, fs)[Speaker.SUB]!!
        val main = p[Speaker.SL]!!
        val ratio = sub.maxOf { abs(it) } / main.maxOf { abs(it) }
        assertEquals(Math.pow(10.0, -0.5), ratio.toDouble(), 0.01)
        // interleaved for the 5.1 slots: the surround left sits in Android's back-left channel (index 4)
        val il = ChannelClip.interleaved16(Speaker.SL, PlayMethod.DOLBY_51, fs)
        assertEquals(p[Speaker.SL]!!.size * 6, il.size)
        val i = sweepAt + fs
        assertTrue(abs(il[i * 6 + 4].toInt()) > 0 || abs(il[(i + 1) * 6 + 4].toInt()) > 0)
        assertEquals(0, il[i * 6 + 5].toInt())
    }

    @Test
    fun levelsAndDistancesComeOutOfA51Room() {
        val readings = readAll(Layout.L51, room51)
        readings.forEach { assertTrue(it.heard, "${it.speaker}: ${it.fatal}") }
        val r = TheatreJudge.judge(Layout.L51, readings, Target.FLAT)
        val by = r.speakers.associateBy { it.speaker }
        r.speakers.forEach { println("${it.speaker}: trim ${it.trimDb} dB, distance ${it.distanceM} m, rms ${it.rmsDb}") }
        // distances against front left: centre 0.2 m nearer, surrounds 1.0 and 0.8 m nearer
        assertEquals(0.0, by.getValue(Speaker.FL).distanceM!!, 0.03)
        assertEquals(-0.2, by.getValue(Speaker.C).distanceM!!, 0.05)
        assertEquals(-1.0, by.getValue(Speaker.SL).distanceM!!, 0.05)
        assertEquals(-0.8, by.getValue(Speaker.SR).distanceM!!, 0.05)
        // trims bring everyone to the average of the main speakers (surrounds included): centre down, surrounds up
        assertTrue(by.getValue(Speaker.C).trimDb!! < -2.5)
        assertTrue(by.getValue(Speaker.SL).trimDb!! >= 1.5)
        assertTrue(by.getValue(Speaker.SL).trimDb!! - by.getValue(Speaker.FL).trimDb!! in 2.5..3.5)
        assertEquals(TheatreMove.SET_LEVELS, r.move)
        assertTrue(r.notes.isEmpty(), r.notes.toString())
        assertTrue(r.speakers.all { it.trimDb!! * 2 == Math.rint(it.trimDb * 2) }, "0.5 dB steps")
    }

    @Test
    fun aLevelRoomInTimeIsDone() {
        val even = Layout.L31.speakers.associateWith { if (it.sub) Sim(3.0, 0.0, subLowpassHz = 250.0) else Sim(3.0, 0.0) }
        val r = TheatreJudge.judge(Layout.L31, readAll(Layout.L31, even), Target.FLAT)
        val mainTrims = r.speakers.filter { !it.speaker.sub }.maxOf { abs(it.trimDb!!) }
        assertTrue(mainTrims <= 1.0, "spread $mainTrims")
        // the mains line up; the sub's own low-pass delays it, which its distance setting should include
        val mains = r.speakers.filter { !it.speaker.sub }.maxOf { abs(it.distanceM!!) }
        assertTrue(mains < TheatreJudge.DISTANCE_OK_M, "distance $mains")
        assertTrue(r.speakers.first { it.speaker.sub }.distanceM!! > 0.0)
        assertTrue(r.notes.isEmpty(), r.notes.toString())
        assertTrue(r.move != TheatreMove.CHECK_SPEAKER, r.move.name)
    }

    @Test
    fun aMissingSpeakerIsNamed() {
        val noSl = room51 - Speaker.SL
        val r = TheatreJudge.judge(Layout.L51, readAll(Layout.L51, noSl), Target.FLAT)
        assertEquals(TheatreMove.CHECK_SPEAKER, r.move)
        assertEquals(Speaker.SL, r.moveSpeaker)
        assertTrue(r.headline.contains("surround left"), r.headline)
        assertTrue(!r.speakers.first { it.speaker == Speaker.SL }.heard)
    }

    @Test
    fun aSurroundFoldedIntoTheFrontIsCaught() {
        // the home theatre set to 3.1: surround left comes out of front left
        val r = TheatreJudge.judge(Layout.L51, readAll(Layout.L51, room51, routeTo = mapOf(Speaker.SL to Speaker.FL)), Target.FLAT)
        assertEquals(TheatreMove.CHECK_SPEAKER, r.move)
        assertEquals(Speaker.SL, r.moveSpeaker)
        assertTrue(r.notes.any { it.startsWith("Surround left sounds like front left") }, r.notes.toString())
    }

    @Test
    fun eqIsFittedFromTheStockReading() {
        val stock = readAll(Layout.L20, room51)
        val later = readAll(Layout.L20, room51.mapValues { (_, s) -> Sim(s.distanceM, s.gainDb + 1.0, s.subLowpassHz) })
        val a = TheatreJudge.judge(Layout.L20, stock, Target.FLAT)
        val b = TheatreJudge.judge(Layout.L20, later, Target.FLAT, stock = stock)
        assertEquals(a.speakers.map { it.eq }, b.speakers.map { it.eq })
    }

    @Test
    fun wordsForChanges() {
        assertEquals("level", TheatreJudge.trimText(0.5))
        assertEquals("+1.5 dB", TheatreJudge.trimText(1.5))
        assertEquals("−2.0 dB", TheatreJudge.trimText(-2.0))
        assertEquals("in time", TheatreJudge.distanceText(0.1))
        assertEquals("+0.40 m", TheatreJudge.distanceText(0.4))
        assertEquals(0.5, TheatreJudge.roundHalf(0.7))
        assertEquals(0.0, TheatreJudge.roundHalf(-0.2))
        assertNull(ChannelClip.nowPlaying(0.5))
        assertEquals(20.0, ChannelClip.nowPlaying(ChannelClip.LEAD_SECONDS + Marker.SWEEP_OFFSET_SEC + 1e-6)!!, 0.01)
    }
}
