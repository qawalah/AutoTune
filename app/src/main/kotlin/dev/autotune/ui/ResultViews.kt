package dev.autotune.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.autotune.UiState
import dev.autotune.moveLabel

/*
 * One result, two layers, the same everywhere: the summary (how far from the target, what changed, the verdict, the
 * warnings and what to do next) comes first on the Tune result screens and on the test's results page; charts and
 * diagnostics sit under Details, closed until opened.
 */

/**
 * The summary layer of a test's result. With only the stock reading it is the stock review; with runs it is
 * stock → latest. The display holds the readings, each run's figure with its system score beside it where one is
 * already worked out, and the notes as one warning icon in its corner. With runs there is no headline line under the
 * readings (removed at the owner's request, 2026-11-09): its words are in the bubble beside the warning icon. The next
 * move is the pinned key, never a line here ([resultNext] gives the results page its words).
 */
@Composable
fun ResultSummary(s: UiState, namesTest: Boolean = true) {
    val runs = s.runs
    val stock = runs.firstOrNull() ?: return
    val latest = runs.last()
    // the readings sit on the deck, so their tints are the deck's (the body's green and amber are made for the dark body)
    val c = deckColors
    val targets = dev.autotune.dsp.Target.ALL
    val target = targets[s.targetIdx.coerceIn(0, targets.size - 1)].name + dev.autotune.dsp.Target.tasteSuffix(s.bassDb, s.trebleDb)
    val name = s.testName.ifBlank { "This test" }
    val about = "The figures are the distance from the target, in dB (RMS over the audible range). Lower is better; 0 would match the target exactly. " +
        "The score beside a figure (0-100) rates the system itself, as on the report card; it shows where it is already worked out for that run.\n\nTarget: $target."
    fun db(v: Double) = "%.1f".format(v)
    // the score already worked out for a run (the focus run's report, the compared runs, the stock review); never computed here
    fun score(idx: Int) = dev.autotune.knownScore(
        idx, s.selectedRuns.maxOrNull(), s.report?.overall, s.compare?.colorIdx.orEmpty(), s.compare?.scores.orEmpty(), s.stock?.score, s.runScores,
    )
    if (runs.size == 1) {
        val a = s.stock
        val suspicious = a?.suspicious == true
        // every warning and finding behind one icon in the display's corner
        val notes = s.resultWarnings + (a?.findings?.filter { it.severity > 0 }?.map { "${it.title}: ${it.detail}" } ?: emptyList())
        // on a page already titled with the test, the sheet names the reading instead
        RunSheet(
            if (namesTest) name else "Stock reading",
            if (namesTest) "Stock reading · ${stock.whenText}" else stock.whenText,
            trailing = { AlertsIcon(notes) },
        ) {
            ReadingCells(
                listOfNotNull(
                    Reading(db(stock.rmsDb), "dB", "stock · from target", if (suspicious) c.warn else null, score(stock.index)),
                    a?.predictedDb?.takeIf { !suspicious }?.let { Reading("≈" + db(it), "dB", "with the eq", c.signal) },
                ),
            )
            // the summary in a few words, the why in its bubble
            val pred = a?.predictedDb?.takeIf { !suspicious }
            Headline(
                when {
                    suspicious -> "This reading looks wrong."
                    pred != null -> "The EQ should bring it to ≈%.1f dB.".format(pred)
                    else -> "Stock reading saved."
                },
                about + (a?.verdict?.let { "\n\n$it" } ?: ""),
            )
        }
    } else {
        val better = latest.rmsDb < stock.rmsDb - 0.3
        val best = runs.drop(1).minByOrNull { it.rmsDb }
        val headline = dev.autotune.resultHeadline(stock.rmsDb, latest.rmsDb, best?.rmsDb, best?.label)
        RunSheet(
            if (namesTest) name else "${latest.label} vs stock",
            if (namesTest) "${latest.label} vs stock · ${latest.whenText}" else latest.whenText,
            trailing = {
                InfoButton("About this result", "$headline\n\n$about", small = true)
                AlertsIcon(s.resultWarnings)
            },
        ) {
            // each run's figure with its score: the latest (with the change from the stock), the stock, the best
            val change = "%+.1f".format(latest.rmsDb - stock.rmsDb).replace('-', '−') + " vs stock"
            ReadingCells(
                listOfNotNull(
                    Reading(db(latest.rmsDb), "dB", "${latest.label} · from target", score = score(latest.index), note = change, noteTint = if (better) c.good else c.warn),
                    Reading(db(stock.rmsDb), "dB", "stock", score = score(stock.index)),
                    if (best != null && best != latest) Reading(db(best.rmsDb), "dB", "best · ${best.label}", score = score(best.index)) else null,
                ),
            )
        }
    }
}

