@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.autotune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.autotune.Source
import dev.autotune.TuneViewModel
import dev.autotune.UiState
import dev.autotune.graphicGains
import dev.autotune.ApplyWay
import dev.autotune.dsp.Describe
import dev.autotune.dsp.EqChain
import dev.autotune.dsp.EqFilter
import dev.autotune.dsp.Export
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/** The EQ page: on/off, the graph, classic sliders, grouped profiles, and an Advanced section for everything else. */
@Composable
fun EqTab(s: UiState, vm: TuneViewModel, go: (Tab) -> Unit, pick: Pickers) {
    val mine = s.editFilters != null
    val custom = mine || s.sliderLayer.isNotEmpty()
    var expanded by rememberSaveable { mutableIntStateOf(-1) }
    var confirmSuggested by remember { mutableStateOf(false) }
    var saveDialog by remember { mutableStateOf(false) }
    val hasTest = s.testName.isNotBlank()
    val chain = s.exportChain
    val active = s.profiles.firstOrNull { it.id == s.activeProfileId }
    val bandCount = chain?.filters?.size ?: 0
    var bandSet by rememberSaveable { mutableIntStateOf(1) }
    val centres = GRAPHIC_SETS[bandSet].first
    val qOf = GRAPHIC_SETS[bandSet].second

    val testKind = s.tests.firstOrNull { it.id == s.currentTestId }?.kind
    var locked by rememberSaveable { mutableStateOf(true) }
    ScreenTitle("EQ", if (hasTest) s.testName else null, contextIcon = testKind?.let { kindIcon(it) })

    // ---- on / off
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AutoTune EQ", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (s.systemEqOn) "On · playing ${s.eqSource.ifBlank { "flat" }}" else "Off · nothing is applied",
                    style = MaterialTheme.typography.bodyMedium, color = if (s.systemEqOn) At.c.signal else At.c.muted, maxLines = 2,
                )
            }
            InfoButton(
                "What the AutoTune EQ can and can't do",
                "AutoTune equalises the audio this phone plays, which also feeds a car or home theatre over Bluetooth, aux or USB.\n\n" +
                    "Limits:\n• It can't control Samsung's own sound settings (Adapt Sound, Dolby Atmos, Equalizer). Switch those off or they stack with this.\n" +
                    "• Whether it reaches every app depends on the phone and Android version. If you hear no change, this phone blocks it.\n" +
                    "• It only affects what this phone plays, not another device.\n\n" +
                    "Measure the stock reading with it off. A notification shows while it is on.",
            )
            PowerToggle(s.systemEqOn, { if (it) pick.notifications(); vm.setSystemEq(it) }, "AutoTune EQ")
        }
        GroupedPick("EQ currently in use", s.eqChoices, s.eqPick, vm::selectEq, if (bandCount > 0) "Your own bands" else "No EQ yet: tune, or use the sliders below")
        Muted("“Suggested EQ” entries are AutoTune's proposals from tuning; whatever is picked here is what plays. AutoTune EQ affects only this phone's audio, and how well it works depends on the phone.")
        if (s.sliderLayer.isNotEmpty()) Muted("Plus your slider changes.")
        s.systemEqMessage?.let { Muted(it) }
        FlavourAndPreamp(s, vm)
    }

    // ---- does the EQ reach the speakers?
    if (bandCount > 0) {
        s.eqCheck?.let { EqCheckCard(it, vm) } ?: Row(verticalAlignment = Alignment.CenterVertically) {
            TextAction("Check the EQ reaches the speakers", vm::eqCheck, enabled = !s.busy && s.hasPermission, icon = Ic.CHECK)
            InfoButton(
                "EQ check",
                "Plays the test sweep twice through this phone: once with AutoTune EQ off, once with the EQ in use on, and compares the change with what the EQ should do. It tells you whether the EQ really reaches the speakers. Android Auto (wireless or cable) and some car systems take the sound past the phone's own EQ. AutoTune EQ is left the way it was.",
            )
        }
    }

    // ---- graph
    s.eqChart?.let { chart ->
        Panel {
            ResponseChart(chart, height = 200.dp, showTarget = s.showTargetEq, onToggleTarget = { vm.setShowTarget(false, !s.showTargetEq) })
            if (s.runs.isNotEmpty()) Muted("The dashed white line is a prediction from the last measurement plus this EQ. Measure to confirm.")
        }
    }

    // ---- equaliser: classic sliders
    Group(
        "Equaliser",
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconAction(if (locked) Ic.LOCK else Ic.UNLOCK, if (locked) "Unlock the sliders" else "Lock the sliders") { locked = !locked }
                TextAction("Flat", { vm.editClear() }, enabled = bandCount > 0 && !locked, icon = Ic.UNDO)
                TextAction("Save", { saveDialog = true }, enabled = bandCount > 0, icon = Ic.SAVE)
            }
        },
    ) {
        DropSelect(listOf("5 bands", "10 bands", "15 bands"), bandSet, { bandSet = it })
        GraphicSliders(s, vm, centres, qOf, locked)
        val note = when {
            locked -> "Locked against accidental changes. Tap the lock to adjust."
            s.sliderLayer.isNotEmpty() -> "Your slider changes sit on top of the EQ in use. Each slider shows what the EQ really does at that frequency."
            bandCount > 0 -> "A slider adds to the EQ in use; its own bands stay as they are."
            else -> "Flat. Drag a slider, or tune to get a suggestion."
        }
        note?.let { Muted(it) }
        chain?.let { c -> if (c.filters.isNotEmpty()) Muted("Preamp %.1f dB keeps the EQ from clipping.".format(c.preampDb)) }
        s.exportWarnings.forEach { Warning(it) }
        // big boosts from the sliders are easy to make by accident and AutoTune's own EQ never makes them
        s.sliderLayer.maxByOrNull { it.gainDb }?.takeIf { it.gainDb > 6.0 }?.let { b ->
            Warning(
                "Your slider changes boost %+.0f dB at %s. AutoTune's own EQ never boosts more than 3 dB; boosts this big often distort. Remove them below if they were not meant."
                    .format(b.gainDb, if (b.freqHz >= 1000) "%.1f kHz".format(b.freqHz / 1000) else "%.0f Hz".format(b.freqHz)),
            )
        }
        if (s.sliderLayer.isNotEmpty()) TextAction("Remove my slider changes", vm::resetSliders, icon = Ic.UNDO)
    }

    // ---- the one primary action: verify
    if (hasTest && s.runs.isNotEmpty()) {
        // follow the way this test applies its EQ: a car tuned on the head unit must be verified on the head unit
        val onEquipment = !s.systemEqOn && (s.source != Source.PHONE || (s.applyWay == ApplyWay.EQUIPMENT && s.runs.size > 1))
        PinAction("Measure this EQ", {
            when {
                s.systemEqOn -> vm.applyWithAutoTuneEq()
                onEquipment -> vm.appliedOnEquipment()
                else -> vm.tryInApp()
            }
            vm.tuneGo(dev.autotune.TuneStep.VERIFY)
            go(Tab.MEASURE)
        }, enabled = bandCount > 0, icon = Ic.MEASURE)
        Muted(
            when {
                s.systemEqOn -> "AutoTune EQ is on, so the next run is measured with it."
                onEquipment -> "Set these bands on your equipment first, then measure."
                else -> "The next run plays the sweep through this EQ inside the app, without changing your equipment."
            },
        )
        if (!s.systemEqOn && s.source == Source.PHONE) {
            if (onEquipment) TextAction("Try it inside the app instead", { vm.tryInApp(); vm.tuneGo(dev.autotune.TuneStep.VERIFY); go(Tab.MEASURE) }, enabled = bandCount > 0, icon = Ic.PLAY)
            else TextAction("I set it on my equipment, measure", { vm.appliedOnEquipment(); vm.tuneGo(dev.autotune.TuneStep.VERIFY); go(Tab.MEASURE) }, enabled = bandCount > 0, icon = Ic.EDIT)
        }
    }

    // ---- saved EQs: picked above, renamed or deleted here
    if (s.profiles.isNotEmpty()) {
        Fold("Saved EQs", summary = "${s.profiles.size} saved · rename or delete") {
            Muted("Pick which EQ is in use at the top of this page. Each test also keeps the EQ of every run.")
            s.profiles.sortedWith(compareBy<dev.autotune.EqProfile> { it.test.ifBlank { "~" } }.thenByDescending { it.created }).forEach { p ->
                ProfileRow(p, active = p.id == s.activeProfileId, edited = false, vm = vm)
            }
        }
    }

    // ---- everything else
    Fold("Advanced", summary = "Exact bands, listen, measure, automatic switching") {
        Fold("Exact bands", summary = if (bandCount == 0) "None yet" else "$bandCount bands" + if (mine) " · your own" else " · from tuning") {
            if (!mine) {
                val sug = s.suggestedChain
                if (sug == null || sug.filters.isEmpty()) {
                    Text(
                        if (s.runs.isEmpty()) "No suggestion yet. Measure a stock reading, or move a slider above."
                        else "The system is already close to the target. No EQ needed.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    if (s.sliderLayer.isNotEmpty()) Text("Tuned bands", style = MaterialTheme.typography.titleSmall)
                    BandTable(filterLines(sug, s.previousChain), sug.preampDb, compared = s.previousChain != null)
                    if (s.sliderLayer.isNotEmpty()) {
                        Text("Your slider changes (added on top)", style = MaterialTheme.typography.titleSmall)
                        s.sliderLayer.forEach { f ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(hzText(f.freqHz) + " Hz\u00B7Q" + "%.1f".format(f.q), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(signed(f.gainDb) + " dB", style = MaterialTheme.typography.bodyMedium, fontFamily = PlexMono)
                            }
                        }
                    }
                    TextAction("Edit these exactly", vm::editStart, icon = Ic.EDIT)
                }
            } else {
                val list = s.editFilters.orEmpty()
                if (list.isEmpty()) Text("No bands yet. Add one to start.", style = MaterialTheme.typography.bodyMedium)
                list.forEachIndexed { i, f ->
                    BandRow(i, f, open = expanded == i, onToggle = {
                        if (expanded == i) { expanded = -1; vm.editSort() } else { if (expanded >= 0) vm.editSort(); expanded = if (expanded >= 0) -1 else i }
                    }, vm = vm, onRemoved = { expanded = -1 })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Add band", { expanded = -1; vm.editAdd() }, icon = Ic.PLUS)
                    if (list.size > 1) SecondaryButton("Sort", { expanded = -1; vm.editSort() }, icon = Ic.TESTS)
                }
            }
            // lossy on purpose, so it is an explicit action with the difference reported
            Text("Convert for a head unit", style = MaterialTheme.typography.titleSmall)
            Muted("Head units and graphic EQs only have fixed bands. This replaces the tuned bands and sliders with peaking bands at those centres. It is lossy, and the difference is reported.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GRAPHIC_SETS.forEach { (cs, q) ->
                    SecondaryButton("Convert to ${cs.size} bands", { expanded = -1; vm.convertToBands(cs, q) }, enabled = bandCount > 0, icon = Ic.EQ)
                }
            }
            s.convertMessage?.let { Muted(it) }
        }

        Fold("Try it", summary = "Listen with and without, measure, export") {
            Text("Listen: ${s.clipName}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (s.playing && s.playingWithEq) PrimaryButton("With EQ", { vm.playClip(true) }, icon = Ic.PLAY) else SecondaryButton("With EQ", { vm.playClip(true) }, enabled = !s.busy, icon = Ic.PLAY)
                if (s.playing && !s.playingWithEq) PrimaryButton("Without", { vm.playClip(false) }, icon = Ic.PLAY) else SecondaryButton("Without", { vm.playClip(false) }, enabled = !s.busy, icon = Ic.PLAY)
                if (s.playing) TextAction("Stop", vm::stopClip, icon = Ic.STOP)
                TextAction("Other clip", pick.clip, icon = Ic.FILE)
            }
            SecondaryButton("Export as files", { go(Tab.EXPORT) }, enabled = bandCount > 0, icon = Ic.EXPORT)
        }

        Fold(
            "Automatic switching",
            summary = if (s.automationOn && s.rules.isNotEmpty()) "On · ${s.rules.size} ${if (s.rules.size == 1) "device" else "devices"}" else "Off",
        ) { AutomationSection(s, vm, pick) }
    }

    if (saveDialog) SaveProfileDialog(
        suggested = if (!mine && s.runs.isNotEmpty()) "${s.testName} EQ" else "",
        onDismiss = { saveDialog = false },
        onSave = { saveDialog = false; vm.saveProfile(it) },
    )

    if (confirmSuggested) {
        AlertDialog(
            onDismissRequest = { confirmSuggested = false },
            title = { Text("Go back to the tuning EQ?") },
            text = { Text("Your own bands are removed. The tuning EQ is calculated from the latest results. Save your bands as a profile first if you want to keep them.") },
            confirmButton = { TextButton(onClick = { confirmSuggested = false; expanded = -1; vm.editReset() }) { Text("Use tuning EQ", color = At.c.bad) } },
            dismissButton = { TextButton(onClick = { confirmSuggested = false }) { Text("Keep mine") } },
        )
    }
}

