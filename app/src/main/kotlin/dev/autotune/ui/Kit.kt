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
import androidx.compose.ui.draw.drawBehind
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
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(MaterialTheme.shapes.large).background(At.c.warn.copy(alpha = 0.10f))
            .clickable(role = Role.Button, onClickLabel = "Read the notes") { open = true }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AtIcon(Ic.ALERT, At.c.warn, size = 18.dp)
        Text(
            if (items.size == 1) " " + items[0] else " ${items.size} notes: " + items[0],
            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = At.c.text, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        AtIcon(Ic.CHEVRON, At.c.muted, size = 16.dp)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items.forEach { Warning(it) }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Got it") } },
        )
    }
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
                Text(if (mode.advanced) "advanced" else "simple", style = MaterialTheme.typography.labelMedium, color = At.c.text)
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

/** A deck: the grey plate of a Braun device (the SK 4's turntable deck). 6dp corners, no border, no shadow. */
val CardShape = RoundedCornerShape(6.dp)

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
        modifier.fillMaxWidth().clip(CardShape).background(At.c.panel)
            .animateContentSize(tween(motionMs(320), easing = EaseArrive)).padding(pad),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { androidx.compose.runtime.CompositionLocalProvider(LocalInCard provides true) { content() } }
}

/** A card: one section of a screen (what was measured, what is set, a group of choices). */
@Composable
fun Panel(modifier: Modifier = Modifier, pad: Dp = 14.dp, content: @Composable () -> Unit) = CardBox(modifier, pad, content)

/**
 * A run sheet: the shop-green header strip that names what the sheet is about, ruled from the content below it, like
 * the header of a dyno printout. [trailing] sits at the strip's right end.
 */
@Composable
fun RunSheet(title: String, sub: String? = null, trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    val c = At.c
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(c.panel).animateContentSize(tween(motionMs(320), easing = EaseArrive)),
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
 * One row of ruled cells, each a reading with its name under it, the way a dyno sheet prints its figures. A cell's
 * [Reading.tint] colours the figure only.
 */
class Reading(val value: String, val unit: String?, val name: String, val tint: Color? = null)

@Composable
fun ReadingCells(cells: List<Reading>) {
    // the first figure is the display's main reading; the rest sit beside it, smaller
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        cells.forEachIndexed { i, r ->
            Column(Modifier.weight(if (i == 0) 1.3f else 1f), horizontalAlignment = if (i == 0) Alignment.Start else Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        r.value, style = if (i == 0) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall.copy(fontSize = 24.sp, lineHeight = 30.sp),
                        color = r.tint ?: At.c.text, maxLines = 1,
                    )
                    if (r.unit != null) Text(" ${r.unit}", Modifier.padding(bottom = if (i == 0) 8.dp else 4.dp), style = MaterialTheme.typography.labelSmall, color = At.c.muted, maxLines = 1)
                }
                Text(r.name.lowercase(), style = MaterialTheme.typography.labelSmall, color = At.c.muted, maxLines = 1)
            }
        }
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
    else Column(Modifier.fillMaxWidth().clip(CardShape).background(At.c.panel)) { body() }
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

/**
 * Page title with an optional line of context under it, ruled off from the page like a sheet's header. On a page
 * opened over a tab ([LocalBack] set) it starts with a back arrow and leaves the menu out.
 */
