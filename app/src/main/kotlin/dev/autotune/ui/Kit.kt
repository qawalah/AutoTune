@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package dev.autotune.ui

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface Sharer {
    fun text(t: String)
    fun files(list: List<File>, mime: String, title: String)
}

// ---------------------------------------------------------------- icons (drawn, one stroke weight)

enum class Ic {
    TESTS, MEASURE, EQ, LIVE, EXPORT, GEAR, PLUS, INFO, CHEVRON, CHEVRON_DOWN, CLOSE, CHECK, BACK, ALERT, MORE, MENU,
    TUNE, MIC, SPEAKER, PLAY, STOP, SHARE, SAVE, AGAIN, TRASH, POWER, UNDO, EDIT, DIAG, DONE, BLUETOOTH, NEXT, FILE,
    CAR, HOME, FLAT, CURVE, LOCK, UNLOCK, AI, SEND, EXPAND,
}

/** The icon for the kind of system a test tunes. */
fun kindIcon(k: dev.autotune.TargetKind): Ic = when (k) {
    dev.autotune.TargetKind.CAR -> Ic.CAR
    dev.autotune.TargetKind.HOME -> Ic.HOME
    dev.autotune.TargetKind.FLAT -> Ic.FLAT
    dev.autotune.TargetKind.CUSTOM -> Ic.CURVE
    dev.autotune.TargetKind.HAND -> Ic.EDIT
}

