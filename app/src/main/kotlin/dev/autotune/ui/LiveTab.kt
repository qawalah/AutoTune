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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
            if (s.rtaRunning && live != null) Box(Modifier.align(Alignment.TopEnd)) { FullScreenKey(vm, pick) }
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

    // sideways, the shell draws [LiveFull] over the whole screen (no tabs, no scrolling): nothing to lay out here
    if (landscape) return

    // Simple: the spectrum needs no title
    if (isAdvanced()) Group("Spectrum", divider = false) { spectrum(248.dp) } else Panel(pad = 12.dp) { spectrum(248.dp) }
    Panel(pad = 10.dp) { holdRow() }

    // ---- level: only once there is one to read, so the Sound deck sits right under the spectrum
    if (level != null || s.rtaRunning) Panel { levelPanel() }

    // ---- sound source
    soundGroup()
}

/** The corner key on the spectrum: opens the spectrum full screen, with the level and the tone slider beside it. */
@Composable
private fun FullScreenKey(vm: TuneViewModel, pick: Pickers) {
    val zoom = LocalChartZoom.current ?: return
    val me = remember { Any() }
    DisposableEffect(zoom, me) { onDispose { zoom.closeCustom(me) } }
    IconAction(Ic.EXPAND, "Show the spectrum full screen") {
        zoom.showCustom(me) { close -> LiveFull(vm, close, pick) }
    }
}

/**
 * Everything Live shows, on one screen with no scrolling: the spectrum, the live reading, what is played (with the tone's
 * slider) and Hold peaks. Sideways it is the spectrum on the left and the readings and keys at its right; upright, the
 * spectrum above and the keys below. It is the whole Live tab when the phone is turned sideways and the full-screen view
 * of the spectrum ([onClose] is its Close key; null on the tab itself). It reads the view model's flows itself, so it
 * keeps moving while it is open.
 */
@Composable
fun LiveFull(vm: TuneViewModel, onClose: (() -> Unit)? = null, pick: Pickers? = null) {
    val s by vm.state.collectAsState()
    val live by vm.rtaBars.collectAsState()
    val level by vm.level.collectAsState()
    val hold by vm.rtaHold.collectAsState()
    val tone = s.liveSignal == LiveSignal.TONE
    val target = Rta.targetBars(Target.ALL[s.targetIdx.coerceIn(0, Target.ALL.size - 1)])
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        val wide = maxWidth > maxHeight
        val spectrum: @Composable (Modifier) -> Unit = { m ->
            BoxWithConstraints(m) {
                val bars = live
                if (s.rtaRunning && bars != null) {
                    RtaBars(
                        bars, if (tone) null else target, height = maxHeight, caption = false,
                        hold = if (s.peakHold) hold else null, marker = if (tone) Tone.bandIndex(s.toneHz) else null,
                    )
                } else Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (s.rtaRunning) "listening" else "standby", style = MaterialTheme.typography.labelSmall, color = At.c.muted)
                    if (!s.rtaRunning) Text("start reads the spectrum and level", style = MaterialTheme.typography.labelSmall, color = At.c.muted)
                }
            }
        }
        val keys: @Composable (Modifier) -> Unit = { m -> LiveKeys(s, level, vm, onClose, m) }
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // the spectrum with the equaliser under it: adjust a band and watch what it does
                Column(Modifier.weight(1.55f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    spectrum(Modifier.weight(1f).fillMaxWidth())
                    LiveEqStrip(s, vm, pick, Modifier.fillMaxWidth())
                }
                keys(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                spectrum(Modifier.weight(1f).fillMaxWidth())
                keys(Modifier.fillMaxWidth())
            }
        }
    }
}

