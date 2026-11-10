package dev.autotune.dsp.tools

import dev.autotune.dsp.ChannelClip
import dev.autotune.dsp.PlayMethod
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes each 5.1 speaker's test clip as raw 16-bit 48 kHz PCM in Android's 5.1 order (FL FR FC LFE BL BR), for
 * tools/theatre/make-clips.sh to encode as Dolby Digital. Run with `./gradlew :dsp:theatreClips -Pout=<dir>`.
 */
object WriteTheatreClips {
    @JvmStatic
    fun main(args: Array<String>) {
        val dir = File(args.firstOrNull() ?: "build/theatre").apply { mkdirs() }
        for (s in PlayMethod.DOLBY_51.slots.filterNotNull()) {
            val pcm = ChannelClip.interleaved16(s, PlayMethod.DOLBY_51)
            val b = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            pcm.forEach { b.putShort(it) }
            File(dir, "${s.code}.raw").writeBytes(b.array())
            println("${s.code}.raw")
        }
    }
}
