@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.autotune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.autotune.LevelInfo
import dev.autotune.LiveSignal
import dev.autotune.Source
import dev.autotune.TuneViewModel
import dev.autotune.UiState
import dev.autotune.dsp.Rta
import dev.autotune.dsp.Target
import dev.autotune.dsp.Tone
import kotlin.math.pow

/**
 * Live RTA and sound level. Meant to be read at a glance while adjusting the EQ.
 * Turned sideways (auto-rotate on) the spectrum fills the left and the level, hold and sound sit at its right; the
 * corner key shows the spectrum full screen.
 */
@Composable
fun LiveTab(s: UiState, live: DoubleArray?, level: LevelInfo?, vm: TuneViewModel, pick: Pickers, go: (Tab) -> Unit) {
    val ready = s.hasPermission
    val hold by vm.rtaHold.collectAsState()
    val conf = LocalConfiguration.current
    val landscape = conf.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    if (!landscape) ScreenTitle("Live")

    if (!ready) {
        Panel {
            StatusLine(true, if (s.phoneMic) "Phone microphone (a USB mic is more accurate)" else "USB microphone: ${s.micName}")
            StatusLine(s.hasPermission, if (s.hasPermission) "Microphone access allowed" else "Microphone access needed")
            if (!s.hasPermission) TextAction("Allow microphone", pick.permission, icon = Ic.MIC)
        }
    }

    // Start and Stop are the screen's main key, at the bottom within reach
    if (!s.rtaRunning) PinAction("Start", { vm.startRta(s.rtaWithEq) }, icon = Ic.PLAY, enabled = ready && !s.busy, suggested = false)
    else PinAction("Stop", vm::stopRta, icon = Ic.STOP)

    val tone = s.liveSignal == LiveSignal.TONE
    val target = Rta.targetBars(Target.ALL[s.targetIdx.coerceIn(0, Target.ALL.size - 1)])
    val marker = if (tone) Tone.bandIndex(s.toneHz) else null

    // The spectrum is the primary live instrument. Never draw a synthetic target-shaped
    // spectrum while idle: that can be mistaken for a real microphone reading.
    // The graph keeps the same shape in every state, so the screen does not jump when Start is pressed.
    val spectrum: @Composable (Dp) -> Unit = { h ->
        Box {
            when {
                s.rtaRunning && live != null ->
                    RtaBars(live, if (tone) null else target, height = h, caption = false, hold = if (s.peakHold) hold else null, marker = marker)
                s.rtaRunning -> Box(Modifier.fillMaxWidth().height(h), contentAlignment = Alignment.Center) {
                    Text("Listening for the first reading…", style = MaterialTheme.typography.bodyMedium, color = At.c.muted)
                }
                else -> Box(Modifier.fillMaxWidth().height(h), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("standby", style = MaterialTheme.typography.labelSmall, color = At.c.muted)
                        // what Start does, in its own few words (2.32: a deck of its own said it in a sentence)
                        Text("start reads the spectrum and level", style = MaterialTheme.typography.labelSmall, color = At.c.muted)
                    }
                }
            }
            // the corner key opens the spectrum full screen, which turns sideways with the phone
            if (s.rtaRunning && live != null) Box(Modifier.align(Alignment.TopEnd)) { FullScreenKey(s, live, level, vm) }
        }
        if (!s.rtaRunning && s.status?.startsWith("RTA stopped") == true) Warning(s.status!!)
    }
    // hold: keep the highest bar and level reached, with a key that clears it
    val holdRow: @Composable () -> Unit = {
        SwitchRow(
            "Hold peaks", s.peakHold, vm::setPeakHold,
            info = "Keeps the highest each bar and the loudest peak have reached, as a white cap on each bar, until you press Reset or turn this off.",
        )
        if (s.peakHold) TextAction("Reset peaks", vm::resetPeaks, icon = Ic.UNDO, opens = false)
    }

    val levelPanel: @Composable () -> Unit = { LevelPanel(s, level, vm) }
    val soundGroup: @Composable () -> Unit = { SoundGroup(s, vm, go) }

    if (landscape) {
        // sideways: the spectrum fills the left; level, hold and sound sit beside it
        val h = (conf.screenHeightDp - 160).coerceAtLeast(180).dp
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1.5f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel(pad = 10.dp) { spectrum(h) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (level != null || s.rtaRunning) Panel { levelPanel() }
                Panel(pad = 10.dp) { holdRow() }
                soundGroup()
            }
        }
        return
    }

    // Simple: the spectrum needs no title
    if (isAdvanced()) Group("Spectrum", divider = false) { spectrum(248.dp) } else Panel(pad = 12.dp) { spectrum(248.dp) }
    Panel(pad = 10.dp) { holdRow() }

    // ---- level: only once there is one to read, so the Sound deck sits right under the spectrum
    if (level != null || s.rtaRunning) Panel { levelPanel() }

    // ---- sound source
    soundGroup()
}