@Composable
fun AtIcon(kind: Ic, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        val sw = 1.8f * u
        val st = Stroke(sw, cap = StrokeCap.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), sw, StrokeCap.Round)
        when (kind) {
            Ic.TESTS -> {
                line(9f, 6f, 20f, 6f); line(9f, 12f, 20f, 12f); line(9f, 18f, 20f, 18f)
                drawCircle(tint, 1.4f * u, Offset(4.8f * u, 6f * u)); drawCircle(tint, 1.4f * u, Offset(4.8f * u, 12f * u)); drawCircle(tint, 1.4f * u, Offset(4.8f * u, 18f * u))
            }
            Ic.MEASURE -> {
                drawCircle(tint, 8.5f * u, Offset(12f * u, 12f * u), style = st)
                drawCircle(tint, 3.2f * u, Offset(12f * u, 12f * u))
            }
            Ic.EQ -> {
                line(6f, 4f, 6f, 20f); line(12f, 4f, 12f, 20f); line(18f, 4f, 18f, 20f)
                drawCircle(tint, 2.6f * u, Offset(6f * u, 15f * u)); drawCircle(tint, 2.6f * u, Offset(12f * u, 8f * u)); drawCircle(tint, 2.6f * u, Offset(18f * u, 13f * u))
            }
            Ic.LIVE -> {
                line(4.5f, 20f, 4.5f, 14f); line(9.5f, 20f, 9.5f, 8f); line(14.5f, 20f, 14.5f, 11f); line(19.5f, 20f, 19.5f, 5f)
            }
            Ic.EXPORT -> {
                line(12f, 4f, 12f, 15f); line(7.5f, 10.5f, 12f, 15f); line(16.5f, 10.5f, 12f, 15f)
                line(5f, 19f, 19f, 19f)
            }
            Ic.GEAR -> {
                drawCircle(tint, 3.2f * u, Offset(12f * u, 12f * u), style = st)
                for (k in 0 until 8) {
                    val a = Math.toRadians(k * 45.0)
                    val c = Math.cos(a).toFloat(); val s = Math.sin(a).toFloat()
                    line(12f + 6.4f * c, 12f + 6.4f * s, 12f + 9.4f * c, 12f + 9.4f * s)
                }
                drawCircle(tint, 6.4f * u, Offset(12f * u, 12f * u), style = Stroke(sw * 0.9f))
            }
            Ic.PLUS -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            Ic.INFO -> {
                drawCircle(tint, 9f * u, Offset(12f * u, 12f * u), style = st)
                line(12f, 11f, 12f, 16.5f); drawCircle(tint, 1.1f * u, Offset(12f * u, 7.8f * u))
            }
            Ic.CHEVRON -> { line(9f, 5f, 16f, 12f); line(16f, 12f, 9f, 19f) }
            Ic.CHEVRON_DOWN -> { line(5f, 9f, 12f, 16f); line(12f, 16f, 19f, 9f) }
            Ic.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            Ic.CHECK -> { line(5f, 12.5f, 10f, 17.5f); line(10f, 17.5f, 19f, 7f) }
            Ic.BACK -> { line(19f, 12f, 5f, 12f); line(11f, 6f, 5f, 12f); line(11f, 18f, 5f, 12f) }
            Ic.MENU -> for (y in listOf(6f, 12f, 18f)) drawLine(tint, Offset(4f * u, y * u), Offset(20f * u, y * u), strokeWidth = 2f * u, cap = StrokeCap.Round)
            Ic.MORE -> { drawCircle(tint, 1.7f * u, Offset(12f * u, 5.5f * u)); drawCircle(tint, 1.7f * u, Offset(12f * u, 12f * u)); drawCircle(tint, 1.7f * u, Offset(12f * u, 18.5f * u)) }
            Ic.ALERT -> {
                line(12f, 4f, 21f, 19.5f); line(21f, 19.5f, 3f, 19.5f); line(3f, 19.5f, 12f, 4f)
                line(12f, 10f, 12f, 14.5f); drawCircle(tint, 1f * u, Offset(12f * u, 17f * u))
            }
            Ic.TUNE -> { // a wave settling onto a straight line
                val p = androidx.compose.ui.graphics.Path()
                for (k in 0..40) {
                    val x = 3f + k * 18f / 40f
                    val a = 6f * (1f - k / 40f)
                    val y = 12f + a * kotlin.math.sin(k / 40f * 6f * Math.PI.toFloat())
                    if (k == 0) p.moveTo(x * u, y * u) else p.lineTo(x * u, y * u)
                }
                drawPath(p, tint, style = st)
            }
            Ic.MIC -> {
                drawRoundRect(tint, Offset(9f * u, 3f * u), Size(6f * u, 11f * u), androidx.compose.ui.geometry.CornerRadius(3f * u), style = st)
                drawArc(tint, 0f, 180f, false, Offset(5.5f * u, 5f * u), Size(13f * u, 12f * u), style = st)
                line(12f, 17f, 12f, 21f); line(8.5f, 21f, 15.5f, 21f)
            }
            Ic.SPEAKER -> {
                line(4f, 9.5f, 7.5f, 9.5f); line(4f, 14.5f, 7.5f, 14.5f); line(4f, 9.5f, 4f, 14.5f)
                line(7.5f, 9.5f, 12f, 5f); line(12f, 5f, 12f, 19f); line(12f, 19f, 7.5f, 14.5f)
                drawArc(tint, -45f, 90f, false, Offset(9f * u, 8f * u), Size(8f * u, 8f * u), style = st)
                drawArc(tint, -45f, 90f, false, Offset(6.5f * u, 4.5f * u), Size(14f * u, 15f * u), style = st)
            }
            Ic.PLAY -> {
                val p = androidx.compose.ui.graphics.Path().apply { moveTo(7f * u, 5f * u); lineTo(19f * u, 12f * u); lineTo(7f * u, 19f * u); close() }
                drawPath(p, tint)
            }
            Ic.STOP -> drawRoundRect(tint, Offset(6.5f * u, 6.5f * u), Size(11f * u, 11f * u), androidx.compose.ui.geometry.CornerRadius(1.5f * u))
            Ic.SHARE -> {
                drawCircle(tint, 2.4f * u, Offset(17.5f * u, 6f * u)); drawCircle(tint, 2.4f * u, Offset(6.5f * u, 12f * u)); drawCircle(tint, 2.4f * u, Offset(17.5f * u, 18f * u))
                line(6.5f, 12f, 17.5f, 6f); line(6.5f, 12f, 17.5f, 18f)
            }
            Ic.SAVE -> {
                line(5f, 4f, 16f, 4f); line(16f, 4f, 20f, 8f); line(20f, 8f, 20f, 20f); line(20f, 20f, 5f, 20f); line(5f, 20f, 5f, 4f)
                line(8.5f, 4f, 8.5f, 9f); line(8.5f, 9f, 15f, 9f); line(15f, 9f, 15f, 4f)
                drawCircle(tint, 2.3f * u, Offset(12.5f * u, 14.5f * u), style = st)
            }
            Ic.AGAIN -> {
                drawArc(tint, -60f, 300f, false, Offset(5f * u, 5f * u), Size(14f * u, 14f * u), style = st)
                line(18.5f, 3.5f, 15.5f, 6.6f); line(15.5f, 6.6f, 19.6f, 8.4f)
            }
            Ic.TRASH -> {
                line(4f, 6.5f, 20f, 6.5f); line(9.5f, 6.5f, 10f, 3.5f); line(10f, 3.5f, 14f, 3.5f); line(14f, 3.5f, 14.5f, 6.5f)
                line(6f, 6.5f, 7f, 20f); line(7f, 20f, 17f, 20f); line(17f, 20f, 18f, 6.5f)
                line(10f, 10f, 10f, 16.5f); line(14f, 10f, 14f, 16.5f)
            }
            Ic.POWER -> {
                drawArc(tint, -60f, 300f, false, Offset(4.5f * u, 5f * u), Size(15f * u, 15f * u), style = st)
                line(12f, 3f, 12f, 11.5f)
            }
            Ic.UNDO -> {
                drawArc(tint, 180f, 180f, false, Offset(7f * u, 8f * u), Size(12f * u, 10f * u), style = st)
                line(19f, 13f, 19f, 17f); line(7f, 13f, 4f, 10f); line(7f, 13f, 10f, 10.5f)
            }
            Ic.EDIT -> {
                line(5f, 19f, 6f, 15f); line(6f, 15f, 16f, 5f); line(16f, 5f, 19f, 8f); line(19f, 8f, 9f, 18f); line(9f, 18f, 5f, 19f)
                line(14f, 7f, 17f, 10f)
            }
            Ic.DIAG -> {
                drawRoundRect(tint, Offset(5f * u, 3f * u), Size(14f * u, 18f * u), androidx.compose.ui.geometry.CornerRadius(2f * u), style = st)
                line(7.5f, 13f, 9.5f, 13f); line(9.5f, 13f, 11f, 9f); line(11f, 9f, 13f, 17f); line(13f, 17f, 14.5f, 13f); line(14.5f, 13f, 16.5f, 13f)
            }
            Ic.DONE -> {
                drawCircle(tint, 9f * u, Offset(12f * u, 12f * u), style = st)
                line(7.5f, 12.5f, 10.5f, 15.5f); line(10.5f, 15.5f, 16.5f, 9f)
            }
            Ic.BLUETOOTH -> { line(7f, 7.5f, 17f, 16.5f); line(17f, 16.5f, 12f, 21f); line(12f, 21f, 12f, 3f); line(12f, 3f, 17f, 7.5f); line(17f, 7.5f, 7f, 16.5f) }
            Ic.NEXT -> { line(5f, 12f, 19f, 12f); line(13f, 6f, 19f, 12f); line(13f, 18f, 19f, 12f) }
            Ic.CAR -> {
                // body, cabin, wheels
                line(3f, 15f, 3f, 12.5f); line(3f, 12.5f, 6f, 11.5f); line(6f, 11.5f, 8.5f, 7.5f); line(8.5f, 7.5f, 15.5f, 7.5f)
                line(15.5f, 7.5f, 18f, 11.5f); line(18f, 11.5f, 21f, 12.5f); line(21f, 12.5f, 21f, 15f)
                line(3f, 15f, 5f, 15f); line(9.5f, 15f, 14.5f, 15f); line(19f, 15f, 21f, 15f)
                drawCircle(tint, 2.2f * u, Offset(7.2f * u, 15.5f * u), style = st); drawCircle(tint, 2.2f * u, Offset(16.8f * u, 15.5f * u), style = st)
            }
            Ic.HOME -> {
                // a screen on a stand with a speaker either side: a home theatre
                drawRoundRect(tint, Offset(6.5f * u, 5f * u), Size(11f * u, 8f * u), androidx.compose.ui.geometry.CornerRadius(1f * u), style = st)
                line(12f, 13f, 12f, 16f); line(9f, 16f, 15f, 16f)
                drawRoundRect(tint, Offset(2f * u, 9f * u), Size(3f * u, 10f * u), androidx.compose.ui.geometry.CornerRadius(1f * u), style = st)
                drawRoundRect(tint, Offset(19f * u, 9f * u), Size(3f * u, 10f * u), androidx.compose.ui.geometry.CornerRadius(1f * u), style = st)
            }
            Ic.FLAT -> { line(3f, 12f, 21f, 12f); line(3f, 7f, 3f, 17f); line(21f, 7f, 21f, 17f) }
            Ic.CURVE -> {
                val p = androidx.compose.ui.graphics.Path().apply { moveTo(3f * u, 8f * u); cubicTo(8f * u, 8f * u, 9f * u, 13f * u, 12f * u, 13f * u); cubicTo(15f * u, 13f * u, 16f * u, 16f * u, 21f * u, 17f * u) }
                drawPath(p, tint, style = st)
            }
            Ic.LOCK, Ic.UNLOCK -> {
                drawRoundRect(tint, Offset(5f * u, 11f * u), Size(14f * u, 10f * u), androidx.compose.ui.geometry.CornerRadius(2f * u), style = st)
                if (kind == Ic.LOCK) drawArc(tint, 180f, 180f, false, Offset(8f * u, 4f * u), Size(8f * u, 13f * u), style = st)
                else drawArc(tint, 200f, 160f, false, Offset(11f * u, 3f * u), Size(8f * u, 12f * u), style = st)
                drawCircle(tint, 1.3f * u, Offset(12f * u, 16f * u))
            }
            Ic.AI -> {
                // a four-point sparkle and a small one: the AI assistant
                fun star(cx: Float, cy: Float, r: Float, w: Float) {
                    val p = androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx * u, (cy - r) * u)
                        quadraticTo((cx + w) * u, (cy - w) * u, (cx + r) * u, cy * u)
                        quadraticTo((cx + w) * u, (cy + w) * u, cx * u, (cy + r) * u)
                        quadraticTo((cx - w) * u, (cy + w) * u, (cx - r) * u, cy * u)
                        quadraticTo((cx - w) * u, (cy - w) * u, cx * u, (cy - r) * u)
                        close()
                    }
                    drawPath(p, tint, style = androidx.compose.ui.graphics.drawscope.Stroke(sw * 0.9f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
                star(10f, 13f, 8f, 1.6f)
                star(18.5f, 5.5f, 3.2f, 0.7f)
            }
            Ic.EXPAND -> {
                // four corners pointing out: open full screen
                line(4f, 9f, 4f, 4f); line(4f, 4f, 9f, 4f)
                line(15f, 4f, 20f, 4f); line(20f, 4f, 20f, 9f)
                line(4f, 15f, 4f, 20f); line(4f, 20f, 9f, 20f)
                line(15f, 20f, 20f, 20f); line(20f, 20f, 20f, 15f)
            }
            Ic.SEND -> {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(4f * u, 4.5f * u); lineTo(20.5f * u, 12f * u); lineTo(4f * u, 19.5f * u); lineTo(6.5f * u, 12f * u); close()
                }
                drawPath(p, tint, style = androidx.compose.ui.graphics.drawscope.Stroke(sw, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                line(6.5f, 12f, 12f, 12f)
            }
            Ic.FILE -> {
                line(6f, 3f, 14f, 3f); line(14f, 3f, 19f, 8f); line(19f, 8f, 19f, 21f); line(19f, 21f, 6f, 21f); line(6f, 21f, 6f, 3f)
                line(14f, 3f, 14f, 8f); line(14f, 8f, 19f, 8f)
            }
        }
    }
}

