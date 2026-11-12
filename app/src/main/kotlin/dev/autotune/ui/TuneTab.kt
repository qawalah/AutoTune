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
import androidx.compose.ui.unit.dp
import androidx.compose.animation.togetherWith
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
fun TuneTab(
    s: UiState, vm: TuneViewModel, sharer: Sharer, go: (Tab) -> Unit, openResults: () -> Unit, pick: Pickers,
    /** Opens the home-theatre page to connect AutoTune on the TV or the PC and start a new home theatre named this. */
    openCompanion: (String) -> Unit = { go(Tab.THEATRE) },
) {
    // Exit leaves the workflow and keeps the place: Continue comes back to the same step
    val exit = { vm.tuneExit() }
    val anim = animationsOn()
    // each step slides in from the side it comes from: forward from the end, Back from the start
    // Stock and Verify are measuring phases with no page of their own: the step they started from stays under the
    // measuring screen, and a closed or failed measurement returns to it
    val shown = if (s.tuneStep == TuneStep.STOCK || s.tuneStep == TuneStep.VERIFY) dev.autotune.returnAfterMeasuring(s.measureFrom, s.runs.size) else s.tuneStep
    Transit(
        shown,
        spec = {
            if (!anim) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
            else slideSideways(dev.autotune.stepSlide(initialState, targetState))
        },
    ) { step ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (step) {
                // Done is the Tune screen showing the finished tune
                TuneStep.HOME, TuneStep.DONE -> TuneHome(s, vm, pick, go)
                // five steps: the fresh start is part of Tune itself, and the stock review heads the EQ step
                TuneStep.FRESH, TuneStep.SETUP -> SetupStep(s, vm, sharer, pick, exit, openCompanion)
                TuneStep.STOCK_RESULT, TuneStep.APPLY, TuneStep.STOCK, TuneStep.VERIFY -> ApplyStep(s, vm, pick, exit)
                TuneStep.RESULT -> ResultStep(s, vm, pick, openResults, exit)
            }
        }
    }
}

private const val STEPS = 5

// ------------------------------------------------------------------ home

@Composable
private fun TuneHome(s: UiState, vm: TuneViewModel, pick: Pickers, go: (Tab) -> Unit) {
    // no header here: the screen starts with the dial (the wordmark, the mode key, the title and the speaker art went)
    // the dial in the middle of the screen: it fills the space above the lines and keys under it
    val screenH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    Box(Modifier.fillMaxWidth().heightIn(min = (screenH * 0.58f).dp), contentAlignment = Alignment.Center) {
        PulsingButton("Tune", "new test", Ic.TUNE, enabled = !s.busy, onClick = vm::tuneStart)
        // the speaker says it all; Advanced adds controls, never a paragraph under it
    }
    // Simple, with nothing to fix: the mic and the EQ as one quiet line under the speaker instead of a deck
    val micReady = s.hasPermission && !s.micAlsoOutput && (s.phoneMic || (s.micName != null && s.calibrationName != null))
    if (micReady && !isAdvanced()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            QuietStatus(At.c.good, (if (s.phoneMic) "phone mic" else s.micName ?: "mic").lowercase())
            QuietStatus(if (s.systemEqOn) At.c.signal else At.c.muted, if (s.systemEqOn) "eq on" else "eq off")
        }
    } else Panel(pad = 14.dp) {
        MicLine(s, pick)
        Row(verticalAlignment = Alignment.CenterVertically) {
            AtIcon(Ic.POWER, if (s.systemEqOn) At.c.signal else At.c.muted, size = 18.dp)
            Text(
                "  " + say("EQ: ", "EQ currently in use: ") + if (s.systemEqOn) s.eqSource.ifBlank { "flat" } else say("off", "none (AutoTune EQ off)"),
                style = MaterialTheme.typography.bodyMedium, maxLines = 2,
            )
        }
    }
    TunedPanel(s, vm, pick, go)
    if (s.runs.isNotEmpty()) {
        SecondaryButton("Continue “${dev.autotune.shortTestName(s.testName, "").first}”", vm::tuneContinue, Modifier.fillMaxWidth(), icon = Ic.NEXT)
    }
    // AutoTune on a TV or a PC is reached through Set up now: Tune › Home theatre or PC speakers › Installed (2.20)
}