@Composable
private fun ProfileRow(p: dev.autotune.EqProfile, active: Boolean, edited: Boolean, vm: TuneViewModel) {
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(MaterialTheme.shapes.small)
            .background(if (active) At.c.panelHigh else At.c.panel).padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(10.dp).clip(CircleShape).then(
                if (active) Modifier.background(At.c.signal) else Modifier.background(Color.Transparent).border(1.5.dp, At.c.muted, CircleShape),
            ),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Muted(p.origin + (if (edited) " · changed since saved" else ""))
        }
        Box {
            IconAction(Ic.MORE, "More for ${p.name}") { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (edited) DropdownMenuItem(text = { Text("Update with current EQ") }, onClick = { menu = false; vm.updateProfile(p.id) })
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; rename = true })
                DropdownMenuItem(text = { Text("Delete", color = At.c.bad) }, onClick = { menu = false; delete = true })
            }
        }
    }
    if (rename) SaveProfileDialog(title = "Rename profile", suggested = p.name, onDismiss = { rename = false }, onSave = { rename = false; vm.renameProfile(p.id, it) })
    if (delete) AlertDialog(
        onDismissRequest = { delete = false },
        title = { Text("Delete “${p.name}”?") },
        text = { Text("Automatic switching that uses it is removed too.") },
        confirmButton = { TextButton(onClick = { delete = false; vm.deleteProfile(p.id) }) { Text("Delete", color = At.c.bad) } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text("Keep") } },
    )
}