// ---------------------------------------------------------------- structure

/** A sheet: a ruled surface for what was measured and what is set (graph, meter, controls). Everything else stays flat. */
@Composable
fun Panel(modifier: Modifier = Modifier, pad: Dp = 16.dp, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(At.c.panel)
            .border(1.dp, At.c.line, MaterialTheme.shapes.medium).padding(pad),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

/**
 * A run sheet: the shop-green header strip that names what the sheet is about, ruled from the content below it, like
 * the header of a dyno printout. [trailing] sits at the strip's right end.
 */
@Composable
fun RunSheet(title: String, sub: String? = null, trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    val c = At.c
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(c.panel)
            .border(1.dp, c.line, MaterialTheme.shapes.medium),
    ) {
        Row(
            Modifier.fillMaxWidth().background(c.shell).heightIn(min = 44.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = c.onShell, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = c.onShellMuted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            trailing?.invoke()
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

/**
 * One row of ruled cells, each a reading with its name under it, the way a dyno sheet prints its figures. A cell's
 * [Reading.tint] colours the figure only.
 */
class Reading(val value: String, val unit: String?, val name: String, val tint: Color? = null)

@Composable
fun ReadingCells(cells: List<Reading>) {
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        cells.forEachIndexed { i, r ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(At.c.line))
            Column(Modifier.weight(1f).padding(start = if (i == 0) 0.dp else 12.dp, end = 8.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(r.value, style = MaterialTheme.typography.headlineSmall, color = r.tint ?: At.c.text, maxLines = 1)
                    if (r.unit != null) Text(" ${r.unit}", Modifier.padding(bottom = 3.dp), style = MaterialTheme.typography.labelMedium, color = At.c.muted, maxLines = 1)
                }
                Text(r.name, style = MaterialTheme.typography.bodySmall, color = At.c.muted, maxLines = 2)
            }
        }
    }
}

/** A section that stays closed until opened. The summary line says what is inside without opening it. */
@Composable
fun Fold(title: String, summary: String? = null, startOpen: Boolean = false, info: String? = null, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(startOpen) }
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = At.c.line)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClickLabel = if (open) "Close $title" else "Open $title") { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (summary != null) Muted(summary)
            }
            if (info != null) InfoButton(title, info)
            AtIcon(if (open) Ic.CHEVRON_DOWN else Ic.CHEVRON, At.c.muted, size = 20.dp)
        }
        if (open) Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

