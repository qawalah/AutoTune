@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.autotune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.autotune.TestSummary
import dev.autotune.TuneViewModel
import dev.autotune.UiState
import dev.autotune.dsp.SystemReport

/** The list of tests. One tap on a test opens its results; a new test starts from the button at the bottom. */
@Composable
fun TestsTab(
    s: UiState, vm: TuneViewModel, openResults: (String) -> Unit, continueTest: (String) -> Unit = {}, openTheatre: (String) -> Unit = {},
    openCompanion: () -> Unit = {},
) {
    val readingCount = s.tests.sumOf { it.runCount }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // the home theatres, read from their files once the tab opens
    val theatres by androidx.compose.runtime.produceState(emptyList<dev.autotune.theatre.TheatreTest>()) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dev.autotune.theatre.TheatreTests.list(ctx.filesDir) }
    }
    var renameId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteId by remember { mutableStateOf<String?>(null) }
    ScreenTitle("Tests", context = if (s.tests.isEmpty()) null else dev.autotune.countText(s.tests.size, "system") + " · " + dev.autotune.countText(readingCount, "reading"))
    // the second way to AutoTune on a TV or a PC, beside Tune › Set up (owner, 2026-11-12); after the first connection
    // it is one row for the remembered TV or PC, connected or not (2.36)
    dev.autotune.theatre.CompanionLink.init(ctx)
    val linkOpen by dev.autotune.theatre.CompanionLink.link.open.collectAsState()
    val remembered = dev.autotune.theatre.CompanionLink.rememberedName(ctx)
    if (remembered == null) Panel(pad = 6.dp) { Actions { TextAction("Connect to AutoTune on a TV or PC", openCompanion, icon = Ic.HOME, opens = true) } }
    else Column(Modifier.fillMaxWidth().settleIn().deckPlate()) {
        DeckColors {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClickLabel = "Open $remembered", onClick = openCompanion).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (linkOpen) At.c.signal else At.c.line))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(remembered, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Muted(if (linkOpen) "connected" else "not connected")
                }
                AtIcon(Ic.CHEVRON, At.c.muted, size = 18.dp)
            }
        }
    }
    if (theatres.isNotEmpty()) TheatreList(theatres, openTheatre)
    if (s.tests.isEmpty()) {
        if (theatres.isNotEmpty()) return
        // the title and one fact; what a test is lives in the bubble, the key below starts one
        Group(
            "No tests yet", divider = false,
            info = "A test is one car or one room. It keeps a stock reading of the system as it is now, then every run after you add EQ.",
        ) { Muted("New test starts one") }
        return
    }
    // the list is one deck, ruled between its rows like the tracks on a record sleeve
    Column(Modifier.fillMaxWidth().settleIn().deckPlate()) {
        DeckColors {
            s.tests.forEachIndexed { i, t ->
                if (i > 0) HorizontalDivider(color = At.c.line, modifier = Modifier.padding(start = 90.dp))
                TestRow(
                    t, t.id == s.currentTestId, onOpen = { openResults(t.id) },
                    // a finished test is tuned further, not continued (2.32)
                    finished = dev.autotune.testFinished(dev.autotune.hasKeptEq(s.profiles, t.id), if (t.id == s.currentTestId) s.resumeStep else null),
                    onContinue = { continueTest(t.id) },
                    onRename = { renameId = t.id; renameText = t.name },
                    onDelete = { deleteId = t.id },
                )
            }
        }
    }
    renameId?.let { id ->
        AlertDialog(
            onDismissRequest = { renameId = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = atFieldColors(), shape = atFieldShape())
            },
            confirmButton = { DialogKey("Save", { vm.renameTest(id, renameText); renameId = null }) },
            dismissButton = { DialogKey("Cancel", { renameId = null }) },
        )
    }
    deleteId?.let { id ->
        val name = s.tests.firstOrNull { it.id == id }?.let { dev.autotune.shortTestName(it.name, it.target).first } ?: "this test"
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete $name?") },
            text = { Text(DELETE_TEST_TEXT) },
            confirmButton = { DialogKey("Delete", { vm.deleteTest(id); deleteId = null }, danger = true) },
            dismissButton = { DialogKey("Keep it", { deleteId = null }) },
        )
    }
}

