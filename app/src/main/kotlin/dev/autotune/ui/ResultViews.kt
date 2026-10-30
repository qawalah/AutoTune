package dev.autotune.ui

import androidx.compose.foundation.layout.Arrangement
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
 * stock → latest. [showNext] adds the recommended next move in words (the results page, where the button is
 * Continue tuning); the Tune screens show that move as their own button instead.
 */
@Composable
fun ResultSummary(s: UiState, showNext: Boolean) {
    val runs = s.runs
    val stock = runs.firstOrNull() ?: return
    val latest = runs.last()
    Panel {
        Text("Summary", style = MaterialTheme.typography.labelLarge, color = At.c.muted)
        if (runs.size == 1) {
            val a = s.stock
            val suspicious = a?.suspicious == true
            Row(verticalAlignment = Alignment.CenterVertically) {
                AtIcon(if (suspicious) Ic.ALERT else Ic.MEASURE, if (suspicious) At.c.warn else At.c.signal, size = 28.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("%.1f dB from the target".format(stock.rmsDb), style = MaterialTheme.typography.headlineSmall)
                    Muted("Stock system, no EQ")
                }
            }
            a?.let { Text(it.verdict, style = MaterialTheme.typography.bodyMedium) }
            if (a != null && !suspicious && a.predictedDb != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AtIcon(Ic.EQ, At.c.signal, size = 18.dp)
                    Text(
                        "  With the suggested EQ (${a.bands} bands): about %.1f dB".format(a.predictedDb),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    )
                }
            }
            if (showNext) NextLine(if (suspicious) "Measure the stock again" else "Review the suggested EQ", null)
        } else {
            val better = latest.rmsDb < stock.rmsDb - 0.3
            val best = runs.drop(1).minByOrNull { it.rmsDb }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AtIcon(if (better) Ic.DONE else Ic.ALERT, if (better) At.c.good else At.c.warn, size = 28.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("%.1f → %.1f dB".format(stock.rmsDb, latest.rmsDb), style = MaterialTheme.typography.headlineSmall)
                    Muted("Distance from the target: stock → ${latest.label}")
                }
            }
            if (best != null && best != latest) Muted("Best so far: ${best.label}, %.1f dB".format(best.rmsDb))
            s.resultWarnings.forEach { Warning(it) }
            val rec = s.recommendation
            if (showNext && rec != null) NextLine(moveLabel(rec.move, runs.getOrNull(rec.bestIdx)?.label), rec.why)
        }
    }
}

@Composable
private fun NextLine(move: String, why: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AtIcon(Ic.NEXT, At.c.signal, size = 18.dp)
            Text("  Next: $move", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        why?.let { Muted(it) }
    }
}

/** The heading that separates the summary from the diagnostic layer. */
@Composable
fun DetailsHeading(sub: String = "Charts and diagnostics, closed until you open them.") {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Details", style = MaterialTheme.typography.titleLarge)
        Muted(sub)
    }
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
        info = "The target is the sound AutoTune tunes towards. Professionals tune to a curve with the bass lifted, not to flat, then season to taste.\n\n" +
            "Bass adds a shelf below about 100 Hz; Treble adds one above about 4 kHz. More bass in the target means AutoTune cuts less of the car's or room's own bass. " +
            "Boosts stay limited to +3 dB, so a lift only shows where the speakers already play the bass.",
    ) {
        DropSelect(
            targets.map { it.name }, idx, { vm.setTargetSound(it, s.bassDb, s.trebleDb) }, label = "Target curve",
            icons = targets.map { kindIcon(dev.autotune.targetKind(it)) }, details = targets.map { targetDetail(it) },
        )
        DropSelect(
            BASS_STEPS.map(::db), BASS_STEPS.indexOf(s.bassDb).let { if (it < 0) BASS_STEPS.indexOf(0.0) else it },
            { vm.setTargetSound(idx, BASS_STEPS[it], s.trebleDb) }, label = "Bass to taste", icon = Ic.SPEAKER,
        )
        DropSelect(
            TREBLE_STEPS.map(::db), TREBLE_STEPS.indexOf(s.trebleDb).let { if (it < 0) TREBLE_STEPS.indexOf(0.0) else it },
            { vm.setTargetSound(idx, s.bassDb, TREBLE_STEPS[it]) }, label = "Treble to taste", icon = Ic.CURVE,
        )
        Muted("Changing these refits the suggested EQ at once. Measure again to check it.")
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("EQ check", style = MaterialTheme.typography.titleMedium)
                Text(v.check.verdict, style = MaterialTheme.typography.bodyMedium)
                Muted("${v.eqName} · ${v.whenText}")
            }
        }
        if (v.check.severity != 3) ResponseChart(v.chart, height = 170.dp, targetLabel = "No change")
        Row {
            TextAction("Check again", vm::eqCheck, icon = Ic.AGAIN)
            TextAction("Hide", vm::clearEqCheck, icon = Ic.CLOSE)
        }
    }
}