@Composable
private fun SaveProfileDialog(suggested: String, onDismiss: () -> Unit, onSave: (String) -> Unit, title: String = "Save as a profile") {
    var name by remember { mutableStateOf(suggested) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("Save", color = At.c.signal) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AutomationSection(s: UiState, vm: TuneViewModel, pick: Pickers) {
    var dev by rememberSaveable { mutableStateOf<String?>(null) }
    var prof by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.refreshBluetooth() }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Switch profile when a Bluetooth device connects", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = s.automationOn,
            onCheckedChange = { on ->
                if (on) { pick.notifications(); if (!s.btPermission) pick.bluetooth() }
                vm.setAutomation(on)
            },
            colors = SwitchDefaults.colors(checkedTrackColor = At.c.signal, checkedThumbColor = At.c.onSignal),
        )
    }
    if (s.profiles.isEmpty()) Muted("Save a profile first, then choose when it should switch on.")

    s.rules.forEach { r ->
        val name = s.profiles.firstOrNull { it.id == r.profileId }?.name ?: "(deleted profile)"
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.deviceName, style = MaterialTheme.typography.titleSmall)
                Muted("…" + r.address.takeLast(5) + " connects → $name")
            }
            TextAction("Remove", { vm.removeRule(r.address) }, danger = true, icon = Ic.TRASH)
        }
    }

    if (s.profiles.isNotEmpty()) {
        if (!s.btPermission) {
            SecondaryButton("Allow Bluetooth to list your devices", pick.bluetooth, icon = Ic.BLUETOOTH)
        } else if (s.btDevices.isEmpty()) {
            Muted("No paired Bluetooth devices found. Pair the car or speaker in Android settings first.")
        } else {
            val sameName = s.btDevices.groupingBy { it.name.lowercase() }.eachCount()
            fun detail(d: dev.autotune.BtDevice): String {
                val ruled = s.rules.firstOrNull { it.address.equals(d.address, true) }?.let { r -> s.profiles.firstOrNull { it.id == r.profileId }?.name }
                return listOfNotNull(
                    d.kind, "…" + d.address.takeLast(5),
                    if (d.lastSeen > 0) "last seen " + ago(d.lastSeen) else null,
                    ruled?.let { "uses $it" },
                ).joinToString(" · ")
            }
            val chosen = s.btDevices.firstOrNull { it.address == dev }
            Text("Add a device", style = MaterialTheme.typography.titleSmall)
            PickField(
                "When this connects", chosen?.let { it.name + if ((sameName[it.name.lowercase()] ?: 1) > 1) "  (…" + it.address.takeLast(5) + ")" else "" },
                "Choose a Bluetooth device", s.btDevices, { it.name }, { detail(it) }, { dev = it.address },
            )
            PickField(
                "Switch to this profile", s.profiles.firstOrNull { it.id == prof }?.name, "Choose a profile",
                s.profiles, { it.name }, { it.origin }, { prof = it.id },
            )
            SecondaryButton("Add", {
                val d = s.btDevices.firstOrNull { it.address == dev }
                val pid = prof
                if (d != null && pid != null) { vm.setRule(d.address, d.name, pid); dev = null; prof = null }
            }, Modifier.fillMaxWidth(), enabled = dev != null && prof != null, icon = Ic.PLUS)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Turn the EQ off when the device disconnects", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = s.offOnDisconnect, onCheckedChange = vm::setOffOnDisconnect,
                colors = SwitchDefaults.colors(checkedTrackColor = At.c.signal, checkedThumbColor = At.c.onSignal),
            )
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Samsung Modes and Routines", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        InfoButton(
            "Using AutoTune from Samsung Routines",
            "Routines can open an app, but it cannot hand the app a setting such as which profile to use. So the most dependable way is the Bluetooth switching above: AutoTune watches for the device itself. " +
                "Samsung may stop background apps; in Settings, Battery, set AutoTune to Unrestricted so it keeps watching.\n\n" +
                "To start a profile from a routine anyway:\n" +
                "• Long-press the AutoTune icon: there is a shortcut for each profile (up to four) and one for EQ off. Where your One UI lets a routine's Open app action pick an app shortcut, choose one of those.\n" +
                "• Tasker, MacroDroid and similar apps can send the intent dev.autotune.action.APPLY_PROFILE with the extra profile set to the profile's name (or EQ_OFF to switch it off), or open the link autotune://profile/NAME.",
        )
    }
    Muted("Routines can start a profile through AutoTune's shortcuts. Tap the i for how.")
}

