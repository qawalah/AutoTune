package dev.autotune.ui

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.autotune.CheckItem
import dev.autotune.MeasureStage
import dev.autotune.MeasureView
import dev.autotune.TuneViewModel
import dev.autotune.UiState
import dev.autotune.audio.LiveCapture
import dev.autotune.audio.MeasurementRunner
import dev.autotune.dsp.Marker
import kotlin.math.pow

private const val SWEEP_SEC = 10.0

/** What the phone is playing [t] seconds after it started the test sound, and how far through the sweep it is. */
private fun playing(t: Double): Pair<String, Float> {
    val pre = MeasurementRunner.PREROLL_SEC
    val beeps = Marker.LENGTH_SEC
    val lead = Marker.SWEEP_OFFSET_SEC
    return when {
        t < pre -> "Waking the speakers" to 0f
        t < pre + beeps -> "Start beeps" to 0f
        t < pre + lead -> "Get ready" to 0f
        t < pre + lead + SWEEP_SEC -> {
            val x = (t - pre - lead) / SWEEP_SEC
            val f = 20.0 * 1000.0.pow(x)
            "Sweeping " + (if (f >= 1000) "%.1f kHz".format(f / 1000) else "%.0f Hz".format(f)) to x.toFloat()
        }
        else -> "Listening to the room ring out" to 1f
    }
}

/**
 * Full-screen measuring view, in the app's own theme: a ring that fills as the sweep climbs with the live level inside,
 * the live waveform, a line saying what is happening, the signal meter, and every check as it is done.
 */