/** A titled block of content, separated from the previous one by a hairline. */
@Composable
fun Group(title: String, info: String? = null, divider: Boolean = true, trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (divider) HorizontalDivider(color = At.c.line, modifier = Modifier.padding(bottom = 6.dp))
        Row(Modifier.heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                if (info != null) InfoButton(title, info)
            }
            trailing?.invoke()
        }
        content()
    }
}

@Composable
fun InfoButton(title: String, text: String) {
    var open by remember { mutableStateOf(false) }
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "About $title") { open = true }
            .semantics { contentDescription = "About $title" },
        contentAlignment = Alignment.Center,
    ) { AtIcon(Ic.INFO, At.c.muted, size = 20.dp) }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { Text(text, Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Got it") } },
        )
    }
}

/**
 * Page title with an optional line of context under it, ruled off from the page like a sheet's header. On a page
 * opened over a tab ([LocalBack] set) it starts with a back arrow and leaves the menu out.
 */
@Composable
fun ScreenTitle(title: String, context: String? = null, onContext: (() -> Unit)? = null, contextIcon: Ic? = null, actions: @Composable () -> Unit = {}) {
    val back = LocalBack.current
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (back != null) Box(Modifier.offset(x = (-12).dp)) { IconAction(Ic.BACK, "Back", back) }
            Column(Modifier.weight(1f).then(if (back != null) Modifier.offset(x = (-8).dp) else Modifier)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (context != null) {
                    Row(
                        if (onContext != null) Modifier.clickable(onClick = onContext).heightIn(min = 24.dp) else Modifier,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (contextIcon != null) { AtIcon(contextIcon, At.c.signal, size = 18.dp); androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp)) }
                        Text(context, style = MaterialTheme.typography.bodyMedium, color = At.c.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
            actions()
            if (back == null) LocalMenu.current()
        }
        SheetRule()
    }
}