/** The live reading and the keys of [LiveFull], packed to fit a phone turned sideways. */
@Composable
private fun LiveKeys(s: UiState, level: LevelInfo?, vm: TuneViewModel, onClose: (() -> Unit)?, modifier: Modifier) {
    val ready = s.hasPermission
    val canPlay = s.source == Source.PHONE || s.testName.isBlank()
    // it fits a phone sideways as it is; a large font setting scrolls it rather than cutting a key off
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // the reading: dBFS on the LCD with the peak beside it, the bar under it
        if (level != null) {
            val peak = if (s.peakHold) level.peakMaxDb else level.peakDb
            LcdWindow {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("%.1f".format(level.rmsDb), style = MaterialTheme.typography.displaySmall)
                    Text(" dBFS", Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.labelMedium, color = At.c.muted)
                    Box(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            (if (s.peakHold) "peak hold " else "peak ") + "%.1f".format(peak),
                            style = MaterialTheme.typography.labelMedium, fontFamily = AtFigures,
                        )
                        Text(
                            if (s.phoneMic) "no dB SPL with the phone mic" else "≈ %.0f dB SPL".format(level.splDb),
                            style = MaterialTheme.typography.labelSmall, color = At.c.muted,
                        )
                    }
                }
            }
            LevelBar(level, s.peakHold)
            // a held peak that touched clipping stays flagged until reset
            if (peak > -1.0) Text("peaks near clipping: lower the volume", style = MaterialTheme.typography.labelSmall, color = At.c.warn)
        } else {
            Text(if (s.rtaRunning) "listening" else "standby", style = MaterialTheme.typography.labelSmall, color = At.c.muted)
        }

        // what is played, and the key that starts and stops it all
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canPlay) Box(Modifier.weight(1f)) {
                DropSelect(
                    listOf("Pink noise", "Sweep", "Off", "Tone"), s.liveSignal.ordinal,
                    { vm.setLiveSignal(LiveSignal.entries[it]) }, icon = Ic.SPEAKER,
                )
            } else Box(Modifier.weight(1f))
            if (!s.rtaRunning) PrimaryButton("Start", { vm.startRta(s.rtaWithEq) }, enabled = ready && !s.busy, icon = Ic.PLAY)
            else PrimaryButton("Stop", vm::stopRta, icon = Ic.STOP)
        }

        // the tone's pitch: readout, steps and the slider, always in view
        if (canPlay && s.liveSignal == LiveSignal.TONE) {
            val semitone = 2.0.pow(1.0 / 12.0)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Tone.text(s.toneHz), Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontFamily = AtFigures)
                SecondaryButton("−", { vm.setToneHz(s.toneHz / semitone); vm.saveToneHz() })
                Box(Modifier.padding(start = 6.dp)) { SecondaryButton("+", { vm.setToneHz(s.toneHz * semitone); vm.saveToneHz() }) }
            }
            Slider(
                value = Tone.fractionOf(s.toneHz).toFloat(),
                onValueChange = { vm.setToneHz(Tone.freqAt(it.toDouble())) },
                onValueChangeFinished = { vm.saveToneHz() },
                colors = sliderColors(),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Hold peaks", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (s.peakHold) TextAction("Reset", vm::resetPeaks, icon = Ic.UNDO, opens = false)
            LatchKey(s.peakHold, vm::setPeakHold, "Hold peaks")
            if (onClose != null) TextAction("Close", onClose, icon = Ic.CLOSE)
        }
    }
}

/** The level bar: -80 dBFS (quieter than any room) to 0 (clipping), the held peak a tick on it. */
@Composable
private fun LevelBar(level: LevelInfo, hold: Boolean) {
    Box(Modifier.fillMaxWidth().height(8.dp).fieldFace(MaterialTheme.shapes.extraSmall)) {
        val frac = ((level.rmsDb + 80.0) / 80.0).toFloat().coerceIn(0f, 1f)
        Box(
            Modifier.fillMaxWidth(frac).height(8.dp).clip(MaterialTheme.shapes.extraSmall)
                .background(if (level.peakDb > -1.0) At.c.bad else At.c.signal),
        )
        if (hold) {
            val pf = ((level.peakMaxDb + 80.0) / 80.0).toFloat().coerceIn(0f, 1f)
            if (pf > 0f) Box(Modifier.fillMaxWidth(pf).height(8.dp), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(At.c.text))
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
        LevelBar(level, s.peakHold)
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