@Composable
fun MeasureOverlay(v: MeasureView, s: UiState, vm: TuneViewModel) {
    val level by LiveCapture.level.collectAsState()
    val wave by LiveCapture.wave.collectAsState()
    val clock: State<Long> = produceState(System.currentTimeMillis()) {
        while (true) withFrameMillis { value = System.currentTimeMillis() }
    }
    val busy = s.busy && (v.stage == MeasureStage.MEASURING || v.stage == MeasureStage.ANALYSING)
    val allGood = v.checks.none { it.state >= 2 }
    val hasLevel = busy && level > -110f

    // a clean result closes by itself; anything worth reading stays until the user closes it
    LaunchedEffect(v.stage, allGood) {
        if (v.stage == MeasureStage.SAVED && allGood) { kotlinx.coroutines.delay(2600); vm.closeMeasureView() }
    }

    val start = LiveCapture.playStartMs
    val (headline, progress) = when (v.stage) {
        MeasureStage.MEASURING -> if (v.phoneRoute) {
            if (start > 0) playing((clock.value - start) / 1000.0).let { it.first to it.second as Float? } else "Opening the microphone" to null
        } else (s.status?.removeSuffix("...") ?: "Listening") to s.progress
        MeasureStage.ANALYSING -> "Analysing" to null
        MeasureStage.SPOT_DONE -> "Spot ${v.spot} of ${v.spots} done" to 1f
        MeasureStage.SAVED -> v.message to 1f
        MeasureStage.FAILED -> "Measurement failed" to 0f
    }
    val accent = when (v.stage) {
        MeasureStage.FAILED -> At.c.bad
        MeasureStage.SAVED -> if (allGood) At.c.good else At.c.warn
        else -> At.c.signal
    }
    val sub = when (v.stage) {
        MeasureStage.MEASURING -> if (v.phoneRoute) "Keep still and quiet while the sweep plays." else "Play the file on the other device now."
        MeasureStage.ANALYSING -> "Finding the sweep and checking the reading"
        MeasureStage.SAVED -> v.result
        else -> v.message.takeIf { it.isNotBlank() }
    }

    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides At.c.text) {
    Column(
        Modifier.fillMaxSize().background(At.c.bg).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // the device header: the wordmark and what is being measured
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Wordmark(large = true)
            Text(
                "  " + v.title.lowercase() + if (v.spots > 1) " · spot ${v.spot} of ${v.spots}" else "", Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, color = At.c.muted, maxLines = 1,
            )
            if (!busy) IconAction(Ic.CLOSE, "Close") { vm.closeMeasureView() }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(At.c.line))

        Panel {
            // the display: the live level large and light, a pilot light for the stage
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    when {
                        hasLevel -> Text("%.0f".format(level).replace('-', '−'), style = MaterialTheme.typography.displayLarge, color = At.c.text)
                        v.stage == MeasureStage.SAVED -> AtIcon(if (allGood) Ic.DONE else Ic.ALERT, accent, size = 56.dp)
                        v.stage == MeasureStage.FAILED -> AtIcon(Ic.ALERT, accent, size = 56.dp)
                        else -> Text("−", style = MaterialTheme.typography.displayLarge, color = At.c.muted)
                    }
                    Text(if (hasLevel) "dbfs · mic level" else v.stage.name.lowercase().replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = At.c.muted)
                }
                Pilot(accent, blink = busy, modifier = Modifier.padding(bottom = 10.dp))
            }
            // the sweep on a radio dial: 20 Hz to 20 kHz, the needle follows the tone
            SweepDial(progress, accent, waiting = progress == null && (busy || v.stage == MeasureStage.ANALYSING))
            Text(headline, style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
            sub?.let {
                Text(
                    it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(), maxLines = if (v.stage == MeasureStage.FAILED) 4 else 2,
                    color = if (v.stage == MeasureStage.FAILED) At.c.bad else At.c.muted,
                )
            }
            // the live signal only while something is being recorded
            if (busy) {
                Waveform(wave, active = v.stage == MeasureStage.MEASURING)
                SignalMeter(level, active = hasLevel)
            }
        }

        Group("Checks", divider = false) {
            v.checks.forEachIndexed { i, c ->
                // the first unfinished check is the one being worked on
                val current = c.state == 0 && v.checks.take(i).all { it.state != 0 } && busy
                CheckRow(c, current)
            }
        }

        when {
            busy -> SecondaryButton("Stop", vm::cancel, Modifier.fillMaxWidth(), icon = Ic.STOP)
            v.stage == MeasureStage.SPOT_DONE -> {
                PrimaryButton("Measure spot ${v.spot + 1}", { vm.measure() }, Modifier.fillMaxWidth(), icon = Ic.MEASURE)
                SecondaryButton("Close", vm::closeMeasureView, Modifier.fillMaxWidth())
            }
            v.stage == MeasureStage.FAILED -> {
                // what the Stock and Verify pages used to offer after a failure lives here now
                LevelResult(s)
                PrimaryButton("Try again", { vm.measure() }, Modifier.fillMaxWidth(), icon = Ic.AGAIN, enabled = !s.busy)
                SecondaryButton("Check the level", vm::levelCheck, Modifier.fillMaxWidth(), icon = Ic.LIVE, enabled = !s.busy)
                SecondaryButton("Ask AI why", vm::aiExplainFailure, Modifier.fillMaxWidth(), icon = Ic.AI)
                SecondaryButton("Close", vm::closeMeasureView, Modifier.fillMaxWidth())
            }
            else -> PrimaryButton("See the result", vm::closeMeasureView, Modifier.fillMaxWidth(), icon = Ic.NEXT)
        }
        Spacer(Modifier.height(8.dp))
    }
    }
}

