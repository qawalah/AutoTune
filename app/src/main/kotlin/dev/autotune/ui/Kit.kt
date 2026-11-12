@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package dev.autotune.ui

import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.layout.boundsInWindow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.autotune.HeaderRect
import dev.autotune.alertsCount
import dev.autotune.artAt
import dev.autotune.fitTitlePx
import dev.autotune.headerBottomPx
import dev.autotune.headerPhase
import dev.autotune.ramp
import dev.autotune.slimArtRect
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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

// ---------------------------------------------------------------- icons (one Braun family)

/**
 * The app's icons, drawn as one family in the Braun audio language: plain geometry on a 24-unit grid, the shapes of
 * Braun controls and speakers (circles, square keys, slots, a record, a cassette), one stroke weight (1.8 units),
 * square ends and sharp corners on straight things, round ends only on round things (waves, curves, sticks). Filled
 * shapes are kept for knobs, fader caps, pivots and pilot lights. Readable from 20dp.
 *
 * New entries are appended, never reordered.
 */
enum class Ic {
    TESTS, MEASURE, EQ, LIVE, EXPORT, GEAR, PLUS, INFO, CHEVRON, CHEVRON_DOWN, CLOSE, CHECK, BACK, ALERT, MORE, MENU,
    TUNE, MIC, SPEAKER, PLAY, STOP, SHARE, SAVE, AGAIN, TRASH, POWER, UNDO, EDIT, DIAG, DONE, BLUETOOTH, NEXT, FILE,
    CAR, HOME, FLAT, CURVE, LOCK, UNLOCK, AI, SEND, EXPAND,
    // 2.2.0: bass and treble, the preamp, and one per flavour
    BASS, TREBLE, GAIN, VOLUME, NIGHT, SPEECH, DRUM, VOCAL, GUITAR, SYNTH,
}

/** The icon for the kind of system a test tunes. */
fun kindIcon(k: dev.autotune.TargetKind): Ic = when (k) {
    dev.autotune.TargetKind.CAR -> Ic.CAR
    dev.autotune.TargetKind.HOME -> Ic.HOME
    dev.autotune.TargetKind.FLAT -> Ic.FLAT
    dev.autotune.TargetKind.CUSTOM -> Ic.CURVE
    dev.autotune.TargetKind.HAND -> Ic.EDIT
}

/** The icon that says what a flavour is: the tune itself, bass, a drum for rock, a singer's mic for pop, and so on. */
fun flavourIcon(f: dev.autotune.Flavour): Ic = when (f) {
    dev.autotune.Flavour.NONE -> Ic.TUNE
    dev.autotune.Flavour.BASS -> Ic.BASS
    dev.autotune.Flavour.ROCK -> Ic.DRUM
    dev.autotune.Flavour.POP -> Ic.VOCAL
    dev.autotune.Flavour.ELECTRONIC -> Ic.SYNTH
    dev.autotune.Flavour.ACOUSTIC -> Ic.GUITAR
    dev.autotune.Flavour.SPEECH -> Ic.SPEECH
    dev.autotune.Flavour.LOUDNESS -> Ic.VOLUME
    dev.autotune.Flavour.NIGHT -> Ic.NIGHT
}

/** The icon for each way the dynamic EQ comes in. */
fun loudModeIcon(m: dev.autotune.LoudMode): Ic = when (m) {
    dev.autotune.LoudMode.OFF -> Ic.FLAT
    dev.autotune.LoudMode.VOLUME -> Ic.VOLUME
    dev.autotune.LoudMode.BUTTON -> Ic.POWER
    dev.autotune.LoudMode.LEVEL -> Ic.MIC
}

// ---- the geometry of the Tune knob and the Settings gear (pure, tested in IconTest)

const val KNOB_CX = 12f
const val KNOB_CY = 14.6f
const val KNOB_R = 5.9f
/** The knob's pointer, set towards the upper right as if tuned to a station. */
const val KNOB_POINTER_DEG = -60f
const val GEAR_BODY_R = 6.4f

/** The tuning scale over the knob: [n] ticks spread over its upper half, from [fromDeg] to [toDeg] (screen angles, -90 is up). */
fun knobTicks(n: Int = 5, fromDeg: Float = -150f, toDeg: Float = -30f): List<Float> =
    if (n <= 1) listOf((fromDeg + toDeg) / 2f) else List(n) { fromDeg + (toDeg - fromDeg) * it / (n - 1) }

/** The knob's ridged edge as a closed outline: [ridges] shallow ridges of [depth] round a circle of [r]. */
fun knobEdge(cx: Float, cy: Float, r: Float, ridges: Int = 24, depth: Float = 0.32f, steps: Int = 288): List<Pair<Float, Float>> =
    List(steps) { k ->
        val a = 2 * Math.PI * k / steps
        val rr = r + depth * 0.5f * kotlin.math.cos(a * ridges).toFloat()
        Pair(cx + rr * kotlin.math.cos(a).toFloat(), cy + rr * kotlin.math.sin(a).toFloat())
    }

/**
 * The gear's square teeth round (12, 12): [n] of them, evenly spaced, each a square-ended block [width] wide from
 * inside the hub's edge out to [outer]. Each tooth is its four corners.
 */
fun gearTeeth(n: Int = 8, inner: Float = GEAR_BODY_R - 0.8f, outer: Float = 9.7f, width: Float = 3.6f): List<List<Pair<Float, Float>>> =
    List(n) { k ->
        val a = 2 * Math.PI * k / n - Math.PI / 2
        val dx = kotlin.math.cos(a).toFloat(); val dy = kotlin.math.sin(a).toFloat()
        val nx = -dy * width / 2; val ny = dx * width / 2
        listOf(
            Pair(12f + dx * inner + nx, 12f + dy * inner + ny), Pair(12f + dx * outer + nx, 12f + dy * outer + ny),
            Pair(12f + dx * outer - nx, 12f + dy * outer - ny), Pair(12f + dx * inner - nx, 12f + dy * inner - ny),
        )
    }

@Composable
fun AtIcon(kind: Ic, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) { drawIc(kind, tint) }
}

/** Draws [kind] into this scope, which is treated as the icon's 24-unit square. */
fun DrawScope.drawIc(kind: Ic, tint: Color) = drawIcAt(kind, tint, size.width)