/** The rule under a sheet's header: a band in the shell colour over a hairline. */
@Composable
fun SheetRule() {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(At.c.shell))
        Box(Modifier.fillMaxWidth().padding(top = 2.dp).height(1.dp).background(At.c.line))
    }
}

/** The hamburger menu, supplied once by the screen and shown in every tab's title row. */
val LocalMenu = androidx.compose.runtime.compositionLocalOf<@Composable () -> Unit> { {} }

/** Set while a page is open over a tab: what its back arrow does. Null on the tabs themselves. */
val LocalBack = androidx.compose.runtime.compositionLocalOf<(() -> Unit)?> { null }

@Composable
fun IconAction(kind: Ic, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { AtIcon(kind, At.c.text) }
}

// ---------------------------------------------------------------- controls

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: Ic? = null) {
    androidx.compose.material3.Button(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = At.c.signal, contentColor = At.c.onSignal,
            disabledContainerColor = At.c.panelHigh, disabledContentColor = At.c.muted,
        ),
    ) { ButtonContent(text, icon, if (enabled) At.c.onSignal else At.c.muted) }
}

@Composable
private fun ButtonContent(text: String, icon: Ic?, tint: Color) {
    if (icon != null) {
        AtIcon(icon, tint, size = 18.dp)
        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
    }
    Text(text, style = MaterialTheme.typography.labelLarge)
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: Ic? = null) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (enabled) At.c.muted.copy(alpha = 0.6f) else At.c.line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = At.c.text, disabledContentColor = At.c.muted),
    ) { ButtonContent(text, icon, if (enabled) At.c.text else At.c.muted) }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, enabled: Boolean = true, danger: Boolean = false, icon: Ic? = null) {
    TextButton(
        onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = if (danger) At.c.bad else At.c.signal, disabledContentColor = At.c.muted),
    ) { ButtonContent(text, icon, if (!enabled) At.c.muted else if (danger) At.c.bad else At.c.signal) }
}

/**
 * A choice between a few options, always as a drop-down (never pills): the current value in a field, the options in a
 * menu. [label] is drawn above the field when given; [details] adds a muted line under each option in the menu.
 */