@Composable
private fun BandRow(i: Int, f: EqFilter, open: Boolean, onToggle: () -> Unit, vm: TuneViewModel, onRemoved: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(At.c.panel)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onToggle).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("%.0f Hz".format(f.freqHz), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontFamily = PlexMono)
            Text("%+.1f dB".format(f.gainDb), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontFamily = PlexMono, color = if (f.gainDb < 0) At.c.text else At.c.signal)
            Text("Q %.1f".format(f.q), Modifier.weight(0.8f), style = MaterialTheme.typography.titleSmall, fontFamily = PlexMono, color = At.c.muted)
            AtIcon(if (open) Ic.CHEVRON_DOWN else Ic.CHEVRON, At.c.muted, size = 20.dp)
        }
        if (open) {
            Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 8.dp)) {
                SliderRow("Frequency", "%.0f Hz".format(f.freqHz)) {
                    Slider(
                        value = ln(f.freqHz).toFloat(), valueRange = ln(20f)..ln(20000f),
                        onValueChange = { vm.editSet(i, f.copy(freqHz = exp(it.toDouble()))) }, colors = sliderColors(),
                    )
                }
                SliderRow("Gain", "%+.1f dB".format(f.gainDb)) {
                    Slider(
                        value = f.gainDb.toFloat(), valueRange = -15f..15f,
                        onValueChange = { vm.editSet(i, f.copy(gainDb = Math.round(it * 2.0) / 2.0)) }, colors = sliderColors(),
                    )
                }
                SliderRow("Narrowness (Q)", "%.1f".format(f.q)) {
                    Slider(
                        value = ln(f.q).toFloat(), valueRange = ln(0.3f)..ln(12f),
                        onValueChange = { vm.editSet(i, f.copy(q = exp(it.toDouble()))) }, colors = sliderColors(),
                    )
                }
                TextAction("Remove band", { vm.editRemove(i); onRemoved() }, danger = true, icon = Ic.TRASH)
            }
        }
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = At.c.signal, activeTrackColor = At.c.signal, inactiveTrackColor = At.c.line,
)

