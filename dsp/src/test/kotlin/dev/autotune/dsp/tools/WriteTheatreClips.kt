package dev.autotune.dsp.tools

import dev.autotune.dsp.ChannelClip
import dev.autotune.dsp.Channels
import dev.autotune.dsp.LevelNoise
import dev.autotune.dsp.PassClip
import dev.autotune.dsp.PassKind
import dev.autotune.dsp.PlayMethod
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes the TV's test clips as raw 16-bit 48 kHz PCM in Android's 5.1 order (FL FR FC LFE BL BR), for
 * tools/theatre/make-clips.sh to encode as Dolby Digital. Run with `./gradlew :dsp:theatreClips -Pout=<dir>`.
 * - `<code>.raw`: one speaker's clip (beeps, then its sweep).
 * - `distances-<layout>.raw` and `eq-<layout>.raw`: the distances test (each speaker's sweep) and the EQ test (every
 *   speaker together) of each layout Dolby Digital 5.1 reaches, each in one clip.
 * - `noise-<code>.raw`: three loops of the speaker's level noise: exactly 256 Dolby Digital frames, so it loops cleanly.
 */
object WriteTheatreClips {
    private val method = PlayMethod.DOLBY_51

    @JvmStatic
    fun main(args: Array<String>) {
        val dir = File(args.firstOrNull() ?: "build/theatre").apply { mkdirs() }
        for (s in method.slots.filterNotNull()) {
            write(dir, "${s.code}.raw", ChannelClip.interleaved16(s, method))
            val loop = Channels.interleave16(LevelNoise.planes(s), method)
            write(dir, "noise-${s.code}.raw", ShortArray(loop.size * 3) { loop[it % loop.size] })
        }
        for (l in method.layouts) for (k in listOf(PassKind.DISTANCES, PassKind.EQ)) {
            write(dir, "${k.code}-${l.name}.raw", Channels.interleave16(PassClip.planes(l, kind = k), method))
        }
    }

    private fun write(dir: File, name: String, pcm: ShortArray) {
        val b = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { b.putShort(it) }
        File(dir, name).writeBytes(b.array())
        println(name)
    }
}