@Composable
fun DropSelect(
    options: List<String>, selected: Int, onSelect: (Int) -> Unit, enabled: Boolean = true,
    label: String? = null, details: List<String?>? = null, icon: Ic? = null, icons: List<Ic>? = null,
) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        if (label != null) Text(label, style = MaterialTheme.typography.labelMedium, color = At.c.muted, modifier = Modifier.padding(bottom = 4.dp))
        Box {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(MaterialTheme.shapes.small).background(At.c.panelHigh)
                    .clickable(enabled = enabled, role = Role.DropdownList, onClickLabel = "Choose ${label ?: "option"}") { open = true }
                    .padding(start = 14.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val shown = icons?.getOrNull(selected) ?: icon
                if (shown != null) { AtIcon(shown, if (enabled) At.c.signal else At.c.muted, size = 20.dp); androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp)) }
                Text(
                    options.getOrNull(selected) ?: "Choose", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) At.c.text else At.c.muted, maxLines = 2,
                )
                AtIcon(Ic.CHEVRON_DOWN, At.c.muted, size = 20.dp)
            }
            androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.widthIn(min = 240.dp, max = 360.dp)) {
                options.forEachIndexed { i, o ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text(o, style = MaterialTheme.typography.bodyLarge, color = if (i == selected) At.c.signal else At.c.text)
                                details?.getOrNull(i)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = At.c.muted) }
                            }
                        },
                        leadingIcon = icons?.getOrNull(i)?.let { ic -> { AtIcon(ic, if (i == selected) At.c.signal else At.c.muted, size = 20.dp) } },
                        trailingIcon = { if (i == selected) AtIcon(Ic.CHECK, At.c.signal, size = 18.dp) },
                        onClick = { open = false; onSelect(i) },
                    )
                }
            }
        }
    }
}

/**
 * The big Tune button: a disc in the middle of the screen with rings that pulse out from it while it waits.
 */
@Composable
fun PulsingButton(label: String, sub: String?, icon: Ic, enabled: Boolean, onClick: () -> Unit) {
    val c = At.c
    val anim = animationsOn() && enabled
    val phase = if (anim) {
        val inf = rememberInfiniteTransition(label = "pulse")
        inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "p").value
    } else 0f
    Box(Modifier.size(260.dp).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(260.dp)) {
            if (anim) for (k in 0 until 2) {
                val p = (phase + k * 0.5f) % 1f
                val r = size.minDimension / 2 * (0.66f + 0.34f * p)
                drawCircle(c.signal.copy(alpha = 0.35f * (1f - p)), r, style = Stroke(3.dp.toPx() * (1f - p) + 1f))
            }
        }
        val breathe = if (anim) 1f + 0.03f * kotlin.math.sin(phase * 2f * Math.PI.toFloat()) else 1f
        Box(
            Modifier.size(170.dp * breathe).clip(CircleShape)
                .background(if (enabled) c.signal else c.panelHigh)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AtIcon(icon, if (enabled) c.onSignal else c.muted, size = 34.dp)
                Text(label, style = MaterialTheme.typography.headlineMedium, color = if (enabled) c.onSignal else c.muted, fontWeight = FontWeight.SemiBold)
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = if (enabled) c.onSignal.copy(alpha = 0.8f) else c.muted)
            }
        }
    }
}

/**
 * Where the user is in a multi-step flow, in one row: Back, the step's title with where it sits and what comes next,
 * and Exit; then a slim bar with one segment per step. Back goes one screen back; Exit leaves and keeps the place.
 */
@Composable
fun StepHeader(index: Int, total: Int, title: String, onExit: (() -> Unit)?, onBack: (() -> Unit)? = null, names: List<String>? = null) {
    val c = At.c
    val next = names?.getOrNull(index + 1)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) Box(Modifier.offset(x = (-12).dp)) { IconAction(Ic.BACK, "Back", onBack) }
            Column(Modifier.weight(1f).then(if (onBack != null) Modifier.offset(x = (-8).dp) else Modifier)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(
                    "Step ${index + 1} of $total" + (next?.let { " · Next: $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1,
                )
            }
            if (onExit != null) TextAction("Exit", onExit, icon = Ic.CLOSE)
        }
        Row(
            Modifier.fillMaxWidth().semantics { contentDescription = "Step ${index + 1} of $total" },
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (i in 0 until total) {
                Box(
                    Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(1.dp))
                        .background(if (i < index) (if (c.dark) c.muted else c.shell) else if (i == index) c.signal else c.line),
                )
            }
        }
    }
}