@Composable
private fun SliderRow(label: String, value: String, slider: @Composable () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = At.c.muted)
            Text(value, style = MaterialTheme.typography.labelMedium, fontFamily = PlexMono)
        }
        slider()
    }
}

// ------------------------------------------------------------------ lists shared with Export

/** One row of an EQ listing. [was] is the gain it had in the EQ set before (null = no earlier EQ to compare with). */
class BandLine(val label: String, val gainDb: Double, val was: Double?, val note: String? = null) {
    val changed: Boolean get() = was != null && (note != null || kotlin.math.abs(gainDb - was) >= 0.25)
}

private fun signed(v: Double): String = ("%+.1f".format(v)).replace('-', '\u2212')
private fun hzText(f: Double): String = if (f < 1000) "%.1f".format(f).removeSuffix(".0") else "%.0f".format(f)

/** Every band on its own row: frequency, a bar that runs left for cuts and right for boosts, the gain, and what changed. */
@Composable
fun BandTable(lines: List<BandLine>, preampDb: Double, compared: Boolean) {
    var onlyChanged by rememberSaveable { mutableStateOf(false) }
    val shown = if (onlyChanged) lines.filter { it.changed } else lines
    val changedCount = lines.count { it.changed }

    // preamp first: it is the one number that must be set before the bands
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(At.c.panelHigh).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Preamp", style = MaterialTheme.typography.titleSmall)
            Muted("Set this first")
        }
        Text(signed(preampDb) + " dB", style = MaterialTheme.typography.titleMedium, fontFamily = PlexMono, color = if (preampDb < -0.05) At.c.bad else At.c.text)
    }

    if (compared) {
        DropSelect(
            listOf("All ${lines.size}", "Changed $changedCount"), if (onlyChanged) 1 else 0,
            { onlyChanged = it == 1 },
        )
        Muted("Rows with a tint and “was” changed since your last run.")
    }

    Column {
        shown.forEachIndexed { i, l ->
            if (i > 0) androidx.compose.material3.HorizontalDivider(color = At.c.line.copy(alpha = 0.6f))
            BandLineRow(l, compared)
        }
        if (shown.isEmpty()) Muted("Nothing changed since the last run.")
    }
}

