package dev.autotune.dsp.tools

import dev.autotune.dsp.Layout
import dev.autotune.dsp.PassAnalysis
import dev.autotune.dsp.PassJudge
import dev.autotune.dsp.PlayMethod
import dev.autotune.dsp.Target
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.system.exitProcess

/**
 * Checks the encoded system-test clips: each `pass-<layout>.raw` (decoded back from Dolby Digital by
 * tools/theatre/make-clips.sh) is played through a room with every speaker at the same distance and level, and every
 * speaker and the whole system must be heard, in time. Run with `./gradlew :dsp:theatreCheck -Pout=<dir>`.
 */
object CheckTheatreClips {
    @JvmStatic
    fun main(args: Array<String>) {
        val dir = File(args.firstOrNull() ?: "build/theatre")
        val slots = PlayMethod.DOLBY_51.slots
        var bad = 0
        for (l in PlayMethod.DOLBY_51.layouts) {
            val f = File(dir, "pass-${l.name}.decoded.raw")
            if (!f.exists()) { println("missing ${f.name}"); bad++; continue }
            val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            val frames = b.remaining() / 2 / slots.size
            // every channel reaches the mic at once and at the same level, with two seconds of quiet either side
            val pad = 2 * 48000
            val mic = FloatArray(frames + 2 * pad)
            for (i in 0 until frames) {
                var sum = 0f
                for (k in slots.indices) {
                    val v = b.short / 32768f
                    // the LFE is played 10 dB louder by a receiver
                    sum += if (slots[k]?.sub == true) v * 3.16f else v
                }
                mic[pad + i] = sum
            }
            val pass = PassAnalysis.read(mic, 48000, l, null)
            val report = PassJudge.judge(l, pass, Target.FLAT, null)
            val heard = pass.speakers.count { it.heard }
            val spread = report.speakers.speakers.filter { !it.speaker.sub }.mapNotNull { it.distanceM }.maxOfOrNull { abs(it) } ?: 9.0
            val ok = heard == l.speakers.size && pass.system?.heard == true && spread < 0.05
            println("${l.name}: $heard/${l.speakers.size} heard, system ${if (pass.system?.heard == true) "heard" else "not heard"}, distances within ${"%.3f".format(spread)} m ${if (ok) "OK" else "BAD"}")
            if (!ok) bad++
        }
        if (bad > 0) exitProcess(1)
    }
}