/** Draws [kind] at [px] square from this scope's origin. */
fun DrawScope.drawIcAt(kind: Ic, tint: Color, px: Float) {
    val u = px / 24f
    val sw = 1.8f * u
    val flat = Stroke(sw, cap = StrokeCap.Butt, join = androidx.compose.ui.graphics.StrokeJoin.Miter)
    val soft = Stroke(sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun seg(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, p(x1, y1), p(x2, y2), sw, StrokeCap.Butt)
    fun poly(vararg xy: Float, closed: Boolean = false, round: Boolean = false) {
        val path = androidx.compose.ui.graphics.Path()
        var i = 0
        while (i < xy.size) { if (i == 0) path.moveTo(xy[0] * u, xy[1] * u) else path.lineTo(xy[i] * u, xy[i + 1] * u); i += 2 }
        if (closed) path.close()
        drawPath(path, tint, style = if (round) soft else flat)
    }
    fun ring(cx: Float, cy: Float, r: Float) = drawCircle(tint, r * u, p(cx, cy), style = Stroke(sw))
    fun dot(cx: Float, cy: Float, r: Float) = drawCircle(tint, r * u, p(cx, cy))
    fun box(x: Float, y: Float, w: Float, h: Float, r: Float = 1.5f) =
        drawRoundRect(tint, p(x, y), Size(w * u, h * u), androidx.compose.ui.geometry.CornerRadius(r * u), style = flat)
    fun cap(x: Float, y: Float, w: Float, h: Float) = drawRect(tint, p(x, y), Size(w * u, h * u))
    fun wave(x0: Float, x1: Float, y: Float, amp: Float, periods: Float) {
        val path = androidx.compose.ui.graphics.Path()
        val n = 64
        for (k in 0..n) {
            val t = k / n.toFloat()
            val x = x0 + (x1 - x0) * t
            val yy = y - amp * kotlin.math.sin(t * periods * 2 * Math.PI).toFloat()
            if (k == 0) path.moveTo(x * u, yy * u) else path.lineTo(x * u, yy * u)
        }
        drawPath(path, tint, style = soft)
    }
    fun polar(cx: Float, cy: Float, r: Float, deg: Float): Offset {
        val a = Math.toRadians(deg.toDouble())
        return p(cx + r * kotlin.math.cos(a).toFloat(), cy + r * kotlin.math.sin(a).toFloat())
    }
    fun ray(cx: Float, cy: Float, r0: Float, r1: Float, deg: Float) = drawLine(tint, polar(cx, cy, r0, deg), polar(cx, cy, r1, deg), sw, StrokeCap.Butt)
    fun oval(x: Float, y: Float, w: Float, h: Float) = androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(x * u, y * u, (x + w) * u, (y + h) * u)) }
    fun circlePath(cx: Float, cy: Float, r: Float) = oval(cx - r, cy - r, 2 * r, 2 * r)
    fun outline(a: androidx.compose.ui.graphics.Path, b: androidx.compose.ui.graphics.Path, op: androidx.compose.ui.graphics.PathOperation) {
        val out = androidx.compose.ui.graphics.Path()
        out.op(a, b, op)
        drawPath(out, tint, style = flat)
    }

    when (kind) {
        // ---- the tabs: a record, the fader strip, the tuning knob, level bars, a gear
        Ic.TESTS -> { // a record on the platter, the tonearm resting on it
            ring(10f, 14f, 7.5f); dot(10f, 14f, 1.5f)
            dot(19.5f, 4f, 1.7f); poly(19.5f, 4f, 19.5f, 11f, 14.8f, 15.7f)
        }
        Ic.EQ -> { // three fader slots with square caps
            seg(6f, 3f, 6f, 21f); seg(12f, 3f, 12f, 21f); seg(18f, 3f, 18f, 21f)
            cap(3f, 13.2f, 6f, 3.6f); cap(9f, 6.2f, 6f, 3.6f); cap(15f, 10.2f, 6f, 3.6f)
        }
        Ic.TUNE -> { // a Braun radio's tuning knob: the ridged knob with its pointer cut in, a short scale over it
            val knob = androidx.compose.ui.graphics.Path()
            knobEdge(KNOB_CX, KNOB_CY, KNOB_R).forEachIndexed { i, (x, y) -> if (i == 0) knob.moveTo(x * u, y * u) else knob.lineTo(x * u, y * u) }
            knob.close()
            val slot = androidx.compose.ui.graphics.Path()
            val ends = listOf(KNOB_POINTER_DEG - 90f, KNOB_POINTER_DEG + 90f)
            val a = polar(KNOB_CX, KNOB_CY, 1.4f, KNOB_POINTER_DEG); val b = polar(KNOB_CX, KNOB_CY, KNOB_R - 1.1f, KNOB_POINTER_DEG)
            val half = 0.95f * u
            ends.forEachIndexed { i, d ->
                val r = Math.toRadians(d.toDouble())
                val off = Offset(half * kotlin.math.cos(r).toFloat(), half * kotlin.math.sin(r).toFloat())
                if (i == 0) { slot.moveTo(a.x + off.x, a.y + off.y); slot.lineTo(b.x + off.x, b.y + off.y) }
                else { slot.lineTo(b.x + off.x, b.y + off.y); slot.lineTo(a.x + off.x, a.y + off.y); slot.close() }
            }
            val cut = androidx.compose.ui.graphics.Path(); cut.op(knob, slot, androidx.compose.ui.graphics.PathOperation.Difference)
            drawPath(cut, tint)
            knobTicks().forEach { d -> ray(KNOB_CX, KNOB_CY, 9.2f, 10.8f, d) }
        }
        Ic.LIVE -> { // a slot grille of level bars
            seg(4f, 20.5f, 4f, 14f); seg(8f, 20.5f, 8f, 8.5f); seg(12f, 20.5f, 12f, 4f); seg(16f, 20.5f, 16f, 10f); seg(20f, 20.5f, 20f, 13f)
        }
        Ic.GEAR -> { // a gear: a round hub with its hole, eight square teeth, one line round it all
            var body = circlePath(12f, 12f, GEAR_BODY_R)
            gearTeeth().forEach { t ->
                val tooth = androidx.compose.ui.graphics.Path()
                t.forEachIndexed { i, (x, y) -> if (i == 0) tooth.moveTo(x * u, y * u) else tooth.lineTo(x * u, y * u) }
                tooth.close()
                body = androidx.compose.ui.graphics.Path().also { it.op(body, tooth, androidx.compose.ui.graphics.PathOperation.Union) }
            }
            drawPath(body, tint, style = flat)
            ring(12f, 12f, 3f)
        }
        Ic.MEASURE -> { // a needle meter: the scale arc, three marks, the needle on its pivot
            drawArc(tint, 205f, 130f, false, p(3f, 7f), Size(18f * u, 18f * u), style = flat)
            ray(12f, 16f, 6.3f, 9f, 230f); ray(12f, 16f, 6.3f, 9f, 270f); ray(12f, 16f, 6.3f, 9f, 310f)
            ray(12f, 16f, 0f, 8f, 295f); dot(12f, 16f, 1.8f)
            seg(4f, 20.5f, 20f, 20.5f)
        }
        // ---- what the sound comes from and goes to
        Ic.MIC -> { // the measurement mic: a small round head on a long tube
            box(9f, 2.5f, 6f, 6.5f, 3f)
            poly(10.1f, 9.5f, 11f, 21.5f, 13f, 21.5f, 13.9f, 9.5f)
        }
        Ic.SPEAKER -> { // a speaker cabinet: woofer and tweeter
            box(5f, 2.5f, 14f, 19f); ring(12f, 15f, 3.8f); ring(12f, 7.6f, 1.7f)
        }
        Ic.CAR -> {
            poly(2.5f, 16f, 2.5f, 12f, 6f, 11f, 8.5f, 6.5f, 15.5f, 6.5f, 18f, 11f, 21.5f, 12f, 21.5f, 16f)
            seg(2.5f, 16f, 4.6f, 16f); seg(9.4f, 16f, 14.6f, 16f); seg(19.4f, 16f, 21.5f, 16f)
            ring(7f, 16.5f, 2.3f); ring(17f, 16.5f, 2.3f)
        }
        Ic.HOME -> { // a screen with a soundbar under it: a home theatre
            box(2.5f, 3.5f, 19f, 12f, 1f); seg(12f, 15.5f, 12f, 17.5f)
            box(5f, 18.5f, 14f, 3f, 1f)
        }
        Ic.BLUETOOTH -> poly(7f, 7.5f, 17f, 16.5f, 12f, 21f, 12f, 3f, 17f, 7.5f, 7f, 16.5f)
        // ---- the sound itself
        Ic.FLAT -> { seg(3f, 12f, 21f, 12f); seg(3f, 7f, 3f, 17f); seg(21f, 7f, 21f, 17f) }
        Ic.CURVE -> {
            val path = androidx.compose.ui.graphics.Path().apply { moveTo(3f * u, 8f * u); cubicTo(8f * u, 8f * u, 9f * u, 13f * u, 12f * u, 13f * u); cubicTo(15f * u, 13f * u, 16f * u, 16f * u, 21f * u, 17f * u) }
            drawPath(path, tint, style = soft)
        }
        Ic.BASS -> wave(2.5f, 21.5f, 12f, 6.5f, 1f) // one slow, wide wave
        Ic.TREBLE -> wave(2.5f, 21.5f, 12f, 3f, 5f) // a tight, small wave
        Ic.GAIN -> { // one fader: the preamp
            seg(12f, 3f, 12f, 21f); cap(7.5f, 10.2f, 9f, 3.6f)
            seg(3.5f, 4f, 6.5f, 4f); seg(3.5f, 8f, 5.5f, 8f); seg(3.5f, 12f, 6.5f, 12f); seg(3.5f, 16f, 5.5f, 16f); seg(3.5f, 20f, 6.5f, 20f)
        }
        Ic.VOLUME -> { // the volume ramp, set low
            poly(2.5f, 19.5f, 21.5f, 19.5f, 21.5f, 5f, closed = true); seg(8f, 11f, 8f, 22f)
        }
        Ic.NIGHT -> outline(circlePath(11.5f, 12.5f, 8.5f), circlePath(16.5f, 8f, 7f), androidx.compose.ui.graphics.PathOperation.Difference)
        Ic.SPEECH -> {
            val bubble = androidx.compose.ui.graphics.Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(3f * u, 3.5f * u, 21f * u, 16f * u, androidx.compose.ui.geometry.CornerRadius(2f * u))) }
            val tail = androidx.compose.ui.graphics.Path().apply { moveTo(7f * u, 15f * u); lineTo(6.5f * u, 21f * u); lineTo(12.5f * u, 15f * u); close() }
            outline(bubble, tail, androidx.compose.ui.graphics.PathOperation.Union)
            seg(7f, 8f, 17f, 8f); seg(7f, 11.5f, 14f, 11.5f)
        }
        Ic.DRUM -> { // a snare drum and two sticks: rock
            drawOval(tint, p(4f, 8.5f), Size(16f * u, 5f * u), style = flat)
            seg(4f, 11f, 4f, 18f); seg(20f, 11f, 20f, 18f)
            drawArc(tint, 0f, 180f, false, p(4f, 15.5f), Size(16f * u, 5f * u), style = flat)
            drawLine(tint, p(5f, 2.5f), p(12.5f, 9.5f), sw, StrokeCap.Round); drawLine(tint, p(19f, 2.5f), p(11.5f, 9.5f), sw, StrokeCap.Round)
        }
        Ic.VOCAL -> { // a singer's ball mic: pop
            ring(15f, 8f, 4.5f)
            drawLine(tint, p(15f - 4.5f * 0.74f, 8f - 4.5f * 0.673f), p(15f + 4.5f * 0.74f, 8f + 4.5f * 0.673f), sw, StrokeCap.Butt)
            poly(10.79f, 10.25f, 4.09f, 18.36f, 5.71f, 19.84f, 13.15f, 12.41f)
        }
        Ic.GUITAR -> { // an acoustic guitar: acoustic and classical
            outline(circlePath(8.5f, 16f, 5f), circlePath(12f, 11f, 3.6f), androidx.compose.ui.graphics.PathOperation.Union)
            ring(8.8f, 15.6f, 1.5f)
            seg(14f, 8.2f, 18.6f, 2.6f); seg(17f, 1.8f, 20.2f, 3.6f)
        }
        Ic.SYNTH -> poly(2.5f, 16f, 6.5f, 16f, 6.5f, 8f, 11.5f, 8f, 11.5f, 16f, 16.5f, 16f, 16.5f, 8f, 21.5f, 8f) // a square wave: electronic
        // ---- actions
        Ic.PLAY -> poly(7.5f, 5f, 18.5f, 12f, 7.5f, 19f, closed = true)
        Ic.STOP -> box(6.5f, 6.5f, 11f, 11f, 1f)
        Ic.EXPORT -> { seg(12f, 15f, 12f, 4f); poly(7.5f, 8.5f, 12f, 4f, 16.5f, 8.5f); poly(4f, 13f, 4f, 20f, 20f, 20f, 20f, 13f) }
        Ic.SHARE -> {
            ring(17.5f, 5.5f, 2.4f); ring(6.5f, 12f, 2.4f); ring(17.5f, 18.5f, 2.4f)
            seg(8.6f, 10.8f, 15.4f, 6.7f); seg(8.6f, 13.2f, 15.4f, 17.3f)
        }
        Ic.SAVE -> { // a cassette: kept on tape
            box(2.5f, 5f, 19f, 14f); ring(8.5f, 10.5f, 2f); ring(15.5f, 10.5f, 2f); seg(8.5f, 12.5f, 15.5f, 12.5f)
            poly(6.5f, 19f, 8f, 16f, 16f, 16f, 17.5f, 19f)
        }
        Ic.AGAIN -> {
            drawArc(tint, -60f, 300f, false, p(5f, 5f), Size(14f * u, 14f * u), style = flat)
            poly(15.6f, 2.6f, 15.6f, 6.4f, 19.4f, 6.4f)
        }
        Ic.UNDO -> {
            drawArc(tint, 180f, 180f, false, p(7f, 8f), Size(12f * u, 10f * u), style = flat)
            seg(19f, 13f, 19f, 17.5f); poly(4f, 10f, 7f, 13f, 10f, 10f)
        }
        Ic.TRASH -> {
            seg(4f, 6.5f, 20f, 6.5f); poly(9.5f, 6.5f, 9.5f, 3.5f, 14.5f, 3.5f, 14.5f, 6.5f)
            poly(6f, 6.5f, 7f, 20.5f, 17f, 20.5f, 18f, 6.5f)
            seg(10f, 10f, 10f, 17f); seg(14f, 10f, 14f, 17f)
        }
        Ic.POWER -> { drawArc(tint, -60f, 300f, false, p(4.5f, 5f), Size(15f * u, 15f * u), style = flat); seg(12f, 3f, 12f, 11.5f) }
        Ic.EDIT -> { poly(5f, 19f, 6f, 15f, 16f, 5f, 19f, 8f, 9f, 18f, closed = true); seg(14f, 7f, 17f, 10f) }
        Ic.DIAG -> { box(4.5f, 3f, 15f, 18f); poly(7f, 13f, 9.5f, 13f, 11f, 9f, 13f, 17f, 14.5f, 13f, 17f, 13f) }
        Ic.FILE -> { poly(6f, 3f, 14f, 3f, 19f, 8f, 19f, 21f, 6f, 21f, closed = true); poly(14f, 3f, 14f, 8f, 19f, 8f) }
        Ic.LOCK, Ic.UNLOCK -> {
            box(5f, 11f, 14f, 10f)
            if (kind == Ic.LOCK) drawArc(tint, 180f, 180f, false, p(8f, 4f), Size(8f * u, 13f * u), style = flat)
            else drawArc(tint, 200f, 160f, false, p(11f, 3f), Size(8f * u, 12f * u), style = flat)
            dot(12f, 16f, 1.4f)
        }
        Ic.SEND -> { poly(4f, 4.5f, 20.5f, 12f, 4f, 19.5f, 6.5f, 12f, closed = true); seg(6.5f, 12f, 12f, 12f) }
        Ic.EXPAND -> {
            poly(4f, 9f, 4f, 4f, 9f, 4f); poly(15f, 4f, 20f, 4f, 20f, 9f)
            poly(4f, 15f, 4f, 20f, 9f, 20f); poly(15f, 20f, 20f, 20f, 20f, 15f)
        }
        Ic.AI -> { // a four-point spark and a small one
            fun star(cx: Float, cy: Float, r: Float, w: Float) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx * u, (cy - r) * u)
                    quadraticTo((cx + w) * u, (cy - w) * u, (cx + r) * u, cy * u)
                    quadraticTo((cx + w) * u, (cy + w) * u, cx * u, (cy + r) * u)
                    quadraticTo((cx - w) * u, (cy + w) * u, (cx - r) * u, cy * u)
                    quadraticTo((cx - w) * u, (cy - w) * u, cx * u, (cy - r) * u)
                    close()
                }
                drawPath(path, tint, style = soft)
            }
            star(10f, 13f, 8f, 1.6f); star(18.5f, 5.5f, 3.2f, 0.7f)
        }
        // ---- the plain marks
        Ic.PLUS -> { seg(12f, 5f, 12f, 19f); seg(5f, 12f, 19f, 12f) }
        Ic.CLOSE -> { seg(6f, 6f, 18f, 18f); seg(18f, 6f, 6f, 18f) }
        Ic.CHECK -> poly(5f, 12.5f, 10f, 17.5f, 19f, 7f)
        Ic.CHEVRON -> poly(9f, 5f, 16f, 12f, 9f, 19f)
        Ic.CHEVRON_DOWN -> poly(5f, 9f, 12f, 16f, 19f, 9f)
        Ic.BACK -> { seg(20f, 12f, 5f, 12f); poly(11f, 6f, 5f, 12f, 11f, 18f) }
        Ic.NEXT -> { seg(4f, 12f, 19f, 12f); poly(13f, 6f, 19f, 12f, 13f, 18f) }
        Ic.MENU -> { seg(4f, 6.5f, 20f, 6.5f); seg(4f, 12f, 20f, 12f); seg(4f, 17.5f, 20f, 17.5f) }
        Ic.MORE -> { dot(12f, 5.5f, 1.7f); dot(12f, 12f, 1.7f); dot(12f, 18.5f, 1.7f) }
        Ic.INFO -> { ring(12f, 12f, 9f); seg(12f, 10.5f, 12f, 17f); dot(12f, 7.6f, 1.2f) }
        Ic.DONE -> { ring(12f, 12f, 9f); poly(7.5f, 12.5f, 10.5f, 15.5f, 16.5f, 9f) }
        Ic.ALERT -> {
            drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(12f * u, 3.5f * u); lineTo(21.5f * u, 20f * u); lineTo(2.5f * u, 20f * u); close() }, tint, style = flat)
            seg(12f, 9.5f, 12f, 14.5f); dot(12f, 17.1f, 1.1f)
        }
    }
}