@Composable
private fun BandLineRow(l: BandLine, compared: Boolean) {
    val g = l.gainDb
    val flat = kotlin.math.abs(g) < 0.05
    val tone = when { flat -> At.c.muted; g < 0 -> At.c.bad; else -> At.c.signal }
    val dim = compared && !l.changed && !flat
    val axis = At.c.line
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp)
            .background(if (l.changed) At.c.panelHigh else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(76.dp)) {
            Text(
                l.label.substringBefore('\u00B7'), style = MaterialTheme.typography.titleSmall, fontFamily = PlexMono,
                color = if (flat || dim) At.c.muted else At.c.text,
            )
            if ('\u00B7' in l.label) Text("Q " + l.label.substringAfter("\u00B7Q"), style = MaterialTheme.typography.labelSmall, fontFamily = PlexMono, color = At.c.muted)
        }
        androidx.compose.foundation.Canvas(Modifier.weight(1f).height(18.dp).padding(horizontal = 4.dp)) {
            val mid = size.width / 2f
            drawLine(axis, androidx.compose.ui.geometry.Offset(mid, 0f), androidx.compose.ui.geometry.Offset(mid, size.height), 2f)
            val len = (kotlin.math.abs(g).coerceAtMost(12.0) / 12.0 * mid).toFloat()
            if (len > 0.5f) {
                drawRect(
                    tone.copy(alpha = if (dim) 0.45f else 1f),
                    topLeft = androidx.compose.ui.geometry.Offset(if (g < 0) mid - len else mid, size.height * 0.1f),
                    size = androidx.compose.ui.geometry.Size(len, size.height * 0.8f),
                )
            }
        }
        Column(Modifier.width(66.dp), horizontalAlignment = Alignment.End) {
            Text(
                signed(g), style = MaterialTheme.typography.titleSmall, fontFamily = PlexMono,
                fontWeight = if (g < 0 && !flat) FontWeight.Bold else FontWeight.Medium,
                color = if (dim) tone.copy(alpha = 0.6f) else tone,
            )
            if (l.changed) {
                Text(
                    l.note ?: ("was " + signed(l.was ?: 0.0)),
                    style = MaterialTheme.typography.labelSmall, fontFamily = PlexMono, color = At.c.muted, maxLines = 1,
                )
            }
        }
    }
}

/** The exact filters of [chain], compared with the filters that were set before. */
fun filterLines(chain: EqChain, previous: EqChain?): List<BandLine> {
    val prev = previous?.filters.orEmpty().toMutableList()
    val lines = chain.filters.map { f ->
        val label = hzText(f.freqHz) + " Hz\u00B7Q" + "%.1f".format(f.q)
        if (previous == null) return@map BandLine(label, f.gainDb, null)
        val m = prev.firstOrNull { p -> p.freqHz / f.freqHz in 0.89..1.12 && p.q / f.q in 0.7..1.4 }
        if (m == null) BandLine(label, f.gainDb, 0.0, note = "new")
        else { prev.remove(m); BandLine(label, f.gainDb, m.gainDb) }
    }
    val removed = if (previous == null) emptyList() else prev.map { p -> BandLine(hzText(p.freqHz) + " Hz\u00B7Q" + "%.1f".format(p.q), 0.0, p.gainDb, note = "removed") }
    return (lines + removed).sortedBy { it.label.substringBefore(' ').toDoubleOrNull() ?: 0.0 }
}