@Composable
private fun Check(icon: Ic, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        AtIcon(icon, At.c.signal, Modifier.padding(top = 1.dp, end = 12.dp), 20.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

// ------------------------------------------------------------------ 2. set up

/**
 * Set up is a few short screens, one question each (owner, 2026-11-12: "separate in steps ... not to overwhelm the
 * user"; see [dev.autotune.setupPages]): what is tuned, as four picture keys; for a home theatre or a PC, whether
 * AutoTune is on it (yes leaves for its page); the names; then how the test sound gets to the speakers, whose key
 * starts the stock reading. Simple never asks the target ([dev.autotune.simpleTarget]); Advanced asks it on the last
 * screen with the measurement options. Back walks the screens before it leaves Set up.
 */
@Composable
private fun SetupStep(s: UiState, vm: TuneViewModel, sharer: Sharer, pick: Pickers, exit: () -> Unit, openCompanion: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var equipName by rememberSaveable { mutableStateOf("") }
    // starts from the defaults in Settings (target, spots, window); on an output tuned before only the equipment and
    // the way the sound reaches the speakers are taken from last time, so a changed default always takes effect
    val mem = s.setupMemory?.takeIf { it.targetIdx in s.targetNames.indices }
    val kinds = s.targetNames.indices.map { dev.autotune.targetKind(Target.ALL.getOrNull(it)) }
    val defIdx = Target.ALL.indexOfFirst { it.name == s.defTarget }.takeIf { it >= 0 } ?: mem?.targetIdx ?: s.targetIdx
    val out = s.outputName
    var equipment by rememberSaveable(out) {
        mutableStateOf(dev.autotune.startEquipment(mem?.equipment, s.lastEquipment, s.carMode, kinds.getOrElse(defIdx) { dev.autotune.TargetKind.CAR }))
    }
    var page by rememberSaveable { mutableStateOf(dev.autotune.SetupPage.EQUIPMENT) }
    // the answer on the AutoTune screen; with it the names screen is the last and its key connects
    var viaCompanion by rememberSaveable(equipment) { mutableStateOf(equipment.companion != null && equipment in s.companionInstalled) }
    val pages = dev.autotune.setupPages(equipment, viaCompanion)

    val advanced = isAdvanced()
    val targetIdxs = dev.autotune.equipmentTargets(equipment, kinds)
    var advTargetIdx by rememberSaveable(out, equipment) { mutableIntStateOf(dev.autotune.equipmentTarget(equipment, kinds, defIdx, mem?.targetIdx)) }
    // Simple does not ask: Harman-style in a car, the room curve elsewhere
    val targetIdx = if (advanced) advTargetIdx else Target.ALL.indexOf(dev.autotune.simpleTarget(equipment)).coerceAtLeast(0)
    val routes = dev.autotune.equipmentRoutes(equipment)
    val memSource = mem?.let { m -> Source.entries.getOrNull(m.sourceIdx) }
    var route by rememberSaveable(out, equipment) { mutableStateOf(dev.autotune.equipmentRoute(equipment, memSource ?: s.source)) }
    val car = equipment == dev.autotune.Equipment.CAR
    var spotsIdx by rememberSaveable(car, out) {
        mutableIntStateOf(listOf(1, 3, 5).indexOf(s.defSpots).takeIf { it >= 0 } ?: if (car) 1 else 0)
    }
    var gateIdx by rememberSaveable(out) { mutableIntStateOf(listOf(0, 80, 200, 400, 600).indexOf(s.defGateMs).coerceAtLeast(0)) }
    val spots = listOf(1, 3, 5)

    fun next() { dev.autotune.nextSetupPage(equipment, page, viaCompanion)?.let { page = it } }
    val back: () -> Unit = { dev.autotune.previousSetupPage(equipment, page, viaCompanion)?.let { page = it } ?: vm.tuneBack() }
    // the phone's Back walks the screens too; on the first it is the Tune screen's own Back
    androidx.activity.compose.BackHandler(enabled = page != dev.autotune.SetupPage.EQUIPMENT && !s.busy) { back() }

    StepHeader(0, STEPS, "Set up", exit, onBack = back, names = dev.autotune.STEP_NAMES)
    val count = "${pages.indexOf(page) + 1} of ${pages.size}"
    when (page) {
        dev.autotune.SetupPage.EQUIPMENT -> {
            Panel {
                CardTitle(page.title, count)
                // a key picks and moves on: the next question follows from it
                EquipmentTiles(equipment, { equipment = it; next() }, enabled = !s.busy)
                if (mem?.equipment != null && out != null) Muted("as last time on $out")
            }
            // no key below: a picture key picks and moves on (owner, 2026-11-12: "this button is not necessary")
        }

        dev.autotune.SetupPage.COMPANION -> {
            val q = dev.autotune.companionQuestion(equipment) ?: return
            val installed = equipment in s.companionInstalled
            // either answer goes on to the names; with yes their key connects
            val connect = { viaCompanion = true; dev.autotune.nextSetupPage(equipment, page, true)?.let { page = it }; Unit }
            val phoneOnly = { viaCompanion = false; if (installed) vm.forgetCompanion(equipment); dev.autotune.nextSetupPage(equipment, page, false)?.let { page = it }; Unit }
            Panel { CardTitle("Is AutoTune on ${equipment.companion}?", count, info = q.info) }
            // two equal answers in the middle of the screen, nothing pinned or suggested (owner, 2026-11-12)
            val screenH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
            Box(Modifier.fillMaxWidth().heightIn(min = (screenH * 0.42f).dp), contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PictureKey("Yes, AutoTune is on ${equipment.companion}", false, connect, !s.busy, Modifier.weight(1f), minHeight = 160.dp) { ink ->
                        drawEquipment(equipment, ink)
                    }
                    PictureKey("No, use this phone only", false, phoneOnly, !s.busy, Modifier.weight(1f), minHeight = 160.dp) { ink ->
                        drawPhoneOnly(ink)
                    }
                }
            }
        }

        dev.autotune.SetupPage.NAMES -> {
            Panel {
                CardTitle(page.title, count)
                OutlinedTextField(
                    value = equipName, onValueChange = { equipName = it },
                    label = { Text("Equipment name") }, placeholder = { Text("e.g. " + equipNamePlaceholder(equipment, s.outputName)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Test name (optional)") }, placeholder = { Text(namePlaceholder(equipment)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
                )
            }
            // with AutoTune on the TV or PC this is the last screen: a new home theatre with these names
            if (viaCompanion && equipment.companion != null) PinAction(
                "Connect to AutoTune on ${equipment.companion}",
                { vm.rememberEquipment(equipment, true); openCompanion(dev.autotune.companionTheatreName(equipName, name)) }, icon = Ic.HOME,
            ) else PinAction("Next", ::next, icon = Ic.NEXT)
        }

        dev.autotune.SetupPage.SOUND -> {
            Panel {
                CardTitle(page.title, count, info = dev.autotune.stockChecklist(equipment))
                if (advanced) DropSelect(
                    targetIdxs.map { s.targetNames[it] }, targetIdxs.indexOf(advTargetIdx).coerceAtLeast(0), { advTargetIdx = targetIdxs[it] },
                    label = "Target sound", icons = targetIdxs.map { kindIcon(kinds[it]) },
                    details = targetIdxs.map { targetDetail(Target.ALL.getOrNull(it)) },
                )
                DropSelect(
                    routes.map { dev.autotune.routeLabel(equipment, it) }, routes.indexOf(route).coerceAtLeast(0), { route = routes[it] },
                    label = "How the sound reaches the speakers", icon = Ic.SPEAKER,
                )
                if (route == Source.PHONE) {
                    OutputPicker(s, vm)
                    Actions { if (s.playing) TextAction("Stop the test sound", vm::stopClip, icon = Ic.STOP) else TextAction("Test sound", vm::testSound, enabled = !s.busy, icon = Ic.PLAY) }
                } else OtherDeviceSignal(route, vm, sharer)
                MicLine(s, pick)
            }
            // spots and window keep sensible defaults and wait in Advanced
            if (advanced) Fold("Measurement options", summary = listOf("1 spot", "3 spots", "5 spots")[spotsIdx] + " · " + listOf("auto window", "80 ms", "200 ms", "400 ms", "600 ms")[gateIdx]) {
                DropSelect(
                    listOf("1 spot", "3 spots", "5 spots"), spotsIdx, { spotsIdx = it }, label = "Mic spots per reading", icon = Ic.MIC,
                    details = listOf("Quickest", "Recommended for a car: averages out one unlucky spot", "Most reliable"),
                )
                DropSelect(
                    listOf("Auto (80 ms for a car, 400 ms for a room)", "80 ms", "200 ms", "400 ms", "600 ms"), gateIdx, { gateIdx = it }, label = "Analysis window",
                )
            }
            // the tools for a closer look wait here, in the step they belong to (owner, 2026-11-11), not on the Tune home
            Adv {
                Fold("Advanced tools", summary = "Mic spots, channel, window, left/right, subwoofer") {
                    Settings(s, vm, s.hasPermission)
                    ExtraChecks(s, vm, s.hasPermission)
                }
            }
            // no stop in between: this key creates the test and starts the stock reading
            // the last prerequisite: the test sound must reach the equipment, not the phone's own speaker
            val blocked = dev.autotune.phoneSpeakerBlock(equipment, route == Source.PHONE, s.outputPhoneSpeaker)
            PinAction(
                "Measure the stock system",
                { vm.tuneSetup(name, targetIdx, route, spots[spotsIdx], listOf(null, 80, 200, 400, 600)[gateIdx], equipment, equipName) },
                icon = Ic.MEASURE, enabled = blocked == null, hint = blocked,
            )
        }
    }
}

/** An example equipment name: the output the phone plays to when there is one, else one for the kind. */
private fun equipNamePlaceholder(e: dev.autotune.Equipment, output: String?) = output?.takeIf { e != dev.autotune.Equipment.PC } ?: when (e) {
    dev.autotune.Equipment.CAR -> "F-150"
    dev.autotune.Equipment.HOME -> "LG soundbar"
    dev.autotune.Equipment.PC -> "Desk speakers"
    dev.autotune.Equipment.OTHER -> "Kitchen speaker"
}

private fun namePlaceholder(e: dev.autotune.Equipment) = when (e) {
    dev.autotune.Equipment.CAR -> "e.g. Driver seat"
    dev.autotune.Equipment.HOME -> "e.g. Sofa, middle seat"
    dev.autotune.Equipment.PC -> "e.g. Desk chair"
    dev.autotune.Equipment.OTHER -> "e.g. Kitchen table"
}

// ------------------------------------------------------------------ 3 / 5. measure

/** One finding: an icon for how much it matters, a title and what it means. */
@Composable
fun FindingRow(f: dev.autotune.Finding) {
    val (icon, tint) = when (f.severity) {
        2 -> Ic.ALERT to At.c.bad
        1 -> Ic.INFO to At.c.warn
        else -> Ic.DONE to At.c.good
    }
    // the title on the page, what it means in its bubble
    Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        AtIcon(icon, tint, Modifier.padding(end = 10.dp), 20.dp)
        Text(f.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        if (f.detail.isNotBlank()) InfoButton(f.title, f.detail, small = true)
    }
}

// ------------------------------------------------------------------ 4. apply

@Composable
private fun ApplyStep(s: UiState, vm: TuneViewModel, pick: Pickers, exit: () -> Unit) {
    val chain = s.exportChain
    val first = s.runs.size <= 1
    val latest = s.runs.lastOrNull()
    // equipment first when this test is applied there, or when an EQ check found the phone's EQ does not reach this output
    val bypassed = s.outputName?.let { it in s.eqBypassOutputs } == true
    var equipment by rememberSaveable { mutableStateOf(s.source != Source.PHONE || (s.applyWay == ApplyWay.EQUIPMENT && s.runs.size > 1) || bypassed) }
    var bandSet by rememberSaveable { mutableIntStateOf(1) }
    // "Suggested EQ" is AutoTune's proposal; anything else picked on the EQ tab is "the EQ currently in use"
    val isSuggestion = s.eqPick.startsWith("suggest:")
    StepHeader(2, STEPS, "Refine", exit, onBack = vm::tuneBack, names = dev.autotune.STEP_NAMES)
    // after the stock reading this step opens with its summary: the old stock review, folded in
    val a = s.stock
    var useAnyway by rememberSaveable { mutableStateOf(false) }
    if (first && a != null && s.runs.isNotEmpty()) {
        ResultSummary(s)
        if (a.suspicious && !useAnyway) {
            // a reading that looks wrong is measured again before any EQ is fitted to it
            PinAction("Measure the stock again", vm::tuneMeasureStock, icon = Ic.AGAIN)
            Actions {
                TextAction("Use this reading anyway", { useAnyway = true }, icon = Ic.NEXT)
                if (isAdvanced()) TextAction("Ask AI to review", vm::aiOpen, icon = Ic.AI)
            }
            return
        }
    }
    Panel {
        CardTitle(
            when {
                !isSuggestion -> "EQ currently in use"
                first -> "Suggested EQ"
                else -> "New suggested EQ after ${latest?.label}"
            },
            if (first) null else latest?.let { r ->
                val sc = dev.autotune.knownScore(r.index, s.selectedRuns.maxOrNull(), s.report?.overall, s.compare?.colorIdx.orEmpty(), s.compare?.scores.orEmpty(), s.stock?.score)
                "${r.label}: %.1f dB from target".format(r.rmsDb) + (sc?.let { " · score $it" } ?: "")
            },
        )
        if (!isSuggestion) {
            Muted(s.eqSource)
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
                    SecondaryButton("Preview in the app and measure", { vm.tuneApply(ApplyWay.IN_APP) }, Modifier.fillMaxWidth(), icon = Ic.PLAY)
                } else {
                    Warning("The phone's EQ damaged the treble on this phone before. Preview inside the app, or set it on your equipment.")
                    PinAction("Preview in the app and measure", { vm.tuneApply(ApplyWay.IN_APP) }, icon = Ic.PLAY)
                }
                SecondaryButton("Set it on my equipment", { equipment = true }, Modifier.fillMaxWidth(), icon = Ic.EDIT)
                s.systemEqMessage?.takeIf { !s.systemEqOn && it.isNotBlank() && it != "EQ off." }?.let { Warning(it) }
                if (s.systemEqSuspect) TextAction("Use the phone's EQ anyway", { pick.notifications(); vm.tuneApply(ApplyWay.AUTOTUNE_EQ) }, icon = Ic.POWER)
            } else {
                val (where, how) = dev.autotune.equipmentEqWhere(s.equipment)
                InfoLabel(where, how)
                EqListView(chain, s, vm)
                // Equalizer APO reads a file, so the PC's file sits with the bands, in Simple too
                if (s.equipment == dev.autotune.Equipment.PC) Actions {
                    TextAction("Save the EQ files", { vm.exportFiles(share = false) }, icon = Ic.SAVE)
                    TextAction("Share", { vm.exportFiles(share = true) }, icon = Ic.SHARE)
                }
                PinAction("It's set, measure", { vm.tuneApply(ApplyWay.EQUIPMENT) }, icon = Ic.NEXT)
                if (s.source == Source.PHONE) TextAction("Use the phone's EQ instead", { equipment = false }, icon = Ic.POWER)
            }
        }

        if (first && isAdvanced()) Actions {
            TextAction("Measure the stock again", vm::tuneMeasureStock, icon = Ic.AGAIN)
            TextAction("Ask AI to review", vm::aiOpen, icon = Ic.AI)
        }
        RunNote(s, vm)
        // everything else, in Advanced and closed until needed
        if (isAdvanced()) TargetSoundFold(s, vm)
        if (isAdvanced()) Fold("Adjust and files", summary = "Adjust by hand, exact bands, save or share files") {
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
            Actions {
                TextAction("Save files", { vm.exportFiles(share = false) }, icon = Ic.SAVE)
                TextAction("Share", { vm.exportFiles(share = true) }, icon = Ic.SHARE)
            }
            s.status?.takeIf { it.startsWith("Saved") || it.startsWith("Choose") || it.startsWith("Could") }?.let { Muted(it) }
        }
    }
}

// ------------------------------------------------------------------ 6. result

@Composable
private fun ResultStep(s: UiState, vm: TuneViewModel, pick: Pickers, openResults: () -> Unit, exit: () -> Unit) {
    var discard by remember { mutableStateOf(false) }
    val runs = s.runs
    val stock = runs.firstOrNull()
    val latest = runs.lastOrNull()
    val best = runs.drop(1).minByOrNull { it.rmsDb }

    StepHeader(4, STEPS, "Result", exit, onBack = vm::tuneBack, names = dev.autotune.STEP_NAMES)
    if (stock == null || latest == null || runs.size < 2) {
        // the header's back arrow returns to the EQ; no pinned key repeats it
        Muted("No run with the EQ yet.")
        return
    }
    ResultSummary(s)
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
        // the EQ check settled it: the EQ arrives (the run missed it) or this output bypasses the phone's EQ
        dev.autotune.NextMove.REPEAT -> PinAction("Measure again", vm::tuneRepeat, icon = Ic.AGAIN, hint = rec?.why)
        dev.autotune.NextMove.SET_ON_EQUIPMENT -> PinAction("Set it on my equipment", vm::tuneOnEquipment, icon = Ic.EDIT, hint = rec?.why)
        // a tune measured in the app's preview is measured once on the phone's EQ before it is kept there
        dev.autotune.NextMove.MEASURE_ON_PHONE_EQ -> PinAction(
            "Switch on the phone's EQ and measure", { pick.notifications(); vm.tuneOnPhoneEq(rec?.keepIdx ?: runs.lastIndex) }, icon = Ic.POWER, hint = rec?.why,
        )
    }
    s.eqCheck?.let { EqCheckCard(it, vm) }
    FollowAndChecks(s, vm)
    val move = rec?.move ?: dev.autotune.NextMove.REFINE
    RunNote(s, vm)
    Group("Other choices", divider = false) {
      Actions {
        if (move != dev.autotune.NextMove.REFINE && !latest.converged) TextAction("Another round with the new suggested EQ", vm::tuneRefine, icon = Ic.TUNE)
        if (move != dev.autotune.NextMove.KEEP_LATEST) TextAction("Keep this EQ and finish", { vm.tuneKeep(false) }, icon = Ic.DONE)
        if (move != dev.autotune.NextMove.KEEP_BEST && bestRun != null && bestRun != latest) TextAction("Keep the EQ of ${bestRun.label} and finish", { vm.tuneKeep(true) }, icon = Ic.UNDO)
        if (move == dev.autotune.NextMove.SET_ON_EQUIPMENT && phoneEq) TextAction("Measure again with the phone's EQ", vm::tuneRepeat, icon = Ic.AGAIN)
        // Simple keeps the moves that finish or continue; the checks and tools are in Advanced
        if (isAdvanced()) {
            if (move != dev.autotune.NextMove.REPEAT && move != dev.autotune.NextMove.SET_ON_EQUIPMENT) TextAction("Measure again, nothing changed", vm::tuneRepeat, icon = Ic.AGAIN)
            if (move == dev.autotune.NextMove.CHECK_EQ && phoneEq) TextAction("Apply the EQ again", { vm.tuneGo(TuneStep.APPLY) }, icon = Ic.POWER)
            if (move != dev.autotune.NextMove.CHECK_EQ && phoneEq) TextAction("Check the EQ reaches the speakers", vm::eqCheck, icon = Ic.CHECK)
            TextAction("Ask AI to review", vm::aiOpen, icon = Ic.AI)
            TextAction("All results of this test", openResults, icon = Ic.TESTS)
        }
        TextAction("Discard this run", { discard = true }, danger = true, icon = Ic.TRASH)
      }
    }
    if (isAdvanced()) TargetSoundFold(s, vm)
    if (isAdvanced()) DetailsHeading()
    if (isAdvanced()) Fold("Runs", summary = "${runs.size} readings, the latest against the stock") {
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

/**
 * A finished tune on the Tune screen (what Done used to be): stock and tuned readings, where the EQ now plays, and the
 * next useful step pinned: switch it on with the car it was tuned in, else open the EQ.
 */
@Composable
private fun TunedPanel(s: UiState, vm: TuneViewModel, pick: Pickers, go: (Tab) -> Unit) {
    s.status?.takeIf { it.startsWith("No run had") }?.let { Muted(it) }
    if (!dev.autotune.showsTuned(s.tunedTestId, s.tunedRuns, s.currentTestId, s.runs.size)) return
    val stock = s.runs.firstOrNull() ?: return
    val best = s.runs.drop(1).minByOrNull { it.rmsDb } ?: return
    Panel {
        CardTitle("Tuned")
        // the score beside the dB where it is already known for that run
        fun score(i: Int) = dev.autotune.knownScore(i, s.selectedRuns.maxOrNull(), s.report?.overall, s.compare?.colorIdx.orEmpty(), s.compare?.scores.orEmpty(), s.stock?.score)
        ReadingCells(listOf(Reading("%.1f".format(best.rmsDb), "dB", "tuned", score = score(best.index)), Reading("%.1f".format(stock.rmsDb), "dB", "stock", score = score(stock.index))))
        Muted(
            when {
                s.systemEqOn -> "AutoTune EQ is on with it."
                s.source == Source.PHONE && s.applyWay != ApplyWay.EQUIPMENT -> "Saved. Switch on AutoTune EQ to use it."
                else -> "Saved. Keep these bands on your equipment."
            },
        )
        s.status?.takeIf { it.startsWith("Switches on whenever") }?.let { Muted(it) }
    }
    val playsOnPhone = s.source == Source.PHONE && s.applyWay != ApplyWay.EQUIPMENT
    val route = s.btRoute
    if (route != null && dev.autotune.offerAutoSwitch(route, playsOnPhone, s.tunedProfileId, s.rules, s.automationOn)) {
        PinAction("Use this EQ whenever ${route.name} connects", { pick.bluetooth(); vm.autoSwitchToTuned() }, icon = Ic.BLUETOOTH)
    }
    // no "Open the EQ" key: the EQ tab is right under it
}

/** Advanced: a note saved with the next run (what was changed for it). */
@Composable
private fun RunNote(s: UiState, vm: TuneViewModel) {
    if (!isAdvanced()) return
    var note by rememberSaveable { mutableStateOf(false) }
    if (note || s.runNote.isNotBlank()) {
        OutlinedTextField(
            value = s.runNote, onValueChange = vm::setRunNote,
            label = { Text("Note for the next run") }, placeholder = { Text("e.g. tweeters down 2 dB") },
            singleLine = true, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
        )
    } else Actions { TextAction("Add a note to the next run", { note = true }, icon = Ic.EDIT) }
}

// ------------------------------------------------------------------ pieces

/** One line when the mic is fine; the fixes only when something is missing. */
/** A pilot light and a small lowercase label: a status read at a glance. */
@Composable
private fun QuietStatus(light: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(light))
        Text(label, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall, color = At.c.muted, maxLines = 1)
    }
}