// ---------------------------------------------------------------- structure

// ---------------------------------------------------------------- motion

/** Arrivals: a fast start and a long soft landing. */
val EaseArrive = androidx.compose.animation.core.CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Exits: quick, and shorter than the entrance. */
val EaseLeave = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 1f, 1f)

/** [ms] when animations are on, 0 when the system's "Remove animations" is set. */
@Composable
fun motionMs(ms: Int): Int = if (animationsOn()) ms else 0

/** Scales a control to 0.97 while it is pressed. */
@Composable
fun Modifier.pressScale(source: androidx.compose.foundation.interaction.InteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val k by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.97f else 1f, tween(motionMs(120), easing = EaseArrive), label = "press")
    return this.then(Modifier.graphicsLayer { scaleX = k; scaleY = k })
}

/** A full page rises in about 28dp over what was there. */
fun riseIn(): androidx.compose.animation.ContentTransform =
    (androidx.compose.animation.fadeIn(tween(300, easing = EaseArrive)) +
        androidx.compose.animation.slideInVertically(tween(360, easing = EaseArrive)) { (it * 0.04f).toInt().coerceAtMost(84) }) togetherWith
        androidx.compose.animation.fadeOut(tween(100, easing = EaseLeave))

/** Back: the page drops away and what was under it fades back. */
fun dropOut(): androidx.compose.animation.ContentTransform =
    androidx.compose.animation.fadeIn(tween(240, delayMillis = 40, easing = EaseArrive)) togetherWith
        (androidx.compose.animation.fadeOut(tween(140, easing = EaseLeave)) +
            androidx.compose.animation.slideOutVertically(tween(160, easing = EaseLeave)) { (it * 0.04f).toInt().coerceAtMost(84) })

/**
 * Between tabs or steps: the old screen slips out about 18dp against the travel in 100 ms, the new one slides in about
 * 36dp from the side it comes from. [dir] is +1 moving forward (to the end), -1 back, 0 a plain cross-fade.
 */
fun slideSideways(dir: Int): androidx.compose.animation.ContentTransform {
    if (dir == 0) return androidx.compose.animation.fadeIn(tween(220, easing = EaseArrive)) togetherWith androidx.compose.animation.fadeOut(tween(100, easing = EaseLeave))
    return (androidx.compose.animation.fadeIn(tween(300, easing = EaseArrive)) +
        androidx.compose.animation.slideInHorizontally(tween(360, easing = EaseArrive)) { w -> dir * (w * 0.09f).toInt().coerceAtMost(110) }) togetherWith
        (androidx.compose.animation.fadeOut(tween(100, easing = EaseLeave)) +
            androidx.compose.animation.slideOutHorizontally(tween(100, easing = EaseLeave)) { w -> -dir * (w * 0.045f).toInt().coerceAtMost(55) })
}

/** A full-screen sheet rises from the bottom. */
@Composable
fun sheetIn(): androidx.compose.animation.EnterTransition =
    androidx.compose.animation.fadeIn(tween(motionMs(260), easing = EaseArrive)) +
        androidx.compose.animation.slideInVertically(tween(motionMs(380), easing = EaseArrive)) { it / 12 }

@Composable
fun sheetOut(): androidx.compose.animation.ExitTransition =
    androidx.compose.animation.fadeOut(tween(motionMs(140), easing = EaseLeave)) +
        androidx.compose.animation.slideOutVertically(tween(motionMs(180), easing = EaseLeave)) { it / 16 }

/**
 * Animates from one screen to the next. Only the arriving screen can pin the main action: the one leaving draws into a
 * slot of its own, so its pin never flickers back while it fades out.
 */
@Composable
fun <T> Transit(
    target: T, modifier: Modifier = Modifier,
    spec: androidx.compose.animation.AnimatedContentTransitionScope<T>.() -> androidx.compose.animation.ContentTransform,
    content: @Composable (T) -> Unit,
) {
    val pin = LocalPin.current
    androidx.compose.animation.AnimatedContent(targetState = target, modifier = modifier, transitionSpec = spec, label = "transit") { t ->
        val arriving = transition.targetState == androidx.compose.animation.EnterExitState.Visible
        val spare = remember { PinSlot() }
        androidx.compose.runtime.CompositionLocalProvider(LocalPin provides if (arriving) pin else spare) { content(t) }
    }
}

// ---------------------------------------------------------------- Simple / Advanced

/** How much the app shows: Simple (the default) keeps the essentials; Advanced adds explanations and extra controls. */
class ViewMode(val advanced: Boolean, val set: (Boolean) -> Unit)

val LocalViewMode = androidx.compose.runtime.compositionLocalOf { ViewMode(false) {} }

@Composable
fun isAdvanced(): Boolean = LocalViewMode.current.advanced

/** The short wording in Simple, the full one in Advanced. */
@Composable
fun say(simple: String, full: String): String = if (isAdvanced()) full else simple

/** Content shown only in Advanced; it folds in and out when the mode changes. */
@Composable
fun Adv(content: @Composable () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(
        visible = isAdvanced(),
        enter = androidx.compose.animation.fadeIn(tween(motionMs(220), easing = EaseArrive)) + androidx.compose.animation.expandVertically(tween(motionMs(320), easing = EaseArrive)),
        exit = androidx.compose.animation.fadeOut(tween(motionMs(100), easing = EaseLeave)) + androidx.compose.animation.shrinkVertically(tween(motionMs(180), easing = EaseLeave)),
    ) { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } }
}

/**
 * A field's label with an optional info bubble beside it. Explanations live in bubbles, never as paragraphs on the
 * page, in Simple and Advanced alike.
 */
@Composable
fun InfoLabel(label: String, info: String? = null) {
    Row(Modifier.heightIn(min = 24.dp).padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = At.c.muted)
        if (info != null) InfoButton(label, info, small = true)
    }
}

/**
 * Warnings folded into one line: the icon, how many, and the first one's start. Tapping it lists them all in a
 * bubble. Use this wherever a reading carries cautions, so they never take over the page.
 */