/** The corner key on the spectrum: opens it full screen. Nothing else on the page is needed there. */
@Composable
private fun FullScreenKey(s: UiState, live: DoubleArray, level: LevelInfo?, vm: TuneViewModel) {
    val zoom = LocalChartZoom.current ?: return
    val me = remember { Any() }
    // what the full-screen view draws is read from these as it changes, so it keeps moving while it is open
    val liveNow by rememberUpdatedState(live)
    val levelNow by rememberUpdatedState(level)
    val holdNow by vm.rtaHold.collectAsState()
    val liveFlow by vm.rtaBars.collectAsState()
    val levelFlow by vm.level.collectAsState()
    val stateFlow by vm.state.collectAsState()
    DisposableEffect(zoom, me) { onDispose { zoom.closeCustom(me) } }
    IconAction(Ic.EXPAND, "Show the spectrum full screen") {
        zoom.showCustom(me) { close ->
            // the flows, not the values captured when it opened, so the bars keep moving
            val st = stateFlow
            val bars = liveFlow ?: liveNow
            val lv = levelFlow ?: levelNow
            val tone = st.liveSignal == LiveSignal.TONE
            val tgt = Rta.targetBars(Target.ALL[st.targetIdx.coerceIn(0, Target.ALL.size - 1)])
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Spectrum", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    if (lv != null) Text(
                        if (st.peakHold) "%.1f dBFS   peak hold %.1f".format(lv.rmsDb, lv.peakMaxDb) else "%.1f dBFS   peak %.1f".format(lv.rmsDb, lv.peakDb),
                        style = MaterialTheme.typography.labelMedium, color = At.c.muted, fontFamily = AtFigures,
                    )
                    TextAction("Close", close, icon = Ic.CLOSE)
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    if (st.rtaRunning && bars != null) {
                        RtaBars(
                            bars, if (tone) null else tgt, height = maxHeight, caption = false,
                            hold = if (st.peakHold) holdNow else null, marker = if (tone) Tone.bandIndex(st.toneHz) else null,
                        )
                    } else Text("Press Start to read the spectrum", Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodyMedium, color = At.c.muted)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hold peaks", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    if (st.peakHold) TextAction("Reset", vm::resetPeaks, icon = Ic.UNDO, opens = false)
                    LatchKey(st.peakHold, vm::setPeakHold, "Hold peaks")
                }
            }
        }
    }
}