/** The EQ as a list, always low to high. Toggle between exact filters and the sliders of a standard graphic EQ. */
@Composable
fun EqListView(chain: EqChain, s: UiState, vm: TuneViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Standard bands instead of exact frequencies", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = s.showBands, onCheckedChange = vm::setShowBands,
            colors = SwitchDefaults.colors(checkedTrackColor = At.c.signal, checkedThumbColor = At.c.onSignal),
        )
        InfoButton(
            "Exact frequencies or standard bands",
            "Exact frequencies: parametric filters (frequency, gain, Q). Most accurate. Use them in a parametric EQ (Poweramp Equalizer, a DSP or head unit with parametric bands).\n\n" +
                "Standard bands: the same correction converted to the fixed sliders of a graphic EQ. Pick the number of bands your EQ has. It is less precise, because narrow cuts get spread over the nearest band, but it works on any slider EQ.",
        )
    }
    val prev = s.previousChain
    if (!s.showBands) {
        BandTable(filterLines(chain, prev), chain.preampDb, compared = prev != null)
    } else {
        DropSelect(Export.BAND_SETS.map { it.name }, s.bandSetIdx, vm::setBandSet, label = "Your EQ has")
        val set = Export.BAND_SETS[s.bandSetIdx]
        val now = Export.graphicBands(chain, set.centers)
        val before = prev?.let { p -> Export.graphicBands(p, set.centers).toMap() }
        BandTable(
            now.map { (f, g) -> BandLine(hzText(f) + " Hz", g, before?.get(f)) },
            chain.preampDb, compared = before != null,
        )
    }
}

private fun ago(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60000
    return when {
        m < 2 -> "just now"
        m < 60 -> "$m min ago"
        m < 60 * 24 -> "${m / 60} h ago"
        else -> "${m / (60 * 24)} d ago"
    }
}


// ------------------------------------------------------------------ classic graphic sliders

internal val GRAPHIC_SETS = listOf(
    listOf(63.0, 250.0, 1000.0, 4000.0, 16000.0) to 0.7,
    listOf(31.5, 63.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0) to 1.4,
    listOf(25.0, 40.0, 63.0, 100.0, 160.0, 250.0, 400.0, 630.0, 1000.0, 1600.0, 2500.0, 4000.0, 6300.0, 10000.0, 16000.0) to 2.1,
)

private fun freqLabel(f: Double): String {
    fun n(v: Double) = ("%.1f".format(v)).removeSuffix(".0")
    return if (f >= 1000) n(f / 1000) + "k" else n(f)
}