@Composable
fun Alerts(items: List<String>, title: String = "Notes on this reading") {
    if (items.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    AlertsLine(items) { open = true }
    if (open) NotesDialog(items, title) { open = false }
}

/**
 * The notes as an icon only: the warning sign in a 48dp target, read out as "1 note"; a tap lists them all. Used on
 * a result's display, where the line of text was too much.
 */
@Composable
fun AlertsIcon(items: List<String>, title: String = "Notes on this reading") {
    if (items.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Read the notes") { open = true }
            .semantics { contentDescription = alertsCount(items.size) },
        contentAlignment = Alignment.Center,
    ) { AtIcon(Ic.ALERT, At.c.warn, size = 22.dp) }
    if (open) NotesDialog(items, title) { open = false }
}

@Composable
private fun AlertsLine(items: List<String>, onOpen: () -> Unit) {
    val open = onOpen
    // a key-grey line with a hairline edge, like the other keys: no tinted fill; the warn colour stays on the icon
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(MaterialTheme.shapes.medium).background(At.c.panelHigh)
            .border(1.dp, At.c.line, MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClickLabel = "Read the notes") { open() }.padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AtIcon(Ic.ALERT, At.c.warn, size = 18.dp)
        Text(
            alertsCount(items.size), Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.labelMedium, color = At.c.text, maxLines = 1,
        )
        Text(
            " · " + items.first(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = At.c.muted, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        AtIcon(Ic.CHEVRON, At.c.muted, size = 16.dp)
    }
}

/** Every note in one dialog, each a warning line. */
@Composable
private fun NotesDialog(items: List<String>, title: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items.forEach { Warning(it) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Got it") } },
    )
}

/** The Simple / Advanced switch at the top of every screen, as a compact drop-down. */
@Composable
fun ModeSwitch() {
    val mode = LocalViewMode.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.heightIn(min = 48.dp).clip(MaterialTheme.shapes.small)
                .clickable(role = Role.DropdownList, onClickLabel = "Choose how much to show") { open = true }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.clip(MaterialTheme.shapes.extraSmall).background(At.c.panelHigh).border(1.dp, At.c.line, MaterialTheme.shapes.extraSmall)
                    .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (mode.advanced) "advanced" else "simple", color = At.c.text, maxLines = 1,
                    style = MaterialTheme.typography.labelMedium.let { it.copy(fontSize = cappedSp(it.fontSize, androidx.compose.ui.platform.LocalDensity.current.fontScale)) },
                )
                AtIcon(Ic.CHEVRON_DOWN, At.c.muted, Modifier.padding(start = 4.dp), size = 16.dp)
            }
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.widthIn(min = 220.dp)) {
            listOf(false to ("Simple" to "Less text, the main choices"), true to ("Advanced" to "Every option and explanation")).forEach { (adv, t) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(t.first, style = MaterialTheme.typography.bodyLarge, color = if (adv == mode.advanced) At.c.signal else At.c.text)
                            Text(t.second, style = MaterialTheme.typography.bodySmall, color = At.c.muted)
                        }
                    },
                    trailingIcon = { if (adv == mode.advanced) AtIcon(Ic.CHECK, At.c.signal, size = 18.dp) },
                    onClick = { open = false; mode.set(adv) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- cards

/** A deck: the plate of a Braun device (the SK 4's turntable deck). 8dp corners, a hairline edge, no shadow. */
val CardShape = RoundedCornerShape(8.dp)

/** The colours a deck draws with: the look's deck colours where it has them (Braun's light decks on a dark body). */
val deckColors: AtColors @Composable get() = At.c.deck ?: At.c

/** The deck's plate: its colour and a hairline edge, plain or, in the Gradient edge look, shaded dark to light. */
@Composable
fun Modifier.deckPlate(): Modifier {
    val d = deckColors
    val plate = this.clip(CardShape).background(d.panel)
    val e = d.edge
    return if (e != null) plate.border(1.dp, androidx.compose.ui.graphics.Brush.verticalGradient(e), CardShape)
    else plate.border(1.dp, d.line, CardShape)
}

/** Everything in [content] takes the deck's colours, Material's included, so text and keys read on the plate. */
@Composable
fun DeckColors(content: @Composable () -> Unit) {
    val d = At.c.deck
    if (d == null) { content(); return }
    androidx.compose.runtime.CompositionLocalProvider(LocalAt provides d, androidx.compose.material3.LocalContentColor provides d.text) {
        MaterialTheme(colorScheme = atScheme(d), typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
    }
}

/** True inside a card, so a card placed in another card draws flat instead of nesting. */
val LocalInCard = androidx.compose.runtime.compositionLocalOf { false }

/** Actions inside [Actions] fill the width and stack under each other. */
val LocalStacked = androidx.compose.runtime.compositionLocalOf { false }

/** A group of actions, one per row, each the full width. */
@Composable
fun Actions(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.runtime.CompositionLocalProvider(LocalStacked provides true) { content() }
    }
}

@Composable
private fun CardBox(modifier: Modifier, pad: Dp, content: @Composable () -> Unit) {
    if (LocalInCard.current) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        return
    }
    Column(
        modifier.fillMaxWidth().deckPlate()
            .animateContentSize(tween(motionMs(320), easing = EaseArrive)).padding(pad),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { DeckColors { androidx.compose.runtime.CompositionLocalProvider(LocalInCard provides true) { content() } } }
}

/** A card: one section of a screen (what was measured, what is set, a group of choices). */
@Composable
fun Panel(modifier: Modifier = Modifier, pad: Dp = 14.dp, content: @Composable () -> Unit) = CardBox(modifier, pad, content)

/**
 * A run sheet: the shop-green header strip that names what the sheet is about, ruled from the content below it, like
 * the header of a dyno printout. [trailing] sits at the strip's right end.
 */
@Composable
fun RunSheet(title: String, sub: String? = null, trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) = DeckColors {
    val c = At.c
    Column(
        Modifier.fillMaxWidth().deckPlate().animateContentSize(tween(motionMs(320), easing = EaseArrive)),
    ) {
        // the deck's own label line, like the lettering above a device's controls
        Row(
            Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(start = 16.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (sub != null) Text(sub.lowercase(), style = MaterialTheme.typography.labelSmall, color = c.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            trailing?.invoke()
        }
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.runtime.CompositionLocalProvider(LocalInCard provides true) { content() }
        }
    }
}

/**
 * One reading of the display: the figure and its unit, the system [score] beside it when one is known for that run,
 * and its name under it. [tint] colours the figure; [note] is a short tinted addition to the name ("−2.0 vs stock").
 */
class Reading(
    val value: String, val unit: String?, val name: String, val tint: Color? = null, val score: Int? = null,
    val note: String? = null, val noteTint: Color? = null,
)

/**
 * The display's readings: the first is the main one, large and light; the rest sit beside it, smaller, each with
 * its score box. When they do not all fit on one line (a score box needs its room), the main reading takes the first
 * line and the others share the second, so no figure or box is ever squeezed.
 */
@Composable
fun ReadingCells(cells: List<Reading>) {
    androidx.compose.ui.layout.Layout(
        content = { cells.forEachIndexed { i, r -> ReadingCell(r, main = i == 0) } },
        modifier = Modifier.fillMaxWidth(),
    ) { ms, cons ->
        val gap = 12.dp.roundToPx()
        val w = cons.maxWidth
        val natural = ms.map { it.maxIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity) }
        val oneLine = dev.autotune.readingsFitOneLine(natural, gap, w)
        if (oneLine) {
            // the main reading at the start; the others each end a column that shares out the spare room, bottoms aligned
            val main = ms[0].measure(androidx.compose.ui.unit.Constraints(maxWidth = natural[0].coerceAtMost(w)))
            val rest = ms.drop(1)
            val others = rest.mapIndexed { k, m -> m.measure(androidx.compose.ui.unit.Constraints(maxWidth = natural[k + 1])) }
            val spare = if (rest.isEmpty()) 0 else (w - main.width - others.sumOf { it.width } - gap * rest.size).coerceAtLeast(0) / rest.size
            val h = (listOf(main) + others).maxOf { it.height }
            layout(w, h) {
                main.place(0, h - main.height)
                var x = main.width
                others.forEach { p ->
                    x += gap + spare + p.width
                    p.place(x - p.width, h - p.height)
                }
            }
        } else {
            // two lines: the main reading, then the others side by side in equal columns from the start
            val main = ms[0].measure(androidx.compose.ui.unit.Constraints(maxWidth = w))
            val rest = ms.drop(1)
            val share = if (rest.isEmpty()) w else (w - gap * (rest.size - 1)) / rest.size
            val others = rest.map { it.measure(androidx.compose.ui.unit.Constraints(maxWidth = share.coerceAtLeast(0))) }
            val vgap = 10.dp.roundToPx()
            val second = others.maxOfOrNull { it.height } ?: 0
            val h = main.height + (if (others.isEmpty()) 0 else vgap + second)
            layout(w, h) {
                main.place(0, 0)
                others.forEachIndexed { k, p -> p.place((share + gap) * k, main.height + vgap + second - p.height) }
            }
        }
    }
}

/** One reading: the figure, its unit and its score box on one line (never squeezed), its name and note under it. */
@Composable
private fun ReadingCell(r: Reading, main: Boolean) {
    Column(horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                r.value, style = if (main) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall.copy(fontSize = 24.sp, lineHeight = 30.sp),
                color = r.tint ?: At.c.text, maxLines = 1, softWrap = false,
            )
            if (r.unit != null) Text(" ${r.unit}", Modifier.padding(bottom = if (main) 8.dp else 4.dp), style = MaterialTheme.typography.labelSmall, color = At.c.muted, maxLines = 1, softWrap = false)
        }
        val label = androidx.compose.ui.text.buildAnnotatedString {
            append(r.name.lowercase())
            if (r.note != null) {
                append(" · ")
                withStyle(androidx.compose.ui.text.SpanStyle(color = r.noteTint ?: At.c.muted)) { append(r.note) }
            }
        }
        // the score in one place for every reading: at the start of the line under its figure, so the figures stay
        // clear and the scores line up across the cells
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (r.score != null) ScoreTag(r.score, Modifier.padding(end = 6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = At.c.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

/** The system score (0-100) as a small ruled figure beside a dB reading: the number, then "/100". */
@Composable
fun ScoreTag(score: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(MaterialTheme.shapes.extraSmall).border(1.dp, At.c.line, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 5.dp, vertical = 1.dp)
            .semantics(mergeDescendants = true) { contentDescription = "score $score of 100, ${dev.autotune.dsp.SystemJudge.grade(score).lowercase()}" },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text("$score", style = MaterialTheme.typography.titleSmall, color = At.c.text, maxLines = 1, softWrap = false)
        Text("/100", Modifier.padding(start = 1.dp, bottom = 1.dp), style = MaterialTheme.typography.labelSmall, color = At.c.muted, maxLines = 1, softWrap = false)
    }
}

/**
 * The tuning scale of a Braun radio, for the distance from the target: ticks every half dB, a grey mark at the stock,
 * a faint mark at the best run, and the red needle at the reading shown. Lower is better, so the needle moving left
 * is progress.
 */
@Composable
fun TuningScale(needleDb: Double, stockDb: Double?, bestDb: Double? = null, predicted: Boolean = false) {
    val c = At.c
    val max = dev.autotune.scaleMax(listOfNotNull(needleDb, stockDb, bestDb))
    val anim by androidx.compose.animation.core.animateFloatAsState(needleDb.toFloat(), tween(motionMs(600), easing = EaseArrive), label = "needle")
    Column(Modifier.fillMaxWidth().semantics { contentDescription = "%.1f dB from the target".format(needleDb) }) {
        Canvas(Modifier.fillMaxWidth().height(40.dp).clip(MaterialTheme.shapes.small).background(c.bg)) {
            val pad = 10.dp.toPx(); val w = size.width - 2 * pad; val h = size.height
            fun x(db: Double) = pad + w * (db / max).toFloat().coerceIn(0f, 1f)
            val steps = (max * 2).toInt()
            for (k in 0..steps) {
                val major = k % 2 == 0
                drawLine(c.muted.copy(alpha = if (major) 0.9f else 0.5f), Offset(x(k / 2.0), h * 0.2f), Offset(x(k / 2.0), h * if (major) 0.55f else 0.4f), 1.dp.toPx())
            }
            stockDb?.let { drawLine(c.muted, Offset(x(it), h * 0.12f), Offset(x(it), h * 0.88f), 2.dp.toPx()) }
            bestDb?.let { drawLine(c.good, Offset(x(it), h * 0.6f), Offset(x(it), h * 0.88f), 2.dp.toPx()) }
            val nx = x(anim.toDouble())
            if (predicted) {
                var y = 0f
                while (y < h) { drawLine(c.signal, Offset(nx, y), Offset(nx, (y + 4.dp.toPx()).coerceAtMost(h)), 2.5.dp.toPx()); y += 7.dp.toPx() }
            } else drawLine(c.signal, Offset(nx, 0f), Offset(nx, h), 2.5.dp.toPx())
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val step = if (max <= 8) 2 else if (max <= 16) 4 else 10
            var v = 0
            while (v <= max.toInt()) { Text("$v", style = MaterialTheme.typography.labelSmall, color = c.muted); v += step }
        }
    }
}

/** A section that stays closed until opened. The summary line says what is inside without opening it. */
@Composable
fun Fold(title: String, summary: String? = null, startOpen: Boolean = false, info: String? = null, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(startOpen) }
    val inCard = LocalInCard.current
    val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 90f else 0f, tween(motionMs(220), easing = EaseArrive), label = "chevron")
    val body: @Composable () -> Unit = {
        if (inCard) HorizontalDivider(color = At.c.line)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClickLabel = if (open) "Close $title" else "Open $title") { open = !open }
                .padding(horizontal = if (inCard) 0.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (summary != null) Muted(summary)
            }
            if (info != null) InfoButton(title, info)
            AtIcon(Ic.CHEVRON, At.c.muted, Modifier.graphicsLayer { rotationZ = turn }, size = 20.dp)
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = open,
            enter = androidx.compose.animation.fadeIn(tween(motionMs(220), easing = EaseArrive)) + androidx.compose.animation.expandVertically(tween(motionMs(320), easing = EaseArrive)),
            exit = androidx.compose.animation.fadeOut(tween(motionMs(100), easing = EaseLeave)) + androidx.compose.animation.shrinkVertically(tween(motionMs(200), easing = EaseLeave)),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(bottom = 14.dp).padding(horizontal = if (inCard) 0.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) { androidx.compose.runtime.CompositionLocalProvider(LocalInCard provides true) { content() } }
        }
    }
    if (inCard) Column(Modifier.fillMaxWidth()) { body() }
    else Column(Modifier.fillMaxWidth().deckPlate()) { DeckColors { body() } }
}