@Composable
fun ScreenTitle(title: String, context: String? = null, onContext: (() -> Unit)? = null, contextIcon: Ic? = null, actions: @Composable () -> Unit = {}) {
    val back = LocalBack.current
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (back != null) {
                Box(Modifier.offset(x = (-12).dp)) { IconAction(Ic.BACK, "Back", back) }
                Text(
                    title, Modifier.weight(1f).offset(x = (-8).dp), style = MaterialTheme.typography.titleLarge,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            } else {
                // the small wordmark, as the braun badge sits on every device, then the tab's name
                Text("autotune", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp), color = At.c.text)
                Text(
                    "  " + title.lowercase(), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                    color = At.c.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            actions()
            ModeSwitch()
            if (back == null) LocalMenu.current()
        }
        if (context != null) {
            Row(
                (if (onContext != null) Modifier.clickable(onClick = onContext) else Modifier).heightIn(min = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (contextIcon != null) { AtIcon(contextIcon, At.c.muted, size = 16.dp); androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp)) }
                Text(context, style = MaterialTheme.typography.bodySmall, color = At.c.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        SheetRule()
    }
}

/** The hairline under a screen's header. */
@Composable
fun SheetRule() {
    Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(1.dp).background(At.c.line))
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
    val tint = if (!enabled) At.c.muted else if (danger) At.c.bad else At.c.signal
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
        colors = ButtonDefaults.textButtonColors(contentColor = if (danger) At.c.bad else At.c.signal, disabledContentColor = At.c.muted),
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
    // the record of a Braun PS 2: a black disc with fine grooves, Tune is its red label. While it waits, a faint
    // ring runs out over the grooves, like a stylus about to drop.
    val c = At.c
    val anim = animationsOn() && enabled
    val phase = if (anim) {
        val inf = rememberInfiniteTransition(label = "record")
        inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "p").value
    } else 0f
    val vinyl = if (c.dark) Color(0xFF0B0B0A) else Color(0xFF1C1C1B)
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        Modifier.size(260.dp).pressScale(src).clip(CircleShape)
            .clickable(interactionSource = src, indication = androidx.compose.material3.ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(260.dp)) {
            val r = size.minDimension / 2
            drawCircle(c.alu.copy(alpha = 0.55f), r)
            drawCircle(vinyl, r * 0.95f)
            for (g in 1..14) drawCircle(Color.White.copy(alpha = 0.035f), r * (0.44f + g * 0.036f), style = Stroke(1f))
            if (anim) {
                val rr = r * (0.42f + 0.52f * phase)
                drawCircle(Color.White.copy(alpha = 0.10f * (1f - phase)), rr, style = Stroke(1.5.dp.toPx()))
            }
        }
        Box(
            Modifier.size(110.dp).clip(CircleShape).background(if (enabled) c.signal else c.panelHigh),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label.lowercase(), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = if (enabled) c.onSignal else c.muted)
                if (sub != null) Text(sub.lowercase(), style = MaterialTheme.typography.labelSmall, color = if (enabled) c.onSignal.copy(alpha = 0.8f) else c.muted)
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

/** Big on/off switch for the system EQ. */
@Composable
fun PowerToggle(on: Boolean, onChange: (Boolean) -> Unit, label: String, enabled: Boolean = true) {
    val t = updateTransition(on, label = "power")
    val x by t.animateDp(transitionSpec = { tween(if (animationsOn()) 160 else 0) }, label = "x") { if (it) 28.dp else 0.dp }
    Box(
        Modifier.size(72.dp, 44.dp).clip(RoundedCornerShape(22.dp))
            .background(if (on) At.c.text else At.c.panelHigh).border(1.dp, At.c.line, RoundedCornerShape(22.dp))
            .toggleable(value = on, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = label },
    ) {
        Box(
            Modifier.padding(5.dp).offset(x = x).size(34.dp).clip(CircleShape)
                .background(if (on) At.c.good else At.c.muted),
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
            AtIcon(sel?.let { kindIcon(it.kind) } ?: Ic.EQ, At.c.signal, size = 22.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    sel?.let { c -> c.title + (c.db?.let { " · %.1f dB from target".format(it) } ?: "") } ?: emptyText,
                    style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                sel?.let { c -> Text(listOfNotNull(c.groupSub, c.groupTitle).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = At.c.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
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
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
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

/** One choice on one line: a check when it is in use, its name, BEST on the best run, the reading at the end. */
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
            if (c.best) {
                Text(
                    "BEST", Modifier.padding(start = 8.dp).clip(RoundedCornerShape(4.dp)).background(At.c.good.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall, color = At.c.good,
                )
            }
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
    val rule = At.c.line
    Column(
        // ruled off from the page, so content scrolling under it reads as passing under, not cut off
        Modifier.fillMaxWidth().background(At.c.bg).drawBehind { drawLine(rule, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 1.dp.toPx()) }
            .padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        a.hint?.takeIf { isAdvanced() || !a.enabled }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = At.c.muted, maxLines = 2) }
        // the main key: graphite, the action centred in clear type; a pilot light in the accent at the start says this
        // is the suggested move, lit while it is ready
        val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val keyColor = if (a.enabled) At.c.text else At.c.panelHigh
        val onKey = if (a.enabled) At.c.bg else At.c.muted
        Box(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).pressScale(src).clip(MaterialTheme.shapes.medium).background(keyColor)
                .clickable(interactionSource = src, indication = androidx.compose.material3.ripple(), enabled = a.enabled, role = Role.Button, onClick = a.onClick)
                .semantics { contentDescription = "Suggested: ${a.text}" }
                .padding(horizontal = 18.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.align(Alignment.CenterStart).size(12.dp).clip(CircleShape).background(if (a.enabled) At.c.signal else At.c.line))
            // a new recommendation slides in, so the change is noticed
            val inMs = motionMs(300)
            val outMs = motionMs(90)
            androidx.compose.animation.AnimatedContent(
                targetState = a.text, modifier = Modifier.padding(horizontal = 28.dp),
                transitionSpec = {
                    (androidx.compose.animation.fadeIn(tween(inMs, easing = EaseArrive)) + androidx.compose.animation.slideInVertically(tween(inMs, easing = EaseArrive)) { it / 3 }) togetherWith
                        androidx.compose.animation.fadeOut(tween(outMs, easing = EaseLeave))
                },
                contentAlignment = Alignment.Center,
                label = "pin",
            ) { text ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = onKey, maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Text("suggested", style = MaterialTheme.typography.labelSmall, color = onKey.copy(alpha = 0.6f))
                }
            }
        }
    }
}
