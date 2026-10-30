@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.autotune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.autotune.ApplyWay
import dev.autotune.Source
import dev.autotune.TestFileKind
import dev.autotune.TuneStep
import dev.autotune.TuneViewModel
import dev.autotune.UiState
import dev.autotune.YOUTUBE_SEARCH
import dev.autotune.audio.OutChannel
import dev.autotune.dsp.Target
import kotlin.math.abs

/**
 * Tuning is one guided workflow: the Tune button, a fresh-start check, set-up, the stock reading, its analysis, the EQ
 * (shown and applied right here), a reading with the EQ, and the result, which either finishes or goes round again.
 * Every measurement ends on one screen with one recommended action; the other choices are listed under it.
 * Back goes to the previous screen; the cross leaves the workflow. Nothing in the loop needs another tab.
 */
@Composable
fun TuneTab(s: UiState, vm: TuneViewModel, sharer: Sharer, go: (Tab) -> Unit, openResults: () -> Unit, pick: Pickers) {
    // Exit leaves the workflow and keeps the place: Continue comes back to the same step
    val exit = { vm.tuneExit() }
    when (s.tuneStep) {
        TuneStep.HOME -> TuneHome(s, vm, pick)
        TuneStep.FRESH -> FreshStep(s, vm, exit)
        TuneStep.SETUP -> SetupStep(s, vm, pick, exit)
        TuneStep.STOCK -> MeasureStep(s, vm, sharer, pick, stock = true, exit = exit)
        TuneStep.STOCK_RESULT -> StockResultStep(s, vm, openResults, exit)
        TuneStep.APPLY -> ApplyStep(s, vm, pick, exit)
        TuneStep.VERIFY -> MeasureStep(s, vm, sharer, pick, stock = false, exit = exit)
        TuneStep.RESULT -> ResultStep(s, vm, openResults, exit)
        TuneStep.DONE -> DoneStep(s, vm, go, openResults)
    }
}

private const val STEPS = 7

// ------------------------------------------------------------------ home

@Composable
private fun TuneHome(s: UiState, vm: TuneViewModel, pick: Pickers) {
    ScreenTitle(
        "Tune", if (s.testName.isBlank()) null else s.testName,
        contextIcon = s.tests.firstOrNull { it.id == s.currentTestId }?.kind?.let { kindIcon(it) },
    )
    Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PulsingButton("Tune", if (s.runs.isNotEmpty()) "start or continue" else "start here", Ic.TUNE, enabled = !s.busy, onClick = vm::tuneStart)
        Spacer(Modifier.height(12.dp))
        Text(
            "Measures your system, suggests an EQ, applies it and checks it, step by step.",
            style = MaterialTheme.typography.bodyMedium, color = At.c.muted, textAlign = TextAlign.Center,
        )
    }
    Panel(pad = 14.dp) {
        MicLine(s, pick)
        Row(verticalAlignment = Alignment.CenterVertically) {
            AtIcon(Ic.POWER, if (s.systemEqOn) At.c.signal else At.c.muted, size = 18.dp)
            Text(
                "  EQ currently in use: " + if (s.systemEqOn) s.eqSource.ifBlank { "flat" } else "none (AutoTune EQ off)",
                style = MaterialTheme.typography.bodyMedium, maxLines = 2,
            )
        }
    }
    if (s.runs.isNotEmpty()) {
        SecondaryButton("Continue “${s.testName}”", vm::tuneContinue, Modifier.fillMaxWidth(), icon = Ic.NEXT)
        Muted("Picks up at " + dev.autotune.resumePlace(dev.autotune.resumeAt(s.resumeStep, s.runs.size)) + ".")
    }
    Fold("Advanced tools", summary = "Mic spots, channel, window, left/right, subwoofer") {
        Settings(s, vm, s.hasPermission)
        ExtraChecks(s, vm, s.hasPermission)
    }
}

// ------------------------------------------------------------------ 1. fresh start

@Composable
private fun FreshStep(s: UiState, vm: TuneViewModel, exit: () -> Unit) {
    StepHeader(0, STEPS, "Fresh start", exit, onBack = vm::tuneBack, names = dev.autotune.STEP_NAMES)
    Panel {
        CardTitle("Start from a fresh system?", "The first reading is the reference, so it must be the system with no EQ at all.")
        Check(Ic.POWER, if (s.systemEqOn) "AutoTune EQ is on now. It is switched off when you start fresh." else "AutoTune EQ is off.")
        Check(Ic.EQ, "Set the head unit or DSP EQ flat: every band at 0, bass boost and loudness off.")
        Check(Ic.SPEAKER, "Phone sound effects off: Samsung Adapt Sound, Dolby Atmos, the phone's own equaliser.")
        Check(Ic.MIC, "Mic at your head position, pointing up. Engine off, doors closed, normal listening volume.")
        PinAction("Yes, start fresh", vm::tuneFresh, icon = Ic.POWER)
        if (s.runs.isNotEmpty()) {
            SecondaryButton("No, continue “${s.testName}”", vm::tuneContinue, Modifier.fillMaxWidth(), icon = Ic.NEXT)
            Muted("Continuing keeps the stock reading and the runs of that test.")
        }
    }
}

