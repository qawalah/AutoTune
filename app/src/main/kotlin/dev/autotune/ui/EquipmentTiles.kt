package dev.autotune.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.autotune.Equipment

/**
 * Set up's first question as four big keys with a drawing each (owner, 2026-11-12: "big buttons with graphics"), two
 * by two. The one picked has the accent edge and drawing; the others are drawn in ink with a hairline edge. The one
 * exception to "choices are drop-downs": it is the first thing Tune asks, and the drawings say it at a glance.
 */
@Composable
fun EquipmentTiles(selected: Equipment, onPick: (Equipment) -> Unit, enabled: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Equipment.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { e -> EquipmentTile(e, e == selected, { onPick(e) }, enabled, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun EquipmentTile(e: Equipment, on: Boolean, onClick: () -> Unit, enabled: Boolean, modifier: Modifier) =
    PictureKey(e.title, on, onClick, enabled, modifier) { drawEquipment(e, it) }

/**
 * A big key with a line drawing over its words, the Set up picture keys' shape. [on] gives it the accent edge and
 * drawing; two keys that are equal answers both pass false.
 */
@Composable
fun PictureKey(
    title: String, on: Boolean, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier,
    minHeight: androidx.compose.ui.unit.Dp = 118.dp, draw: DrawScope.(Color) -> Unit,
) {
    val c = At.c
    val shape = MaterialTheme.shapes.medium
    val ink = if (on) c.signal else c.text
    Column(
        modifier.heightIn(min = minHeight).clip(shape).background(c.panelHigh)
            .border(if (on) 2.dp else 1.dp, if (on) c.signal else c.line, shape)
            .selectable(selected = on, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Canvas(Modifier.fillMaxWidth().height(52.dp)) { draw(ink) }
        Text(title, style = MaterialTheme.typography.labelLarge, color = c.text, textAlign = TextAlign.Center, maxLines = 3)
    }
}

/**
 * The phone on its own, for "No, use this phone only": a phone upright, its speaker slot and camera at the top, the
 * microphone at the foot, and the sound it hears coming in from both sides.
 */
fun DrawScope.drawPhoneOnly(tint: Color) {
    val u = minOf(size.width / 48f, size.height / 26f)
    val ox = (size.width - 48f * u) / 2f
    val oy = (size.height - 26f * u) / 2f
    val sw = 1.5.dp.toPx()
    fun p(x: Float, y: Float) = Offset(ox + x * u, oy + y * u)
    drawRoundRect(tint, p(18.5f, 1.5f), Size(11f * u, 23f * u), CornerRadius(2.2f * u), style = Stroke(sw))
    drawLine(tint, p(22.5f, 4f), p(25.5f, 4f), sw, StrokeCap.Round)
    drawCircle(tint, 0.9f * u, p(24f, 21.5f))
    for (side in listOf(-1f, 1f)) {
        val cx = 24f + side * 3f
        for (r in listOf(13f, 17f)) drawArc(
            tint, if (side < 0) 155f else -25f, 50f, false, p(cx - r, 13f - r), Size(2 * r * u, 2 * r * u),
            style = Stroke(sw, cap = StrokeCap.Round),
        )
    }
}

/**
 * The drawings, in the line icons' manner (even strokes, square ends, no fill) on a 48 × 26 unit field centred in the
 * canvas: a car from the side, a TV between two tower speakers, a monitor between two desk speakers, and a portable
 * speaker with its sound.
 */
fun DrawScope.drawEquipment(e: Equipment, tint: Color) {
    val u = minOf(size.width / 48f, size.height / 26f)
    val ox = (size.width - 48f * u) / 2f
    val oy = (size.height - 26f * u) / 2f
    val sw = 1.5.dp.toPx()
    val line = Stroke(sw, cap = StrokeCap.Butt, join = StrokeJoin.Miter)
    val soft = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(ox + x * u, oy + y * u)
    fun seg(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, p(x1, y1), p(x2, y2), sw, StrokeCap.Butt)
    fun poly(vararg xy: Float, closed: Boolean = false) {
        val path = Path()
        var i = 0
        while (i < xy.size) { val q = p(xy[i], xy[i + 1]); if (i == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y); i += 2 }
        if (closed) path.close()
        drawPath(path, tint, style = soft)
    }
    fun ring(cx: Float, cy: Float, r: Float) = drawCircle(tint, r * u, p(cx, cy), style = Stroke(sw))
    fun dot(cx: Float, cy: Float, r: Float) = drawCircle(tint, r * u, p(cx, cy))
    fun box(x: Float, y: Float, w: Float, h: Float, r: Float = 1.2f) =
        drawRoundRect(tint, p(x, y), Size(w * u, h * u), CornerRadius(r * u), style = line)
    fun arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float) =
        drawArc(tint, start, sweep, false, p(cx - r, cy - r), Size(2 * r * u, 2 * r * u), style = soft)

    when (e) {
        Equipment.CAR -> {
            // body, roof and glass, from the side; the wheels sit in their arches
            poly(3f, 19f, 3f, 14.5f, 6f, 13.2f, 13f, 12.2f, 18.5f, 6.5f, 31f, 6.5f, 37.5f, 12.2f, 43f, 13.4f, 45f, 15.5f, 45f, 19f)
            seg(3f, 19f, 8.6f, 19f); seg(15.4f, 19f, 32.6f, 19f); seg(39.4f, 19f, 45f, 19f)
            poly(15.5f, 12.2f, 19.6f, 8.2f, 24f, 8.2f, 24f, 12.2f, closed = true)
            poly(26f, 12.2f, 26f, 8.2f, 30.2f, 8.2f, 34.2f, 12.2f, closed = true)
            ring(12f, 19.5f, 3.4f); dot(12f, 19.5f, 0.9f)
            ring(36f, 19.5f, 3.4f); dot(36f, 19.5f, 0.9f)
            seg(1f, 24.5f, 47f, 24.5f)
        }
        Equipment.HOME -> {
            // the TV on its foot, a soundbar under it and a tower speaker each side
            box(12f, 2.5f, 24f, 14.5f, 0.8f)
            seg(24f, 17f, 24f, 19f); seg(20f, 19f, 28f, 19f)
            box(15f, 21f, 18f, 3f, 1f); dot(19f, 22.5f, 0.6f); dot(24f, 22.5f, 0.6f); dot(29f, 22.5f, 0.6f)
            for (x in listOf(2.5f, 39.5f)) {
                box(x, 3f, 6f, 21.5f, 1f); ring(x + 3f, 7.5f, 1.3f); ring(x + 3f, 14f, 2.1f); ring(x + 3f, 20.2f, 2.1f)
            }
        }
        Equipment.PC -> {
            // a monitor on its stand, a keyboard in front, a small speaker each side
            box(13f, 2.5f, 22f, 14f, 0.8f)
            seg(24f, 16.5f, 24f, 20f); seg(19.5f, 20f, 28.5f, 20f)
            box(14f, 22.5f, 20f, 2.5f, 0.6f)
            for (x in listOf(3.5f, 38.5f)) {
                box(x, 8f, 6f, 14.5f, 1f); ring(x + 3f, 11.6f, 1.1f); ring(x + 3f, 17.6f, 2f)
            }
        }
        Equipment.OTHER -> {
            // a portable speaker with two drivers and its strap, the sound going out both ways
            box(11f, 8f, 26f, 13f, 5f)
            ring(18.5f, 14.5f, 3.4f); ring(29.5f, 14.5f, 3.4f); dot(18.5f, 14.5f, 0.9f); dot(29.5f, 14.5f, 0.9f)
            poly(16f, 8f, 18f, 4.5f, 30f, 4.5f, 32f, 8f)
            arc(24f, 14.5f, 16f, -25f, 50f); arc(24f, 14.5f, 20f, -22f, 44f)
            arc(24f, 14.5f, 16f, 155f, 50f); arc(24f, 14.5f, 20f, 158f, 44f)
        }
    }
}