/** What deleting a test takes with it, in one line (2.36): the kept EQ goes too; EQs saved by hand stay. */
private const val DELETE_TEST_TEXT = "Its readings and its EQ are deleted. EQs you saved yourself stay."

/** The home theatres, one row each: its name, its runs and when it was last tested. Opens on the Home theatre page. */
@Composable
private fun TheatreList(theatres: List<dev.autotune.theatre.TheatreTest>, open: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().settleIn().deckPlate()) {
        DeckColors {
            theatres.forEachIndexed { i, t ->
                if (i > 0) HorizontalDivider(color = At.c.line, modifier = Modifier.padding(start = 90.dp))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClickLabel = "Open ${t.name}", onClick = { open(t.id) })
                        .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EquipmentBadge(dev.autotune.Equipment.HOME, Modifier.padding(end = 12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(t.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        val last = t.runs.lastOrNull()
                        Muted(listOfNotNull(
                            last?.layout?.title,
                            if (t.runs.isEmpty()) "no runs" else "${t.runs.size} ${if (t.runs.size == 1) "run" else "runs"}",
                            last?.let { java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(it.whenMillis)) },
                        ).joinToString(" · "))
                    }
                    AtIcon(Ic.CHEVRON, At.c.muted, size = 18.dp)
                }
            }
        }
    }
}

