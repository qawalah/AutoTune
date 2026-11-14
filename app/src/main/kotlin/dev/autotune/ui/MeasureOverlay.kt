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
import androidx.compose.runtime.setValue
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
            dev.autotune.ui.gauge.sweepText(x) to x.toFloat()
        }
        else -> "Listening to the room ring out" to 1f
    }
}

/**
 * Full-screen measuring view, in the app's own theme: a ring that fills as the sweep climbs with the live level inside,
 * the live waveform, a line saying what is happening, the signal meter, and every check as it is done.
 */
@Composable
fun MeasureOverlay(v: MeasureView, s: UiState, vm: TuneViewModel, backEnabled: Boolean = true) {
    val level by LiveCapture.level.collectAsState()
    val wave by LiveCapture.wave.collectAsState()
    val clock: State<Long> = produceState(System.currentTimeMillis()) {
        while (true) withFrameMillis { value = System.currentTimeMillis() }
    }
    val busy = s.busy && (v.stage == MeasureStage.MEASURING || v.stage == MeasureStage.ANALYSING)
    // the screen stays on while it measures, and between the spots of a reading (2.31)
    KeepScreenOn(busy || v.stage == MeasureStage.MEASURING || v.stage == MeasureStage.ANALYSING || v.stage == MeasureStage.SPOT_DONE)
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
        MeasureStage.SPOT_DONE -> dev.autotune.spotDoneText(v.spot, v.spots).replaceFirstChar { it.uppercase() } to 1f
        MeasureStage.SAVED -> v.message to 1f
        MeasureStage.FAILED -> "Measurement failed" to 0f
    }
    val accent = when (v.stage) {
        MeasureStage.FAILED -> At.c.bad
        MeasureStage.SAVED -> if (allGood) At.c.good else At.c.warn
        else -> At.c.signal
    }
    val sub = when (v.stage) {
        // a status of its own while measuring (the second sweep after a Bluetooth pause) comes before the standing hint
        MeasureStage.MEASURING -> v.message.takeIf { it.isNotBlank() }
            ?: if (v.phoneRoute) "Keep still and quiet while the sweep plays." else "Play the file on the other device now."
        MeasureStage.ANALYSING -> "Finding the sweep and checking the reading"
        MeasureStage.SAVED -> v.result
        else -> v.message.takeIf { it.isNotBlank() }
    }

    // between spots the next one starts by itself after a short countdown; Pause holds it (2.32, BACKLOG)
    var paused by androidx.compose.runtime.remember(v.spot, v.stage) { androidx.compose.runtime.mutableStateOf(false) }
    val spotStart = androidx.compose.runtime.remember(v.spot, v.stage) { System.currentTimeMillis() }
    val left = if (v.stage == MeasureStage.SPOT_DONE && !paused) dev.autotune.countdownLeft(spotStart, clock.value) else null
    LaunchedEffect(v.stage, v.spot, paused) {
        if (v.stage == MeasureStage.SPOT_DONE && !paused) {
            while (dev.autotune.countdownLeft(spotStart, System.currentTimeMillis()) > 0) kotlinx.coroutines.delay(200)
            vm.measure()
        }
    }
    // Back never drops measured spots without asking (2.36); with none measured it stops or closes as before
    var askDrop by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = backEnabled) {
        when (dev.autotune.measureBack(v.stage, s.busy, v.spot)) {
            // the countdown waits while the question is up
            dev.autotune.MeasureBack.ASK -> { if (v.stage == MeasureStage.SPOT_DONE) paused = true; askDrop = true }
            dev.autotune.MeasureBack.STOP -> vm.cancel()
            dev.autotune.MeasureBack.CLOSE -> vm.closeMeasureView()
        }
    }
    if (askDrop) {
        val n = dev.autotune.spotsMeasured(v.stage, v.spot)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askDrop = false },
            title = { Text("Stop measuring?") },
            text = { Text(dev.autotune.dropSpotsText(n)) },
            confirmButton = { DialogKey(if (n == 1) "Stop and drop spot 1" else "Stop and drop the spots", { askDrop = false; if (s.busy) vm.cancel() else vm.closeMeasureView() }, danger = true) },
            dismissButton = { DialogKey("Keep measuring", { askDrop = false }) },
        )
    }
    val scroll = rememberScrollState()
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides At.c.text) {
    Box(Modifier.fillMaxSize().background(At.c.bg).statusBarsPadding().navigationBarsPadding()) {
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // the device header: the wordmark and what is being measured
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Wordmark(large = true)
            // the spot is named here while it is measured; once done the headline says it, once (2.32)
            Text(
                "  " + v.title.lowercase() + if (v.spots > 1 && v.stage != MeasureStage.SPOT_DONE) " · spot ${v.spot} of ${v.spots}" else "", Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, color = At.c.muted, maxLines = 1,
            )
            // no cross: each stage ends with one key that says what it does (Stop, Close, See the result)
        }
        HairRule()
        // Stock and Verify have no page: they light on the step bar while this screen measures them
        val step = when (s.tuneStep) {
            dev.autotune.TuneStep.STOCK -> 1
            dev.autotune.TuneStep.VERIFY -> 3
            else -> null
        }
        step?.let { StepBar(it, dev.autotune.STEP_NAMES.size) }

        Panel {
            // the display: the live level large and light, a pilot light for the stage (on the LCD in the Concept look)
            LcdWindow {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    when {
                        hasLevel -> Text("%.0f".format(level).replace('-', '−'), style = MaterialTheme.typography.displayLarge, color = At.c.text)
                        // between spots: the seconds to the next one, large, as the reading would be
                        left != null -> Text("$left", style = MaterialTheme.typography.displayLarge, color = At.c.text)
                        v.stage == MeasureStage.SAVED -> AtIcon(if (allGood) Ic.DONE else Ic.ALERT, accent, size = 56.dp)
                        v.stage == MeasureStage.FAILED -> AtIcon(Ic.ALERT, accent, size = 56.dp)
                        v.stage == MeasureStage.SPOT_DONE -> AtIcon(Ic.DONE, At.c.good, size = 56.dp)
                        // never a bare dash (2.32): the mic, waiting
                        else -> AtIcon(Ic.MIC, At.c.muted, size = 56.dp)
                    }
                    Text(
                        when {
                            hasLevel -> "dbfs · mic level"
                            left != null -> "s to spot ${v.spot + 1}"
                            v.stage == MeasureStage.SPOT_DONE -> "paused"
                            v.stage == MeasureStage.MEASURING -> "opening the mic"
                            else -> v.stage.name.lowercase().replace('_', ' ')
                        },
                        style = MaterialTheme.typography.labelSmall, color = At.c.muted,
                    )
                }
                Pilot(accent, blink = busy, modifier = Modifier.padding(bottom = 10.dp))
            }
            }
            // the sweep on a radio dial: 20 Hz to 20 kHz, the needle follows the tone
            dev.autotune.ui.gauge.SweepDial(progress, accent, gaugeColors(), waiting = progress == null && (busy || v.stage == MeasureStage.ANALYSING), animate = animationsOn())
            Text(headline, style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
            sub?.let {
                Text(
                    it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(), maxLines = if (v.stage == MeasureStage.FAILED) 4 else 2,
                    color = if (v.stage == MeasureStage.FAILED) At.c.bad else At.c.muted,
                )
            }
            // the live signal only while something is being recorded
            if (busy) {
                dev.autotune.ui.gauge.WaveLine(wave, active = v.stage == MeasureStage.MEASURING, gaugeColors())
                dev.autotune.ui.gauge.SignalMeter(level, active = hasLevel, gaugeColors(), animate = animationsOn())
            }
        }

        val rows: @Composable () -> Unit = {
            v.checks.forEachIndexed { i, c ->
                // the first unfinished check is the one being worked on
                val current = c.state == 0 && v.checks.take(i).all { it.state != 0 } && busy
                CheckRow(c, current)
            }
        }
        // Simple: every check green is one line that opens the list (2.32)
        if (!isAdvanced() && dev.autotune.allChecksPassed(v.checks.map { it.state })) Fold("Checks", summary = "all ${v.checks.size} passed") { rows() }
        else Group("Checks", divider = false) { rows() }

        when {
            busy -> SecondaryButton("Stop", vm::cancel, Modifier.fillMaxWidth(), icon = Ic.STOP)
            // one graphite key for the move; the other choices stacked under it, one per row, as everywhere else
            v.stage == MeasureStage.SPOT_DONE -> {
                PrimaryButton(if (paused) "Measure spot ${v.spot + 1}" else "Measure spot ${v.spot + 1} now", { vm.measure() }, Modifier.fillMaxWidth(), icon = Ic.MEASURE)
                Actions {
                    if (!paused) TextAction("Pause", { paused = true }, icon = Ic.STOP)
                    // what Close does to the spots already measured, in its own words
                    TextAction(if (v.spot > 1) "Close and drop spots 1-${v.spot}" else "Close and drop spot 1", vm::closeMeasureView, icon = Ic.CLOSE)
                }
            }
            v.stage == MeasureStage.FAILED -> {
                // what the Stock and Verify pages used to offer after a failure lives here now
                LevelResult(s)
                PrimaryButton("Try again", { vm.measure() }, Modifier.fillMaxWidth(), icon = Ic.AGAIN, enabled = !s.busy)
                Actions {
                    TextAction("Check the level", vm::levelCheck, enabled = !s.busy, icon = Ic.LIVE)
                    TextAction("Ask AI why", vm::aiExplainFailure, icon = Ic.AI, opens = true)
                    TextAction("Close, nothing is saved", vm::closeMeasureView, icon = Ic.CLOSE)
                }
            }
            else -> PrimaryButton("See the result", vm::closeMeasureView, Modifier.fillMaxWidth(), icon = Ic.NEXT)
        }
        Spacer(Modifier.height(8.dp))
    }
    // the checks and the keys can run on under the edge: say so
    MoreBelow(scroll, Modifier.align(Alignment.BottomCenter))
    }
    }
}

/** The phone's colours for the shared measuring display. */
@Composable
fun gaugeColors() = dev.autotune.ui.gauge.GaugeColors(At.c.bg, At.c.text, At.c.muted, At.c.line, At.c.good, At.c.warn, At.c.bad, At.c.signal)

@Composable
private fun Pilot(color: androidx.compose.ui.graphics.Color, blink: Boolean, size: androidx.compose.ui.unit.Dp = 14.dp, modifier: Modifier = Modifier) =
    dev.autotune.ui.gauge.Pilot(color, blink, animationsOn(), size, modifier)

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