/** A titled block of content, separated from the previous one by a hairline. */
@Composable
fun Group(title: String, info: String? = null, divider: Boolean = true, trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    // a card of its own; inside another card it is a titled block under a hairline
    val inCard = LocalInCard.current
    CardBox(Modifier, 14.dp) {
        if (inCard && divider) HorizontalDivider(color = At.c.line, modifier = Modifier.padding(bottom = 6.dp))
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
fun InfoButton(title: String, text: String, small: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Box(
        Modifier.size(if (small) 32.dp else 48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "About $title") { open = true }
            .semantics { contentDescription = "About $title" },
        contentAlignment = Alignment.Center,
    ) { AtIcon(Ic.INFO, At.c.muted, size = if (small) 16.dp else 20.dp) }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { Text(text, Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Got it") } },
        )
    }
}

/** What a screen's header shows: its name, the one line under it, and the graphic's key (the name in lower case). */
data class HeaderSpec(val title: String, val context: String? = null, val contextIcon: Ic? = null) {
    val artKey: String get() = title.lowercase()
}

/** Where a screen hands its header to the app shell, which draws it over the content ([ScreenHeader]). */
class TitleSlot {
    var spec by mutableStateOf<HeaderSpec?>(null)
}

val LocalTitleSlot = androidx.compose.runtime.staticCompositionLocalOf<TitleSlot?> { null }

/** The header's top line: the wordmark or Back at the start, the Simple / Advanced key in the middle. */
val HEADER_ROW: Dp = 56.dp

/** The tall header's own space under the top line, about a quarter of the screen (One UI). */
@Composable
fun headerArea(): Dp = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.27f).dp.coerceIn(170.dp, 260.dp)

/** The whole tall header: the top line, the space, the hairline. */
@Composable
fun headerTall(): Dp = HEADER_ROW + headerArea() + 1.dp

/** How far the content scrolls while the header folds into the slim bar, in pixels. */
@Composable
fun headerRangePx(): Int = with(androidx.compose.ui.platform.LocalDensity.current) { headerArea().roundToPx() }

/** The name as a mark, the lettering on the device's face: AUTOTUNE in spaced capitals. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, large: Boolean = false, fixedSize: Boolean = false) {
    // a mark, not a text to read: on the header it keeps its size at any font scale (it is read out as "AutoTune")
    val fs = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val base = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall
    Text(
        "AUTOTUNE", modifier.semantics { contentDescription = "AutoTune" },
        style = base.copy(
            fontWeight = FontWeight.Bold, letterSpacing = if (large) 2.4.sp else 2.sp,
            fontSize = if (fixedSize) (base.fontSize.value / fs).sp else base.fontSize,
        ),
        color = At.c.text, maxLines = 1,
    )
}

/**
 * The screen header (the one Samsung One UI feature kept). It belongs to the content: the screen keeps the tall
 * header's room at its top, and the app shell draws the header over that room ([ScreenHeader]), so scrolling up to the
 * very top always brings the full header back. Tall: the name in large type, centred on the screen's own Braun graphic
 * ([HeaderArt]). As the content scrolls, the graphic shrinks and glides into the slim bar's right corner and the name
 * settles at its left; the Simple / Advanced key stays in the middle of the top line throughout.
 *
 * Outside the app shell (no [LocalTitleSlot]) the tall header is drawn in place.
 */
@Composable
fun ScreenTitle(title: String, context: String? = null, contextIcon: Ic? = null) {
    val spec = HeaderSpec(title, context, contextIcon)
    val slot = LocalTitleSlot.current
    if (slot == null) {
        HeaderFace(spec, { 0f })
        return
    }
    androidx.compose.runtime.SideEffect { slot.spec = spec }
    // a screen that stops showing its title (Tune's steps) must not leave the header behind
    androidx.compose.runtime.DisposableEffect(slot) { onDispose { slot.spec = null } }
    androidx.compose.foundation.layout.Spacer(Modifier.fillMaxWidth().height(headerTall()))
}

/** The header the app shell draws over a screen's top, folded as far as [scroll] says. */
@Composable
fun ScreenHeader(slot: TitleSlot, scroll: androidx.compose.foundation.ScrollState, modifier: Modifier = Modifier) {
    val spec = slot.spec ?: return
    HeaderFace(spec, { scroll.value.toFloat() }, modifier)
}

/**
 * A scroll that always leaves room for the header to fold: the content is at least the viewport plus [roomPx] high,
 * so even a short screen can open compact, and its top still reveals the full header.
 */
@Composable
fun Modifier.scrollWithHeaderRoom(scroll: androidx.compose.foundation.ScrollState, roomPx: Int): Modifier {
    val viewport = remember { IntArray(1) }
    return this
        .layout { m, c ->
            viewport[0] = if (c.hasBoundedHeight) c.maxHeight else 0
            val p = m.measure(c)
            layout(p.width, p.height) { p.place(0, 0) }
        }
        .verticalScroll(scroll)
        .layout { m, c ->
            val min = if (roomPx > 0 && viewport[0] > 0) maxOf(c.minHeight, viewport[0] + roomPx) else c.minHeight
            val p = m.measure(c.copy(minHeight = if (c.hasBoundedHeight) min.coerceAtMost(c.maxHeight) else min))
            layout(p.width, p.height) { p.place(0, 0) }
        }
}

/** [v] at the font scale, held to at most [cap] times its size so the header keeps its shape. */
private fun cappedSp(v: androidx.compose.ui.unit.TextUnit, fontScale: Float, cap: Float = 1.3f): androidx.compose.ui.unit.TextUnit =
    (v.value * minOf(fontScale, cap) / fontScale).sp

/**
 * The header itself, folded by [scrollPx] (read in the layer and draw phases only, so the fold never recomposes):
 * the plate's lower edge follows the content up; the graphic shrinks and glides from beside the name into the slim
 * bar's right corner ([artAt]); the large name moves and scales into the bar's start and hands over to the bar's own
 * one-line name; the wordmark makes way before it arrives. With "Remove animations" the tall parts scroll away with
 * the content and the slim parts switch on once it has gone ([headerPhase]).
 */
@Composable
private fun HeaderFace(spec: HeaderSpec, scrollPx: () -> Float, modifier: Modifier = Modifier) {
    val back = LocalBack.current
    val anim = animationsOn()
    val c = At.c
    val density = androidx.compose.ui.platform.LocalDensity.current
    val area = headerArea()
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth().height(HEADER_ROW + area + 1.dp)) {
        val pad = 16.dp
        val inner = maxWidth - pad * 2
        val artW = inner * 0.46f
        val artH = minOf(area * 0.62f, artW * 0.64f)
        val centreY = HEADER_ROW + area * 0.52f
        val nameW = inner - artW - 14.dp
        val slimX = if (back != null) 40.dp else 0.dp
        val px = with(density) {
            HeaderPx(
                pad = pad.toPx(), row = HEADER_ROW.toPx(), range = area.toPx(), hair = 1.dp.toPx(),
                tall = (HEADER_ROW + area + 1.dp).toPx(), slim = (HEADER_ROW + 1.dp).toPx(),
                centreY = centreY.toPx(), slimX = slimX.toPx(), nameW = nameW.toPx(),
                art = HeaderRect((pad + inner - artW).toPx(), (centreY - artH / 2).toPx(), artW.toPx(), artH.toPx()),
            )
        }
        fun phase() = headerPhase(scrollPx(), px.range, anim)
        fun bottom() = headerBottomPx(scrollPx(), px.tall, px.slim)

        // the name: large, fitted so its longest word never breaks; and the bar's name it hands over to
        val slimStyle = MaterialTheme.typography.titleLarge.let { it.copy(fontSize = cappedSp(it.fontSize, density.fontScale), lineHeight = cappedSp(it.lineHeight, density.fontScale)) }
        val baseStyle = MaterialTheme.typography.displaySmall.copy(fontFamily = AtSans, fontWeight = FontWeight.Normal, letterSpacing = (-0.4).sp)
            .let { it.copy(fontSize = cappedSp(36.sp, density.fontScale), lineHeight = cappedSp(40.sp, density.fontScale)) }
        val bigStyle = remember(spec.title, baseStyle, px.nameW) {
            val longest = spec.title.split(' ').filter { it.isNotEmpty() }.maxOfOrNull { measurer.measure(it, baseStyle).size.width.toFloat() } ?: 0f
            val base = with(density) { baseStyle.fontSize.toPx() }
            val fit = fitTitlePx(base, longest, px.nameW, with(density) { slimStyle.fontSize.toPx() } * 1.25f)
            val k = fit / base
            baseStyle.copy(fontSize = baseStyle.fontSize * k, lineHeight = baseStyle.lineHeight * k)
        }
        val firstLinePx = with(density) { bigStyle.lineHeight.toPx() }
        val shrink = with(density) { slimStyle.fontSize.toPx() / bigStyle.fontSize.toPx() }
        val nameTop = remember { FloatArray(1) }

        // the plate and its hairline, the lower edge riding up with the content
        Box(Modifier.matchParentSize().drawBehind {
            val b = bottom()
            drawRect(c.bg, size = Size(size.width, b))
            drawRect(c.line, Offset(0f, b - px.hair), Size(size.width, px.hair))
        })

        // what moves: the graphic and the large name, clipped to the plate
        Box(Modifier.matchParentSize().drawWithContent {
            clipRect(top = if (anim) 0f else px.row, bottom = bottom() - px.hair) { this@drawWithContent.drawContent() }
        }) {
            HeaderArt(
                spec.artKey,
                // it plays when the header is tall enough to see it, not while it sits folded small in the corner
                shown = { phase() < 0.5f },
                modifier = Modifier.offset(x = pad + inner - artW, y = centreY - artH / 2).size(artW, artH).graphicsLayer {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    val p = phase()
                    if (anim) {
                        val r = artAt(p, px.art, px.slimArt)
                        translationX = r.x - px.art.x; translationY = r.y - px.art.y
                        scaleX = r.h / px.art.h; scaleY = scaleX
                    } else {
                        translationY = -scrollPx(); alpha = if (p >= 1f) 0f else 1f
                    }
                },
            )
            Column(
                Modifier.width(nameW)
                    .layout { m, cons ->
                        val pl = m.measure(cons)
                        layout(pl.width, pl.height) {
                            nameTop[0] = px.centreY - pl.height / 2f
                            pl.place(px.pad.toInt(), nameTop[0].toInt())
                        }
                    }
                    .graphicsLayer {
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        val p = phase()
                        if (anim) {
                            val k = 1f + (shrink - 1f) * p
                            scaleX = k; scaleY = k
                            translationX = px.slimX * p
                            translationY = (px.row / 2f - shrink * firstLinePx / 2f - nameTop[0]) * p
                            alpha = 1f - ramp(p, 0.86f, 1f)
                        } else {
                            translationY = -scrollPx(); alpha = if (p >= 1f) 0f else 1f
                        }
                    },
            ) {
                Text(
                    spec.title, style = bigStyle, color = c.text, maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
                )
                if (spec.context != null) Row(
                    Modifier.padding(top = 6.dp).heightIn(min = 20.dp).graphicsLayer { if (anim) alpha = 1f - ramp(phase(), 0f, 0.3f) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (spec.contextIcon != null) { AtIcon(spec.contextIcon, c.muted, size = 16.dp); androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp)) }
                    Text(spec.context, style = MaterialTheme.typography.bodyMedium, color = c.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }

        // without animations the small graphic simply appears in the corner once the header has gone
        if (!anim) HeaderArt(
            spec.artKey,
            with(density) { Modifier.offset(x = px.slimArt.x.toDp(), y = px.slimArt.y.toDp()).size(px.slimArt.w.toDp(), px.slimArt.h.toDp()) }
                .graphicsLayer { alpha = if (phase() >= 1f) 1f else 0f },
        )

        // the top line: the wordmark (Back on a page) and the bar's name at the start, the mode key in the middle
        CentreRow(Modifier.padding(horizontal = pad).fillMaxWidth().height(HEADER_ROW)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (back != null) Box(Modifier.offset(x = (-12).dp)) { IconAction(Ic.BACK, "Back", back) }
                Box(Modifier.then(if (back != null) Modifier.offset(x = (-8).dp) else Modifier), contentAlignment = Alignment.CenterStart) {
                    if (back == null) Wordmark(fixedSize = true, modifier = Modifier.fitWidth().graphicsLayer { alpha = 1f - (if (anim) ramp(phase(), 0.3f, 0.6f) else phase()) })
                    Text(
                        spec.title, style = slimStyle, color = c.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer { alpha = if (anim) ramp(phase(), 0.86f, 1f) else phase() }
                            .clearAndSetSemantics { },
                    )
                }
            }
            ModeSwitch()
        }
    }
}

/** Measures the content at its natural width and, if the room is narrower (a large font scale), draws it smaller to fit. */
private fun Modifier.fitWidth(): Modifier = this.layout { m, c ->
    val p = m.measure(c.copy(maxWidth = androidx.compose.ui.unit.Constraints.Infinity, minWidth = 0))
    val k = if (c.hasBoundedWidth && p.width > c.maxWidth) c.maxWidth.toFloat() / p.width else 1f
    layout((p.width * k).toInt(), p.height) {
        p.placeWithLayer(0, 0) { scaleX = k; scaleY = k; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }
    }
}

/** The header's measures in pixels, worked out once per size. */
private class HeaderPx(
    val pad: Float, val row: Float, val range: Float, val hair: Float, val tall: Float, val slim: Float,
    val centreY: Float, val slimX: Float, val nameW: Float, val art: HeaderRect,
) {
    /** The graphic's small version: 28dp high (the mode key's height), its right end on the row's end. */
    val slimArt: HeaderRect = slimArtRect(art, art.right, row / 2f, row * 28f / 56f)
}

/**
 * A row whose second child sits exactly in the middle whatever the first one holds; the first is held to the room
 * left of it, so the mode key never moves and nothing runs into it.
 */
@Composable
private fun CentreRow(modifier: Modifier, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content, modifier) { ms, cons ->
        val w = cons.maxWidth; val h = cons.maxHeight
        val gap = 8.dp.roundToPx()
        val centre = ms[1].measure(cons.copy(minWidth = 0, minHeight = 0))
        val side = ((w - centre.width) / 2 - gap).coerceAtLeast(0)
        val start = ms[0].measure(androidx.compose.ui.unit.Constraints(0, side, 0, h))
        layout(w, h) {
            start.place(0, (h - start.height) / 2)
            centre.place((w - centre.width) / 2, (h - centre.height) / 2)
        }
    }
}

/** The plain black of a record, the same by day and by night. */
private val Vinyl = Color(0xFF161615)

/**
 * The page's replay count: the app shell adds one when the page is pulled down past its top, and every graphic on it
 * plays its movement again ([rememberArrival], the Tune dial).
 */
val LocalReplay = androidx.compose.runtime.compositionLocalOf { 0 }

/**
 * A graphic's movement: [value] runs 0 to 1, [dev.autotune.GRAPHIC_PLAYS] times, once the graphic is shown
 * ([arrival] on it reports that), then it stays at 1. A pull past the page's top ([LocalReplay]) plays it again.
 */