/** The level reading: dBFS on the LCD, the bar with its held peak, and the facts under it. */
@Composable
private fun LevelPanel(s: UiState, level: LevelInfo?, vm: TuneViewModel) {
    if (level != null) {
        // the reading on the LCD in the Concept look, like every other reading
        LcdWindow {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("%.1f".format(level.rmsDb), style = MaterialTheme.typography.displayLarge)
                Text(" dBFS", Modifier.padding(bottom = 10.dp), style = MaterialTheme.typography.bodyMedium, color = At.c.muted)
                Box(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    if (s.phoneMic) {
                        // the estimate assumes a UMIK-1's gain; for a phone's own mic the number would mean nothing
                        Text("–", style = MaterialTheme.typography.displaySmall)
                        Muted("No dB SPL with the phone mic")
                    } else {
                        Text("≈ %.0f".format(level.splDb), style = MaterialTheme.typography.displaySmall)
                        Muted("dB SPL, estimated")
                    }
                }
            }
        }
        // the bar runs from -80 dBFS (quieter than any room) to 0 (clipping); the held peak is a tick on it
        Box(Modifier.fillMaxWidth().height(8.dp).fieldFace(MaterialTheme.shapes.extraSmall)) {
            val frac = ((level.rmsDb + 80.0) / 80.0).toFloat().coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxWidth(frac).height(8.dp).clip(MaterialTheme.shapes.extraSmall)
                    .background(if (level.peakDb > -1.0) At.c.bad else At.c.signal),
            )
            if (s.peakHold) {
                val pf = ((level.peakMaxDb + 80.0) / 80.0).toFloat().coerceIn(0f, 1f)
                if (pf > 0f) Box(Modifier.fillMaxWidth(pf).height(8.dp), contentAlignment = Alignment.CenterEnd) {
                    Box(Modifier.width(2.dp).fillMaxHeight().background(At.c.text))
                }
            }
        }
        if (s.peakHold) Muted("Peak hold %.1f dBFS   Leq %.1f dBFS".format(level.peakMaxDb, level.leqDb))
        else Muted("Peak %.1f dBFS   Leq %.1f dBFS".format(level.peakDb, level.leqDb))
        // a held peak that touched clipping stays flagged until reset, even after the level has dropped
        val clipped = if (s.peakHold) level.peakMaxDb > -1.0 else level.peakDb > -1.0
        if (clipped) Warning("Peaks are close to clipping. Lower the volume or move the mic away.")
        else if (level.rmsDb < -60.0) Warning("Very quiet. Raise the volume or move the mic closer.")
    } else {
        Text("listening", style = MaterialTheme.typography.labelSmall, color = At.c.muted)
    }
    if (isAdvanced()) Row(verticalAlignment = Alignment.CenterVertically) {
        Text("SPL offset %+.1f dB".format(s.splOffsetDb), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = At.c.muted)
        SecondaryButton("−1", { vm.adjustSplOffset(-1.0) })
        Box(Modifier.padding(start = 6.dp)) { SecondaryButton("+1", { vm.adjustSplOffset(1.0) }) }
        InfoButton(
            "Sound level",
            "dBFS is the level the phone receives; 0 dBFS is the maximum before clipping. It is reliable.\n\n" +
                "dB SPL is an estimate for a UMIK-1 at its default gain, using the calibration file's sensitivity. It can be off by several dB and only holds while Android's mic gain stays the same. Use the offset buttons to match a reference meter or a 94 dB calibrator.\n\n" +
                "Peak is the loudest recent moment. Leq is the average level since you pressed Start.",
        )
    }
}

/** What the phone plays while the spectrum runs: pink noise, a sweep, a tone with its own pitch slider, or nothing. */
@Composable
private fun SoundGroup(s: UiState, vm: TuneViewModel, go: (Tab) -> Unit) {
    Group("Sound") {
        if (s.systemEqOn) Muted("With AutoTune EQ on")
        if (s.source == Source.PHONE || s.testName.isBlank()) {
            Text("Play from the phone", style = MaterialTheme.typography.labelLarge)
            // the order matches LiveSignal (PINK, SWEEP, NONE, TONE): new kinds are only ever appended
            DropSelect(
                listOf("Pink noise", "Sweep", "Off", "Tone"), s.liveSignal.ordinal,
                { vm.setLiveSignal(LiveSignal.entries[it]) },
                info = "Pink noise and the sweep read the whole spectrum. Tone plays one steady pitch from 20 Hz to 20 kHz: drag the slider to find a rattle, a resonance or where the speaker gives out. It is played quietly; the phone's volume sets how loud.",
            )
            if (s.liveSignal == LiveSignal.TONE) ToneControl(s, vm)
            if (s.exportChain?.filters?.isNotEmpty() == true && !s.systemEqOn && isAdvanced()) {
                SwitchRow("Play the noise through my EQ", s.rtaWithEq, { if (s.rtaRunning) vm.setRtaEq(it) else vm.startRta(it) })
            }
        }
        if (isAdvanced()) TextAction("Open EQ", { go(Tab.EQ) }, icon = Ic.EQ)
    }
}

/** The tone generator: its pitch on a log slider from 20 Hz to 20 kHz, with small steps either side. */
@Composable
private fun ToneControl(s: UiState, vm: TuneViewModel) {
    val semitone = 2.0.pow(1.0 / 12.0)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(Tone.text(s.toneHz), Modifier.weight(1f), style = MaterialTheme.typography.displaySmall, fontFamily = AtFigures)
        SecondaryButton("−", { vm.setToneHz(s.toneHz / semitone); vm.saveToneHz() })
        Box(Modifier.padding(start = 6.dp)) { SecondaryButton("+", { vm.setToneHz(s.toneHz * semitone); vm.saveToneHz() }) }
    }
    Slider(
        value = Tone.fractionOf(s.toneHz).toFloat(),
        onValueChange = { vm.setToneHz(Tone.freqAt(it.toDouble())) },
        onValueChangeFinished = { vm.saveToneHz() },
        colors = sliderColors(),
    )
    Row(Modifier.fillMaxWidth()) {
        Text("20 Hz", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = At.c.muted)
        Text("20 kHz", style = MaterialTheme.typography.labelSmall, color = At.c.muted)
    }
}
