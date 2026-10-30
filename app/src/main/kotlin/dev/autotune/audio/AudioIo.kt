package dev.autotune.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaRecorder
import android.net.Uri
import dev.autotune.dsp.Calibration
import dev.autotune.dsp.EqChain
import dev.autotune.dsp.EqProcessor
import dev.autotune.dsp.RtaAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteOrder

enum class OutChannel { BOTH, LEFT, RIGHT }

/** Interleaved stereo float clip. */
class Clip(val name: String, val samples: FloatArray, val fs: Int)

object UsbMic {
    fun find(ctx: Context): AudioDeviceInfo? {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }
}

/** A place the phone can send sound to. */
class Output(val id: Int, val name: String, val device: AudioDeviceInfo)

/**
 * Where the test sound goes. Android sends media to the most recently connected device, and many USB microphones also
 * register as USB headphones, so without this the sweep can end up in the mic instead of the car.
 */
object Outputs {
    private val TYPES = mapOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP to "Bluetooth",
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES to "Aux / headphones",
        AudioDeviceInfo.TYPE_WIRED_HEADSET to "Aux / headset",
        AudioDeviceInfo.TYPE_LINE_ANALOG to "Line out",
        AudioDeviceInfo.TYPE_LINE_DIGITAL to "Digital out",
        AudioDeviceInfo.TYPE_AUX_LINE to "Aux",
        AudioDeviceInfo.TYPE_USB_DEVICE to "USB",
        AudioDeviceInfo.TYPE_USB_HEADSET to "USB",
        AudioDeviceInfo.TYPE_HDMI to "HDMI",
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER to "Phone speaker",
        AudioDeviceInfo.TYPE_BUS to "Car (Android Auto)",
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO to "Bluetooth (call audio)",
        AudioDeviceInfo.TYPE_REMOTE_SUBMIX to "Cast",
    ) + (if (android.os.Build.VERSION.SDK_INT >= 31) mapOf(
        AudioDeviceInfo.TYPE_BLE_SPEAKER to "Bluetooth LE",
        AudioDeviceInfo.TYPE_BLE_HEADSET to "Bluetooth LE",
    ) else emptyMap())

    fun nameOf(d: AudioDeviceInfo): String {
        val kind = TYPES[d.type] ?: "Output type ${d.type}"
        val product = d.productName?.toString().orEmpty()
        return if (product.isBlank() || d.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || product == android.os.Build.MODEL) kind else "$kind: $product"
    }

    /** Where Android will send media right now (Android 13+), or null when the phone can't say. */
    fun systemRoute(ctx: Context): AudioDeviceInfo? {
        if (android.os.Build.VERSION.SDK_INT < 33) return null
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        return runCatching { am.getAudioDevicesForAttributes(attrs).firstOrNull() }.getOrNull()?.let { a ->
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == a.type && (a.address.isBlank() || it.address == a.address) }
        }
    }

    /** True when [d] is the USB microphone's own headphone output. */
    fun isTheMic(d: AudioDeviceInfo?, mic: AudioDeviceInfo?): Boolean {
        if (d == null || mic == null) return false
        if (d.type != AudioDeviceInfo.TYPE_USB_DEVICE && d.type != AudioDeviceInfo.TYPE_USB_HEADSET) return false
        val a = d.productName?.toString().orEmpty().lowercase()
        val b = mic.productName?.toString().orEmpty().lowercase()
        return a.isNotBlank() && b.isNotBlank() && (a.contains(b) || b.contains(a))
    }

    fun list(ctx: Context): List<Output> {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in TYPES }
            .distinctBy { it.type to it.productName.toString() }
            .map { d -> Output(d.id, nameOf(d), d) }
    }

    /** True when the mic also shows up as headphones, so other sound (music, AutoTune EQ) may go into the mic. */
    fun micIsAlsoOutput(list: List<Output>, mic: AudioDeviceInfo?): Boolean {
        return list.any { isTheMic(it.device, mic) }
    }
}

/**
 * What the microphone hears right now, for the measuring screen: the level of the latest block and a short stretch of
 * the waveform, plus when the test sound started playing. Updated from the recording thread about 12 times a second.
 */
object LiveCapture {
    val level = kotlinx.coroutines.flow.MutableStateFlow(-120f)
    val wave = kotlinx.coroutines.flow.MutableStateFlow(FloatArray(0))
    /** Wall-clock time the test sound was started (0 = not playing). */
    @Volatile var playStartMs = 0L

    fun feed(buf: FloatArray, from: Int, n: Int) {
        if (n <= 0) return
        var sq = 0.0
        for (i in from until from + n) sq += buf[i].toDouble() * buf[i]
        level.value = (10 * kotlin.math.log10(sq / n + 1e-12)).toFloat()
        val len = minOf(512, n / 2)
        val end = from + n
        wave.value = FloatArray(len) { k -> buf[end - 2 * (len - k)] }
    }