class Arrival internal constructor(internal val anim: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>) {
    /** Whether the graphic is on screen now (set by [arrival]); the movement waits for it. */
    internal var seen by mutableStateOf(false)
    val value: Float get() = anim.value
}

/**
 * The movement of a graphic, played [dev.autotune.GRAPHIC_PLAYS] times when it is first shown ([key] restarts it, a
 * pull at the page's top replays it): forward over [ms] on [EaseArrive], a quick return on [EaseLeave] between plays.
 * With "Remove animations" it is 1 from the start. Read [Arrival.value] in a draw lambda, so only the drawing repeats.
 */
@Composable
fun rememberArrival(key: Any?, ms: Int = 900, shown: (() -> Boolean)? = null): Arrival {
    val dur = motionMs(ms)
    val replay = LocalReplay.current
    val a = remember(key) { Arrival(androidx.compose.animation.core.Animatable(if (dur == 0) 1f else 0f)) }
    val isShown = androidx.compose.runtime.rememberUpdatedState(shown)
    androidx.compose.runtime.LaunchedEffect(key, replay, dur) {
        if (dur == 0) { a.anim.snapTo(1f); return@LaunchedEffect }
        // a graphic below the visible part of the page (or folded small in the header) waits until it can be seen
        androidx.compose.runtime.snapshotFlow { isShown.value?.invoke() ?: a.seen }.first { it }
        playLegs(a.anim, dur)
    }
    return a
}

/** Runs [anim] through [dev.autotune.arrivalLegs]: forward on [EaseArrive], the returns between plays on [EaseLeave]. */
internal suspend fun playLegs(anim: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, ms: Int) {
    for (to in dev.autotune.arrivalLegs(anim.value)) {
        if (to > anim.value) anim.animateTo(to, tween(ms, easing = EaseArrive))
        else anim.animateTo(to, tween(dev.autotune.returnMs(ms), easing = EaseLeave))
    }
}

/** Reports whether this graphic is on screen to its [Arrival], so the movement plays when it can be seen. */
fun Modifier.arrival(a: Arrival): Modifier = this.onGloballyPositioned { coords ->
    val visible = coords.boundsInWindow().height
    a.seen = dev.autotune.shownOnScreen(visible, coords.size.height.toFloat())
}

/**
 * The header's graphic: the face of a Braun hi-fi component for each screen, flat, in the deck greys, with the accent
 * only on one needle, pilot light or key. Tests is a record player (the SK 4's platter and tonearm), EQ a fader strip,
 * Tune the round speaker grille (the L 2 / T 3), Live a radio's tuning scale with its needle over a slot grille of level
 * bars, Settings a row of push keys and knobs (the audio 1), and anything else the reels of a tape deck (the TG 60).
 * Each plays one short movement when it appears (the reels turn, the tonearm swings in, the faders rise, the needle
 * glides, the knobs turn, the pilot lights last) and then stays still; nothing loops.
 */
@Composable
fun HeaderArt(key: String, modifier: Modifier, shown: (() -> Boolean)? = null) {
    // drawn as a light device on the dark body, in the deck's colours
    val c = deckColors
    val plate = c.panel
    val keyFace = c.panelHigh
    val rule = c.line
    val ink = c.text
    val alu = c.alu
    val accent = c.signal
    val slotInk = androidx.compose.ui.graphics.Color.Black.copy(alpha = if (c.dark) 0.6f else 0.45f)
    val arrival = rememberArrival(key, shown = shown)
    Canvas((if (shown == null) modifier.arrival(arrival) else modifier).semantics { }) {
        val t = arrival.value
        val w = size.width; val h = size.height
        val r6 = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
        val r2 = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
        val hair = 1.dp.toPx()
        fun deck() = drawRoundRect(plate, Offset.Zero, size, r6)
        // the pilot lights last, once the movement has settled
        fun pilot(x: Float, y: Float) = drawCircle(accent.copy(alpha = accent.alpha * ramp(t, 0.6f, 1f)), h * 0.035f, Offset(x, y))
        fun turn(p: Offset, about: Offset, deg: Float): Offset {
            val a = Math.toRadians(deg.toDouble()); val dx = p.x - about.x; val dy = p.y - about.y
            return Offset(about.x + (dx * kotlin.math.cos(a) - dy * kotlin.math.sin(a)).toFloat(), about.y + (dx * kotlin.math.sin(a) + dy * kotlin.math.cos(a)).toFloat())
        }
        when (key) {
            "tests" -> {
                // a record player: the platter's record, grooves, the label; the tonearm swings in from its rest
                deck()
                val ctr = Offset(w * 0.38f, h * 0.5f); val r = h * 0.41f
                drawCircle(alu.copy(alpha = 0.55f), r * 1.06f, ctr)
                drawCircle(Vinyl, r, ctr)
                for (k in 1..4) drawCircle(Color.White.copy(alpha = 0.07f), r * (0.45f + k * 0.12f), ctr, style = Stroke(hair))
                drawCircle(keyFace, r * 0.3f, ctr)
                drawCircle(rule, r * 0.3f, ctr, style = Stroke(hair))
                drawCircle(ink, r * 0.05f, ctr)
                // the label's mark turns once with the platter
                drawCircle(ink.copy(alpha = 0.5f), r * 0.035f, turn(Offset(ctr.x, ctr.y - r * 0.2f), ctr, 360f * t))
                val pivot = Offset(w * 0.86f, h * 0.2f)
                drawCircle(alu, h * 0.08f, pivot); drawCircle(ink, h * 0.03f, pivot)
                val swing = -24f * (1f - t)
                val elbow = turn(Offset(w * 0.86f, h * 0.6f), pivot, swing); val head = turn(Offset(w * 0.64f, h * 0.73f), pivot, swing)
                drawLine(alu, pivot, elbow, h * 0.025f, StrokeCap.Round); drawLine(alu, elbow, head, h * 0.025f, StrokeCap.Round)
                drawCircle(ink, h * 0.03f, head)
                pilot(w * 0.92f, h * 0.88f)
            }
            "eq" -> {
                // a fader strip: slots with a scale, square caps rising one after another to a gentle curve
                deck()
                val n = 6; val top = h * 0.14f; val bottom = h * 0.86f
                val caps = listOf(0.62f, 0.42f, 0.5f, 0.66f, 0.36f, 0.48f)
                for (k in 0 until n) {
                    val x = w * (0.13f + k * 0.148f)
                    for (q in 0..4) { val y = top + (bottom - top) * q / 4f; drawLine(rule, Offset(x - w * 0.045f, y), Offset(x - w * 0.025f, y), hair) }
                    drawRoundRect(slotInk, Offset(x - w * 0.008f, top), Size(w * 0.016f, bottom - top), androidx.compose.ui.geometry.CornerRadius(w * 0.008f))
                    val rise = ramp(t, k * 0.07f, k * 0.07f + 0.6f)
                    val y = bottom - (bottom - h * caps[k]) * rise
                    drawRoundRect(if (k == 2) accent else ink, Offset(x - w * 0.04f, y - h * 0.055f), Size(w * 0.08f, h * 0.11f), r2)
                    drawLine(if (k == 2) c.onSignal else c.bg, Offset(x - w * 0.028f, y), Offset(x + w * 0.028f, y), hair * 1.5f)
                }
            }
            "tune" -> {
                // the round speaker: an aluminium rim, the perforated grille filling in from the centre, the Tune key's pilot
                val ctr = Offset(w * 0.6f, h * 0.5f); val r = h * 0.48f
                drawCircle(alu.copy(alpha = 0.55f), r, ctr)
                drawCircle(plate, r * 0.93f, ctr)
                val step = r * 0.13f; val hole = r * 0.028f; val outer = r * 0.84f
                var y = ctr.y - outer
                while (y <= ctr.y + outer) {
                    var x = ctr.x - outer
                    while (x <= ctr.x + outer) {
                        val d = kotlin.math.hypot(x - ctr.x, y - ctr.y)
                        if (d <= outer) drawCircle(ink.copy(alpha = 0.42f * ramp(t, d / outer * 0.6f, d / outer * 0.6f + 0.4f)), hole, Offset(x, y))
                        x += step
                    }
                    y += step
                }
                drawRoundRect(keyFace, Offset(w * 0.02f, h * 0.7f), Size(w * 0.16f, h * 0.2f), r2)
                drawRoundRect(rule, Offset(w * 0.02f, h * 0.7f), Size(w * 0.16f, h * 0.2f), r2, style = Stroke(hair))
                pilot(w * 0.1f, h * 0.8f)
            }
            "live" -> {
                // a radio: the tuning scale with its red needle gliding to the station, the level bars rising under it
                deck()
                val win = Offset(w * 0.06f, h * 0.12f); val ws = Size(w * 0.88f, h * 0.34f)
                drawRoundRect(c.bg, win, ws, r2)
                val n = 40
                for (k in 0..n) {
                    val x = win.x + ws.width * (0.04f + 0.92f * k / n)
                    val long = k % 5 == 0
                    drawLine(c.muted.copy(alpha = if (long) 0.9f else 0.5f), Offset(x, win.y + ws.height * 0.2f), Offset(x, win.y + ws.height * if (long) 0.62f else 0.45f), hair)
                }
                val nx = win.x + ws.width * (0.1f + 0.53f * t)
                drawLine(accent, Offset(nx, win.y - h * 0.04f), Offset(nx, win.y + ws.height + h * 0.04f), 2.5.dp.toPx())
                val bars = listOf(0.35f, 0.55f, 0.8f, 0.95f, 0.7f, 0.6f, 0.75f, 0.5f, 0.42f, 0.58f, 0.4f, 0.3f, 0.36f, 0.22f)
                val gx = w * 0.06f; val gw = w * 0.88f; val gTop = h * 0.56f; val gBottom = h * 0.88f
                val bw = gw / bars.size
                bars.forEachIndexed { k, v0 ->
                    val v = v0 * ramp(t, k * 0.03f, k * 0.03f + 0.55f)
                    val x = gx + bw * k + bw * 0.28f
                    drawRoundRect(slotInk.copy(alpha = 0.18f), Offset(x, gTop), Size(bw * 0.44f, gBottom - gTop), r2)
                    drawRoundRect(ink.copy(alpha = 0.8f), Offset(x, gBottom - (gBottom - gTop) * v), Size(bw * 0.44f, (gBottom - gTop) * v), r2)
                }
            }
            "settings" -> {
                // the audio 1's controls: a row of push keys, one latching down with its pilot, and two knobs turning to place
                deck()
                val kw = w * 0.13f; val kh = h * 0.2f; val ky = h * 0.18f
                val drop = h * 0.02f * ramp(t, 0.2f, 0.6f)
                for (k in 0 until 5) {
                    val x = w * (0.07f + k * 0.175f)
                    val latched = k == 1
                    drawRoundRect(if (latched) androidx.compose.ui.graphics.lerp(keyFace, rule, 0.55f * ramp(t, 0.2f, 0.6f)) else keyFace, Offset(x, ky + if (latched) drop else 0f), Size(kw, kh), r2)
                    drawRoundRect(rule, Offset(x, ky + if (latched) drop else 0f), Size(kw, kh), r2, style = Stroke(hair))
                    if (latched) pilot(x + kw / 2, ky - h * 0.07f)
                }
                fun knob(cx: Float, r: Float, deg: Float) {
                    val ctr = Offset(cx, h * 0.68f)
                    for (q in 0..6) {
                        val a = Math.toRadians(135.0 + q * 45.0)
                        val o = Offset(ctr.x + (r * 1.25f) * kotlin.math.cos(a).toFloat(), ctr.y + (r * 1.25f) * kotlin.math.sin(a).toFloat())
                        drawCircle(c.muted.copy(alpha = 0.7f), hair * 1.2f, o)
                    }
                    drawCircle(alu, r, ctr)
                    drawCircle(Color.Black.copy(alpha = 0.12f), r, ctr, style = Stroke(hair))
                    val a = Math.toRadians(deg.toDouble())
                    drawLine(ink, Offset(ctr.x + r * 0.25f * kotlin.math.cos(a).toFloat(), ctr.y + r * 0.25f * kotlin.math.sin(a).toFloat()), Offset(ctr.x + r * 0.85f * kotlin.math.cos(a).toFloat(), ctr.y + r * 0.85f * kotlin.math.sin(a).toFloat()), hair * 2f)
                }
                // both knobs start at the scale's low end (135°, lower left) and turn up to their settings
                knob(w * 0.2f, h * 0.14f, 135f + (240f - 135f) * t)
                knob(w * 0.5f, h * 0.14f, 135f + (320f - 135f) * t)
                // a short horizontal fader at the end, sliding to its place
                drawRoundRect(slotInk, Offset(w * 0.68f, h * 0.675f), Size(w * 0.25f, h * 0.025f), r2)
                drawRoundRect(ink, Offset(w * (0.69f + 0.07f * t), h * 0.6f), Size(w * 0.05f, h * 0.17f), r2)
            }
            else -> {
                // a tape deck: two reels turning a little way, the tape between them over the head
                deck()
                // in the folded header the deck is small: just the two reels, larger, without the tape, head and pilot,
                // which turned into stray marks at that size (owner, 2026-11-11)
                val small = h < 56.dp.toPx()
                fun reel(cx: Float, spin: Float) {
                    val ctr = Offset(cx, h * if (small) 0.5f else 0.44f); val r = h * if (small) 0.4f else 0.3f
                    drawCircle(alu.copy(alpha = 0.7f), r, ctr)
                    drawCircle(Vinyl.copy(alpha = 0.85f), r * 0.62f, ctr)
                    drawCircle(keyFace, r * 0.24f, ctr)
                    for (q in 0 until 3) {
                        val a = Math.toRadians(-90.0 + q * 120.0 + spin)
                        drawCircle(plate, r * 0.13f, Offset(ctr.x + r * 0.43f * kotlin.math.cos(a).toFloat(), ctr.y + r * 0.43f * kotlin.math.sin(a).toFloat()))
                    }
                }
                // both reels wind the same way, a turn and a third, then stop with the hubs where they always rest
                val spin = -480f * (1f - t)
                reel(w * 0.27f, spin); reel(w * 0.73f, spin)
                if (!small) {
                    drawLine(ink.copy(alpha = 0.7f), Offset(w * 0.27f, h * 0.74f), Offset(w * 0.73f, h * 0.74f), hair * 1.5f)
                    drawRoundRect(keyFace, Offset(w * 0.44f, h * 0.7f), Size(w * 0.12f, h * 0.14f), r2)
                    pilot(w * 0.9f, h * 0.88f)
                }
            }
        }
    }
}

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
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    androidx.compose.material3.Button(
        onClick = onClick, enabled = enabled, interactionSource = src,
        modifier = modifier.then(if (LocalStacked.current) Modifier.fillMaxWidth() else Modifier).heightIn(min = 52.dp).pressScale(src),
        shape = MaterialTheme.shapes.medium,
        // a graphite key; red is kept for the pilot light, the needle and the EQ
        colors = ButtonDefaults.buttonColors(
            containerColor = At.c.text, contentColor = At.c.bg,
            disabledContainerColor = At.c.panelHigh, disabledContentColor = At.c.muted,
        ),
    ) { ButtonContent(text, icon, if (enabled) At.c.bg else At.c.muted) }
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
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick, enabled = enabled, interactionSource = src,
        modifier = modifier.then(if (LocalStacked.current) Modifier.fillMaxWidth() else Modifier).heightIn(min = 48.dp).pressScale(src),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, At.c.line),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = At.c.panelHigh, contentColor = At.c.text, disabledContentColor = At.c.muted),
    ) { ButtonContent(text, icon, if (enabled) At.c.text else At.c.muted) }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, enabled: Boolean = true, danger: Boolean = false, icon: Ic? = null) {
    // lettered in ink like the keys of a Braun panel: red stays for what is live or acted on, never a plain action
    val tint = if (!enabled) At.c.muted else if (danger) At.c.bad else At.c.text
    if (LocalStacked.current) {
        // in a stack: one full-width row per action, the label at the start and a chevron at the end
        val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).pressScale(src).clip(MaterialTheme.shapes.medium).background(At.c.panelHigh)
                .clickable(interactionSource = src, indication = androidx.compose.material3.ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) { AtIcon(icon, tint, size = 20.dp); androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp)) }
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = if (!enabled) At.c.muted else if (danger) At.c.bad else At.c.text, maxLines = 2)
            AtIcon(Ic.CHEVRON, At.c.muted, size = 18.dp)
        }
        return
    }
    TextButton(
        onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = if (danger) At.c.bad else At.c.text, disabledContentColor = At.c.muted),
    ) { ButtonContent(text, icon, tint) }
}