/** Flavour on top of the tuned EQ, and the preamp (with its slider in Manual). Changes play at once. */
@Composable
fun FlavourAndPreamp(s: UiState, vm: dev.autotune.TuneViewModel) {
    val flavours = dev.autotune.Flavour.entries
    DropSelect(
        flavours.map { it.title }, flavours.indexOf(s.flavour), { vm.setFlavour(flavours[it]) }, label = "Flavour on top of the tune",
        details = flavours.map { it.detail }, icon = Ic.CURVE,
    )
    val modes = dev.autotune.PreampMode.entries
    DropSelect(
        modes.map { it.title }, modes.indexOf(s.preampMode), { vm.setPreampMode(modes[it]) }, label = "Preamp",
        details = modes.map { it.detail }, icon = Ic.SPEAKER,
    )
    if (s.preampMode == dev.autotune.PreampMode.MANUAL) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Slider(
                value = s.manualPreampDb.toFloat(), onValueChange = { vm.setManualPreamp(it.toDouble()) },
                valueRange = -15f..0f, steps = 29, modifier = Modifier.weight(1f),
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = At.c.signal, activeTrackColor = At.c.signal, inactiveTrackColor = At.c.line,
                    activeTickColor = At.c.onSignal.copy(alpha = 0.4f), inactiveTickColor = At.c.muted.copy(alpha = 0.4f),
                ),
            )
            Text("  %.1f dB".format(s.manualPreampDb), style = MaterialTheme.typography.titleSmall, fontFamily = PlexMono)
        }
    }
    s.playPreampDb?.takeIf { s.systemEqOn }?.let {
        Muted("Playing with a preamp of %.1f dB. If it sounds quieter than before, turn the volume up: the shape is what matters.".format(it))
    }
    if (s.flavour != dev.autotune.Flavour.NONE) Muted("The flavour only plays: measurements always use the tuned EQ alone.")
}

/** How the speakers followed the last EQ, and the further tests worth doing. */
@Composable
fun FollowAndChecks(s: UiState, vm: dev.autotune.TuneViewModel) {
    val f = s.eqFollow
    if (f != null && f.summary.isNotBlank()) {
        Group("How the speakers followed the EQ", divider = false) {
            Text(f.summary, style = MaterialTheme.typography.bodyMedium)
            // octave by octave: asked against heard
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                f.bands.forEach { b ->
                    val ok = kotlin.math.abs(b.askedDb) < 1.5 || (b.gotDb / b.askedDb) in 0.5..1.6
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%+.0f".format(b.askedDb), style = MaterialTheme.typography.labelSmall, fontFamily = PlexMono, color = At.c.muted)
                        Text("%+.0f".format(b.gotDb), style = MaterialTheme.typography.labelMedium, fontFamily = PlexMono, color = if (ok) At.c.text else At.c.warn)
                        Text(if (b.centreHz >= 1000) "${(b.centreHz / 1000).toInt()}k" else "${b.centreHz.toInt()}", style = MaterialTheme.typography.labelSmall, color = At.c.muted)
                    }
                }
            }
            Muted("Top: dB the EQ asked for. Middle: dB the mic heard change. Bottom: the octave in Hz.")
        }
    }
    if (s.checkSuggestions.isNotEmpty()) {
        Group("Worth checking", divider = false) {
            s.checkSuggestions.forEach { c ->
                Row(verticalAlignment = Alignment.Top) {
                    AtIcon(if (c.action == dev.autotune.CheckAction.NONE) Ic.INFO else Ic.ALERT, At.c.warn, Modifier.padding(top = 2.dp, end = 10.dp), 20.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(c.title, style = MaterialTheme.typography.titleSmall)
                        Muted(c.why)
                        if (c.action != dev.autotune.CheckAction.NONE) TextAction(
                            when (c.action) {
                                dev.autotune.CheckAction.MORE_SPOTS -> "Use 3 spots"
                                dev.autotune.CheckAction.EQ_CHECK -> "Run the EQ check"
                                dev.autotune.CheckAction.REPEAT -> "Measure again"
                                else -> "Measure the stock again"
                            },
                            { vm.doCheck(c.action) }, icon = Ic.NEXT,
                        )
                    }
                }
            }
        }
    }
}
