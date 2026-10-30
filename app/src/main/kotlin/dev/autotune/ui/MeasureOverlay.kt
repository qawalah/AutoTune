package dev.autotune.ui

import androidx.compose.foundation.Canvas
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
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(v.title, style = MaterialTheme.typography.titleLarge)
                if (v.spots > 1) Muted("Spot ${v.spot} of ${v.spots}")
            }
            if (!busy) IconAction(Ic.CLOSE, "Close") { vm.closeMeasureView() }
        }

        Panel {
            // progress ring with the live level in the middle
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                    if (progress == null && (busy || v.stage == MeasureStage.ANALYSING)) {
                        CircularProgressIndicator(Modifier.size(180.dp), color = accent, trackColor = At.c.line, strokeWidth = 6.dp, strokeCap = StrokeCap.Round)
                    } else {
                        CircularProgressIndicator(
                            progress = { (progress ?: 0f).coerceIn(0f, 1f) }, modifier = Modifier.size(180.dp),
                            color = accent, trackColor = At.c.line, strokeWidth = 6.dp, strokeCap = StrokeCap.Round,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        when {
                            hasLevel -> {
                                Text("%.0f".format(level), style = MaterialTheme.typography.displaySmall, fontFamily = PlexMono)
                                Muted("dBFS")
                            }
                            v.stage == MeasureStage.SAVED -> AtIcon(if (allGood) Ic.DONE else Ic.ALERT, accent, size = 56.dp)
                            v.stage == MeasureStage.FAILED -> AtIcon(Ic.ALERT, accent, size = 56.dp)
                            v.stage == MeasureStage.SPOT_DONE -> AtIcon(Ic.MIC, accent, size = 48.dp)
                            else -> AtIcon(Ic.MEASURE, accent, size = 48.dp)
                        }
                    }
                }
            }
            Text(headline, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            sub?.let {
                Text(
                    it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
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
                PrimaryButton("Try again", { vm.measure() }, Modifier.fillMaxWidth(), icon = Ic.AGAIN)
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
    val line = At.c.signal
    val axis = At.c.line
    Canvas(Modifier.fillMaxWidth().height(72.dp).clip(MaterialTheme.shapes.small).background(At.c.panelHigh)) {
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

/** Live level of the mic, -80 to 0 dBFS, with the range where a measurement is happy marked. */
@Composable
private fun SignalMeter(level: Float, active: Boolean) {
    val good = At.c.good
    val track = At.c.panelHigh
    val col = when { level > -6f -> At.c.bad; level < -60f -> At.c.warn; else -> At.c.signal }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text("Signal", style = MaterialTheme.typography.labelLarge, color = At.c.muted, modifier = Modifier.weight(1f))
            Text(if (active) "%.0f dBFS".format(level) else "–", style = MaterialTheme.typography.labelLarge, fontFamily = PlexMono)
        }
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val w = size.width
            fun x(db: Float) = ((db + 80f) / 80f).coerceIn(0f, 1f) * w
            val r = CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = r)
            drawRect(good.copy(alpha = 0.18f), Offset(x(-55f), 0f), Size(x(-8f) - x(-55f), size.height))
            if (active) drawRoundRect(col, size = Size(x(level), size.height), cornerRadius = r)
        }
        Row {
            Muted("Too quiet", Modifier.weight(1f))
            Text("Good", style = MaterialTheme.typography.bodySmall, color = good, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("Clipping", style = MaterialTheme.typography.bodySmall, color = At.c.muted, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun CheckRow(c: CheckItem, current: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when (c.state) {
                0 -> if (current) CircularProgressIndicator(Modifier.size(18.dp), color = At.c.signal, strokeWidth = 2.dp)
                else Box(Modifier.size(8.dp).clip(CircleShape).background(At.c.line))
                1 -> AtIcon(Ic.CHECK, At.c.good, size = 20.dp)
                2 -> AtIcon(Ic.ALERT, At.c.warn, size = 20.dp)
                else -> AtIcon(Ic.CLOSE, At.c.bad, size = 20.dp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.label, style = MaterialTheme.typography.bodyLarge, color = if (c.state == 0 && !current) At.c.muted else At.c.text)
            c.detail?.takeIf { it.isNotBlank() }?.let { Muted(it) }
        }
    }
}