/**
 * A choice between a few options, always as a drop-down (never pills): the current value in a field, the options in a
 * menu. [label] is drawn above the field when given; [details] adds a muted line under each option in the menu.
 */
@Composable
fun DropSelect(
    options: List<String>, selected: Int, onSelect: (Int) -> Unit, enabled: Boolean = true,
    label: String? = null, details: List<String?>? = null, icon: Ic? = null, icons: List<Ic>? = null, info: String? = null,
    swatches: List<Color?>? = null,
) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        if (label != null) InfoLabel(label, info)
        Box {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(MaterialTheme.shapes.small).background(At.c.panelHigh)
                    .clickable(enabled = enabled, role = Role.DropdownList, onClickLabel = "Choose ${label ?: "option"}") { open = true }
                    .padding(start = 14.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val shown = icons?.getOrNull(selected) ?: icon
                swatches?.getOrNull(selected)?.let { Swatch(it); androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp)) }
                // the field's icon is lettering, in ink; the menu marks the chosen option in the accent
                if (shown != null) { AtIcon(shown, if (enabled) At.c.text else At.c.muted, size = 20.dp); androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp)) }
                Text(
                    options.getOrNull(selected) ?: "Choose", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) At.c.text else At.c.muted, maxLines = 2,
                )
                AtIcon(Ic.CHEVRON_DOWN, At.c.muted, size = 20.dp)
            }
            androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.widthIn(min = 240.dp, max = 360.dp)) {
                // long lists (the palette's colours) scroll, so the last choices are never cut off below the field
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                options.forEachIndexed { i, o ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text(o, style = MaterialTheme.typography.bodyLarge, color = if (i == selected) At.c.signal else At.c.text)
                                details?.getOrNull(i)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = At.c.muted) }
                            }
                        },
                        leadingIcon = icons?.getOrNull(i)?.let { ic -> { AtIcon(ic, if (i == selected) At.c.signal else At.c.muted, size = 20.dp) } }
                            ?: swatches?.getOrNull(i)?.let { col -> { Swatch(col) } },
                        trailingIcon = { if (i == selected) AtIcon(Ic.CHECK, At.c.signal, size = 18.dp) },
                        onClick = { open = false; onSelect(i) },
                    )
                }
                }
            }
        }
    }
}

/** A small square of a colour with a hairline edge, for the palette's drop-downs. */
@Composable
fun Swatch(c: Color, size: Dp = 20.dp) {
    Box(Modifier.size(size).clip(MaterialTheme.shapes.extraSmall).background(c).border(1.dp, At.c.line, MaterialTheme.shapes.extraSmall))
}

/**
 * The big Tune button: a Braun speaker front with the round Tune key at its centre. When it is shown, a pulse runs
 * from the centre out to the rim, twice, then the dial rests; a pull at the top of the page plays it again.
 */
@Composable
fun PulsingButton(label: String, sub: String?, icon: Ic, enabled: Boolean, onClick: () -> Unit) {
    // a Braun speaker front (the T 3's perforated circle): a grille of holes in a round field, an aluminium rim, and
    // Tune as the round push button at its centre
    val c = At.c
    // the face is always light and the holes dark, in every look, by day and by night (dialColours)
    val (face, dot) = dialColours(c)
    // the pulse: a wave from the centre out to the rim, inside out; the holes it passes darken and swell, a faint ring
    // marks its front and the rim answers as it arrives. Two pulses, then still ("Remove animations": always still).
    val dur = motionMs(dev.autotune.PULSE_MS)
    val replay = LocalReplay.current
    val pulse = remember { androidx.compose.animation.core.Animatable(0f) }
    var seen by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(replay, dur, enabled) {
        if (dur == 0 || !enabled) { pulse.snapTo(0f); return@LaunchedEffect }
        androidx.compose.runtime.snapshotFlow { seen }.first { it }
        repeat(dev.autotune.GRAPHIC_PLAYS) { k ->
            if (k > 0) delay(dur / 8L)
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(dur, easing = EaseArrive))
            // the wave has left the rim: the face is at rest again
            pulse.snapTo(0f)
        }
    }
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(Modifier.size(270.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.size(270.dp).onGloballyPositioned { co ->
                seen = dev.autotune.shownOnScreen(co.boundsInWindow().height, co.size.height.toFloat())
            },
        ) {
            val r = size.minDimension / 2
            val ctr = center
            drawCircle(c.alu, r)
            drawCircle(face, r * 0.95f)
            // the front of the pulse, from the centre out (read here, so only the drawing repeats)
            val p = pulse.value
            val front = if (p > 0f) dev.autotune.pulseFront(p, r) else 0f
            val band = r * 0.16f
            // the holes: a square grid clipped to the circle, clear of the button
            val step = 11.dp.toPx(); val hole = 1.9.dp.toPx()
            val inner = r * 0.42f; val outer = r * 0.88f
            var y = ctr.y - outer
            while (y <= ctr.y + outer) {
                var x = ctr.x - outer
                while (x <= ctr.x + outer) {
                    val d = kotlin.math.hypot(x - ctr.x, y - ctr.y)
                    if (d in inner..outer) {
                        val lit = dev.autotune.pulseLight(d, front, band)
                        drawCircle(dot.copy(alpha = 0.34f + 0.6f * lit), hole * (1f + 0.45f * lit), Offset(x, y))
                    }
                    x += step
                }
                y += step
            }
            if (front > 0f) {
                // a faint ring at the front, fading as it spreads; the rim darkens as the wave reaches it
                drawCircle(dot.copy(alpha = 0.22f * (1f - p)), front.coerceAtMost(r * 0.95f), ctr, style = Stroke(1.5.dp.toPx()))
                val atRim = dev.autotune.pulseLight(r * 0.975f, front, band)
                val fade = 1f - ramp(p, 0.92f, 1f)
                if (atRim > 0f) drawCircle(dot.copy(alpha = 0.55f * atRim * fade), r * 0.975f, ctr, style = Stroke(3.dp.toPx()))
            }
        }
        Box(
            Modifier.size(112.dp).pressScale(src).clip(CircleShape).background(if (enabled) c.signal else c.panelHigh)
                .clickable(interactionSource = src, indication = androidx.compose.material3.ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold), color = if (enabled) c.onSignal else c.muted)
                if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = if (enabled) c.onSignal.copy(alpha = 0.8f) else c.muted)
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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) Box(Modifier.offset(x = (-12).dp)) { IconAction(Ic.BACK, "Back", onBack) }
            // one line, so a long title never wraps up under the status bar
            Text(
                title, Modifier.weight(1f).then(if (onBack != null) Modifier.offset(x = (-8).dp) else Modifier),
                style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (onExit != null) TextAction("Exit", onExit, icon = Ic.CLOSE)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Step ${index + 1} of $total" + (next?.let { " · Next: $it" } ?: ""),
                Modifier.weight(1f).padding(start = if (onBack != null) 28.dp else 0.dp),
                style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            ModeSwitch()
        }
        Row(
            Modifier.fillMaxWidth().semantics { contentDescription = "Step ${index + 1} of $total" },
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (i in 0 until total) {
                val col by androidx.compose.animation.animateColorAsState(
                    if (i < index) c.muted else if (i == index) c.signal else c.line,
                    tween(motionMs(380), easing = EaseArrive), label = "step",
                )
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(col))
            }
        }
    }
}

/**
 * An on/off switch drawn as a Braun slide switch (2.18.5, the owner found the latching key unclear): the knob runs right
 * and the track fills with the accent while on, "on"/"off" lettered beside it. The 48dp touch target, the switch role
 * and [label] are kept for accessibility.
 */