/** Big on/off switch for the system EQ. */
@Composable
fun PowerToggle(on: Boolean, onChange: (Boolean) -> Unit, label: String, enabled: Boolean = true) {
    val t = updateTransition(on, label = "power")
    val x by t.animateDp(transitionSpec = { tween(if (animationsOn()) 160 else 0) }, label = "x") { if (it) 28.dp else 0.dp }
    Box(
        Modifier.size(72.dp, 44.dp).clip(RoundedCornerShape(22.dp))
            .background(if (on) At.c.signal else At.c.panelHigh)
            .toggleable(value = on, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = label },
    ) {
        Box(
            Modifier.padding(5.dp).offset(x = x).size(34.dp).clip(CircleShape)
                .background(if (on) At.c.onSignal else At.c.muted),
        )
    }
}

@Composable
fun animationsOn(): Boolean {
    val ctx = LocalContext.current
    return remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
}

/**
 * The measure control. Idle it is a solid disc; while measuring, the ring around it is the progress of the recording
 * (or a turning arc when the length is not known) and the disc becomes Stop.
 */
@Composable
fun MeasureControl(label: String, hint: String?, busy: Boolean, progress: Float?, enabled: Boolean, onClick: () -> Unit, onStop: () -> Unit) {
    val spin = if (busy && progress == null && animationsOn()) {
        val inf = rememberInfiniteTransition(label = "spin")
        inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "a").value
    } else 0f
    val c = At.c
    Box(
        Modifier.size(196.dp).semantics { contentDescription = if (busy) "Stop measuring" else label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(196.dp)) {
            val w = 6.dp.toPx()
            val inset = w / 2
            val d = Size(size.width - w, size.height - w)
            drawArc(c.line, 0f, 360f, false, Offset(inset, inset), d, style = Stroke(w))
            if (busy) {
                if (progress != null) drawArc(c.signal, -90f, 360f * progress, false, Offset(inset, inset), d, style = Stroke(w, cap = StrokeCap.Round))
                else drawArc(c.signal, spin - 90f, 110f, false, Offset(inset, inset), d, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        Box(
            Modifier.size(164.dp).clip(CircleShape)
                .background(if (busy) c.panelHigh else if (enabled) c.signal else c.panelHigh)
                .clickable(enabled = busy || enabled, role = Role.Button, onClick = if (busy) onStop else onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 18.dp)) {
                Text(
                    if (busy) "Stop" else label, style = MaterialTheme.typography.titleLarge,
                    color = if (busy) c.text else if (enabled) c.onSignal else c.muted,
                )
                if (hint != null && !busy) {
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = if (enabled) c.onSignal.copy(alpha = 0.8f) else c.muted)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- text helpers

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = At.c.muted)

/** Status line and warnings under an action. */
@Composable
fun StatusBlock(status: String?, warnings: List<String>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (status != null) Text(status, style = MaterialTheme.typography.bodyMedium)
        warnings.forEach { Warning(it) }
    }
}

@Composable
fun Warning(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AtIcon(Ic.ALERT, At.c.warn, Modifier.padding(top = 1.dp), 18.dp)
        Text(text, style = MaterialTheme.typography.bodySmall, color = At.c.text)
    }
}

/** Scrolls the field above the keyboard when it gets focus. */
@Composable
fun Modifier.bringIntoViewOnFocus(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return this
        .bringIntoViewRequester(requester)
        .onFocusEvent { if (it.isFocused) scope.launch { delay(350); requester.bringIntoView() } }
}


/** A drop-down list: shows the choice (or a prompt) and opens a menu whose items have a title and a detail line. */
@Composable
fun <T> PickField(
    label: String, selectedText: String?, placeholder: String, items: List<T>,
    title: (T) -> String, detail: (T) -> String?, onPick: (T) -> Unit, enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = At.c.muted, modifier = Modifier.padding(bottom = 4.dp))
        Box {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.small).background(At.c.panelHigh)
                    .clickable(enabled = enabled, role = Role.DropdownList, onClickLabel = "Choose $label") { open = true }
                    .padding(start = 14.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selectedText ?: placeholder, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                    color = if (selectedText != null) At.c.text else At.c.muted, maxLines = 2,
                )
                AtIcon(Ic.CHEVRON_DOWN, At.c.muted, size = 20.dp)
            }
            androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.widthIn(min = 280.dp, max = 360.dp)) {
                items.forEach { item ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text(title(item), style = MaterialTheme.typography.bodyLarge)
                                detail(item)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = At.c.muted) }
                            }
                        },
                        onClick = { open = false; onPick(item) },
                    )
                }
            }
        }
    }
}


/**
 * One drop-down for picking which EQ is in use: grouped by test (with the car / home theatre icon), each group listing
 * the next suggestion, the EQ of every run and the saved EQs of that test.
 */
