package dev.autotune.tv

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import dev.autotune.dsp.ChannelClip
import dev.autotune.dsp.PlayMethod
import dev.autotune.dsp.Speaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the TV's sound output can carry, and so how a single speaker is reached. */
class OutputProbe(val method: PlayMethod, val output: String, val dolby: Boolean, val rawChannels: Int)

/**
 * Plays one speaker's test clip on the TV's HDMI output. Over ARC that is a Dolby Digital 5.1 stream the TV passes
 * straight to the home theatre (prepared in assets/theatre/, see tools/theatre/make-clips.sh); with eARC or a box on
 * the receiver it is raw multichannel sound made here.
 */
object TvAudio {
    private val attrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
        .build()

    private val hdmiTypes = buildSet {
        add(AudioDeviceInfo.TYPE_HDMI); add(AudioDeviceInfo.TYPE_HDMI_ARC)
        if (Build.VERSION.SDK_INT >= 31) add(AudioDeviceInfo.TYPE_HDMI_EARC)
    }

    fun probe(ctx: Context): OutputProbe {
        val am = ctx.getSystemService(AudioManager::class.java)
        val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val hdmi = outs.firstOrNull { it.type in hdmiTypes }
        val name = when (hdmi?.type) {
            AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
            null -> outs.firstOrNull()?.productName?.toString() ?: "TV speakers"
            else -> if (Build.VERSION.SDK_INT >= 31 && hdmi.type == AudioDeviceInfo.TYPE_HDMI_EARC) "HDMI eARC" else "HDMI"
        }
        val dolby = direct(AudioFormat.ENCODING_AC3, AudioFormat.CHANNEL_OUT_5POINT1) ||
            hdmi?.encodings?.contains(AudioFormat.ENCODING_AC3) == true
        val raw = when {
            direct(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.CHANNEL_OUT_7POINT1_SURROUND) -> 8
            direct(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.CHANNEL_OUT_5POINT1) -> 6
            else -> 2
        }
        return OutputProbe(PlayMethod.pick(dolby, raw), name, dolby, raw)
    }

    private fun direct(encoding: Int, mask: Int): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return runCatching {
            AudioTrack.isDirectPlaybackSupported(
                AudioFormat.Builder().setEncoding(encoding).setSampleRate(ChannelClip.FS).setChannelMask(mask).build(), attrs,
            )
        }.getOrDefault(false)
    }

    /** Plays [speaker]'s clip with [method]. [onTime] gets the seconds played so far, about ten times a second. */
    suspend fun play(ctx: Context, method: PlayMethod, speaker: Speaker, onTime: (Double) -> Unit) = withContext(Dispatchers.IO) {
        val track: AudioTrack
        val write: suspend () -> Unit
        when (method) {
            PlayMethod.DOLBY_51 -> {
                val bytes = ctx.assets.open("theatre/${speaker.code}.ac3").use { it.readBytes() }
                track = build(AudioFormat.ENCODING_AC3, AudioFormat.CHANNEL_OUT_5POINT1, 64 * 1024)
                write = {
                    var off = 0
                    while (off < bytes.size && isActive) {
                        val w = track.write(bytes, off, minOf(6144, bytes.size - off), AudioTrack.WRITE_BLOCKING)
                        if (w < 0) error("The TV refused the Dolby stream (code $w)")
                        off += w
                    }
                }
            }
            else -> {
                val mask = when (method) {
                    PlayMethod.PCM_71 -> AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
                    PlayMethod.PCM_51 -> AudioFormat.CHANNEL_OUT_5POINT1
                    else -> AudioFormat.CHANNEL_OUT_STEREO
                }
                val pcm = ChannelClip.interleaved16(speaker, method)
                track = build(AudioFormat.ENCODING_PCM_16BIT, mask, ChannelClip.FS * method.slots.size * 2 / 4)
                write = {
                    var off = 0
                    while (off < pcm.size && isActive) {
                        val w = track.write(pcm, off, minOf(8192, pcm.size - off), AudioTrack.WRITE_BLOCKING)
                        if (w < 0) error("The TV refused the sound (code $w)")
                        off += w
                    }
                }
            }
        }
        val start = System.nanoTime()
        try {
            track.play()
            kotlinx.coroutines.coroutineScope {
                val clock = launch { while (isActive) { onTime((System.nanoTime() - start) / 1e9); delay(100) } }
                write()
                // the last part still sits in the buffer: wait until the clip's length has passed
                val left = ChannelClip.seconds - (System.nanoTime() - start) / 1e9 + 0.4
                if (left > 0) delay((left * 1000).toLong())
                clock.cancel()
            }
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private fun build(encoding: Int, mask: Int, bytes: Int): AudioTrack {
        val min = AudioTrack.getMinBufferSize(ChannelClip.FS, mask, encoding).coerceAtLeast(0)
        return AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(AudioFormat.Builder().setEncoding(encoding).setSampleRate(ChannelClip.FS).setChannelMask(mask).build())
            .setBufferSizeInBytes(maxOf(min, bytes))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }
}