    fun reset() { level.value = -120f; wave.value = FloatArray(0); playStartMs = 0L }
}

object MeasurementRunner {
    private const val LEAD_MS = 300L
    /** Silence played before the test sound: wakes a sleeping Bluetooth link so the start of the sweep is not lost. */
    const val PREROLL_SEC = 1.5
    /** Recording keeps going this long after the last sample was handed to the output (Bluetooth delay is up to ~1 s). */
    private const val TAIL_SEC = 3.0

    /**
     * Plays [played] (mono) on the chosen output channel(s), after [PREROLL_SEC] of silence, while recording from [mic].
     * Recording starts before playback and stops [TAIL_SEC] after playback has been handed over, so a late start
     * (Bluetooth) can't cut the end off. The analyser finds the arrival time itself. [output] = where to play (null =
     * the phone's current route).
     */
    suspend fun capture(
        mic: AudioDeviceInfo?, played: FloatArray, fs: Int, channel: OutChannel, output: AudioDeviceInfo? = null,
        onRouted: (AudioDeviceInfo?) -> Unit = {},
    ): FloatArray =
        coroutineScope {
            val pre = (PREROLL_SEC * fs).toInt()
            val cap = (LEAD_MS * fs / 1000).toInt() + pre + played.size + ((TAIL_SEC + 8.0) * fs).toInt()
            val result = FloatArray(cap)
            val recorder = buildRecorder(fs, mic)
            val track = buildTrack(fs)
            if (output != null) track.preferredDevice = output
            val stopAt = java.util.concurrent.atomic.AtomicInteger(Int.MAX_VALUE)
            val gotA = java.util.concurrent.atomic.AtomicInteger(0)
            try {
                recorder.startRecording()
                val reader = async(Dispatchers.IO) {
                    var got = 0
                    while (got < minOf(cap, stopAt.get()) && isActive) {
                        val r = recorder.read(result, got, minOf(4096, minOf(cap, stopAt.get()) - got), AudioRecord.READ_BLOCKING)
                        if (r < 0) throw IllegalStateException("Microphone read failed (code $r)")
                        LiveCapture.feed(result, got, r)
                        got += r
                        gotA.set(got)
                    }
                }
                delay(LEAD_MS)
                val withPre = FloatArray(pre + played.size)
                System.arraycopy(played, 0, withPre, pre, played.size)
                val stereo = interleave(withPre, channel)
                track.play()
                LiveCapture.playStartMs = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    var off = 0
                    while (off < stereo.size && isActive) {
                        val w = track.write(stereo, off, minOf(8192, stereo.size - off), AudioTrack.WRITE_BLOCKING)
                        if (w < 0) throw IllegalStateException("Audio output failed (code $w)")
                        off += w
                    }
                }
                stopAt.set(gotA.get() + (TAIL_SEC * fs).toInt())
                onRouted(runCatching { track.routedDevice }.getOrNull())
                reader.await()
            } finally {
                LiveCapture.playStartMs = 0L
                runCatching { recorder.stop() }
                recorder.release()
                runCatching { track.stop() }
                track.release()
            }
            result.copyOf(minOf(gotA.get(), cap))
        }

    /** Record only (the sound comes from another device). Reports whole seconds elapsed. */
    suspend fun recordOnly(
        mic: AudioDeviceInfo?, fs: Int, seconds: Int,
        /** Checked once a second with what has been recorded so far; true ends the recording early. */
        stopWhen: ((FloatArray, Int) -> Boolean)? = null,
        onSecond: (Int) -> Unit,
    ): FloatArray =
        withContext(Dispatchers.IO) {
            val total = seconds * fs
            val out = FloatArray(total)
            val rec = buildRecorder(fs, mic)
            try {
                rec.startRecording()
                var got = 0
                var lastSec = 0
                while (got < total && isActive) {
                    val r = rec.read(out, got, minOf(4096, total - got), AudioRecord.READ_BLOCKING)
                    if (r < 0) throw IllegalStateException("Microphone read failed (code $r)")
                    LiveCapture.feed(out, got, r)
                    got += r
                    val sec = got / fs
                    if (sec != lastSec) {
                        lastSec = sec; onSecond(sec)
                        if (stopWhen != null && stopWhen(out, got)) return@withContext out.copyOf(got)
                    }
                }
            } finally {
                runCatching { rec.stop() }
                rec.release()
            }
            out
        }

    /** Set once by the app so the recorder can ask the phone what it supports. */
    @Volatile
    var appContext: android.content.Context? = null

    /** The microphone source the last recorder really used ("UNPROCESSED", "VOICE_RECOGNITION" or "MIC"). */
    @Volatile
    var lastMicSource: String? = null
        private set

    /** What happened to the phone's voice processing on the last recording, in words (for the checks and the log). */
    @Volatile
    var lastMicEffects: String? = null
        private set

    private val heldEffects = ArrayList<android.media.audiofx.AudioEffect>()

    /**
     * Some phones attach automatic gain control, noise suppression or echo cancelling to a recording even when the
     * source should be clean. Each one found on this recording is switched off (and kept switched off until the next
     * recording). Returns what was found, e.g. "gain control off, noise suppression off", or "none attached".
     */
    @Synchronized
    private fun switchOffVoiceEffects(session: Int): String {
        heldEffects.forEach { runCatching { it.release() } }
        heldEffects.clear()
        val done = ArrayList<String>()
        fun off(name: String, available: Boolean, make: () -> android.media.audiofx.AudioEffect?) {
            if (!available) return
            val e = runCatching { make() }.getOrNull() ?: return
            runCatching {
                if (e.enabled) { e.enabled = false; done += "$name off" } else done += "$name not active"
                heldEffects += e
            }.onFailure { done += "$name could not be changed"; runCatching { e.release() } }
        }
        off("gain control", android.media.audiofx.AutomaticGainControl.isAvailable()) { android.media.audiofx.AutomaticGainControl.create(session) }
        off("noise suppression", android.media.audiofx.NoiseSuppressor.isAvailable()) { android.media.audiofx.NoiseSuppressor.create(session) }
        off("echo cancelling", android.media.audiofx.AcousticEchoCanceler.isAvailable()) { android.media.audiofx.AcousticEchoCanceler.create(session) }
        return if (done.isEmpty()) "no voice processing attached" else done.joinToString(", ")
    }

    @SuppressLint("MissingPermission")
    internal fun buildRecorder(fs: Int, mic: AudioDeviceInfo?): AudioRecord {
        val fmt = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(fs)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val min = AudioRecord.getMinBufferSize(fs, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        // AGC and noise suppression tilt a sweep, so UNPROCESSED is wanted; not every phone offers it
        // (a USB mic is asked for it regardless: the property describes the phone's own microphones)
        val ctx = appContext
        val unprocessed = mic != null || ctx == null || runCatching {
            ctx.getSystemService(android.media.AudioManager::class.java)
                .getProperty(android.media.AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        }.getOrDefault(false)
        val sources = buildList {
            if (unprocessed) add(MediaRecorder.AudioSource.UNPROCESSED to "UNPROCESSED")
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION")
            add(MediaRecorder.AudioSource.MIC to "MIC")
        }
        for ((src, name) in sources) {
            val rec = runCatching {
                AudioRecord.Builder()
                    .setAudioSource(src)
                    .setAudioFormat(fmt)
                    .setBufferSizeInBytes(maxOf(min * 4, fs * 4))
                    .build()
            }.getOrNull() ?: continue
            if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); continue }
            if (mic != null) rec.preferredDevice = mic
            lastMicSource = name
            lastMicEffects = switchOffVoiceEffects(rec.audioSessionId)
            return rec
        }
        error("Could not open the microphone")
    }

    fun buildTrack(fs: Int): AudioTrack {
        val min = AudioTrack.getMinBufferSize(fs, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(fs)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(min, fs * 2 * 4 / 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun interleave(mono: FloatArray, ch: OutChannel): FloatArray {
        val out = FloatArray(mono.size * 2)
        for (i in mono.indices) {
            out[2 * i] = if (ch == OutChannel.RIGHT) 0f else mono[i]
            out[2 * i + 1] = if (ch == OutChannel.LEFT) 0f else mono[i]
        }
        return out
    }
}

/** Loops a clip, optionally through the EQ; the EQ can be switched on/off while it plays (A/B). */
class ClipPlayer {
    /** Where to play (null = the phone's current route). */
    @Volatile var output: AudioDeviceInfo? = null

    private var job: Job? = null
    @Volatile private var procs: Array<EqProcessor>? = null
    @Volatile private var chain: EqChain? = null
    private var fs = 48000

    val isPlaying: Boolean get() = job?.isActive == true

    fun setChain(c: EqChain?) {
        chain = c
        procs = c?.let { arrayOf(EqProcessor(it, fs), EqProcessor(it, fs)) }
    }

    fun play(scope: CoroutineScope, clip: Clip, c: EqChain?, onStopped: () -> Unit) {
        stop()
        fs = clip.fs
        setChain(c)
        job = scope.launch(Dispatchers.IO) {
            val track = MeasurementRunner.buildTrack(clip.fs)
            output?.let { track.preferredDevice = it }
            try {
                track.play()
                val buf = FloatArray(4096)
                var pos = 0
                while (isActive) {
                    val n = minOf(buf.size, clip.samples.size - pos)
                    val p = procs
                    for (i in 0 until n step 2) {
                        val l = clip.samples[pos + i]
                        val r = clip.samples[pos + i + 1]
                        buf[i] = (if (p != null) p[0].process(l) else l).coerceIn(-1f, 1f)
                        buf[i + 1] = (if (p != null) p[1].process(r) else r).coerceIn(-1f, 1f)
                    }
                    val w = track.write(buf, 0, n, AudioTrack.WRITE_BLOCKING)
                    if (w < 0) break
                    pos += n
                    if (pos >= clip.samples.size) pos = 0
                }
            } finally {
                runCatching { track.stop() }
                track.release()
                withContext(Dispatchers.Main) { onStopped() }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

/** Live RTA: records from the mic continuously and reports smoothed 1/3-octave bar levels (~10 per second). */
class RtaRunner {
    private var job: Job? = null
    val isRunning: Boolean get() = job?.isActive == true

    fun start(
        scope: CoroutineScope,
        mic: AudioDeviceInfo?,
        fs: Int,
        cal: Calibration?,
        onBars: (DoubleArray) -> Unit,
        onStopped: (String?) -> Unit,
        /** Broadband level of each ~43 ms block: (rms dBFS, peak dBFS). */
        onLevel: (Double, Double) -> Unit = { _, _ -> },
    ) {
        stop()
        job = scope.launch(Dispatchers.IO) {
            val rec = try {
                MeasurementRunner.buildRecorder(fs, mic)
            } catch (e: Exception) {
                onStopped(e.message)
                return@launch
            }
            val analyzer = RtaAnalyzer(fs, cal)
            var err: String? = null
            try {
                rec.startRecording()
                val buf = FloatArray(2048)
                while (isActive) {
                    val r = rec.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
                    if (r < 0) { err = "Microphone read failed (code $r)"; break }
                    if (r > 0) {
                        var sq = 0.0
                        var pk = 0f
                        for (k in 0 until r) { val v = buf[k]; sq += v.toDouble() * v; if (kotlin.math.abs(v) > pk) pk = kotlin.math.abs(v) }
                        onLevel(10.0 * kotlin.math.log10(sq / r + 1e-12), 20.0 * kotlin.math.log10(pk.toDouble() + 1e-6))
                    }
                    if (analyzer.push(buf, r)) onBars(analyzer.bandsDb())
                }
            } finally {
                runCatching { rec.stop() }
                rec.release()
                onStopped(err)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

object ClipDecoder {
    /** Decodes any audio file Android supports into interleaved stereo float (first [maxSeconds]). */
    suspend fun decode(ctx: Context, uri: Uri, name: String, maxSeconds: Int = 90): Clip =
        withContext(Dispatchers.IO) {
            val ex = MediaExtractor()
            ex.setDataSource(ctx, uri, null)
            val idx = (0 until ex.trackCount).firstOrNull {
                ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IllegalArgumentException("No audio track in that file")
            ex.selectTrack(idx)
            val inFmt = ex.getTrackFormat(idx)
            val codec = MediaCodec.createDecoderByType(inFmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(inFmt, null, null, 0)
            codec.start()

            var rate = inFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var chans = inFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var out = FloatArray(rate * 2 * 10)
            var len = 0
            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false
            try {
                while (!outDone && isActive) {
                    if (!inDone) {
                        val i = codec.dequeueInputBuffer(10_000)
                        if (i >= 0) {
                            val b = codec.getInputBuffer(i)!!
                            val n = ex.readSampleData(b, 0)
                            if (n < 0) {
                                codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inDone = true
                            } else {
                                codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                                ex.advance()
                            }
                        }
                    }
                    val o = codec.dequeueOutputBuffer(info, 10_000)
                    if (o >= 0) {
                        val ob = codec.getOutputBuffer(o)!!
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        val sb = ob.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val shorts = ShortArray(sb.remaining())
                        sb.get(shorts)
                        val frames = shorts.size / chans
                        if (len + frames * 2 > out.size) out = out.copyOf(maxOf(out.size * 2, len + frames * 2))
                        for (f in 0 until frames) {
                            val l = shorts[f * chans] / 32768f
                            val r = if (chans > 1) shorts[f * chans + 1] / 32768f else l
                            out[len++] = l
                            out[len++] = r
                        }
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || len / 2 >= rate * maxSeconds) {
                            outDone = true
                        }
                    } else if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val f = codec.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        chans = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            } finally {
                runCatching { codec.stop() }
                codec.release()
                ex.release()
            }
            require(len > rate) { "That clip is too short to play" }
            Clip(name, out.copyOf(len), rate)
        }
}
