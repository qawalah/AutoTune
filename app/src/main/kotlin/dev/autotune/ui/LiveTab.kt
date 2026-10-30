@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.autotune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.autotune.LevelInfo
import dev.autotune.Source
import dev.autotune.TuneViewModel
import dev.autotune.UiState
import dev.autotune.dsp.Rta
import dev.autotune.dsp.Target

/** Live RTA and sound level. Meant to be read at a glance while adjusting the EQ. */
@Composable
fun LiveTab(s: UiState, live: DoubleArray?, level: LevelInfo?, vm: TuneViewModel, pick: Pickers, go: (Tab) -> Unit) {
    val ready = s.hasPermission
    ScreenTitle("Live", "Real-time spectrum and level from the mic")

    if (!ready) {
        Panel {
            StatusLine(true, if (s.phoneMic) "Phone microphone (a USB mic is more accurate)" else "USB microphone: ${s.micName}")
            StatusLine(s.hasPermission, if (s.hasPermission) "Microphone access allowed" else "Microphone access needed")
            if (!s.hasPermission) SecondaryButton("Allow microphone", pick.permission, icon = Ic.MIC)
        }
    }

    if (!s.rtaRunning) PrimaryButton("Start", { vm.startRta(s.rtaWithEq) }, Modifier.fillMaxWidth(), enabled = ready && !s.busy, icon = Ic.PLAY)
    else SecondaryButton("Stop", vm::stopRta, Modifier.fillMaxWidth(), icon = Ic.STOP)

    // ---- level
    Panel {
        if (level != null) {
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
            Box(Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.extraSmall).background(At.c.panelHigh)) {
                val frac = ((level.rmsDb + 80.0) / 80.0).toFloat().coerceIn(0f, 1f)
                Box(
                    Modifier.fillMaxWidth(frac).height(8.dp).clip(MaterialTheme.shapes.extraSmall)
                        .background(if (level.peakDb > -1.0) At.c.bad else At.c.signal),
                )
            }
            Muted("Peak %.1f dBFS   Leq %.1f dBFS".format(level.peakDb, level.leqDb))
            if (level.peakDb > -1.0) Warning("Peaks are close to clipping. Lower the volume or move the mic away.")
            else if (level.rmsDb < -60.0) Warning("Very quiet. Raise the volume or move the mic closer.")
        } else {
            Text(if (s.rtaRunning) "Listening…" else "Press Start to read the level.", style = MaterialTheme.typography.bodyMedium, color = At.c.muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
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

    // ---- spectrum
    Panel {
        val target = Rta.targetBars(Target.ALL[s.targetIdx.coerceIn(0, Target.ALL.size - 1)])
        if (live != null && s.rtaRunning) RtaBars(live, target, height = 240.dp)
        else RtaBars(DoubleArray(target.size) { target[it] }, target, height = 240.dp, caption = false)
        if (!s.rtaRunning && s.status?.startsWith("RTA stopped") == true) Warning(s.status!!)
        Muted("Bars level with the white ticks mean the system matches the target.")
    }

    // ---- sound source
    Group("Sound") {
        if (s.systemEqOn) {
            Text("AutoTune EQ is on, so the bars show the sound with it.", style = MaterialTheme.typography.bodyMedium)
        }
        if (s.source == Source.PHONE || s.testName.isBlank()) {
            Text("Play from the phone", style = MaterialTheme.typography.labelLarge)
            DropSelect(
                listOf("Pink noise", "Sweep", "Off"), s.liveSignal.ordinal,
                { vm.setLiveSignal(dev.autotune.LiveSignal.entries[it]) },
            )
            Muted(if (s.rtaRunning) "Changes the sound right away." else "Starts when you press Start. The sweep repeats every 12 seconds.")
            if (s.exportChain?.filters?.isNotEmpty() == true && !s.systemEqOn) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Play the noise through my EQ", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = s.rtaWithEq, onCheckedChange = { if (s.rtaRunning) vm.setRtaEq(it) else vm.startRta(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = At.c.signal, checkedThumbColor = At.c.onSignal),
                    )
                }
            }
        } else {
            Muted("Keep sound playing on the other device. Our pink noise file works well.")
        }
        SecondaryButton("Open EQ", { go(Tab.EQ) }, icon = Ic.EQ)
    }
}
