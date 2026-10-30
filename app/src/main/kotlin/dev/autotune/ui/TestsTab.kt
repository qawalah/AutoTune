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
import dev.autotune.TestSummary
import dev.autotune.TuneViewModel
import dev.autotune.UiState
import dev.autotune.dsp.SystemReport

/** The list of tests. One tap on a test opens its results; a new test starts from the button at the bottom. */
@Composable
fun TestsTab(s: UiState, vm: TuneViewModel, openResults: (String) -> Unit) {
    ScreenTitle("Tests", if (s.tests.isEmpty()) null else "${s.tests.size} ${if (s.tests.size == 1) "system" else "systems"} measured")
    if (s.tests.isEmpty()) {
        Panel {
            Text("No tests yet", style = MaterialTheme.typography.titleLarge)
            Text(
                "A test is one car or one room. It keeps a stock reading of the system as it is now, then every run after you add EQ.",
                style = MaterialTheme.typography.bodyMedium, color = At.c.muted,
            )
            Text("Tap New test to start one.", style = MaterialTheme.typography.bodyMedium)
        }
        Muted("The EQ tab works without a test: you can build and switch on an EQ any time.")
        return
    }
    Column {
        s.tests.forEachIndexed { i, t ->
            if (i > 0) HorizontalDivider(color = At.c.line)
            TestRow(t, t.id == s.currentTestId) { openResults(t.id) }
        }
    }
}