@Composable
internal fun GraphicSliders(s: UiState, vm: TuneViewModel, centres: List<Double>, q: Double, locked: Boolean = false) {
    val gains = graphicGains(s.exportChain, centres)
    val many = centres.size > 10
    val scroll = androidx.compose.foundation.rememberScrollState()
    Row(
        Modifier.fillMaxWidth().then(if (many) Modifier.androidxHorizontalScroll(scroll) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        centres.forEachIndexed { i, f ->
            Column(
                (if (many) Modifier.width(48.dp) else Modifier.weight(1f)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val g = gains[i]
                Text(
                    if (abs(g) < 0.05) "0" else "%+.1f".format(g).replace('-', '\u2212'),
                    style = MaterialTheme.typography.labelSmall, fontFamily = PlexMono,
                    color = if (g < -0.05) At.c.bad else if (g > 0.05) At.c.signal else At.c.muted,
                    maxLines = 1,
                )
                VSlider(g, { vm.graphicSet(centres, q, i, it) }, "${freqLabel(f)} Hz", enabled = !locked)
                Text(freqLabel(f), style = MaterialTheme.typography.labelSmall, color = At.c.muted, maxLines = 1)
            }
        }
    }
}

/** A vertical slider, +12 dB at the top, in half-dB steps. Drag anywhere along it; double-tap-free: tap sets, drag moves. */
@Composable
private fun VSlider(value: Double, onChange: (Double) -> Unit, label: String, enabled: Boolean = true) {
    val range = 12.0
    val track = At.c.line
    val fill = (if (value < 0) At.c.bad else At.c.signal).let { if (enabled) it else it.copy(alpha = 0.45f) }
    val zero = At.c.muted
    val thumb = if (enabled) At.c.text else At.c.muted
    val style = At.c.slider
    val latest = androidx.compose.runtime.rememberUpdatedState(onChange)
    fun at(y: Float, h: Float): Double = (Math.round((range - 2 * range * (y / h).coerceIn(0f, 1f)) * 2) / 2.0)
    androidx.compose.foundation.Canvas(
        // locked: no touch handling at all, so scrolling past the sliders can never move one
        Modifier.width(48.dp).height(176.dp)
            .then(if (enabled) Modifier.androidx_pointer(latest, ::at).androidx_semantics(label, value, range) { latest.value(it) } else Modifier.semantics { contentDescription = "$label, locked" }),
    ) {
        val cx = size.width / 2
        val pad = 12.dp.toPx()
        val top = pad
        val bottom = size.height - pad
        val yOf = { v: Double -> (top + (bottom - top) * ((range - v) / (2 * range))).toFloat() }
        fun o(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x, y)
        when (style) {
            SliderStyle.ROUND -> {
                drawLine(track, o(cx, top), o(cx, bottom), strokeWidth = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(fill, o(cx, yOf(0.0)), o(cx, yOf(value)), strokeWidth = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(zero, o(cx - 12.dp.toPx(), yOf(0.0)), o(cx + 12.dp.toPx(), yOf(0.0)), strokeWidth = 1.5.dp.toPx())
                drawCircle(thumb, 9.dp.toPx(), o(cx, yOf(value)))
                drawCircle(fill, 5.dp.toPx(), o(cx, yOf(value)))
            }
            SliderStyle.FADER -> {
                // a mixing-desk fader: a dark slot, scale ticks every 3 dB, and a flat cap with a centre line
                for (d in -12..12 step 3) {
                    val y = yOf(d.toDouble())
                    val w = if (d == 0) 13.dp.toPx() else 8.dp.toPx()
                    drawLine(if (d == 0) zero else track, o(cx - w - 7.dp.toPx(), y), o(cx - 7.dp.toPx(), y), strokeWidth = 1.dp.toPx())
                    drawLine(if (d == 0) zero else track, o(cx + 7.dp.toPx(), y), o(cx + w + 7.dp.toPx(), y), strokeWidth = 1.dp.toPx())
                }
                drawRoundRect(
                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f), o(cx - 3.dp.toPx(), top),
                    androidx.compose.ui.geometry.Size(6.dp.toPx(), bottom - top), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                )
                drawLine(fill, o(cx, yOf(0.0)), o(cx, yOf(value)), strokeWidth = 2.dp.toPx())
                val capW = 30.dp.toPx(); val capH = 18.dp.toPx(); val y = yOf(value)
                drawRoundRect(thumb, o(cx - capW / 2, y - capH / 2), androidx.compose.ui.geometry.Size(capW, capH), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                drawRoundRect(
                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f), o(cx - capW / 2, y + capH / 2 - 4.dp.toPx()),
                    androidx.compose.ui.geometry.Size(capW, 4.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                )
                drawLine(fill, o(cx - capW / 2 + 3.dp.toPx(), y), o(cx + capW / 2 - 3.dp.toPx(), y), strokeWidth = 2.5.dp.toPx())
            }
            SliderStyle.LED -> {
                // a column of LEDs: lit from 0 dB to the value, the top one at the value brightest
                val n = 24
                val segH = (bottom - top) / n
                for (i in 0 until n) {
                    val lo = range - 2 * range * (i + 1) / n
                    val hi = range - 2 * range * i / n
                    val mid = (lo + hi) / 2
                    val lit = if (value >= 0) mid in 0.0..value else mid in value..0.0
                    val atValue = value in lo..hi
                    val col = when {
                        atValue -> thumb
                        lit -> fill
                        else -> track.copy(alpha = 0.55f)
                    }
                    drawRoundRect(
                        col, o(cx - 9.dp.toPx(), top + i * segH + 1.dp.toPx()),
                        androidx.compose.ui.geometry.Size(18.dp.toPx(), segH - 2.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),
                    )
                }
                drawLine(zero, o(cx - 14.dp.toPx(), yOf(0.0)), o(cx + 14.dp.toPx(), yOf(0.0)), strokeWidth = 1.dp.toPx())
            }
        }
    }
}

private fun Modifier.androidxHorizontalScroll(state: androidx.compose.foundation.ScrollState): Modifier =
    this.then(Modifier.horizontalScroll(state))

private fun Modifier.androidx_pointer(
    latest: androidx.compose.runtime.State<(Double) -> Unit>,
    at: (Float, Float) -> Double,
): Modifier = this
    .pointerInput(Unit) {
        detectTapGestures { o -> latest.value(at(o.y - 12.dp.toPx(), size.height - 24.dp.toPx())) }
    }
    .pointerInput(Unit) {
        detectVerticalDragGestures { change, _ ->
            change.consume()
            latest.value(at(change.position.y - 12.dp.toPx(), size.height - 24.dp.toPx()))
        }
    }

private fun Modifier.androidx_semantics(label: String, value: Double, range: Double, set: (Double) -> Unit): Modifier =
    this.semantics {
        contentDescription = label
        stateDescription = "%+.1f dB".format(value)
        progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), (-range).toFloat()..range.toFloat())
        setProgress { v -> set(Math.round(v.toDouble() * 2) / 2.0); true }
    }

