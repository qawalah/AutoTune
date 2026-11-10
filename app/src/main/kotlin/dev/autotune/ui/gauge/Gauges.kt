package dev.autotune.ui.gauge

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * The measuring display, in plain Compose with no Android imports: the phone's measuring screen, the TV screen and the
 * Windows app all draw it from here, so the three always look and move the same (the Windows app compiles this folder).
 */

/** The colours the display needs, from whichever theme draws it. [well] is the recessed track behind each scale. */
data class GaugeColors(
    val well: Color, val text: Color, val muted: Color, val line: Color,
    val good: Color, val warn: Color, val bad: Color, val signal: Color,
)

/** What is playing at a point of a sweep from 20 Hz to 20 kHz: "Sweeping 734 Hz". */
fun sweepText(fraction: Double): String {
    val f = 20.0 * Math.pow(1000.0, fraction.coerceIn(0.0, 1.0))
    return "Sweeping " + if (f >= 1000) String.format(java.util.Locale.US, "%.1f kHz", f / 1000) else String.format(java.util.Locale.US, "%.0f Hz", f)
}

/** The mic's level large and light ("−31", dbfs · mic level), or a dash when nothing is heard. */
@Composable
fun LevelReading(levelDb: Float?, c: GaugeColors, scale: Float = 1f) {
    val style = MaterialTheme.typography.displayLarge.let { if (scale == 1f) it else it.copy(fontSize = it.fontSize * scale, lineHeight = it.lineHeight * scale) }
    Column {
        Text(levelDb?.let { "%.0f".format(it).replace('-', '−') } ?: "−", style = style, color = if (levelDb == null) c.muted else c.text)
        Text("dbfs · mic level", style = MaterialTheme.typography.labelSmall.let { if (scale == 1f) it else it.copy(fontSize = it.fontSize * scale.coerceAtMost(1.6f)) }, color = c.muted)
    }
}

/** A pilot light: lit in [color]; blinking while work is under way (unless [animate] is off). */
@Composable
fun Pilot(color: Color, blink: Boolean, animate: Boolean, size: Dp = 14.dp, modifier: Modifier = Modifier) {
    val a = if (blink && animate) {
        val inf = rememberInfiniteTransition(label = "pilot")
        inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a").value
    } else 1f
    Box(modifier.size(size).clip(CircleShape).background(color.copy(alpha = a)))
}

/**
 * The sweep as a radio tuning dial: 20 Hz to 20 kHz in decades, the needle at the tone playing now ([progress] 0..1),
 * seeking back and forth while [waiting] for the sweep to start.
 */
@Composable
fun SweepDial(progress: Float?, accent: Color, c: GaugeColors, waiting: Boolean = false, animate: Boolean = true, height: Dp = 40.dp) {
    val moving = if (waiting && animate) {
        val inf = rememberInfiniteTransition(label = "seek")
        inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s").value
    } else null
    val p by animateFloatAsState((progress ?: 0f).coerceIn(0f, 1f), tween(if (animate) 150 else 0, easing = FastOutSlowInEasing), label = "dial")
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Canvas(Modifier.fillMaxWidth().height(height).clip(MaterialTheme.shapes.small).background(c.well)) {
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

/** The last fraction of a second the mic heard, as a plain line ([wave] samples, any scale: drawn to its peak). */
@Composable
fun WaveLine(wave: FloatArray, active: Boolean, c: GaugeColors, height: Dp = 64.dp) {
    Canvas(Modifier.fillMaxWidth().height(height).clip(MaterialTheme.shapes.small).background(c.well)) {
        val mid = size.height / 2
        drawLine(c.line, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
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
        drawPath(path, c.text, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/**
 * Live level of the mic on a meter scale, −80 to 0 dBFS: ticks every 10 dB, the range a measurement is happy with
 * shaded green, and the needle in the accent (warn when too quiet, bad when clipping).
 */
@Composable
fun SignalMeter(level: Float, active: Boolean, c: GaugeColors, animate: Boolean = true, height: Dp = 36.dp) {
    val col = when { level > -6f -> c.bad; level < -60f -> c.warn; else -> c.signal }
    val shown by animateFloatAsState(if (active) level else -80f, tween(if (animate) 120 else 0, easing = FastOutSlowInEasing), label = "vu")
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Canvas(Modifier.fillMaxWidth().height(height).clip(MaterialTheme.shapes.small).background(c.well)) {
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

/**
 * The phone's measuring display as the TV and the PC show it while the phone records: the level large with a pilot
 * light, the sweep dial, what is playing, the waveform and the level meter. [sweep] is how far through the sweep the
 * device playing it is (null outside one); [change] is the level test's reading, shown large in its place.
 */
@Composable
fun MeasureDisplay(
    levelDb: Float?, wave: FloatArray, headline: String, sub: String?, sweep: Float?, waiting: Boolean,
    c: GaugeColors, animate: Boolean = true, scale: Float = 1f, change: Double? = null, okDb: Double = 0.5,
) {
    val recording = levelDb != null && levelDb > -110f
    Column(verticalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                if (change != null) {
                    val ok = kotlin.math.abs(change) < okDb
                    val style = MaterialTheme.typography.displayLarge.let { it.copy(fontSize = it.fontSize * scale, lineHeight = it.lineHeight * scale) }
                    Text(if (ok) "0.0" else String.format(java.util.Locale.US, "%+.1f", change).replace('-', '−'), style = style, color = if (ok) c.good else c.signal)
                    Text(if (ok) "db · level" else if (change > 0) "db · turn it up" else "db · turn it down", style = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize * scale.coerceAtMost(1.6f)), color = c.muted)
                } else LevelReading(levelDb?.takeIf { recording }, c, scale)
            }
            Pilot(c.signal, blink = recording, animate = animate, size = (14 * scale).dp, modifier = Modifier.padding(bottom = (10 * scale).dp))
        }
        SweepDial(sweep, c.signal, c, waiting = waiting && sweep == null, animate = animate, height = (40 * scale).dp)
        Text(headline, style = MaterialTheme.typography.titleLarge.let { if (scale == 1f) it else it.copy(fontSize = it.fontSize * scale.coerceAtMost(1.5f)) }, color = c.text, modifier = Modifier.fillMaxWidth(), maxLines = 1)
        sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium.let { s -> if (scale == 1f) s else s.copy(fontSize = s.fontSize * scale.coerceAtMost(1.4f)) }, color = c.muted, modifier = Modifier.fillMaxWidth(), maxLines = 2) }
        WaveLine(wave, active = recording, c, height = (64 * scale).dp)
        SignalMeter(levelDb ?: -80f, active = recording, c, animate = animate, height = (36 * scale).dp)
    }
}