@Composable
fun LatchKey(on: Boolean, onChange: (Boolean) -> Unit, label: String, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val c = At.c
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier.size(64.dp, 48.dp)
            .toggleable(value = on, enabled = enabled, role = Role.Switch, interactionSource = src, indication = null, onValueChange = onChange)
            .semantics { contentDescription = label }
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f },
        contentAlignment = Alignment.Center,
    ) {
        // a slide switch, as on a Braun radio: the knob runs to the right and the track fills with the accent while on,
        // "on" or "off" lettered beside the knob so the state reads without the colour
        val knob by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0f, tween(motionMs(160), easing = EaseArrive), label = "knob")
        val track by androidx.compose.animation.animateColorAsState(
            if (on && enabled) c.signal else c.panelHigh, tween(motionMs(160), easing = EaseArrive), label = "track",
        )
        Box(
            Modifier.size(58.dp, 32.dp).pressScale(src).clip(RoundedCornerShape(16.dp)).background(track)
                .border(1.dp, if (on && enabled) c.signal else c.line, RoundedCornerShape(16.dp)),
        ) {
            Text(
                if (on) "on" else "off",
                Modifier.align(if (on) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 9.dp),
                style = MaterialTheme.typography.labelSmall, color = if (on && enabled) c.onSignal else c.muted, maxLines = 1,
            )
            Box(
                Modifier.align(Alignment.CenterStart).offset(x = 3.dp + 26.dp * knob).size(26.dp).clip(CircleShape)
                    .background(if (on && enabled) c.onSignal else c.text.copy(alpha = 0.85f)),
            )
        }
    }
}

/** The AutoTune EQ's on/off key: the [LatchKey]. */
@Composable
fun PowerToggle(on: Boolean, onChange: (Boolean) -> Unit, label: String, enabled: Boolean = true) = LatchKey(on, onChange, label, enabled)

/** A setting that is on or off: its name, and the latching key at the end of the row. */
@Composable
fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true, info: String? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = if (enabled) At.c.text else At.c.muted)
        if (info != null) InfoButton(text, info, small = true)
        LatchKey(checked, onChange, text, enabled)
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
        if (status != null) Text(status, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        Alerts(warnings)
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
 * Picking which EQ is in use: one field that opens a sheet. In the sheet the selected EQ's test is open at the top,
 * one line per choice with its reading at the end; every other test (and the saved EQs) is one line that opens when
 * tapped. Test names made by the app read as their date over the short target.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GroupedPick(label: String, choices: List<dev.autotune.EqChoice>, selectedKey: String, onPick: (String) -> Unit, emptyText: String, info: String? = null) {
    var open by remember { mutableStateOf(false) }
    val sel = choices.firstOrNull { it.key == selectedKey }
    Column(Modifier.fillMaxWidth()) {
        InfoLabel(label, info)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.small).background(At.c.panelHigh)
                .clickable(enabled = choices.isNotEmpty(), role = Role.DropdownList, onClickLabel = "Choose $label") { open = true }
                .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AtIcon(sel?.let { kindIcon(it.kind) } ?: Ic.EQ, At.c.text, size = 22.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                // the system's name, then the run's note (or its score and date): never "Run 4" or the target
                Text(
                    sel?.let { c -> c.name ?: (c.title + (c.db?.let { " · %.1f dB from target".format(it) } ?: "")) } ?: emptyText,
                    style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                sel?.let { c -> (if (c.name != null) c.sub else listOfNotNull(c.groupSub, c.groupTitle).joinToString(" · "))?.let { line -> Text(line, style = MaterialTheme.typography.bodySmall, color = At.c.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) } }
            }
            AtIcon(Ic.CHEVRON_DOWN, At.c.muted, size = 20.dp)
        }
    }
    if (open) {
        val sheet = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { open = false }, sheetState = sheet,
            containerColor = At.c.panel, contentColor = At.c.text,
            // the dialog radius of the shape scale (12dp), not Material's soft 28dp sheet
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        ) {
            EqSheet(choices, selectedKey) { key ->
                onPick(key)
                scope.launch { sheet.hide() }.invokeOnCompletion { open = false }
            }
        }
    }
}

@Composable
private fun EqSheet(choices: List<dev.autotune.EqChoice>, selectedKey: String, onPick: (String) -> Unit) {
    val groups = choices.groupBy { it.group }
    val selGroup = choices.firstOrNull { it.key == selectedKey }?.group ?: groups.keys.firstOrNull()
    var openGroups by remember { mutableStateOf(setOfNotNull(selGroup)) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("Choose the EQ", Modifier.padding(start = 4.dp, bottom = 8.dp), style = MaterialTheme.typography.headlineSmall)
        groups.entries.forEachIndexed { gi, (g, list) ->
            if (gi > 0) HorizontalDivider(color = At.c.line, modifier = Modifier.padding(vertical = 4.dp))
            val isOpen = g in openGroups
            val first = list.first()
            val bestDb = list.mapNotNull { it.db }.minOrNull()
            val turn by androidx.compose.animation.core.animateFloatAsState(if (isOpen) 90f else 0f, tween(motionMs(220), easing = EaseArrive), label = "group")
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(MaterialTheme.shapes.large)
                    .clickable(role = Role.Button, onClickLabel = if (isOpen) "Close ${first.groupTitle}" else "Open ${first.groupTitle}") {
                        openGroups = if (isOpen) openGroups - g else openGroups + g
                    }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AtIcon(if (g == "Saved EQs") Ic.SAVE else kindIcon(first.kind), At.c.muted, size = 20.dp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(first.groupTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    val sub = listOfNotNull(first.groupSub, if (g == selGroup && gi == 0 && choices.any { it.key == selectedKey }) "in use" else null).joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = At.c.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                if (!isOpen) Text(bestDb?.let { "best %.1f".format(it) } ?: "${list.size}", style = MaterialTheme.typography.labelMedium, color = At.c.muted)
                AtIcon(Ic.CHEVRON, At.c.muted, Modifier.padding(start = 6.dp).graphicsLayer { rotationZ = turn }, size = 18.dp)
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = isOpen,
                enter = androidx.compose.animation.fadeIn(tween(motionMs(200), easing = EaseArrive)) + androidx.compose.animation.expandVertically(tween(motionMs(300), easing = EaseArrive)),
                exit = androidx.compose.animation.fadeOut(tween(motionMs(90), easing = EaseLeave)) + androidx.compose.animation.shrinkVertically(tween(motionMs(180), easing = EaseLeave)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // the useful few first (suggested, best, kept), then the rest
                    (list.filter { it.primary } + list.filter { !it.primary }).forEach { c -> ChoiceRow(c, c.key == selectedKey) { onPick(c.key) } }
                }
            }
        }
    }
}

/** One choice on one line: a check when it is in use, its name, "best" on the best run, the reading at the end. */
@Composable
private fun ChoiceRow(c: dev.autotune.EqChoice, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(MaterialTheme.shapes.large)
            .background(if (selected) androidx.compose.ui.graphics.lerp(At.c.panel, At.c.signal, 0.12f) else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = listOfNotNull(c.title, c.db?.let { "%.1f dB from the target".format(it) }, c.note, if (c.best) "best" else null).joinToString(", ") }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { if (selected) AtIcon(Ic.CHECK, At.c.signal, size = 18.dp) }
        Row(Modifier.weight(1f).padding(start = 10.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                c.title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge,
                color = if (selected) At.c.signal else At.c.text, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            // lettered on the panel, small and lowercase, not a badge
            if (c.best) Text("best", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelSmall, color = At.c.good, maxLines = 1)
        }
        if (c.db != null) {
            Text("%.1f".format(c.db), style = MaterialTheme.typography.headlineSmall, color = if (c.best) At.c.good else At.c.text)
            Text(" dB", style = MaterialTheme.typography.labelMedium, color = At.c.muted)
        } else if (c.note != null) {
            Text(c.note, style = MaterialTheme.typography.bodySmall, color = At.c.muted, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- the pinned action

/** The screen's one recommended action, shown big above the tabs so it never scrolls away. */
class PinnedAction(val text: String, val icon: Ic?, val enabled: Boolean, val hint: String?, val onClick: () -> Unit, val sub: String? = null)

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
fun PinAction(text: String, onClick: () -> Unit, icon: Ic? = null, enabled: Boolean = true, hint: String? = null, sub: String? = null) {
    val slot = LocalPin.current
    val me = remember { Any() }
    androidx.compose.runtime.SideEffect {
        slot.owner = me
        slot.action = PinnedAction(text, icon, enabled, hint, onClick, sub)
    }
    androidx.compose.runtime.DisposableEffect(slot) {
        onDispose { if (slot.owner === me) { slot.owner = null; slot.action = null } }
    }
}

/** The big button itself: tall, full width, the accent colour. */
@Composable
fun BigActionBar(a: PinnedAction) {
    val rule = At.c.line
    Column(
        // ruled off from the page, so content scrolling under it reads as passing under, not cut off
        Modifier.fillMaxWidth().background(At.c.bg).drawBehind { drawLine(rule, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 1.dp.toPx()) }
            .padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        a.hint?.takeIf { isAdvanced() || !a.enabled }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = At.c.muted, maxLines = 2) }
        MainKey(
            a.text, a.onClick, sub = a.sub ?: "suggested", enabled = a.enabled,
            description = "Suggested: ${a.text}" + (a.sub?.let { ", $it" } ?: ""),
        )
    }
}

/**
 * The main key: graphite, full width, the action centred in clear type over a small second line ([sub]: "suggested",
 * or what comes after the key), and a pilot light in the accent at the start, lit while it is ready. A new label
 * slides in, so the change is noticed. The pinned action ([BigActionBar]) and the tutorial's Next / Get started use it.
 */
@Composable
fun MainKey(text: String, onClick: () -> Unit, sub: String? = null, enabled: Boolean = true, description: String = text) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val keyColor = if (enabled) At.c.text else At.c.panelHigh
    val onKey = if (enabled) At.c.bg else At.c.muted
    Box(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).pressScale(src).clip(MaterialTheme.shapes.medium).background(keyColor)
            .clickable(interactionSource = src, indication = androidx.compose.material3.ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.align(Alignment.CenterStart).size(12.dp).clip(CircleShape).background(if (enabled) At.c.signal else At.c.line))
        // a new label slides in, so the change is noticed
        val inMs = motionMs(300)
        val outMs = motionMs(90)
        androidx.compose.animation.AnimatedContent(
            targetState = text to sub, modifier = Modifier.padding(horizontal = 28.dp),
            transitionSpec = {
                (androidx.compose.animation.fadeIn(tween(inMs, easing = EaseArrive)) + androidx.compose.animation.slideInVertically(tween(inMs, easing = EaseArrive)) { it / 3 }) togetherWith
                    androidx.compose.animation.fadeOut(tween(outMs, easing = EaseLeave))
            },
            contentAlignment = Alignment.Center,
            label = "pin",
        ) { (shown, second) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    shown, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = onKey, maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                if (second != null) Text(
                    second, style = MaterialTheme.typography.labelSmall, color = onKey.copy(alpha = 0.6f), maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * "There is more below" (owner, 2026-11-12: the measuring screen's checks ran on under the edge with nothing to say
 * so): a fade into the body at the bottom of a scrolling screen and a small round key with a down chevron, while
 * [state] can still scroll on by more than a few lines. The key scrolls most of a screen down. Place it in a Box over
 * the scrolling content, aligned to the bottom.
 */
@Composable
fun MoreBelow(state: androidx.compose.foundation.ScrollState, modifier: Modifier = Modifier) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val show = state.maxValue - state.value > with(density) { 48.dp.toPx() }
    androidx.compose.animation.AnimatedVisibility(
        show, modifier.fillMaxWidth(),
        enter = androidx.compose.animation.fadeIn(tween(motionMs(200), easing = EaseArrive)),
        exit = androidx.compose.animation.fadeOut(tween(motionMs(160), easing = EaseLeave)),
    ) {
        val bg = At.c.bg
        Box(
            Modifier.fillMaxWidth().height(56.dp)
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(bg.copy(alpha = 0f), bg.copy(alpha = 0.92f)))),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier.padding(bottom = 6.dp).size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).background(At.c.panelHigh)
                    .border(1.dp, At.c.line, androidx.compose.foundation.shape.CircleShape)
                    .clickable(onClickLabel = "Scroll down") {
                        scope.launch { state.animateScrollTo(minOf(state.maxValue, state.value + with(density) { 480.dp.toPx() }.toInt())) }
                    },
                contentAlignment = Alignment.Center,
            ) { AtIcon(Ic.CHEVRON_DOWN, At.c.text, size = 20.dp) }
        }
    }
}