@Composable
private fun MicLine(s: UiState, pick: Pickers) {
    val ready = s.hasPermission
    Alerts(listOfNotNull(if (s.micAlsoOutput) "Your phone also sees the mic as headphones, so sound can end up in the mic instead of the car. Use Test sound to check, and pick the car under Plays through if needed." else null), "Microphone")
    if (ready && s.phoneMic) {
        StatusLine(true, say("Phone microphone", "Phone microphone. Good for checking, a USB mic is more accurate."))
        return
    }
    if (ready && s.calibrationName != null) {
        StatusLine(true, "${s.micName} with calibration")
        return
    }
    if (s.micName == null) StatusLine(false, "No USB microphone. Plug it into the USB-C port.")
    else StatusLine(true, "USB microphone: ${s.micName}")
    if (!s.hasPermission) StatusLine(false, "Microphone access needed")
    if (s.calibrationName == null && !s.phoneMic) StatusLine(false, say("No calibration file", "No calibration file. Results include the mic's own response."))
    Actions {
        if (!s.hasPermission) PrimaryButton("Allow microphone", pick.permission, icon = Ic.MIC)
        if (s.calibrationName == null && !s.phoneMic) TextAction("Import calibration", pick.calibration, icon = Ic.FILE)
    }
}

@Composable
private fun CardTitle(title: String, sub: String? = null, info: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = At.c.muted, maxLines = 1)
        }
        if (info != null) InfoButton(title, info)
    }
}