@Composable
private fun Check(icon: Ic, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        AtIcon(icon, At.c.signal, Modifier.padding(top = 1.dp, end = 12.dp), 20.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

// ------------------------------------------------------------------ 2. set up

private val ROUTES = listOf(
    Source.PHONE to "This phone plays it (Bluetooth, aux or USB)",
    Source.COMBINED to "Another device plays our sweep + pink file (best)",
    Source.TEST_FILE to "Another device plays our sweep file",
    Source.YOUTUBE to "Another device plays pink noise",
)

@Composable
private fun SetupStep(s: UiState, vm: TuneViewModel, pick: Pickers, exit: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    // starts from the defaults in Settings
    val defIdx = Target.ALL.indexOfFirst { it.name == s.defTarget }.takeIf { it >= 0 } ?: s.targetIdx
    var targetIdx by rememberSaveable { mutableIntStateOf(defIdx.coerceIn(0, (s.targetNames.size - 1).coerceAtLeast(0))) }
    var routeIdx by rememberSaveable { mutableIntStateOf(ROUTES.indexOfFirst { it.first == s.source }.coerceAtLeast(0)) }
    val car = dev.autotune.targetKind(Target.ALL.getOrNull(targetIdx)) == dev.autotune.TargetKind.CAR
    var spotsIdx by rememberSaveable(car) { mutableIntStateOf(listOf(1, 3, 5).indexOf(s.defSpots).takeIf { it >= 0 } ?: if (car) 1 else 0) }
    var gateIdx by rememberSaveable { mutableIntStateOf(listOf(0, 80, 200, 400, 600).indexOf(s.defGateMs).coerceAtLeast(0)) }
    val spots = listOf(1, 3, 5)

    StepHeader(1, STEPS, "Set up", exit, onBack = vm::tuneBack, names = dev.autotune.STEP_NAMES)
    Panel {
        CardTitle("What are you tuning?")
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Name (optional)") }, placeholder = { Text("e.g. F-150 driver seat") },
            singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
        )
        val kinds = s.targetNames.indices.map { dev.autotune.targetKind(Target.ALL.getOrNull(it)) }
        DropSelect(
            s.targetNames, targetIdx, { targetIdx = it }, label = "Kind of system and target sound",
            icons = kinds.map { kindIcon(it) },
            details = s.targetNames.indices.map { targetDetail(Target.ALL.getOrNull(it)) },
        )
        DropSelect(ROUTES.map { it.second }, routeIdx, { routeIdx = it }, label = "How the sound reaches the speakers", icon = Ic.SPEAKER)
        if (ROUTES[routeIdx].first == Source.PHONE) OutputPicker(s, vm)
        MicLine(s, pick)
    }
    // the essentials above; spots and window keep sensible defaults and wait here
    Fold("Measurement options", summary = listOf("1 spot", "3 spots", "5 spots")[spotsIdx] + " · " + listOf("auto window", "80 ms", "200 ms", "400 ms", "600 ms")[gateIdx]) {
        DropSelect(
            listOf("1 spot", "3 spots", "5 spots"), spotsIdx, { spotsIdx = it }, label = "Mic spots per reading", icon = Ic.MIC,
            details = listOf("Quickest", "Recommended for a car: averages out one unlucky spot", "Most reliable"),
        )
        DropSelect(
            listOf("Auto (80 ms for a car, 400 ms for a room)", "80 ms", "200 ms", "400 ms", "600 ms"), gateIdx, { gateIdx = it }, label = "Analysis window",
        )
    }
    PinAction(
        "Next: stock reading",
        { vm.tuneSetup(name, targetIdx, ROUTES[routeIdx].first, spots[spotsIdx], listOf(null, 80, 200, 400, 600)[gateIdx]) },
        icon = Ic.NEXT,
    )
}

// ------------------------------------------------------------------ 3 / 5. measure

@Composable
private fun MeasureStep(s: UiState, vm: TuneViewModel, sharer: Sharer, pick: Pickers, stock: Boolean, exit: () -> Unit) {
    val ready = s.hasPermission
    val n = s.runs.size
    val title = when {
        stock -> "Stock reading"
        s.repeatNext -> "Run $n (repeat)"
        else -> "Run $n"
    }
    val how = when {
        stock && s.runs.isNotEmpty() -> "No EQ. This replaces the stock reading" + (if (s.runs.size > 1) " and the ${s.runs.size - 1} runs after it." else ".")
        stock -> "No EQ: this is the reference every run is compared with."
        s.repeatNext -> "Nothing changed since ${s.runs.lastOrNull()?.label ?: "the last run"}: shows how steady the reading is."
        s.systemEqOn -> "With AutoTune EQ on."
        s.applyWay == ApplyWay.IN_APP -> "The sweep plays through the EQ inside the app. Nothing else changes."
        else -> "With the EQ set on your equipment."
    }
    val failed = !s.busy && s.status?.startsWith("Failed: ") == true

    PinAction(
        when {
            failed -> "Try again"
            s.positions > 1 -> "Measure spot ${s.positionsDone + 1} of ${s.positions}"
            stock -> "Measure the stock system"
            else -> "Measure with the EQ"
        },
        vm::measure, icon = if (failed) Ic.AGAIN else Ic.MEASURE, enabled = ready && !s.busy,
        hint = if (!ready) "Allow microphone access first." else null,
    )
    StepHeader(if (stock) 2 else 5, STEPS, if (stock) "Stock reading" else "Verify: measure with the EQ", exit, onBack = vm::tuneBack, names = dev.autotune.STEP_NAMES)
    Panel {
        CardTitle(title, how)
        MicLine(s, pick)
        if (s.source != Source.PHONE) OtherDeviceSignal(s.source, vm, sharer)
        if (s.source == Source.PHONE) OutputPicker(s, vm)
        // the measure button is pinned at the bottom; here only where the spots stand
        if (s.positionsDone > 0) {
            Text("Spot ${s.positionsDone} of ${s.positions} done. Move the mic a hand's width to the next spot, then measure again.", style = MaterialTheme.typography.bodyMedium)
            TextAction("Start the spots over", vm::resetPositions, icon = Ic.UNDO)
        }
        if (failed) Failure(s) else if (!s.busy) s.status?.let { Muted(it) }
        LevelResult(s)
        if (!stock && !s.repeatNext && (s.eqLevelDb ?: 0.0) < -2.0) {
            Muted(
                "This EQ makes the sound about %.0f dB quieter overall. Turning the volume up a step for this reading is fine: AutoTune compares the shape of the response, not its loudness."
                    .format(-(s.eqLevelDb ?: 0.0)),
            )
        }
        if (!s.busy) Row(verticalAlignment = Alignment.CenterVertically) {
            TextAction("Check the level", vm::levelCheck, enabled = ready, icon = Ic.LIVE)
            TextAction(if (stock) "Back" else "Back to the EQ", vm::tuneBack, icon = Ic.BACK)
        }
    }
    if (stock) {
        Fold("Before you start", summary = "EQ off, mic, volume", startOpen = true) {
            Bullet("Every EQ and sound effect off (head unit, Samsung Adapt Sound, Dolby, loudness).")
            Bullet("Mic at your head position, pointing up. Engine off, doors closed.")
            Bullet("Your normal listening volume.")
        }
    } else {
        Row(verticalAlignment = Alignment.Top) {
            AtIcon(Ic.MIC, At.c.signal, Modifier.padding(top = 1.dp, end = 10.dp), 20.dp)
            Text("Same mic spot and volume as the stock reading. Only the EQ changes between runs.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
    if (!stock && !s.busy) {
        var note by rememberSaveable { mutableStateOf(false) }
        if (note || s.runNote.isNotBlank()) {
            OutlinedTextField(
                value = s.runNote, onValueChange = vm::setRunNote,
                label = { Text("Note for this run") }, placeholder = { Text("e.g. tweeters down 2 dB") },
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
            )
        } else TextAction("Add a note to this run", { note = true }, icon = Ic.EDIT)
    }
    Fold("Measurement settings", summary = "Spots, channel, window") { Settings(s, vm, ready) }
}

// ------------------------------------------------------------------ 3b. stock analysis

@Composable
private fun StockResultStep(s: UiState, vm: TuneViewModel, openResults: () -> Unit, exit: () -> Unit) {
    var confirmStock by remember { mutableStateOf(false) }
    StepHeader(3, STEPS, "Stock review", exit, onBack = vm::tuneBack, names = dev.autotune.STEP_NAMES)
    val a = s.stock
    if (a == null || s.runs.isEmpty()) {
        Muted("No stock reading yet.")
        PinAction("Measure the stock system", { vm.tuneGo(TuneStep.STOCK) }, icon = Ic.MEASURE)
        return
    }
    ResultSummary(s, showNext = false)

    // the one action
    if (a.suspicious) {
        PinAction(
            "Measure the stock again", { vm.tuneGo(TuneStep.STOCK) }, icon = Ic.AGAIN,
            hint = "Check the sound comes from the speakers you are tuning and the mic is uncovered at your head position.",
        )
    } else {
        PinAction(if (a.bands > 0) "Review the suggested EQ" else "Next", { vm.tuneGo(TuneStep.APPLY) }, icon = Ic.NEXT)
    }
    // the graph is always there after a reading
    s.chart?.let { Panel { ResponseChart(it, height = 200.dp) } }

    if (a.findings.isNotEmpty()) {
        Group("What the reading shows", divider = false) { a.findings.forEach { FindingRow(it) } }
    }
    FollowAndChecks(s, vm)
    Group("Other choices", divider = false) {
        if (a.suspicious) TextAction("Use this reading anyway", { vm.tuneGo(TuneStep.APPLY) }, icon = Ic.NEXT)
        else TextAction("Measure the stock again", { if (s.runs.size > 1) confirmStock = true else vm.tuneGo(TuneStep.STOCK) }, icon = Ic.AGAIN)
        TextAction("Ask AI to review", vm::aiOpen, icon = Ic.AI)
    }
    TargetSoundFold(s, vm)
    DetailsHeading()
    s.report?.takeIf { s.runs.size == 1 }?.let { r -> ReportCard(r, "the stock reading") }
    TextAction("All results of this test", openResults, icon = Ic.TESTS)
    if (confirmStock) {
        AlertDialog(
            onDismissRequest = { confirmStock = false },
            title = { Text("Measure the stock system again?") },
            text = { Text("The new stock reading replaces the old one and every run after it. AutoTune EQ is switched off first.") },
            confirmButton = { TextButton(onClick = { confirmStock = false; vm.tuneGo(TuneStep.STOCK) }) { Text("Measure again", color = At.c.bad) } },
            dismissButton = { TextButton(onClick = { confirmStock = false }) { Text("Cancel") } },
        )
    }
}

/** One finding: an icon for how much it matters, a title and what it means. */
@Composable
fun FindingRow(f: dev.autotune.Finding) {
    val (icon, tint) = when (f.severity) {
        2 -> Ic.ALERT to At.c.bad
        1 -> Ic.INFO to At.c.warn
        else -> Ic.DONE to At.c.good
    }
    Row(verticalAlignment = Alignment.Top) {
        AtIcon(icon, tint, Modifier.padding(top = 2.dp, end = 10.dp), 20.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(f.title, style = MaterialTheme.typography.titleSmall)
            Text(f.detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ------------------------------------------------------------------ 4. apply

@Composable
private fun ApplyStep(s: UiState, vm: TuneViewModel, pick: Pickers, exit: () -> Unit) {
    val chain = s.exportChain
    val first = s.runs.size <= 1
    val latest = s.runs.lastOrNull()
    var equipment by rememberSaveable { mutableStateOf(s.source != Source.PHONE || (s.applyWay == ApplyWay.EQUIPMENT && s.runs.size > 1)) }
    var bandSet by rememberSaveable { mutableIntStateOf(1) }
    // "Suggested EQ" is AutoTune's proposal; anything else picked on the EQ tab is "the EQ currently in use"
    val isSuggestion = s.eqPick.startsWith("suggest:")
    StepHeader(4, STEPS, "Apply the suggested EQ", exit, onBack = vm::tuneBack, names = dev.autotune.STEP_NAMES)
    Panel {
        CardTitle(
            when {
                !isSuggestion -> "EQ currently in use"
                first -> "Suggested EQ"
                else -> "New suggested EQ after ${latest?.label}"
            },
            latest?.let { "${it.label} is %.1f dB from the target.".format(it.rmsDb) },
        )
        if (!isSuggestion) {
            Text("This is not AutoTune's suggestion: ${s.eqSource}.", style = MaterialTheme.typography.bodyMedium)
            TextAction("Use the suggested EQ instead", vm::useSuggested, icon = Ic.UNDO)
        }
        s.chart?.let { ResponseChart(it, height = 180.dp) }
        if (chain == null || chain.filters.isEmpty()) {
            Text("Nothing to change: the system already matches the target.", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (!first) PinAction("Keep what is set and finish", { vm.tuneKeep(false) }, icon = Ic.DONE)
            return@Panel
        }
        Text(
            "${chain.filters.size} bands · preamp %.1f dB".format(chain.preampDb) +
                (s.eqLevelDb?.let { " · overall %+.1f dB".format(it) } ?: ""),
            style = MaterialTheme.typography.bodyMedium, color = At.c.muted,
        )
    }

    if (chain != null && chain.filters.isNotEmpty()) {
        // three plain choices; the recommended one is the button
        Group("How do you want to apply it?", divider = false) {
            if (s.source == Source.PHONE && !equipment) {
                if (!s.systemEqSuspect) {
                    PinAction("Use the phone's EQ and measure", { pick.notifications(); vm.tuneApply(ApplyWay.AUTOTUNE_EQ) }, icon = Ic.POWER)
                    Muted("The button below: AutoTune EQ equalises everything this phone plays. It affects only this phone's audio, and how well it works depends on the phone.")
                    SecondaryButton("Preview in the app and measure", { vm.tuneApply(ApplyWay.IN_APP) }, Modifier.fillMaxWidth(), icon = Ic.PLAY)
                } else {
                    Warning("The phone's EQ damaged the treble on this phone before. Preview inside the app, or set it on your equipment.")
                    PinAction("Preview in the app and measure", { vm.tuneApply(ApplyWay.IN_APP) }, icon = Ic.PLAY)
                }
                SecondaryButton("Set it on my equipment", { equipment = true }, Modifier.fillMaxWidth(), icon = Ic.EDIT)
                s.systemEqMessage?.takeIf { !s.systemEqOn && it.isNotBlank() && it != "EQ off." }?.let { Warning(it) }
                if (s.systemEqSuspect) TextAction("Use the phone's EQ anyway", { pick.notifications(); vm.tuneApply(ApplyWay.AUTOTUNE_EQ) }, icon = Ic.POWER)
            } else {
                Text("Set these bands on your head unit, DSP or EQ app, with the preamp first.", style = MaterialTheme.typography.bodyMedium)
                EqListView(chain, s, vm)
                PinAction("It's set, measure", { vm.tuneApply(ApplyWay.EQUIPMENT) }, icon = Ic.NEXT)
                if (s.source == Source.PHONE) TextAction("Use the phone's EQ instead", { equipment = false }, icon = Ic.POWER)
            }
        }

        TargetSoundFold(s, vm)

        // everything else, closed until needed
        Fold("Advanced", summary = "Adjust by hand, exact bands, save or share files") {
            Text("Adjust by hand", style = MaterialTheme.typography.titleSmall)
            var locked by rememberSaveable { mutableStateOf(true) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { DropSelect(listOf("5 bands", "10 bands", "15 bands"), bandSet, { bandSet = it }) }
                IconAction(if (locked) Ic.LOCK else Ic.UNLOCK, if (locked) "Unlock the sliders" else "Lock the sliders") { locked = !locked }
            }
            GraphicSliders(s, vm, GRAPHIC_SETS[bandSet].first, GRAPHIC_SETS[bandSet].second, locked)
            Muted(if (locked) "Locked against accidental changes. Tap the lock to adjust." else "Each slider shows what the EQ does at that frequency. Moving one adds to the suggestion; the tuned bands stay.")
            if (s.sliderLayer.isNotEmpty()) TextAction("Remove my slider changes", vm::resetSliders, icon = Ic.UNDO)
            if (!equipment) {
                Text("Exact bands", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                EqListView(chain, s, vm)
            }
            Text("Files", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Save files", { vm.exportFiles(share = false) }, icon = Ic.SAVE)
                SecondaryButton("Share", { vm.exportFiles(share = true) }, icon = Ic.SHARE)
            }
            s.status?.takeIf { it.startsWith("Saved") || it.startsWith("Choose") || it.startsWith("Could") }?.let { Muted(it) }
        }
    }
}

// ------------------------------------------------------------------ 6. result

@Composable
private fun ResultStep(s: UiState, vm: TuneViewModel, openResults: () -> Unit, exit: () -> Unit) {
    var discard by remember { mutableStateOf(false) }
    val runs = s.runs
    val stock = runs.firstOrNull()
    val latest = runs.lastOrNull()
    val best = runs.drop(1).minByOrNull { it.rmsDb }

    StepHeader(6, STEPS, "Result", exit, onBack = vm::tuneBack, names = dev.autotune.STEP_NAMES)
    if (stock == null || latest == null || runs.size < 2) {
        Muted("No run with the EQ yet.")
        PinAction("Back to the EQ", { vm.tuneGo(TuneStep.APPLY) }, icon = Ic.BACK)
        return
    }
    ResultSummary(s, showNext = false)
    s.chart?.let { Panel { ResponseChart(it, height = 200.dp) } }

    // one recommended move, worked out from the runs; everything else is listed under it
    val rec = s.recommendation
    val bestRun = rec?.let { r -> runs.getOrNull(r.bestIdx) } ?: best
    val phoneEq = s.source == Source.PHONE
    when (rec?.move ?: dev.autotune.NextMove.REFINE) {
        // the EQ seems not to arrive: measure with it off and on to know for sure
        dev.autotune.NextMove.CHECK_EQ ->
            if (phoneEq) PinAction("Check the EQ reaches the speakers", vm::eqCheck, icon = Ic.CHECK, hint = rec?.why)
            else PinAction("Apply the EQ again", { vm.tuneGo(TuneStep.APPLY) }, icon = Ic.POWER, hint = rec?.why)
        dev.autotune.NextMove.KEEP_LATEST -> PinAction("Keep this EQ and finish", { vm.tuneKeep(false) }, icon = Ic.DONE, hint = rec?.why)
        dev.autotune.NextMove.KEEP_BEST -> PinAction("Keep the EQ of ${bestRun?.label ?: "the best run"} and finish", { vm.tuneKeep(true) }, icon = Ic.UNDO, hint = rec?.why)
        dev.autotune.NextMove.REFINE -> PinAction("Apply the new suggested EQ", vm::tuneRefine, icon = Ic.TUNE, hint = rec?.why)
    }
    s.eqCheck?.let { EqCheckCard(it, vm) }
    FollowAndChecks(s, vm)
    val move = rec?.move ?: dev.autotune.NextMove.REFINE
    Group("Other choices", divider = false) {
        if (move != dev.autotune.NextMove.REFINE && !latest.converged) TextAction("Another round with the new suggested EQ", vm::tuneRefine, icon = Ic.TUNE)
        if (move != dev.autotune.NextMove.KEEP_LATEST) TextAction("Keep this EQ and finish", { vm.tuneKeep(false) }, icon = Ic.DONE)
        if (move != dev.autotune.NextMove.KEEP_BEST && bestRun != null && bestRun != latest) TextAction("Keep the EQ of ${bestRun.label} and finish", { vm.tuneKeep(true) }, icon = Ic.UNDO)
        TextAction("Measure again, nothing changed", vm::tuneRepeat, icon = Ic.AGAIN)
        if (move == dev.autotune.NextMove.CHECK_EQ && phoneEq) TextAction("Apply the EQ again", { vm.tuneGo(TuneStep.APPLY) }, icon = Ic.POWER)
        if (move != dev.autotune.NextMove.CHECK_EQ && phoneEq) TextAction("Check the EQ reaches the speakers", vm::eqCheck, icon = Ic.CHECK)
        TextAction("Ask AI to review", vm::aiOpen, icon = Ic.AI)
        TextAction("Discard this run", { discard = true }, danger = true, icon = Ic.TRASH)
    }
    TargetSoundFold(s, vm)
    DetailsHeading()
    Fold("Runs", summary = "${runs.size} readings, the latest against the stock") {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            runs.takeLast(8).forEach { r ->
                Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(runColor(r.index)))
                    Text("  ${r.label}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("%.2f dB".format(r.rmsDb), style = MaterialTheme.typography.bodyMedium, fontWeight = if (r == best) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        s.compare?.let { Muted(it.verdict) }
    }
    TextAction("All results of this test", openResults, icon = Ic.TESTS)
    if (discard) {
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text("Discard ${latest.label}?") },
            text = { Text("The reading is deleted and the suggestion goes back to the run before it.") },
            confirmButton = { TextButton(onClick = { discard = false; vm.tuneDiscard() }) { Text("Discard", color = At.c.bad) } },
            dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep it") } },
        )
    }
}

// ------------------------------------------------------------------ done

@Composable
private fun DoneStep(s: UiState, vm: TuneViewModel, go: (Tab) -> Unit, openResults: () -> Unit) {
    val stock = s.runs.firstOrNull()
    val last = s.runs.drop(1).minByOrNull { it.rmsDb }
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AtIcon(Ic.DONE, At.c.good, size = 64.dp)
        Text("Tuned", style = MaterialTheme.typography.headlineMedium)
        if (stock != null && last != null) Text("%.1f dB → %.1f dB from the target".format(stock.rmsDb, last.rmsDb), style = MaterialTheme.typography.headlineSmall)
        Text(
            when {
                s.systemEqOn -> "Saved as a profile and AutoTune EQ is on with it: ${s.eqSource}."
                s.source == Source.PHONE && s.applyWay != ApplyWay.EQUIPMENT -> "Saved as a profile: ${s.eqSource}. Turn on AutoTune EQ on the EQ tab to use it."
                else -> "Saved as a profile. Keep these bands set on your equipment."
            },
            style = MaterialTheme.typography.bodyMedium, color = At.c.muted, textAlign = TextAlign.Center,
        )
    }
    PinAction("Back to start", { vm.tuneGo(TuneStep.HOME) }, icon = Ic.TUNE)
    SecondaryButton("Open the EQ", { go(Tab.EQ) }, Modifier.fillMaxWidth(), icon = Ic.EQ)
    SecondaryButton("See all results", openResults, Modifier.fillMaxWidth(), icon = Ic.TESTS)
}

// ------------------------------------------------------------------ pieces

/** One line when the mic is fine; the fixes only when something is missing. */
@Composable
private fun MicLine(s: UiState, pick: Pickers) {
    val ready = s.hasPermission
    if (s.micAlsoOutput) {
        Warning("Your phone also sees the mic as headphones, so sound can end up in the mic instead of the car. Use Test sound to check, and pick the car under Plays through if needed.")
    }
    if (ready && s.phoneMic) {
        StatusLine(true, "Phone microphone. Good for checking, a USB mic is more accurate.")
        return
    }
    if (ready && s.calibrationName != null) {
        StatusLine(true, "${s.micName} with calibration")
        return
    }
    if (s.micName == null) StatusLine(false, "No USB microphone. Plug it into the USB-C port.")
    else StatusLine(true, "USB microphone: ${s.micName}")
    if (!s.hasPermission) StatusLine(false, "Microphone access needed")
    if (s.calibrationName == null && !s.phoneMic) StatusLine(false, "No calibration file. Results include the mic's own response.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!s.hasPermission) PrimaryButton("Allow microphone", pick.permission, icon = Ic.MIC)
        if (s.calibrationName == null && !s.phoneMic) SecondaryButton("Import calibration", pick.calibration, icon = Ic.FILE)
    }
}

@Composable
private fun CardTitle(title: String, sub: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = At.c.muted)
    }
}

@Composable
private fun Bullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 8.dp, end = 10.dp).size(5.dp).clip(CircleShape).background(At.c.muted))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Failure(s: UiState) {
    val st = s.status ?: return
    if (s.busy) return
    if (st.startsWith("Failed: ")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AtIcon(Ic.ALERT, At.c.bad, Modifier.padding(top = 1.dp), 18.dp)
            Text(st.removePrefix("Failed: "), style = MaterialTheme.typography.bodyMedium, color = At.c.text)
        }
    } else {
        Text(st, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Ring(s: UiState, vm: TuneViewModel, ready: Boolean, label: String, hint: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MeasureControl(
            if (s.positions > 1) "Spot ${s.positionsDone + 1}" else label,
            if (s.positions > 1) "of ${s.positions}" else hint,
            s.busy, s.progress, enabled = ready, onClick = vm::measure, onStop = vm::cancel,
        )
        if (s.busy) Text(s.status ?: "Measuring…", style = MaterialTheme.typography.bodyMedium, color = At.c.muted, textAlign = TextAlign.Center)
        else if (!ready) Text("Allow microphone access first.", style = MaterialTheme.typography.bodyMedium, color = At.c.muted, textAlign = TextAlign.Center)
        else if (s.positionsDone > 0) {
            Text(
                "Spot ${s.positionsDone} of ${s.positions} done. Move the mic a hand's width to the next spot, then tap again.",
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            )
            TextAction("Start the spots over", vm::resetPositions, icon = Ic.UNDO)
        }
    }
}

/** Where the test sound plays, as a drop-down, so a wrong route (into the mic, or the phone speaker) is obvious. */
@Composable
private fun OutputPicker(s: UiState, vm: TuneViewModel) {
    val ids = listOf<Int?>(null) + s.outputs.map { it.first }
    val names = listOf("Same as music" + (s.outputName?.let { " (now: $it)" } ?: "")) + s.outputs.map { it.second }
    DropSelect(
        names, ids.indexOf(s.outputChoice).coerceAtLeast(0), { vm.setOutput(ids[it]) }, enabled = !s.busy,
        label = "Plays through", icon = Ic.SPEAKER,
        details = listOf("Wherever the phone plays music now") + s.outputs.map { null },
    )
    if (s.outputToMic) Warning("That is the USB mic, not your speakers. Pick your car in the list above.")
    if (s.audioCaptured) Warning("Another app is capturing the phone's sound (screen recorder or casting). Stop it before measuring: it can change what reaches the speakers.")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Muted("Hear it from the speakers? ")
        if (s.playing) TextAction("Stop", vm::stopClip, icon = Ic.STOP) else TextAction("Test sound", vm::testSound, enabled = !s.busy, icon = Ic.PLAY)
    }
}

@Composable
private fun LevelResult(s: UiState) {
    val r = s.levelResult ?: return
    val good = r.startsWith("Good")
    if (good) StatusLine(true, r) else Warning(r)
}

@Composable
private fun Settings(s: UiState, vm: TuneViewModel, ready: Boolean) {
    var confirm by remember { mutableStateOf(false) }
    DropSelect(
        listOf("1 spot", "3 spots", "5 spots"), listOf(1, 3, 5).indexOf(s.positions).coerceAtLeast(0), { vm.setPositions(listOf(1, 3, 5)[it]) },
        enabled = !s.busy && s.positionsDone == 0, label = "Mic spots per reading", icon = Ic.MIC,
    )
    Muted("Several spots around the seat are averaged, so one unlucky dip doesn't steer the EQ.")
    if (s.positionsDone > 0) TextAction("Start the spots over", vm::resetPositions, icon = Ic.UNDO)
    if (s.source == Source.PHONE) {
        DropSelect(
            OutChannel.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, OutChannel.entries.indexOf(s.channel),
            { vm.setChannel(OutChannel.entries[it]) }, enabled = !s.busy, label = "Play through", icon = Ic.SPEAKER,
        )
    }
    SecondaryButton("Level check", vm::levelCheck, enabled = ready && !s.busy, icon = Ic.LIVE)
    LevelResult(s)
    val fdwOpts = listOf(0, 15, 8, 5)
    DropSelect(
        fdwOpts.map { if (it == 0) "Fixed window" else "Frequency-dependent, $it cycles" }, fdwOpts.indexOf(s.fdwCycles).coerceAtLeast(0),
        { vm.setFdw(fdwOpts[it]) }, enabled = !s.busy, label = "Analysis window",
    )
    Muted(
        if (s.fdwCycles == 0) "Fixed: the first ${s.gateMs} ms after the direct sound, for this test."
        else "Frequency-dependent window as in REW: each frequency is read over ${s.fdwCycles} cycles of its own period, up to one second after the direct sound (the fixed gate does not limit it). The bass gets long windows and the treble short ones, so late reflections count less where they cannot be separated anyway. Applies to the next measurement.",
    )
    if (s.runs.isNotEmpty()) TextAction("Measure the stock system again", { confirm = true }, enabled = ready && !s.busy, danger = true, icon = Ic.AGAIN)
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Measure the stock system again?") },
            text = { Text("It replaces the stock reading and deletes every run after it. AutoTune EQ is switched off first.") },
            confirmButton = { TextButton(onClick = { confirm = false; vm.tuneGo(TuneStep.STOCK); vm.stockRun() }) { Text("Measure now", color = At.c.bad) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun OtherDeviceSignal(source: Source, vm: TuneViewModel, sharer: Sharer) {
    when (source) {
        Source.PHONE -> Unit
        Source.TEST_FILE -> {
            Bullet("Tap Measure, then play the sweep file on the other device. The file starts with three beeps; the app waits for them (up to 30 seconds) and stops by itself right after the sweep, about 10 seconds in all.")
            SecondaryButton("Send the sweep file", { vm.shareTestFile(TestFileKind.SWEEP) }, icon = Ic.SHARE)
        }
        Source.COMBINED -> {
            Bullet("Tap Measure, then play the file on the other device. It starts with three beeps, then a sweep and pink noise, about 20 seconds in all. The app waits for the beeps and stops by itself.")
            SecondaryButton("Send the file", { vm.shareTestFile(TestFileKind.COMBINED) }, icon = Ic.SHARE)
        }
        Source.YOUTUBE -> {
            Bullet("Start pink noise on the other device, then tap Measure. It listens for 22 seconds.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Find pink noise", { sharer.text(YOUTUBE_SEARCH) }, icon = Ic.PLAY)
                SecondaryButton("Send our file", { vm.shareTestFile(TestFileKind.PINK) }, icon = Ic.SHARE)
            }
        }
    }
}

@Composable
fun StatusLine(good: Boolean, text: String) {
    Row(Modifier.heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (good) At.c.good else At.c.warn))
        Text("  $text", style = MaterialTheme.typography.bodyMedium)
    }
}

/** Left/right balance and the subwoofer: optional checks beside the tuning loop. */
@Composable
private fun ExtraChecks(s: UiState, vm: TuneViewModel, ready: Boolean) {
    Group(
        "More checks",
        info = "Optional measurements that judge the system beyond its tone curve. Use the same volume and mic spot as the stock reading.\n\n" +
            "Left and right balance finds level and timing differences between the sides.\nSubwoofer and crossover suggests a crossover point, a sub level and the right sub polarity.",
    ) {
        val sweepRoute = s.source == Source.PHONE
        Text("Left and right balance", style = MaterialTheme.typography.titleSmall)
        Muted("Measures the left speaker alone, then the right. Reports the level difference and, when the phone plays, the timing difference.")
        if (!sweepRoute) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Send left sweep", { vm.shareTestFile(TestFileKind.SWEEP_LEFT) }, icon = Ic.SHARE)
                SecondaryButton("Send right sweep", { vm.shareTestFile(TestFileKind.SWEEP_RIGHT) }, icon = Ic.SHARE)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Measure left", { vm.measureSide(OutChannel.LEFT) }, enabled = ready && !s.busy, icon = Ic.MEASURE)
            SecondaryButton("Measure right", { vm.measureSide(OutChannel.RIGHT) }, enabled = ready && !s.busy, icon = Ic.MEASURE)
            if (s.lr != null) TextAction("Reset", vm::resetLr)
        }
        s.lr?.let { lr ->
            lr.result.advice.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Panel { ResponseChart(lr.chart, height = 170.dp) }
        }

        Text("Subwoofer and crossover", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        Muted("Four measurements at the same volume: mains only, sub only, both with sub normal, both with sub inverted.")
        val steps = listOf("MAINS" to "Mains only", "SUB" to "Sub only", "SUM" to "Both, sub normal", "SUM_INV" to "Both, sub inverted")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            steps.forEach { (k, label) ->
                if (k in s.subDone) SecondaryButton("Done: $label", { vm.measureSub(k) }, enabled = ready && !s.busy, icon = Ic.CHECK)
                else PrimaryButton(label, { vm.measureSub(k) }, enabled = ready && !s.busy, icon = Ic.MEASURE)
            }
            if (s.subDone.isNotEmpty()) TextAction("Reset", vm::resetSub)
        }
        s.sub?.notes?.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (s.busy) StatusBlock(s.status, s.warnings) else StatusBlock(s.status?.takeIf { s.lr != null || s.sub != null || it.startsWith("Failed") }, s.warnings)
    }
}