/** The next move in words for a test's results page, shown under its pinned key, with why in the hint. */
fun resultNext(s: UiState): Pair<String?, String?> {
    val runs = s.runs
    if (runs.size == 1) return dev.autotune.nextLine(if (s.stock?.suspicious == true) "Measure the stock again" else "Review the suggested EQ") to null
    val rec = s.recommendation ?: return null to null
    return dev.autotune.nextLine(moveLabel(rec.move, runs.getOrNull(rec.bestIdx)?.label)) to rec.why
}

/** The first sentence of [t], so a verdict reads as one line; the rest goes in its bubble. */
internal fun firstSentence(t: String): String = t.substringBefore(". ").let { if (it.length < t.length && !it.endsWith(".")) "$it." else it }

/** The result in one line, with its explanation in a bubble. */
@Composable
private fun Headline(text: String, info: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        InfoButton("About this result", info, small = true)
    }
}

/** The heading that separates the summary from the diagnostic layer. */
@Composable
fun DetailsHeading() {
    Text("Details", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleLarge)
}

/** What each built-in curve is, in a line, for the target drop-downs. */
fun targetDetail(t: dev.autotune.dsp.Target?): String = when (t) {
    dev.autotune.dsp.Target.CAR -> "A car, light: bass about 5 dB up, treble gently down"
    dev.autotune.dsp.Target.HARMAN_CAR -> "A car, fuller: Harman-style, bass about 8 dB up, treble gently down"
    dev.autotune.dsp.Target.HOME_THEATER -> "A room, light: bass about 4 dB up, treble falling slowly"
    dev.autotune.dsp.Target.HARMAN_ROOM -> "A room, fuller: Harman-style, bass about 6 dB up, treble falling slowly"
    dev.autotune.dsp.Target.FLAT -> "Neutral: every frequency at the same level. Sounds thin in a car or a room"
    else -> "Your own curve"
}

internal val BASS_STEPS = listOf(-3.0, 0.0, 2.0, 4.0, 6.0, 8.0, 10.0, 12.0)
internal val TREBLE_STEPS = listOf(-6.0, -4.0, -2.0, 0.0, 2.0, 4.0)

internal fun db(v: Double) = if (v == 0.0) "As the curve" else "%+.0f dB".format(v)

/**
 * The open test's target curve and the taste on top of it (a bass shelf and a treble shelf). Changing any of them
 * rescores the runs and refits the suggestion at once. Drop-downs, so a stray touch cannot change the sound.
 */
@Composable
fun TargetSoundFold(s: UiState, vm: dev.autotune.TuneViewModel, startOpen: Boolean = false) {
    if (s.runs.isEmpty()) return
    val targets = dev.autotune.dsp.Target.ALL
    val idx = s.targetIdx.coerceIn(0, targets.size - 1)
    Fold(
        "Target and taste",
        summary = targets[idx].name + dev.autotune.dsp.Target.tasteSuffix(s.bassDb, s.trebleDb),
        startOpen = startOpen,
        info = "The target is the sound AutoTune tunes towards. Professionals tune to a curve with the bass lifted, not to flat, then season to taste. Changing it refits the suggested EQ at once; measure again to check it.\n\n" +
            "Bass adds a shelf below about 100 Hz; Treble adds one above about 4 kHz. More bass in the target means AutoTune cuts less of the car's or room's own bass. " +
            "Boosts stay limited to +3 dB, so a lift only shows where the speakers already play the bass.",
    ) {
        DropSelect(
            targets.map { it.name }, idx, { vm.setTargetSound(it, s.bassDb, s.trebleDb) }, label = "Target curve",
            icons = targets.map { kindIcon(dev.autotune.targetKind(it)) }, details = targets.map { targetDetail(it) },
        )
        DropSelect(
            BASS_STEPS.map(::db), BASS_STEPS.indexOf(s.bassDb).let { if (it < 0) BASS_STEPS.indexOf(0.0) else it },
            { vm.setTargetSound(idx, BASS_STEPS[it], s.trebleDb) }, label = "Bass to taste", icon = Ic.BASS,
        )
        DropSelect(
            TREBLE_STEPS.map(::db), TREBLE_STEPS.indexOf(s.trebleDb).let { if (it < 0) TREBLE_STEPS.indexOf(0.0) else it },
            { vm.setTargetSound(idx, s.bassDb, TREBLE_STEPS[it]) }, label = "Treble to taste", icon = Ic.TREBLE,
        )
    }
}