@Composable
private fun TestRow(t: TestSummary, isOpen: Boolean, onOpen: () -> Unit, onContinue: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, finished: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().settleIn().heightIn(min = 76.dp).clickable(onClickLabel = "Open the results of ${t.name}", onClick = onOpen)
            .padding(start = 14.dp, end = 2.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // what it tunes, drawn as on Set up; the open test is marked once, by its pilot light
        EquipmentBadge(t.equipment, Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // an automatic name reads as its date; the target is not repeated on every row
                val shown = dev.autotune.shortTestName(t.name, t.target).first
                Text(shown, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (isOpen) {
                    // the pilot light says what it marks (2.32)
                    Box(Modifier.padding(start = 8.dp).size(8.dp).clip(CircleShape).background(At.c.signal))
                    Text("open", Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelSmall, color = At.c.muted, maxLines = 1)
                }
            }
            val auto = dev.autotune.shortTestName(t.name, t.target).first != t.name
            Muted(listOfNotNull(if (t.runCount == 0) "no readings" else "${t.runCount} ${if (t.runCount == 1) "reading" else "readings"}", if (auto) null else t.whenText.split(" ").take(2).joinToString(" ")).joinToString(" · "))
        }
        if (t.stockRms != null && t.latestRms != null) {
            val better = t.runCount > 1 && t.latestRms < t.stockRms - 0.05
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    // readings large and light, like the display; the stock it came from small beside it
                    if (t.runCount > 1) Text("%.1f → ".format(t.stockRms), Modifier.padding(bottom = 3.dp), style = MaterialTheme.typography.labelMedium, fontFamily = AtFigures, color = At.c.muted)
                    Text(
                        "%.1f".format(if (t.runCount > 1) t.latestRms else t.stockRms),
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp, lineHeight = 32.sp), color = if (better) At.c.good else At.c.text,
                    )
                }
                Text("dB from target", style = MaterialTheme.typography.labelSmall, color = At.c.muted)
                if (t.runCount > 1) {
                    val change = t.stockRms - t.latestRms
                    Text(
                        when {
                            change > 0.3 -> "%.1f dB closer".format(change)
                            change < -0.3 -> "%.1f dB further".format(-change)
                            else -> "about the same"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when { change > 0.3 -> At.c.good; change < -0.3 -> At.c.warn; else -> At.c.muted },
                    )
                } else Text("stock", style = MaterialTheme.typography.labelSmall, color = At.c.muted)
            }
        }
        // the quick actions: open, continue tuning, rename, delete
        var menu by remember { mutableStateOf(false) }
        Box {
            IconAction(Ic.MORE, "Actions for ${t.name}") { menu = true }
            AtMenu(expanded = menu, onDismiss = { menu = false }) {
                androidx.compose.material3.DropdownMenuItem(text = { Text("Open results") }, leadingIcon = { AtIcon(Ic.TESTS, At.c.muted) }, onClick = { menu = false; onOpen() })
                androidx.compose.material3.DropdownMenuItem(text = { Text(if (finished) "Tune further" else "Continue tuning") }, leadingIcon = { AtIcon(Ic.TUNE, At.c.muted) }, onClick = { menu = false; onContinue() })
                androidx.compose.material3.DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { AtIcon(Ic.EDIT, At.c.muted) }, onClick = { menu = false; onRename() })
                androidx.compose.material3.DropdownMenuItem(text = { Text("Delete", color = At.c.bad) }, leadingIcon = { AtIcon(Ic.TRASH, At.c.bad) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

// ------------------------------------------------------------------ the results of one test (a page)

/** A test's results, opened over the tab it came from; [back] returns there. */
@Composable
fun ResultsPage(s: UiState, vm: TuneViewModel, back: () -> Unit, go: (Tab) -> Unit) {
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var discard by remember { mutableStateOf(false) }
    // the test went away (deleted elsewhere): nothing to show here
    if (s.currentTestId == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { back() }
        return
    }

    // the test's short name only; its target and route are in the details
    ScreenTitle(dev.autotune.shortTestName(s.testName, dev.autotune.dsp.Target.ALL[s.targetIdx.coerceIn(0, dev.autotune.dsp.Target.ALL.size - 1)].name).first)

    if (s.runs.isNotEmpty()) {
        // the same summary as the Tune result screens; the page title names the test
        ResultSummary(s, namesTest = false)
        // the one action at the bottom, with the move it leads to on its second line and why in its hint
        // a finished test is not asking to be tuned again: its main key opens the EQ, and Continue is one of the other choices
        val finished = dev.autotune.testFinished(dev.autotune.hasKeptEq(s.profiles, s.currentTestId), s.resumeStep)
        val (next, why) = resultNext(s)
        if (finished) PinAction("Open the EQ", { go(Tab.EQ) }, icon = Ic.EQ)
        else PinAction("Continue tuning", { vm.tuneContinue(); go(Tab.MEASURE) }, icon = Ic.TUNE, hint = why, sub = next)
        // The response is the visual proof of the result; keep its legend directly with the plot.
        s.chart?.let { c ->
            Panel {
                ResponseChart(c, height = 220.dp, showTarget = s.showTargetResults, onToggleTarget = { vm.setShowTarget(true, !s.showTargetResults) })
            }
        }
        // the runs to draw, right under the graph they change; any run but the stock can be deleted from here
        RunsDeck(s, vm)
    }

    if (s.runs.isEmpty()) {
        // the title, its why in the bubble; the one move is the main key, as after every reading
        Group(
            "No readings yet", divider = false,
            info = "Measure the stock system first. It becomes the reference every later run is compared with.",
        ) { Muted("stock reading first") }
        PinAction("Start tuning", { vm.tuneContinue(); go(Tab.MEASURE) }, icon = Ic.TUNE)
    } else {
        FollowAndChecks(s, vm)
        // the other choices, under the result and above the details, one per row
        val finishedTest = dev.autotune.testFinished(dev.autotune.hasKeptEq(s.profiles, s.currentTestId), s.resumeStep)
        Actions {
            if (finishedTest) TextAction("Tune further", { vm.tuneContinue(); go(Tab.MEASURE) }, icon = Ic.TUNE, opens = true)
            else TextAction("Open the EQ", { go(Tab.EQ) }, icon = Ic.EQ, opens = true)
            TextAction("AI review", vm::aiOpen, icon = Ic.AI, opens = true)
        }
        // Simple stops here: the summary, the graph, the checks and the other choices; the rest is in Advanced
        if (isAdvanced()) {
        TargetSoundFold(s, vm)
        DetailsHeading()
        s.stock?.takeIf { it.findings.isNotEmpty() }?.let { a ->
            Fold("Stock findings", summary = "${a.findings.size} from the stock reading") { a.findings.forEach { FindingRow(it) } }
        }
        Fold("Graph options", summary = "Smoothing, and the RTA bar view") {
            val opts = listOf(0, 24, 12, 6, 3)
            DropSelect(opts.map { if (it == 0) "No smoothing" else "1/$it octave smoothing" }, opts.indexOf(s.smoothing).coerceAtLeast(0), { vm.setSmoothing(opts[it]) }, label = "Smoothing")
            val b = s.bars
            if (b != null && b.runs.isNotEmpty()) {
                val main = b.runs.last()
                Panel { RtaBars(main.bars, b.target, ghosts = b.runs.dropLast(1).map { it.bars to runColor(it.colorIdx) }, caption = false) }
                Muted("Bars: ${main.label}" + if (b.runs.size > 1) ". Caps: " + b.runs.dropLast(1).joinToString { it.label } else "")
            }
        }

        s.distortion?.let { d ->
            Fold("Distortion", summary = "THD %.1f%% on average".format(d.averagePct(100.0, 5000.0)), info = "Read from the sweep, as REW does. The 2nd and 3rd harmonics are the extra tones the system creates at two and three times the test frequency. THD is their combined level as a percent of the test tone. Car speakers are often 0.5 to 3 percent in the bass and mids; a number that climbs as you raise the volume means the system is being pushed too hard.") {
                Text("THD %.1f%% on average, 100 Hz to 5 kHz".format(d.averagePct(100.0, 5000.0)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Panel(pad = 12.dp) {
                    TableRow(listOf("Test tone", "HD2", "HD3", "THD"), header = true)
                    listOf(100.0, 200.0, 500.0, 1000.0, 2000.0, 4000.0).forEach { f ->
                        val i = d.freqs.indexOfFirst { it >= f }
                        if (i >= 0) TableRow(listOf(if (f >= 1000) "%.0f kHz".format(f / 1000) else "%.0f Hz".format(f), "%.0f dB".format(d.hd2Db[i]), "%.0f dB".format(d.hd3Db[i]), "%.2f%%".format(d.thdPct[i])))
                    }
                }
            }
        }

        s.compare?.let { c ->
            Fold("Compare runs", summary = c.labels.joinToString(" · ") { it.removeSuffix(" (no EQ)") }) {
                Text(c.verdict, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Panel(pad = 12.dp) {
                    TableRow(listOf("") + c.labels.map { it.removeSuffix(" (no EQ)") }, header = true, colors = c.colorIdx)
                    TableRow(listOf("Error vs target") + c.rms.map { "%.2f dB".format(it) }, bold = true)
                    TableRow(listOf("System score") + c.scores.map { "$it" }, bold = true)
                    c.regionNames.forEachIndexed { ri, n -> TableRow(listOf(n) + c.regionErr[ri].map { "%+.1f".format(it) }) }
                }
            }
        }

        s.report?.let { ReportCard(it, s.runs.firstOrNull { r -> r.index == (s.selectedRuns.maxOrNull() ?: 0) }?.label ?: "") }

        if (s.regions.isNotEmpty()) {
            Fold("Tonal balance", summary = s.regions.filter { kotlin.math.abs(it.errDb) >= 1.5 }.size.let { if (it == 0) "On target in every region" else "$it regions off target" }) {
                s.regions.forEach {
                    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${it.name}  ", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(it.verdict, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        ResonanceGroup(s)

        // the link between results and the EQ
        val sug = s.suggestedChain
        Fold("Suggested EQ for this result", summary = sug?.takeIf { it.filters.isNotEmpty() }?.let { "${it.filters.size} bands" } ?: "None needed") {
            if (sug == null || sug.filters.isEmpty()) {
                Text("Already close to the target. No EQ needed.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    "AutoTune suggests ${sug.filters.size} bands, preamp %.1f dB, based on ${s.runs.firstOrNull { r -> r.index == (s.selectedRuns.maxOrNull() ?: 0) }?.label ?: "this run"}.".format(sug.preampDb),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextAction("Open in EQ", { go(Tab.EQ) }, icon = Ic.EQ, opens = true)
        }

        Fold(
            "Keep this sound as a target",
            info = "If a tuned system sounds exactly how you like it, save its measured response as a target. Next time, pick it when you set up a test and AutoTune tunes towards that sound. The latest ticked run is saved.",
        ) {
            var targetName by remember { mutableStateOf("") }
            val focusLabel = s.runs.firstOrNull { it.index == (s.selectedRuns.maxOrNull() ?: 0) }?.label
            OutlinedTextField(
                value = targetName, onValueChange = { targetName = it }, singleLine = true,
                label = { Text("Name") }, placeholder = { Text("e.g. My car sound") },
                modifier = Modifier.fillMaxWidth().settleIn().bringIntoViewOnFocus(), colors = atFieldColors(), shape = atFieldShape(),
            )
            TextAction("Save ${focusLabel ?: "latest run"} as a target", { vm.saveRunAsTarget(targetName.ifBlank { "My curve" }); targetName = "" }, icon = Ic.CURVE)
        }

        Fold("Notes", summary = s.testNotes.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }?.take(60) ?: "None yet") {
            OutlinedTextField(
                value = s.testNotes, onValueChange = vm::setTestNotes,
                label = { Text("About this setup") },
                placeholder = { Text("Seat, mic spot, amp gain, what was installed") },
                modifier = Modifier.fillMaxWidth().settleIn().bringIntoViewOnFocus(), colors = atFieldColors(), shape = atFieldShape(),
            )
        }
            }
    }

    Fold("Manage test", summary = "Rename, discard the latest run, delete") {
        Actions {
            TextAction("Rename", { rename = true; renameText = s.testName }, icon = Ic.EDIT)
            TextAction("Delete test", { delete = true }, danger = true, icon = Ic.TRASH)
        }
        if (s.runs.size >= 2) TextAction("Discard latest run", { discard = true }, danger = true, icon = Ic.TRASH)
    }

    if (discard) {
        val last = s.runs.lastOrNull()
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text("Discard ${last?.label ?: "the latest run"}?") },
            text = { Text("The reading is deleted and the suggested EQ goes back to the run before it. The stock run is never deleted.") },
            confirmButton = { DialogKey("Discard", { discard = false; vm.discardLastRun() }, danger = true) },
            dismissButton = { DialogKey("Keep it", { discard = false }) },
        )
    }

    if (rename) {
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Rename test") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = atFieldColors(), shape = atFieldShape()) },
            confirmButton = { DialogKey("Save", { s.currentTestId?.let { id -> vm.renameTest(id, renameText) }; rename = false }) },
            dismissButton = { DialogKey("Cancel", { rename = false }) },
        )
    }
    if (delete) {
        AlertDialog(
            onDismissRequest = { delete = false },
            title = { Text("Delete this test?") },
            text = { Text(DELETE_TEST_TEXT) },
            confirmButton = { DialogKey("Delete", { delete = false; back(); s.currentTestId?.let { id -> vm.deleteTest(id) } }, danger = true) },
            dismissButton = { DialogKey("Cancel", { delete = false }) },
        )
    }
}


@Composable
private fun TableRow(cells: List<String>, header: Boolean = false, bold: Boolean = false, colors: List<Int> = emptyList()) {
    Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        cells.forEachIndexed { i, text ->
            if (i == 0) {
                Text(text, Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall, color = At.c.muted)
            } else {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (header && colors.getOrNull(i - 1) != null) {
                        Box(Modifier.padding(end = 5.dp).size(8.dp).clip(CircleShape).background(runColor(colors[i - 1])))
                    }
                    Text(
                        text, maxLines = 1,
                        style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                        fontFamily = if (header) AtSans else AtFigures,
                        fontWeight = if (bold) FontWeight.Medium else null,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ report card and resonances

@Composable
fun ReportCard(r: SystemReport, label: String) {
    Fold(
        "Report card",
        summary = "${r.overall} / 100 · ${r.grade}" + if (r.potential > r.overall + 2) " · about ${r.potential} after EQ" else "",
        info = "Scores the system itself, not just its tone curve.\n\n" +
            "Tonal balance and smoothness: how close and how even the response is to your target. EQ can fix most of this.\n" +
            "Bass and treble extension: how low and high the system still plays within 6 dB of the target. Set by the speakers; EQ can't add what isn't there.\n" +
            "Resonances: narrow peaks and ringing from the room or cabin.\n" +
            "Left and right match: only after the left and right check on the Tune tab.\n\n" +
            "The “after EQ” figure estimates what the suggested EQ would reach. The score is a guide for comparing runs, not an absolute rating.",
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${r.overall}", style = MaterialTheme.typography.displayLarge)
            Text(" / 100", Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodyMedium, color = At.c.muted)
            Column(Modifier.padding(start = 16.dp, bottom = 6.dp)) {
                Text(r.grade, style = MaterialTheme.typography.titleMedium)
                if (r.potential > r.overall + 2) Muted("about ${r.potential} after EQ")
            }
        }
        // the verdict is an explanation: in its bubble, with the run it is based on as the one fact line
        Row(verticalAlignment = Alignment.CenterVertically) {
            Muted("based on ${label.lowercase()}", Modifier.weight(1f))
            InfoButton("Report card", r.verdict, small = true)
        }
        // the meters fill when they come into view, twice, then stay (a pull at the page's top fills them again)
        val fill = rememberArrival("report", 700)
        Column(Modifier.fillMaxWidth().arrival(fill), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        r.categories.forEach { c ->
            // one meter per category, like the level meter of a Braun receiver: a lowercase label, the figure, a
            // square slot ruled at 50 and 75 with an ink bar; what the category means sits in its bubble
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        if (c.score < 50) AtIcon(Ic.ALERT, At.c.warn, Modifier.padding(end = 6.dp), size = 16.dp)
                        Text(
                            c.name.lowercase() + if (c.eqFixable) "" else " · hardware or room", Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.labelMedium, color = At.c.muted, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        InfoButton(c.name, c.detail, small = true)
                    }
                    Text("${c.score}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Light), fontFamily = AtFigures)
                }
                val meter = At.c
                androidx.compose.foundation.Canvas(
                    Modifier.fillMaxWidth().height(8.dp).semantics { contentDescription = "${c.name}: ${c.score} of 100" },
                ) {
                    val hair = 1.dp.toPx()
                    drawRect(meter.bg)
                    drawRect(meter.text.copy(alpha = 0.75f), size = androidx.compose.ui.geometry.Size(size.width * (c.score / 100f).coerceIn(0f, 1f) * fill.value, size.height))
                    listOf(0.5f, 0.75f).forEach { t -> drawLine(meter.line, androidx.compose.ui.geometry.Offset(size.width * t, 0f), androidx.compose.ui.geometry.Offset(size.width * t, size.height), hair) }
                    drawRect(meter.line, style = androidx.compose.ui.graphics.drawscope.Stroke(hair))
                }
            }
        }
        }
    }
}

@Composable
private fun ResonanceGroup(s: UiState) {
    if (s.resonances.isEmpty()) return
    Fold(
        "Resonances (${s.resonances.size})",
        summary = s.resonances.sortedByDescending { it.prominenceDb }.take(3).joinToString { "%.0f Hz".format(it.freqHz) },
        info = "Found in the stock run only, with two tests.\n\n" +
            "Peak test: the response is smoothed to 1/24 octave and compared with its own 1-octave median. A peak counts when it stands at least 4 dB above that local level and is narrow (Q 1.5 or higher), between 25 Hz and 12 kHz.\n\n" +
            "Ring test (sweep runs only): the impulse response is cut into slices and the level at that frequency is followed over time. The time to fall 20 dB, times 3, estimates T60, how long it takes to decay by 60 dB. A long ring means the problem is the room or cabin itself, which EQ can't shorten.\n\n" +
            "The pink-noise route has no impulse response, so it finds peaks but no ring times.",
    ) {
        // a table: fixed columns under headings, figures right-aligned and tabular, the (i) in its own column
        @Composable
        fun androidx.compose.foundation.layout.RowScope.cells(a: String, b: String, c: String, d: String, head: Boolean) {
            val st = if (head) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleSmall
            val col = if (head) At.c.muted else At.c.text
            val end = androidx.compose.ui.text.style.TextAlign.End
            Text(a, Modifier.weight(1.1f), style = st, color = col, fontFamily = AtFigures, textAlign = end, maxLines = 1)
            Text(b, Modifier.weight(1f), style = st, color = col, fontFamily = AtFigures, textAlign = end, maxLines = 1)
            Text(c, Modifier.weight(0.8f), style = st, color = col, fontFamily = AtFigures, textAlign = end, maxLines = 1)
            Text(d, Modifier.weight(1.1f), style = st, color = col, fontFamily = AtFigures, textAlign = end, maxLines = 1)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            cells("hz", "db", "q", "rings, ms", head = true)
            Box(Modifier.width(48.dp))
        }
        s.resonances.forEach { r ->
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                cells(
                    "%.0f".format(r.freqHz), "+%.1f".format(r.prominenceDb), "%.1f".format(r.q),
                    r.t60Ms?.let { (if (r.t60IsLowerBound) ">" else "~") + "%.0f".format(it) } ?: "—", head = false,
                )
                Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) { InfoButton("%.0f Hz".format(r.freqHz), r.suggestion, small = true) }
            }
        }
    }
}


/** The widths of the runs list's score and dB columns: the widest box ("100/100") and figure ("99.9 dB") at this font scale. */
private class RunColumns(val score: androidx.compose.ui.unit.Dp, val db: androidx.compose.ui.unit.Dp)

@Composable
private fun runColumns(): RunColumns {
    val tm = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val t = MaterialTheme.typography
    return with(density) {
        // ScoreTag: the number, "/100", 1dp between, 5dp padding each side, a 1dp edge each side; and a gap before the dB
        val score = tm.measure("100", t.titleSmall).size.width.toDp() + tm.measure("/100", t.labelSmall).size.width.toDp() + 13.dp + 8.dp
        val db = tm.measure("99.9", t.titleMedium.copy(fontFamily = AtFigures)).size.width.toDp() + tm.measure(" dB", t.labelSmall).size.width.toDp() + 6.dp
        RunColumns(score, db)
    }
}

/** The runs of the test: tick one to draw it on the graph; delete any run but the stock reading. */
@Composable
private fun RunsDeck(s: UiState, vm: TuneViewModel) {
    var confirm by remember { mutableStateOf<Int?>(null) }
    Group(
        "Runs", divider = false,
        info = "Tick the runs to draw on the graph. The stock run (no EQ) is your permanent reference and stays.\n\nThe EQ and the export follow the latest ticked run. " +
            "The score (0-100) beside a run's dB rates the system itself; it shows where it is already worked out: the stock and the ticked runs.",
        trailing = {
            if (s.runs.size > 2 && isAdvanced()) {
                val all = s.selectedRuns.size == s.runs.size
                TextAction(if (all) "Stock and latest" else "All", { vm.selectRuns(!all) })
            }
        },
    ) {
        // fixed columns, so the boxes and the figures line up down the list: tick | name and date | score | dB | delete
        val cols = runColumns()
        s.runs.forEach { r ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClickLabel = "Show or hide ${r.label}") { vm.toggleRun(r.index) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = r.index in s.selectedRuns, onCheckedChange = { vm.toggleRun(r.index) },
                    colors = CheckboxDefaults.colors(checkedColor = runColor(r.index), checkmarkColor = At.c.bg, uncheckedColor = At.c.muted),
                )
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(r.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(r.whenText, style = MaterialTheme.typography.labelSmall, color = At.c.muted, maxLines = 1)
                }
                // the score where it is already known (the focus run, the compared runs, the stock review); empty otherwise
                Box(Modifier.width(cols.score).padding(end = 6.dp), contentAlignment = Alignment.CenterEnd) {
                    dev.autotune.knownScore(r.index, s.selectedRuns.maxOrNull(), s.report?.overall, s.compare?.colorIdx.orEmpty(), s.compare?.scores.orEmpty(), s.stock?.score, s.runScores)
                        ?.let { ScoreTag(it) }
                }
                Row(Modifier.width(cols.db), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) {
                    Text("%.1f".format(r.rmsDb), style = MaterialTheme.typography.titleMedium, fontFamily = AtFigures, maxLines = 1, softWrap = false)
                    Text(" dB", Modifier.padding(bottom = 2.dp), style = MaterialTheme.typography.labelSmall, color = At.c.muted, maxLines = 1, softWrap = false)
                }
                if (dev.autotune.canDeleteRun(r.index, s.runs.size)) IconAction(Ic.TRASH, "Delete ${r.label}") { confirm = r.index }
                else Box(Modifier.size(48.dp))
            }
        }
    }
    confirm?.let { i ->
        val label = s.runs.getOrNull(i)?.label ?: return@let
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Delete $label?") },
            text = { Text("The reading is deleted. The suggestion is rebuilt from the runs that are left.") },
            confirmButton = { DialogKey("Delete", { confirm = null; vm.deleteRun(i) }, danger = true) },
            dismissButton = { DialogKey("Keep it", { confirm = null }) },
        )
    }
}