@Composable
private fun Bullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 8.dp, end = 10.dp).size(5.dp).clip(CircleShape).background(At.c.muted))
        Text(text, style = MaterialTheme.typography.bodyMedium)
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
    Alerts(
        listOfNotNull(
            if (s.outputToMic) "That is the USB mic, not your speakers. Pick your car in the list above." else null,
            if (s.audioCaptured) "Another app is capturing the phone's sound (screen recorder or casting). Stop it before measuring: it can change what reaches the speakers." else null,
            dev.autotune.phoneEqReachWarning(s.outputName, s.carMode, s.eqBypassOutputs),
        ),
        "Before measuring",
    )
}

@Composable
internal fun LevelResult(s: UiState) {
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
        info = "Several spots around the seat are averaged, so one unlucky dip doesn't steer the EQ.",
    )
    if (s.positionsDone > 0) TextAction("Start the spots over", vm::resetPositions, icon = Ic.UNDO)
    if (s.source == Source.PHONE) {
        DropSelect(
            OutChannel.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, OutChannel.entries.indexOf(s.channel),
            { vm.setChannel(OutChannel.entries[it]) }, enabled = !s.busy, label = "Play through", icon = Ic.SPEAKER,
        )
    }
    TextAction("Level check", vm::levelCheck, enabled = ready && !s.busy, icon = Ic.LIVE)
    LevelResult(s)
    val fdwOpts = listOf(0, 15, 8, 5)
    DropSelect(
        fdwOpts.map { if (it == 0) "Fixed window" else "Frequency-dependent, $it cycles" }, fdwOpts.indexOf(s.fdwCycles).coerceAtLeast(0),
        { vm.setFdw(fdwOpts[it]) }, enabled = !s.busy, label = "Analysis window",
        info = if (s.fdwCycles == 0) "Fixed: the first ${s.gateMs} ms after the direct sound, for this test."
        else "Frequency-dependent window as in REW: each frequency is read over ${s.fdwCycles} cycles of its own period, up to one second after the direct sound (the fixed gate does not limit it). The bass gets long windows and the treble short ones, so late reflections count less where they cannot be separated anyway. Applies to the next measurement.",
    )
    if (s.runs.isNotEmpty()) TextAction("Measure the stock system again", { confirm = true }, enabled = ready && !s.busy, danger = true, icon = Ic.AGAIN)
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Measure the stock system again?") },
            text = { Text("It replaces the stock reading and deletes every run after it. AutoTune EQ is switched off first.") },
            confirmButton = { TextButton(onClick = { confirm = false; vm.tuneMeasureStock() }) { Text("Measure now", color = At.c.bad) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun OtherDeviceSignal(source: Source, vm: TuneViewModel, sharer: Sharer) {
    when (source) {
        Source.PHONE -> Unit
        Source.TEST_FILE -> {
            Bullet(say("Tap Measure, then play the sweep file on the other device.", "Tap Measure, then play the sweep file on the other device. The file starts with three beeps; the app waits for them (up to 30 seconds) and stops by itself right after the sweep, about 10 seconds in all."))
            TextAction("Send the sweep file", { vm.shareTestFile(TestFileKind.SWEEP) }, icon = Ic.SHARE)
        }
        Source.COMBINED -> {
            Bullet(say("Tap Measure, then play the file on the other device.", "Tap Measure, then play the file on the other device. It starts with three beeps, then a sweep and pink noise, about 20 seconds in all. The app waits for the beeps and stops by itself."))
            TextAction("Send the file", { vm.shareTestFile(TestFileKind.COMBINED) }, icon = Ic.SHARE)
        }
        Source.YOUTUBE -> {
            Bullet("Start pink noise on the other device, then tap Measure. It listens for 22 seconds.")
            Actions {
                TextAction("Find pink noise", { sharer.text(YOUTUBE_SEARCH) }, icon = Ic.PLAY)
                TextAction("Send our file", { vm.shareTestFile(TestFileKind.PINK) }, icon = Ic.SHARE)
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
        if (!sweepRoute) {
            Actions {
                TextAction("Send left sweep", { vm.shareTestFile(TestFileKind.SWEEP_LEFT) }, icon = Ic.SHARE)
                TextAction("Send right sweep", { vm.shareTestFile(TestFileKind.SWEEP_RIGHT) }, icon = Ic.SHARE)
            }
        }
        Actions {
            TextAction("Measure left", { vm.measureSide(OutChannel.LEFT) }, enabled = ready && !s.busy, icon = Ic.MEASURE)
            TextAction("Measure right", { vm.measureSide(OutChannel.RIGHT) }, enabled = ready && !s.busy, icon = Ic.MEASURE)
            if (s.lr != null) TextAction("Reset", vm::resetLr)
        }
        s.lr?.let { lr ->
            lr.result.advice.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Panel { ResponseChart(lr.chart, height = 170.dp) }
        }

        Text("Subwoofer and crossover", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        val steps = listOf("MAINS" to "Mains only", "SUB" to "Sub only", "SUM" to "Both, sub normal", "SUM_INV" to "Both, sub inverted")
        Actions {
            steps.forEach { (k, label) ->
                if (k in s.subDone) TextAction("Done: $label", { vm.measureSub(k) }, enabled = ready && !s.busy, icon = Ic.CHECK)
                else PrimaryButton(label, { vm.measureSub(k) }, enabled = ready && !s.busy, icon = Ic.MEASURE)
            }
            if (s.subDone.isNotEmpty()) TextAction("Reset", vm::resetSub)
        }
        s.sub?.notes?.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (s.busy) StatusBlock(s.status, s.warnings) else StatusBlock(s.status?.takeIf { s.lr != null || s.sub != null || it.startsWith("Failed") }, s.warnings)
    }
}