/** The last fraction of a second the mic heard, as a plain line. */
@Composable
private fun Waveform(wave: FloatArray, active: Boolean) {
    val line = At.c.text
    val axis = At.c.line
    Canvas(Modifier.fillMaxWidth().height(64.dp).clip(MaterialTheme.shapes.small).background(At.c.bg)) {
        val mid = size.height / 2
        drawLine(axis, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
        if (!active || wave.isEmpty()) return@Canvas
        var peak = 1e-4f
        for (x in wave) peak = maxOf(peak, kotlin.math.abs(x))
        val gain = (size.height * 0.42f) / maxOf(peak, 0.02f)
        val path = Path()
        for (i in wave.indices) {
            val px = i.toFloat() / (wave.size - 1).coerceAtLeast(1) * size.width
            val py = mid - wave[i] * gain
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, line, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/**
 * Live level of the mic on a meter scale, −80 to 0 dBFS: ticks every 10 dB, the range a measurement is happy with
 * shaded green, and the needle in the accent (warn when too quiet, bad when clipping).
 */
@Composable
private fun SignalMeter(level: Float, active: Boolean) {
    val c = At.c
    val col = when { level > -6f -> c.bad; level < -60f -> c.warn; else -> c.signal }
    val shown by androidx.compose.animation.core.animateFloatAsState(if (active) level else -80f, androidx.compose.animation.core.tween(motionMs(120), easing = EaseArrive), label = "vu")
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Canvas(Modifier.fillMaxWidth().height(36.dp).clip(MaterialTheme.shapes.small).background(c.bg)) {
            val pad = 10.dp.toPx(); val w = size.width - 2 * pad; val h = size.height
            fun x(db: Float) = pad + ((db + 80f) / 80f).coerceIn(0f, 1f) * w
            drawRect(c.good.copy(alpha = 0.16f), Offset(x(-55f), h * 0.15f), Size(x(-8f) - x(-55f), h * 0.7f))
            for (d in -80..0 step 5) {
                val major = d % 10 == 0
                drawLine(c.muted.copy(alpha = if (major) 0.9f else 0.45f), Offset(x(d.toFloat()), h * 0.2f), Offset(x(d.toFloat()), h * if (major) 0.55f else 0.4f), 1.dp.toPx())
            }
            if (active) drawLine(col, Offset(x(shown), 0f), Offset(x(shown), h), 2.5.dp.toPx())
        }
        Row {
            Text("too quiet", style = MaterialTheme.typography.labelSmall, color = c.muted, modifier = Modifier.weight(1f))
            Text("good", style = MaterialTheme.typography.labelSmall, color = c.good, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("clipping", style = MaterialTheme.typography.labelSmall, color = c.muted, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    }
}

/** The sweep as a radio tuning dial: 20 Hz to 20 kHz in decades, the needle at the tone playing now. */
@Composable
private fun SweepDial(progress: Float?, accent: androidx.compose.ui.graphics.Color, waiting: Boolean) {
    val c = At.c
    val moving = if (waiting && animationsOn()) {
        val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "seek")
        inf.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "s").value
    } else null
    val p by androidx.compose.animation.core.animateFloatAsState((progress ?: 0f).coerceIn(0f, 1f), androidx.compose.animation.core.tween(motionMs(150), easing = EaseArrive), label = "dial")
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Canvas(Modifier.fillMaxWidth().height(40.dp).clip(MaterialTheme.shapes.small).background(c.bg)) {
            val pad = 10.dp.toPx(); val w = size.width - 2 * pad; val h = size.height
            // log ticks: 20, 30 … 90, 100 … per decade
            for (dec in 0..2) for (k in 2..10) {
                val f = k * Math.pow(10.0, dec + 1.0)
                if (f < 20 || f > 20000) continue
                val x = pad + w * (Math.log10(f / 20.0) / 3.0).toFloat()
                val major = k == 10 || (dec == 0 && k == 2)
                drawLine(c.muted.copy(alpha = if (major) 0.9f else 0.45f), Offset(x, h * 0.2f), Offset(x, h * if (major) 0.6f else 0.4f), 1.dp.toPx())
            }
            val pos = moving ?: p
            if (progress != null || moving != null) {
                val x = pad + w * pos
                drawLine(accent.copy(alpha = if (moving != null) 0.55f else 1f), Offset(x, 0f), Offset(x, h), 2.5.dp.toPx())
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("20", "200", "2k", "20k").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = c.muted) }
        }
    }
}

/** A pilot light: lit in [color]; blinking while work is under way. */
@Composable
private fun Pilot(color: androidx.compose.ui.graphics.Color, blink: Boolean, size: androidx.compose.ui.unit.Dp = 14.dp, modifier: Modifier = Modifier) {
    val a = if (blink && animationsOn()) {
        val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "pilot")
        inf.animateFloat(0.3f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "a").value
    } else 1f
    Box(modifier.size(size).clip(CircleShape).background(color.copy(alpha = a)))
}

@Composable
private fun CheckRow(c: CheckItem, current: Boolean) {
    // each check is a pilot light: grey waiting, blinking in the accent while it runs, then green, yellow or red
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            when (c.state) {
                0 -> if (current) Pilot(At.c.signal, blink = true, size = 10.dp) else Box(Modifier.size(10.dp).clip(CircleShape).background(At.c.line))
                1 -> Box(Modifier.size(10.dp).clip(CircleShape).background(At.c.good))
                2 -> Box(Modifier.size(10.dp).clip(CircleShape).background(At.c.warn))
                else -> Box(Modifier.size(10.dp).clip(CircleShape).background(At.c.bad))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(c.label, style = MaterialTheme.typography.bodyMedium, color = if (c.state == 0 && !current) At.c.muted else At.c.text)
            c.detail?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = At.c.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
        }
    }
}