@Composable
private fun TestRow(t: TestSummary, isOpen: Boolean, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 76.dp).clickable(onClickLabel = "Open the results of ${t.name}", onClick = onOpen).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.padding(end = 12.dp)) { AtIcon(kindIcon(t.kind), if (isOpen) At.c.signal else At.c.muted, size = 26.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (isOpen) {
                    Box(Modifier.padding(start = 8.dp).size(8.dp).clip(CircleShape).background(At.c.signal).semantics { contentDescription = "open now" })
                }
            }
            Muted("${t.target} · ${if (t.runCount == 0) "no readings" else "${t.runCount} ${if (t.runCount == 1) "reading" else "readings"}"} · ${t.whenText}")
        }
        if (t.stockRms != null && t.latestRms != null) {
            val better = t.runCount > 1 && t.latestRms < t.stockRms - 0.05
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    if (t.runCount > 1) Text("%.1f → ".format(t.stockRms), style = MaterialTheme.typography.titleMedium, fontFamily = AtFigures, color = At.c.muted)
                    Text(
                        "%.1f".format(if (t.runCount > 1) t.latestRms else t.stockRms),
                        style = MaterialTheme.typography.headlineSmall, color = if (better) At.c.good else At.c.text,
                    )
                }
                Muted("dB from target")
            }
        }
        Box(Modifier.padding(start = 6.dp)) { AtIcon(Ic.CHEVRON, At.c.muted, size = 20.dp) }
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

    ScreenTitle(
        s.testName,
        "${dev.autotune.dsp.Target.ALL[s.targetIdx.coerceIn(0, dev.autotune.dsp.Target.ALL.size - 1)].name} · ${s.source.title}",
        contextIcon = s.tests.firstOrNull { it.id == s.currentTestId }?.kind?.let { kindIcon(it) },
    )

    if (s.runs.isNotEmpty()) {
        // the same summary as the Tune result screens, then the one action; diagnostics follow, closed
        // the page title names the test; the pinned button is the one next move
        ResultSummary(s, showNext = false, namesTest = false)
        PinAction("Continue tuning", { vm.tuneContinue(); go(Tab.MEASURE) }, icon = Ic.TUNE)
        // the graph is always there
        s.chart?.let { c -> Panel { ResponseChart(c, height = 200.dp, showTarget = s.showTargetResults, onToggleTarget = { vm.setShowTarget(true, !s.showTargetResults) }) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextAction("Open the EQ", { go(Tab.EQ) }, icon = Ic.EQ)
            TextAction("AI review", vm::aiOpen, icon = Ic.AI)
        }
    }

    if (s.runs.isEmpty()) {
        Panel {
            Text("No readings yet", style = MaterialTheme.typography.titleLarge)
            Text("Measure the stock system first. It becomes the reference every later run is compared with.", style = MaterialTheme.typography.bodyMedium, color = At.c.muted)
            PrimaryButton("Start tuning", { vm.tuneContinue(); go(Tab.MEASURE) }, icon = Ic.TUNE)
        }
    } else {
        FollowAndChecks(s, vm)
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

        Fold(
            "Runs", summary = "${s.runs.size} readings · ${s.selectedRuns.size} shown",
            info = "Tick the runs to draw on the graph and compare in the table. The stock run (no EQ) is your permanent reference.\n\nThe EQ and the export follow the latest ticked run.",
        ) {
            if (s.runs.size > 1) {
                val both = s.selectedRuns == setOf(0, s.runs.lastIndex)
                val all = s.selectedRuns.size == s.runs.size
                DropSelect(
                    listOf("Stock and latest", "All runs", "Your own choice (tick below)"), if (all) 1 else if (both) 0 else 2,
                    { if (it < 2) vm.selectRuns(it == 1) }, label = "Show",
                )
            }
            s.runs.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { vm.toggleRun(r.index) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = r.index in s.selectedRuns, onCheckedChange = { vm.toggleRun(r.index) },
                        colors = CheckboxDefaults.colors(checkedColor = runColor(r.index), checkmarkColor = At.c.bg, uncheckedColor = At.c.muted),
                    )
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(r.label, style = MaterialTheme.typography.titleSmall)
                        Muted(
                            r.whenText + " · " + (if (r.index == 0) "no EQ" else "${r.appliedBands} bands applied") +
                                if (r.calibration == "unknown") "" else " · mic cal: ${r.calibration}",
                        )
                        if (r.note.isNotBlank()) Muted("“${r.note}”")
                    }
                    Text("%.2f".format(r.rmsDb), style = MaterialTheme.typography.titleSmall, fontFamily = AtFigures)
                    Muted(" dB")
                }
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
                Muted("Latest sweep only. Level of the harmonic against the test tone.")
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
                Muted("Regions show the average distance from the target in dB. Zero is on target.")
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
            SecondaryButton("Open in EQ", { go(Tab.EQ) }, icon = Ic.EQ)
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
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
            )
            SecondaryButton("Save ${focusLabel ?: "latest run"} as a target", { vm.saveRunAsTarget(targetName.ifBlank { "My curve" }); targetName = "" }, icon = Ic.CURVE)
        }

        Fold("Notes", summary = s.testNotes.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }?.take(60) ?: "None yet") {
            OutlinedTextField(
                value = s.testNotes, onValueChange = vm::setTestNotes,
                label = { Text("About this setup") },
                placeholder = { Text("Seat, mic spot, amp gain, what was installed") },
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
            )
        }
    }

    Fold("Manage test", summary = "Rename, discard the latest run, delete") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            confirmButton = { TextButton(onClick = { discard = false; vm.discardLastRun() }) { Text("Discard", color = At.c.bad) } },
            dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep it") } },
        )
    }

    if (rename) {
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Rename test") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { s.currentTestId?.let { id -> vm.renameTest(id, renameText) }; rename = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancel") } },
        )
    }
    if (delete) {
        AlertDialog(
            onDismissRequest = { delete = false },
            title = { Text("Delete this test?") },
            text = { Text("The stock reading and every run are removed. This can't be undone.") },
            confirmButton = { TextButton(onClick = { delete = false; back(); s.currentTestId?.let { id -> vm.deleteTest(id) } }) { Text("Delete", color = At.c.bad) } },
            dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancel") } },
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
        Text(r.verdict, style = MaterialTheme.typography.bodyMedium)
        Muted("Based on $label")
        r.categories.forEach { c ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(c.name + if (c.eqFixable) "" else "  (hardware or room)", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    Text("${c.score}", style = MaterialTheme.typography.labelLarge, fontFamily = AtFigures)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(At.c.panelHigh)) {
                    Box(
                        Modifier.fillMaxWidth(c.score / 100f).height(6.dp).clip(CircleShape)
                            .background(if (c.score >= 75) At.c.good else if (c.score >= 50) At.c.warn else At.c.bad),
                    )
                }
                Muted(c.detail)
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
        s.resonances.forEach { r ->
            val t60 = r.t60Ms?.let { "  rings ${if (r.t60IsLowerBound) ">" else "~"}%.0f ms".format(it) } ?: ""
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("%.0f Hz   +%.1f dB   Q %.1f%s".format(r.freqHz, r.prominenceDb, r.q, t60), style = MaterialTheme.typography.titleSmall, fontFamily = AtFigures)
                Muted(r.suggestion)
            }
        }
    }
}