@Composable
fun GroupedPick(label: String, choices: List<dev.autotune.EqChoice>, selectedKey: String, onPick: (String) -> Unit, emptyText: String) {
    var open by remember { mutableStateOf(false) }
    val sel = choices.firstOrNull { it.key == selectedKey }
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = At.c.muted, modifier = Modifier.padding(bottom = 4.dp))
        Box {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.small).background(At.c.panelHigh)
                    .clickable(enabled = choices.isNotEmpty(), role = Role.DropdownList, onClickLabel = "Choose $label") { open = true }
                    .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AtIcon(sel?.let { kindIcon(it.kind) } ?: Ic.EQ, At.c.signal, size = 22.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(sel?.let { "${it.group} · ${it.title}" } ?: emptyText, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                    sel?.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = At.c.muted, maxLines = 1) }
                }
                AtIcon(Ic.CHEVRON_DOWN, At.c.muted, size = 20.dp)
            }
            androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.widthIn(min = 280.dp, max = 380.dp)) {
                // each group shows its best few; "Show more" opens the rest of that group only
                var more by remember { mutableStateOf(setOf<String>()) }
                val groups = choices.groupBy { it.group }
                groups.forEach { (g, list) ->
                    val first = list.first()
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        AtIcon(kindIcon(first.kind), At.c.muted, size = 18.dp)
                        Text("  $g", style = MaterialTheme.typography.labelLarge, color = At.c.muted)
                    }
                    val hiddenCount = list.count { !it.primary && it.key != selectedKey }
                    val shown = if (g in more) list else list.filter { it.primary || it.key == selectedKey }
                    shown.forEach { c -> PickItem(c, selectedKey) { open = false; onPick(c.key) } }
                    if (hiddenCount > 0 && g !in more) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Show $hiddenCount more", Modifier.padding(start = 26.dp), style = MaterialTheme.typography.labelLarge, color = At.c.signal) },
                            trailingIcon = { AtIcon(Ic.CHEVRON_DOWN, At.c.signal, size = 18.dp) },
                            onClick = { more = more + g },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickItem(c: dev.autotune.EqChoice, selectedKey: String, onClick: () -> Unit) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Column(Modifier.padding(start = 26.dp, top = 2.dp, bottom = 2.dp)) {
                                Text(c.title, style = MaterialTheme.typography.bodyLarge, color = if (c.key == selectedKey) At.c.signal else At.c.text)
                                c.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = At.c.muted) }
                            }
                        },
                        trailingIcon = { if (c.key == selectedKey) AtIcon(Ic.CHECK, At.c.signal, size = 18.dp) },
                        onClick = onClick,
                    )
}

// ---------------------------------------------------------------- the pinned action

/** The screen's one recommended action, shown big above the tabs so it never scrolls away. */
class PinnedAction(val text: String, val icon: Ic?, val enabled: Boolean, val hint: String?, val onClick: () -> Unit)

/** Set by the app shell; a screen pins its action with [PinAction]. */
class PinSlot {
    var action by mutableStateOf<PinnedAction?>(null)
        internal set
    internal var owner: Any? = null
}

val LocalPin = androidx.compose.runtime.staticCompositionLocalOf { PinSlot() }

/**
 * Pins [text] as the screen's main action for as long as this is on screen. Only one screen pins at a time (the one
 * shown); leaving it clears the pin.
 */
@Composable
fun PinAction(text: String, onClick: () -> Unit, icon: Ic? = null, enabled: Boolean = true, hint: String? = null) {
    val slot = LocalPin.current
    val me = remember { Any() }
    androidx.compose.runtime.SideEffect {
        slot.owner = me
        slot.action = PinnedAction(text, icon, enabled, hint, onClick)
    }
    androidx.compose.runtime.DisposableEffect(slot) {
        onDispose { if (slot.owner === me) { slot.owner = null; slot.action = null } }
    }
}

/** The big button itself: tall, full width, the accent colour. */
@Composable
fun BigActionBar(a: PinnedAction) {
    Column(
        Modifier.fillMaxWidth().background(At.c.bg).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        a.hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = At.c.muted, maxLines = 2) }
        androidx.compose.material3.Button(
            onClick = a.onClick, enabled = a.enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = At.c.signal, contentColor = At.c.onSignal,
                disabledContainerColor = At.c.panelHigh, disabledContentColor = At.c.muted,
            ),
        ) {
            if (a.icon != null) {
                AtIcon(a.icon, if (a.enabled) At.c.onSignal else At.c.muted, size = 24.dp)
                androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
            }
            // the condensed cut, so long actions still fit on one line in big type
            Text(a.text, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 21.sp, lineHeight = 26.sp), maxLines = 2)
        }
    }
}
