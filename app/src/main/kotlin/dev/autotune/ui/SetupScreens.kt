@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.autotune.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.autotune.BuildConfig
import dev.autotune.Source
import dev.autotune.ThemeMode
import dev.autotune.TuneViewModel
import dev.autotune.UiState

@Composable
private fun OverlayFrame(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(Ic.CLOSE, "Close", onClose)
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp))
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}

// ------------------------------------------------------------------ setup

@Composable
fun SetupScreen(s: UiState, ai: dev.autotune.AiUi, vm: TuneViewModel, pick: Pickers, onClose: () -> Unit) {
    OverlayFrame("Setup", onClose) {
        Group("Microphone", divider = false, info = "The calibration file removes the microphone's own frequency response from the measurement. For a UMIK-1 use the file for your serial number from miniDSP (a .txt file). Without it, results include the mic's own response (small for a UMIK-1, but not zero).") {
            StatusLine(true, if (s.phoneMic) "Phone microphone (plug in a USB mic for better accuracy)" else "USB microphone: ${s.micName}")
            StatusLine(s.hasPermission, if (s.hasPermission) "Microphone access allowed" else "Microphone access needed")
            StatusLine(s.calibrationName != null, if (s.calibrationName != null) "${s.calibrationName} (${s.calibrationInfo})" else "No calibration loaded")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!s.hasPermission) SecondaryButton("Allow microphone", pick.permission, icon = Ic.MIC)
                SecondaryButton(if (s.calibrationName == null) "Import calibration" else "Replace calibration", pick.calibration, icon = Ic.FILE)
                if (s.calibrationName != null) TextAction("Remove", vm::removeCalibration, danger = true, icon = Ic.TRASH)
            }
        }

        Group("Defaults for new tests", info = "What the Set up step starts with. You can still change each one there, and the target and taste later on any result.") {
            val targets = dev.autotune.dsp.Target.ALL
            val tIdx = targets.indexOfFirst { it.name == s.defTarget }.let { if (it < 0) 0 else it }
            val spots = listOf(0, 1, 3, 5)
            val gates = listOf(0, 80, 200, 400, 600)
            fun set(t: String? = s.defTarget, b: Double = s.defBassDb, tr: Double = s.defTrebleDb, sp: Int = s.defSpots, g: Int = s.defGateMs) = vm.setDefaults(t, b, tr, sp, g)
            DropSelect(
                targets.map { it.name }, tIdx, { set(t = targets[it].name) }, label = "Target curve",
                icons = targets.map { kindIcon(dev.autotune.targetKind(it)) }, details = targets.map { targetDetail(it) },
            )
            DropSelect(BASS_STEPS.map(::db), BASS_STEPS.indexOf(s.defBassDb).let { if (it < 0) 1 else it }, { set(b = BASS_STEPS[it]) }, label = "Bass to taste", icon = Ic.SPEAKER)
            DropSelect(TREBLE_STEPS.map(::db), TREBLE_STEPS.indexOf(s.defTrebleDb).let { if (it < 0) 3 else it }, { set(tr = TREBLE_STEPS[it]) }, label = "Treble to taste", icon = Ic.CURVE)
            DropSelect(
                listOf("Automatic (3 in a car, 1 in a room)", "1 spot", "3 spots", "5 spots"), spots.indexOf(s.defSpots).coerceAtLeast(0),
                { set(sp = spots[it]) }, label = "Mic spots per reading", icon = Ic.MIC,
            )
            DropSelect(
                listOf("Automatic (80 ms in a car, 400 ms in a room)", "80 ms", "200 ms", "400 ms", "600 ms"), gates.indexOf(s.defGateMs).coerceAtLeast(0),
                { set(g = gates[it]) }, label = "Analysis window",
            )
        }

        Group(
            "AutoTune EQ", info = "What Android allows, and what AutoTune does about it:\n\n" +
                "• Range: about -30 to +15 dB per band. Not a real limit.\n" +
                "• Bands: Android's effect works on fixed bands. 31 bands (1/3 octave) is the default; 61 bands (1/6 octave) follows narrow cuts more closely but asks more of the phone. The suggestion is fitted to what the bands can do.\n" +
                "• Headroom: digital audio cannot go above full scale, so boosts need a preamp. Choose how on the EQ tab.\n" +
                "• Reach: the EQ works on what this phone plays. Android Auto (wireless or cable), some apps with their own audio path and some car systems can take the sound past it; the EQ check tells you.\n" +
                "• Samsung: apps cannot switch Samsung's own Equalizer, Adapt Sound or Dolby Atmos. Turn those off so they do not stack.",
        ) {
            DropSelect(
                listOf("31 bands (1/3 octave)", "61 bands (1/6 octave)"), if (s.systemEqFine) 1 else 0, { vm.setSystemEqFine(it == 1) },
                label = "Band resolution", icon = Ic.EQ,
                details = listOf("Tested; light on the phone", "Finer narrow cuts; more work for the phone. Measure again after switching"),
            )
        }

        Group("Saved files", info = "Where Save puts the EQ files, the report and the measurements. Share always goes through the share sheet instead.") {
            StatusLine(true, "Saved to " + (s.exportFolder ?: "Downloads/AutoTune"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton(if (s.exportFolder == null) "Choose a folder" else "Choose another folder", pick.exportFolder, icon = Ic.FILE)
                if (s.exportFolder != null) TextAction("Use Downloads", { vm.setExportFolder(null) }, icon = Ic.UNDO)
            }
        }

        Group("AI assistant", info = "Claude (Anthropic) or Gemini (Google) reviews a test on request: what limits the result, what to check, what to do next, and optional EQ changes that the app holds to its own limits. It needs your own API key for the one you pick; each review costs a few cents on that key's account. ${aiPrivacy(ai.provider)}") {
            AiKeySetup(ai, vm)
        }

        Group("Diagnostics", info = "A record of every measurement (levels, the EQ in effect, how it was applied, warnings), every EQ change and every failure, plus the last recordings. Send it when something sounds wrong, so the cause can be found. It stays on this phone until you share it.") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Keep the last 12 recordings", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = s.keepRecordings, onCheckedChange = vm::setKeepRecordings,
                    colors = SwitchDefaults.colors(checkedTrackColor = At.c.signal, checkedThumbColor = At.c.onSignal),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton("Send diagnostics", vm::shareDiagnostics, icon = Ic.DIAG)
                TextAction("Clear", vm::clearDiagnostics, danger = true, icon = Ic.TRASH)
            }
        }

        Group("Appearance") {
            DropSelect(
                Look.entries.map { it.title }, Look.entries.indexOf(s.look), { vm.setLook(Look.entries[it]) }, label = "Look",
                details = Look.entries.map { it.detail }, icon = Ic.EQ,
            )
            // a strip of the look's colours as it is now: accent, panel, two measurement colours
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(At.c.signal, At.c.panelHigh, runColor(1), runColor(2), At.c.good).forEach { col ->
                    androidx.compose.foundation.layout.Box(Modifier.size(28.dp).clip(MaterialTheme.shapes.small).background(col))
                }
            }
            DropSelect(ThemeMode.entries.map { it.title }, ThemeMode.entries.indexOf(s.themeMode), { vm.setThemeMode(ThemeMode.entries[it]) }, label = "Light or dark")
            if (Build.VERSION.SDK_INT >= 31) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Use my wallpaper colours", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = s.dynamicColor, onCheckedChange = vm::setDynamicColor,
                        colors = SwitchDefaults.colors(checkedTrackColor = At.c.signal, checkedThumbColor = At.c.onSignal),
                    )
                }
            }
        }

        Group("Target curves", info = "A curve file is a text or CSV file with one “frequency, dB” pair per line, covering about 20 Hz to 20 kHz, for example a house curve you like. You can also tune a system until it sounds right and use “Keep this sound as a target” on the test's results.") {
            SecondaryButton("Import a curve file", pick.curve, icon = Ic.FILE)
            if (s.customTargets.isEmpty()) Muted("No saved curves yet.")
            s.customTargets.forEach { n ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(n, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextAction("Delete", { vm.deleteTarget(n) }, danger = true, icon = Ic.TRASH)
                }
            }
            s.status?.let { Muted(it) }
        }

        Group("About") {
            Text("AutoTune ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall, fontFamily = PlexMono)
            Muted("Measure, correct and re-measure car and home audio with a USB microphone. Nothing here is verified on every phone: the AutoTune EQ in particular depends on the phone and Android version, and Samsung's own sound settings can't be controlled by apps.")
            Muted("Type set in IBM Plex, licensed under the SIL Open Font License 1.1.")
        }
    }
}
