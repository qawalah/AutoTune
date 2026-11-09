@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.autotune.ui

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

// ------------------------------------------------------------------ setup

/** The Settings tab: how much the app shows, the mic, defaults, the EQ, files, AI, diagnostics and the look. */
@Composable
fun SettingsTab(s: UiState, ai: dev.autotune.AiUi, vm: TuneViewModel, pick: Pickers, go: (Tab) -> Unit = {}) {
    run {
        ScreenTitle("Settings")
        Group("Show", divider = false, info = "Simple keeps each screen to the essentials and the main choices. Advanced adds the explanations, extra checks and every option. The switch at the top of each screen changes the same setting.") {
            DropSelect(
                listOf("Simple", "Advanced"), if (s.advanced) 1 else 0, { vm.setAdvanced(it == 1) }, label = "How much to show",
                details = listOf("Less text, the main choices (default)", "Every option and explanation"),
            )
        }
        Group("Microphone", divider = false, info = "The calibration file removes the microphone's own frequency response from the measurement. For a UMIK-1 use the file for your serial number from miniDSP (a .txt file). Without it, results include the mic's own response (small for a UMIK-1, but not zero).") {
            StatusLine(true, if (s.phoneMic) "Phone microphone (plug in a USB mic for better accuracy)" else "USB microphone: ${s.micName}")
            StatusLine(s.hasPermission, if (s.hasPermission) "Microphone access allowed" else "Microphone access needed")
            StatusLine(s.calibrationName != null, if (s.calibrationName != null) "${s.calibrationName} (${s.calibrationInfo})" else "No calibration loaded")
            Actions {
                if (!s.hasPermission) SecondaryButton("Allow microphone", pick.permission, icon = Ic.MIC)
                SecondaryButton(if (s.calibrationName == null) "Import calibration" else "Replace calibration", pick.calibration, icon = Ic.FILE)
                if (s.calibrationName != null) TextAction("Remove", vm::removeCalibration, danger = true, icon = Ic.TRASH)
            }
        }

        Group("EQ files", info = "Save or share an EQ as files for other equipment: ParametricEQ.txt for Poweramp and Equalizer APO, GraphicEQ.txt for Wavelet, and the test's report and measurements. In Advanced you can also choose the folder Save uses; Share always goes through the share sheet.") {
            Actions { TextAction("Export EQ files", { go(Tab.EXPORT) }, icon = Ic.EXPORT) }
            if (isAdvanced()) {
                StatusLine(true, "Saved to " + (s.exportFolder ?: "Downloads/AutoTune"))
                Actions {
                    SecondaryButton(if (s.exportFolder == null) "Choose a folder" else "Choose another folder", pick.exportFolder, icon = Ic.FILE)
                    if (s.exportFolder != null) TextAction("Use Downloads", { vm.setExportFolder(null) }, icon = Ic.UNDO)
                }
            }
        }

        if (isAdvanced()) Group("Defaults for new tests", info = "What the Set up step starts with. You can still change each one there, and the target and taste later on any result.") {
            val targets = dev.autotune.dsp.Target.ALL
            val tIdx = targets.indexOfFirst { it.name == s.defTarget }.let { if (it < 0) 0 else it }
            val spots = listOf(0, 1, 3, 5)
            val gates = listOf(0, 80, 200, 400, 600)
            fun set(t: String? = s.defTarget, b: Double = s.defBassDb, tr: Double = s.defTrebleDb, sp: Int = s.defSpots, g: Int = s.defGateMs) = vm.setDefaults(t, b, tr, sp, g)
            DropSelect(
                targets.map { it.name }, tIdx, { set(t = targets[it].name) }, label = "Target curve",
                icons = targets.map { kindIcon(dev.autotune.targetKind(it)) }, details = targets.map { targetDetail(it) },
            )
            DropSelect(BASS_STEPS.map(::db), BASS_STEPS.indexOf(s.defBassDb).let { if (it < 0) 1 else it }, { set(b = BASS_STEPS[it]) }, label = "Bass to taste", icon = Ic.BASS)
            DropSelect(TREBLE_STEPS.map(::db), TREBLE_STEPS.indexOf(s.defTrebleDb).let { if (it < 0) 3 else it }, { set(tr = TREBLE_STEPS[it]) }, label = "Treble to taste", icon = Ic.TREBLE)
            DropSelect(
                listOf("Automatic (3 in a car, 1 in a room)", "1 spot", "3 spots", "5 spots"), spots.indexOf(s.defSpots).coerceAtLeast(0),
                { set(sp = spots[it]) }, label = "Mic spots per reading", icon = Ic.MIC,
            )
            DropSelect(
                listOf("Automatic (80 ms in a car, 400 ms in a room)", "80 ms", "200 ms", "400 ms", "600 ms"), gates.indexOf(s.defGateMs).coerceAtLeast(0),
                { set(g = gates[it]) }, label = "Analysis window",
            )
        }

        if (isAdvanced()) Group(
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

        if (isAdvanced()) Group("AI assistant", info = "Claude (Anthropic) or Gemini (Google) reviews a test on request: what limits the result, what to check, what to do next, and optional EQ changes that the app holds to its own limits. It needs your own API key for the one you pick; each review costs a few cents on that key's account. ${aiPrivacy(ai.provider)}") {
            AiKeySetup(ai, vm)
        }

        if (isAdvanced()) Group("Diagnostics", info = "A record of every measurement (levels, the EQ in effect, how it was applied, warnings), every EQ change and every failure, plus the last recordings. Send it when something sounds wrong, so the cause can be found. It stays on this phone until you share it.") {
            SwitchRow("Keep the last 12 recordings", s.keepRecordings, vm::setKeepRecordings)
            Actions {
                SecondaryButton("Send diagnostics", vm::shareDiagnostics, icon = Ic.DIAG)
                TextAction("Clear", vm::clearDiagnostics, danger = true, icon = Ic.TRASH)
            }
        }

        Group("Appearance", info = "Keep the interface quiet and readable. Every look is after Braun: warm neutrals, graphite controls and one restrained accent.") {
            DropSelect(
                Look.entries.map { it.title }, Look.entries.indexOf(s.look), { vm.setLook(Look.entries[it]) }, label = "Look",
                details = Look.entries.map { it.detail }, icon = Ic.EQ,
            )
            // a strip of the look's colours as it is now: navigation, accent, fields, two measurement colours
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(At.c.shell, At.c.signal, At.c.panelHigh, runColor(1), runColor(2)).forEach { col ->
                    androidx.compose.foundation.layout.Box(Modifier.size(28.dp).clip(MaterialTheme.shapes.small).background(col))
                }
            }
            val accents = dev.autotune.Accent.entries
            DropSelect(
                accents.map { it.title }, accents.indexOf(s.accent), { vm.setAccent(accents[it]) }, label = "Accent", icon = Ic.POWER,
                details = accents.map { if (it == dev.autotune.Accent.LOOK) "The look's own colour" else null },
                info = "The accent marks what is live or what you act on: the Tune record, the needle, the pilot light and the EQ.",
            )
            DropSelect(ThemeMode.entries.map { it.title }, ThemeMode.entries.indexOf(s.themeMode), { vm.setThemeMode(ThemeMode.entries[it]) }, label = "Light or dark")
        }

        if (isAdvanced()) PaletteEditor(s.palette, vm, paletteInkNote(s.look, isNight(s.themeMode), s.palette))

        if (isAdvanced()) Group("Target curves", info = "A curve file is a text or CSV file with one “frequency, dB” pair per line, covering about 20 Hz to 20 kHz, for example a house curve you like. You can also tune a system until it sounds right and use “Keep this sound as a target” on the test's results.") {
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

        Group("About", info = "Measure, correct and re-measure car and home audio with a USB microphone. Nothing here is verified on every phone: the AutoTune EQ in particular depends on the phone and Android version, and Samsung's own sound settings can't be controlled by apps.") {
            Text("AutoTune ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall, fontFamily = AtFigures)
            Muted("Type set in Inter, licensed under the SIL Open Font License 1.1.")
        }
    }
}

/**
 * The palette: body, deck, ink, accent and hairline, each a drop-down of Braun's colours, the look's own, or a hex
 * colour typed in. Changes show at once; Reset palette goes back to the look's own colours.
 */
@Composable
private fun PaletteEditor(p: dev.autotune.Palette, vm: TuneViewModel, inkNote: String? = null) {
    var custom by androidx.compose.runtime.remember { mutableStateOf<dev.autotune.PaletteSlot?>(null) }
    Group(
        "Palette",
        info = "Your own colours over the look. Body is the page, Deck the plates that hold each section, Ink the text, " +
            "Accent what is live or acted on, Hairline the edges and rules. “As the look” keeps the look's own. " +
            "Text that would not read on your colours falls back to an ink that does; a dark ink at night brings the light decks with it, so it shows. " +
            "The accent set here wins over the Accent above.",
    ) {
        dev.autotune.PaletteSlot.entries.forEach { slot ->
            val choices = dev.autotune.paletteChoices(slot)
            val now = p[slot]
            val own = now != null && choices.none { it.argb == now }
            // the look's own, the presets, the typed colour when there is one, and a way to type one
            val names = listOf("As the look") + choices.map { it.name } + (if (own) listOf(dev.autotune.hexColour(now!!)) else emptyList()) + "Custom…"
            val details = listOf<String?>(null) + choices.map { dev.autotune.hexColour(it.argb) } + (if (own) listOf<String?>("your colour") else emptyList()) + "type a hex colour"
            val swatches = listOf<androidx.compose.ui.graphics.Color?>(null) + choices.map { androidx.compose.ui.graphics.Color(it.argb) } +
                (if (own) listOf(androidx.compose.ui.graphics.Color(now!!)) else emptyList()) + null
            val selected = when {
                now == null -> 0
                own -> names.size - 2
                else -> 1 + choices.indexOfFirst { it.argb == now }
            }
            DropSelect(
                names, selected, { i ->
                    when {
                        i == 0 -> vm.setPaletteColour(slot, null)
                        i == names.size - 1 -> custom = slot
                        i <= choices.size -> vm.setPaletteColour(slot, choices[i - 1].argb)
                    }
                },
                label = slot.title, details = details, swatches = swatches,
            )
            // a dark ink at night brings light decks with it; one that cannot read anywhere says so (a status, one line)
            if (slot == dev.autotune.PaletteSlot.INK && inkNote != null) Muted(inkNote)
        }
        Actions { TextAction("Reset palette", vm::resetPalette, enabled = !p.isDefault, icon = Ic.UNDO) }
    }
    custom?.let { slot -> HexColourDialog(slot.title, p[slot], { vm.setPaletteColour(slot, it); custom = null }, { custom = null }) }
}

/** Type a colour as #RGB or #RRGGBB; Use only lights up for a colour that parses. */
@Composable
private fun HexColourDialog(title: String, start: Long?, onUse: (Long) -> Unit, onDismiss: () -> Unit) {
    var text by androidx.compose.runtime.remember { mutableStateOf(start?.let { dev.autotune.hexColour(it) } ?: "#") }
    val parsed = dev.autotune.parseHexColour(text)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$title colour") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(9) }, singleLine = true, modifier = Modifier.weight(1f),
                    isError = parsed == null && text.length > 1,
                    supportingText = { Text(if (parsed == null) "#RGB or #RRGGBB" else dev.autotune.hexColour(parsed)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                Swatch(parsed?.let { androidx.compose.ui.graphics.Color(it) } ?: At.c.panelHigh, 40.dp)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { parsed?.let(onUse) }, enabled = parsed != null) { Text("Use") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