/** The EQ check's verdict with the measured change drawn against what the EQ asks for. */
@Composable
fun EqCheckCard(v: dev.autotune.EqCheckView, vm: dev.autotune.TuneViewModel) {
    val (icon, tint) = when (v.check.severity) {
        0 -> Ic.DONE to At.c.good
        1, 3 -> Ic.INFO to At.c.warn
        else -> Ic.ALERT to At.c.bad
    }
    Panel {
        Row(verticalAlignment = Alignment.Top) {
            AtIcon(icon, tint, Modifier.padding(top = 2.dp, end = 10.dp), 22.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("EQ check", style = MaterialTheme.typography.titleMedium)
                Text(firstSentence(v.check.verdict), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
            InfoButton("EQ check", v.check.verdict + "\n\n${v.eqName} · ${v.whenText}", small = true)
        }
        if (v.check.severity != 3) ResponseChart(v.chart, height = 170.dp, targetLabel = "No change")
        Actions {
            TextAction("Check again", vm::eqCheck, icon = Ic.AGAIN)
            TextAction("Hide", vm::clearEqCheck, icon = Ic.CLOSE)
        }
    }
}

/** Flavour on top of the tuned EQ, and the preamp (with its slider in Manual). Changes play at once. */
@Composable
fun FlavourAndPreamp(s: UiState, vm: dev.autotune.TuneViewModel, preamp: Boolean = true) {
    val flavours = dev.autotune.Flavour.entries
    DropSelect(
        flavours.map { it.title }, flavours.indexOf(s.flavour), { vm.setFlavour(flavours[it]) }, label = "Flavour",
        details = flavours.map { it.detail }, icons = flavours.map { flavourIcon(it) },
        info = "A flavour plays on top of the tuned EQ. It only plays: measurements always use the tuned EQ alone.",
    )
    if (!preamp) return
    val modes = dev.autotune.PreampMode.entries
    DropSelect(
        modes.map { it.title }, modes.indexOf(s.preampMode), { vm.setPreampMode(modes[it]) }, label = "Preamp",
        details = modes.map { it.detail }, icon = Ic.GAIN,
        info = "The preamp lowers the whole EQ so boosts cannot clip." +
            (s.playPreampDb?.takeIf { s.systemEqOn }?.let { " Playing now with %.1f dB.".format(it) } ?: "") +
            " If it sounds quieter than before, turn the volume up: the shape is what matters.",
    )
    if (s.preampMode == dev.autotune.PreampMode.MANUAL) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Slider(
                value = s.manualPreampDb.toFloat(), onValueChange = { vm.setManualPreamp(it.toDouble()) },
                valueRange = dev.autotune.PREAMP_MIN.toFloat()..dev.autotune.PREAMP_MAX.toFloat(), steps = 41, modifier = Modifier.weight(1f),
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = At.c.signal, activeTrackColor = At.c.signal, inactiveTrackColor = At.c.line,
                    activeTickColor = At.c.onSignal.copy(alpha = 0.4f), inactiveTickColor = At.c.muted.copy(alpha = 0.4f),
                ),
            )
            Text("  %+.1f dB".format(s.manualPreampDb), style = MaterialTheme.typography.titleSmall, fontFamily = AtFigures)
        }
    }
}

/** How the speakers followed the last EQ, and the further tests worth doing. */
@Composable
fun FollowAndChecks(s: UiState, vm: dev.autotune.TuneViewModel) {
    val f = s.eqFollow
    if (f != null && f.summary.isNotBlank() && isAdvanced()) {
        Group(
            "How the speakers followed the EQ", divider = false,
            info = f.summary + "\n\nTop row: dB the EQ asked for. Middle: dB the mic heard change. Bottom: the octave in Hz.",
        ) {
            // octave by octave: asked against heard
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                f.bands.forEach { b ->
                    val ok = kotlin.math.abs(b.askedDb) < 1.5 || (b.gotDb / b.askedDb) in 0.5..1.6
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%+.0f".format(b.askedDb), style = MaterialTheme.typography.labelSmall, fontFamily = AtFigures, color = At.c.muted)
                        Text("%+.0f".format(b.gotDb), style = MaterialTheme.typography.labelMedium, fontFamily = AtFigures, color = if (ok) At.c.text else At.c.warn)
                        Text(if (b.centreHz >= 1000) "${(b.centreHz / 1000).toInt()}k" else "${b.centreHz.toInt()}", style = MaterialTheme.typography.labelSmall, color = At.c.muted)
                    }
                }
            }
        }
    }
    if (s.checkSuggestions.isNotEmpty()) {
        Group("Worth checking", divider = false) {
            s.checkSuggestions.forEach { c ->
                // the check is the action itself; why it is worth doing is in its bubble
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (c.action == dev.autotune.CheckAction.NONE) {
                            Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                                AtIcon(Ic.INFO, At.c.warn, Modifier.padding(start = 2.dp, end = 10.dp), 20.dp)
                                Text(c.title, style = MaterialTheme.typography.titleSmall)
                            }
                        } else Actions { TextAction(c.title, { vm.doCheck(c.action) }, icon = Ic.ALERT) }
                    }
                    InfoButton(c.title, c.why, small = true)
                }
            }
        }
    }
}
