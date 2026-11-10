package dev.autotune.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import dev.autotune.dsp.Layout
import dev.autotune.dsp.Speaker

/** The listener's seat on the room map, in the same units as [Speaker.x] and [Speaker.y]. */
private const val SEAT_Y = 0.62f

/**
 * The room from above: the screen at the top, the seat, and each speaker of [layout] as a Braun speaker (a square
 * plate with its cone). The speaker [playing] lights in the accent with rings going out from it, larger with
 * [level] (0..1, the mic's level on the phone; null pulses evenly). [notes] puts a short line under a speaker
 * ("+1.5 dB"; a new line for each line of it); speakers in [silent] are drawn faint. [large] is for the TV.
 */
@Composable
fun SpeakerMap(
    layout: Layout, playing: Speaker?, modifier: Modifier = Modifier, notes: Map<Speaker, String> = emptyMap(),
    silent: Set<Speaker> = emptySet(), level: Float? = null, large: Boolean = false,
) {
    val c = At.c
    val tm = rememberTextMeasurer()
    val moving = animationsOn()
    val pulse = if (moving && playing != null) {
        rememberInfiniteTransition(label = "rings").animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "ring").value
    } else 0.35f
    val label = MaterialTheme.typography.labelSmall
    val small = TextStyle(fontFamily = label.fontFamily, fontSize = if (large) 15.sp else 11.sp, fontFeatureSettings = "tnum")
    val name = TextStyle(fontFamily = label.fontFamily, fontSize = if (large) 18.sp else 12.sp, fontFeatureSettings = "tnum")
    val describe = buildString {
        append("Room map, ${layout.title}")
        playing?.let { append(", ${it.title.lowercase()} playing") }
    }
    Canvas(modifier.fillMaxWidth().aspectRatio(if (large) 1.6f else 1.25f).semantics { contentDescription = describe }) {
        val w = size.width; val h = size.height
        val pad = w * 0.07f
        fun at(x: Float, y: Float) = Offset(pad + (x + 1f) / 2f * (w - 2 * pad), pad + y * (h - 2 * pad))
        val unit = minOf(w, h)
        val hair = (unit / 260f).coerceAtLeast(1f)

        // the room and the screen on its front wall
        drawRoundRect(c.line, Offset(hair, hair), Size(w - 2 * hair, h - 2 * hair), CornerRadius(unit * 0.03f), style = Stroke(hair))
        val tvW = w * 0.34f
        drawRoundRect(c.text, Offset((w - tvW) / 2, pad * 0.35f), Size(tvW, unit * 0.018f), CornerRadius(unit * 0.01f))
        // the seat
        val seat = at(0f, SEAT_Y)
        val sw = unit * 0.16f; val sh = unit * 0.07f
        drawRoundRect(c.muted, Offset(seat.x - sw / 2, seat.y - sh / 2), Size(sw, sh), CornerRadius(sh / 3), style = Stroke(hair * 1.6f))
        drawCircle(c.muted, unit * 0.012f, seat)

        for (s in layout.speakers) {
            val p = at(s.x, s.y)
            val box = unit * (if (s.sub) 0.095f else 0.075f) * (if (large) 1.25f else 1f)
            val live = s == playing
            val faint = s in silent
            if (live) rings(p, box, pulse, level, c.signal, hair)
            val face = if (live) c.signal else c.panelHigh
            val ink = when { live -> c.onSignal; faint -> c.line; else -> c.text }
            drawRoundRect(face, Offset(p.x - box / 2, p.y - box / 2), Size(box, box), CornerRadius(box * 0.12f))
            drawRoundRect(if (faint) c.line else c.text, Offset(p.x - box / 2, p.y - box / 2), Size(box, box), CornerRadius(box * 0.12f), style = Stroke(hair * 1.4f))
            drawCircle(ink, box * 0.3f, p, style = Stroke(hair * 1.6f))
            drawCircle(ink, box * 0.08f, p)

            val code = tm.measure(s.short, name.copy(color = if (faint) c.muted else c.text))
            var y = p.y + box / 2 + unit * 0.012f
            drawText(code, topLeft = Offset(p.x - code.size.width / 2f, y))
            y += code.size.height
            notes[s]?.split('\n')?.forEach { n ->
                val t = tm.measure(n, small.copy(color = if (live) c.signal else c.muted))
                drawText(t, topLeft = Offset((p.x - t.size.width / 2f).coerceIn(hair * 4, w - t.size.width - hair * 4), y))
                y += t.size.height
            }
        }
    }
}

/** Sound going out from a playing speaker: three rings a third of a cycle apart, fading as they grow. */
private fun DrawScope.rings(p: Offset, box: Float, phase: Float, level: Float?, colour: androidx.compose.ui.graphics.Color, hair: Float) {
    val reach = box * (1.2f + 1.6f * (level ?: 0.7f).coerceIn(0f, 1f))
    for (k in 0 until 3) {
        val t = (phase + k / 3f) % 1f
        val r = box * 0.55f + t * reach
        drawCircle(colour.copy(alpha = (1f - t) * 0.7f), r, p, style = Stroke(hair * 2f))
    }
}
