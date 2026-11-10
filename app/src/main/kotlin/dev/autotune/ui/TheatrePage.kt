package dev.autotune.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.autotune.ChartData
import dev.autotune.Curve
import dev.autotune.audio.LiveCapture
import dev.autotune.dsp.FreqGrid
import dev.autotune.dsp.Target
import dev.autotune.dsp.TheatreJudge
import dev.autotune.dsp.TheatreReport
import dev.autotune.theatre.TheatreUi
import dev.autotune.theatre.TheatreViewModel
import dev.autotune.theatre.SystemStep
import dev.autotune.theatre.systemKey
import dev.autotune.theatre.systemNext
import dev.autotune.theatre.theatreKey

private const val ABOUT_TV =
    "Open AutoTune on the TV (Android TV or Google TV, on the same Wi-Fi). It shows a four-digit code: enter it here once.\n\n" +
        "The TV plays a test sound on one speaker at a time and the phone listens at your seat. Over HDMI ARC the sound goes as " +
        "Dolby Digital 5.1, which every home theatre splits into its speakers; with eARC, or a box plugged into the receiver, " +
        "it can go as raw 7.1.\n\nOr open AutoTune on a Windows PC: it shows its own code and plays front left and right on the " +
        "output you pick there. Its EQ runs in Equalizer APO, so it reaches everything the PC plays."

private const val ABOUT_RESULT =
    "Each change is what to add to the home theatre's current setting: raise or lower a speaker's level, or set its distance " +
        "farther or nearer. Levels are matched over 500 Hz to 2 kHz (the sub over 40 to 100 Hz, where the target puts the bass); " +
        "distances come from when each speaker's sound arrives against front left.\n\nSet them in the home theatre's speaker " +
        "settings, then test again: the readings should come out level and in time."

private const val ABOUT_LEVELS =
    "Each speaker plays pink noise between 500 Hz and 2 kHz (the sub its own, lower noise) and the phone reads it at your " +
        "seat once a second. Front left sets the level; turn each other speaker's level on the home theatre until it reads " +
        "level, and the next one starts by itself after three seconds there. The sub is set to the target's bass above the " +
        "others.\n\nKeep the phone at your seat, at ear height, and the room quiet."

private const val ABOUT_SYSTEM =
    "One pass: the start beeps once, each speaker's sweep in turn, then every speaker together. The single sweeps give " +
        "each speaker's level and distance (when its sound arrives against front left); the last one is the system as you " +
        "hear it, and gives one EQ for every speaker, fitted from the first full pass so EQs never stack."

private const val ABOUT_EQ =
    "An EQ for each speaker, fitted from the first full test so EQs never stack. \"Use the EQ on the TV\" sets it on the " +
        "TV's own sound. It only reaches sound the TV turns into PCM itself: Dolby or DTS passed straight to the home theatre " +
        "(most streaming apps) skips it, so for those, enter the bands in the home theatre's EQ if it has one.\n\n" +
        "\"Use the EQ on the PC\" sets it in Equalizer APO on the PC, one EQ per channel: it reaches everything the PC plays " +
        "on that output, and stays on when AutoTune there is closed."

/** Tune a home theatre (or a PC's speakers) with the TV or PC playing each speaker and the phone listening. A page over the current tab. */
@Composable
fun TheatrePage(pick: Pickers) {
    val vm: TheatreViewModel = viewModel()
    val u by vm.ui.collectAsState()
    DisposableEffect(Unit) { vm.start(); onDispose { vm.pause() } }
    LaunchedEffect(u.needsMic) { if (u.needsMic) { vm.micGranted(true); pick.permission() } }

    ScreenTitle("Home theatre", context = u.tv ?: "not connected", contextIcon = Ic.HOME)
    if (u.tv == null) Connect(u, vm) else Connected(u, vm)
}

@Composable
private fun Connect(u: TheatreUi, vm: TheatreViewModel) {
    var tvIdx by rememberSaveable { mutableIntStateOf(0) }
    val tvName = u.found.getOrNull(tvIdx.coerceAtMost((u.found.size - 1).coerceAtLeast(0)))
    var code by rememberSaveable(tvName) { mutableStateOf(tvName?.let { u.codes[it] }.orEmpty()) }
    var address by rememberSaveable { mutableStateOf("") }
    RunSheet("Find the TV or PC", if (u.found.isEmpty()) "looking on this wi-fi" else "${u.found.size} found") {
        if (u.found.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Nothing found yet", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            InfoButton("Find the TV or PC", ABOUT_TV, small = true)
        } else DropSelect(u.found, tvIdx.coerceAtMost(u.found.size - 1), { tvIdx = it }, label = "TV or PC", icon = Ic.HOME, info = ABOUT_TV)
        OutlinedTextField(
            value = code, onValueChange = { v -> code = v.filter { it.isDigit() }.take(4) },
            label = { Text("Code on its screen") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
        )
        Adv {
            OutlinedTextField(
                value = address, onValueChange = { address = it.trim() },
                label = { Text("Address (when it is not found)") }, placeholder = { Text("192.168.1.20") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
            )
        }
    }
    u.error?.let { Alerts(listOf(it), title = "Connecting") }
    val byAddress = address.isNotBlank() && isAdvanced()
    PinAction(
        if (u.connecting) "Connecting" else "Connect", icon = Ic.SEND,
        enabled = !u.connecting && code.length == 4 && (byAddress || tvName != null),
        onClick = { if (byAddress) vm.connectAddress(address, code) else vm.connect(tvIdx, code) },
        sub = tvName?.lowercase() ?: "the tv or pc",
    )
}

@Composable
private fun Connected(u: TheatreUi, vm: TheatreViewModel) {
    if (u.twoTests) { TwoTests(u, vm); return }
    val r = u.report?.takeIf { it.layout == u.layout }
    val level by LiveCapture.level.collectAsState()
    // the mic's level makes the rings of the playing speaker grow: -60 dBFS is still, -10 is full
    val ring = if (u.playing != null) ((level + 60f) / 50f).coerceIn(0f, 1f) else null
    Panel(pad = 12.dp) {
        SpeakerMap(
            u.layout, u.playing, level = ring,
            notes = r?.speakers?.associate { it.speaker to TheatreJudge.trimText(it.trimDb) }.orEmpty().takeIf { !u.busy }.orEmpty(),
            silent = r?.speakers?.filter { !it.heard }?.map { it.speaker }?.toSet().orEmpty(),
        )
        Text(
            u.status ?: listOfNotNull(u.output, u.method?.title).joinToString(" · ").lowercase(),
            style = MaterialTheme.typography.labelSmall, color = At.c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }

    // what the TV or PC reports about its EQ (on a PC: Equalizer APO missing, or not on the output)
    u.error?.let { Alerts(listOf(it), title = "The ${u.device}") }

    if (r != null && !u.busy) Summary(r, u)

    val method = u.method
    if (method != null && method.layouts.size > 1) {
        DropSelect(
            method.layouts.map { it.title }, method.layouts.indexOf(u.layout).coerceAtLeast(0), { vm.setLayout(method.layouts[it]) },
            enabled = !u.busy, label = "Speakers", icon = Ic.SPEAKER,
            info = "The speakers your home theatre has. ${method.title} from this ${u.device} reaches up to ${method.layouts.first().title}.",
        )
    }

    // the one key: stop while testing; else what the result asks for
    when {
        u.busy -> PinAction("Stop", vm::stop, icon = Ic.STOP, sub = u.playing?.title?.lowercase())
        r == null -> PinAction("Test every speaker", vm::testAll, icon = Ic.MEASURE, sub = "${u.layout.speakers.size} speakers, about ${u.layout.speakers.size * 15} s")
        else -> { val (k, sub) = theatreKey(r, u.device); PinAction(k, vm::next, icon = if (k.startsWith("Use")) Ic.EQ else Ic.MEASURE, sub = sub) }
    }

    if (!u.busy) Actions {
        if (r != null && !theatreKey(r).first.startsWith("Test every") && theatreKey(r).first != "Test again") TextAction("Test every speaker", vm::testAll, icon = Ic.MEASURE)
        if (r != null && r.move != dev.autotune.dsp.TheatreMove.USE_EQ) TextAction("Use the EQ on the ${u.device}", vm::useEq, icon = Ic.EQ)
        if (u.tvEqOn) TextAction("Switch the ${u.device}'s EQ off", vm::tvEqOff, icon = Ic.POWER)
        if (isAdvanced()) {
            if (u.hasStock) TextAction("Start over (new reference)", vm::startOver, icon = Ic.AGAIN)
        }
        TextAction("Disconnect", vm::disconnect, icon = Ic.CLOSE)
    }
    if (isAdvanced() && !u.busy) {
        var one by rememberSaveable { mutableIntStateOf(0) }
        val sp = u.layout.speakers
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) { DropSelect(sp.map { it.title }, one.coerceAtMost(sp.size - 1), { one = it }, label = "One speaker", icon = Ic.SPEAKER) }
            SecondaryButton("Test", { vm.testOne(sp[one.coerceAtMost(sp.size - 1)]) }, icon = Ic.MEASURE)
        }
    }

    if (r != null && !u.busy) Details(r, u)
}

@Composable
private fun Summary(r: TheatreReport, u: TheatreUi) {
    val c = deckColors
    val whenText = u.latest?.whenMillis?.let { java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it)) }
    RunSheet("Speakers", whenText, trailing = { AlertsIcon(r.notes) }) {
        val spread = r.levelSpreadDb
        val dist = r.distanceSpreadM
        ReadingCells(
            listOf(
                Reading("±%.1f".format(spread), "dB", "levels", if (spread > TheatreJudge.LEVEL_OK_DB) c.warn else c.good),
                Reading("%.2f".format(dist), "m", "distance", if (dist > TheatreJudge.DISTANCE_OK_M) c.warn else c.good),
                Reading("${r.heardCount}/${r.speakers.size}", null, "heard", if (r.heardCount < r.speakers.size) c.warn else null),
            ),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.headline, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            InfoButton("About this result", ABOUT_RESULT, small = true)
        }
    }
}

@Composable
private fun Details(r: TheatreReport, u: TheatreUi, showEq: Boolean = true) {
    // the graph is always shown: every speaker against the target, level-matched to the mains
    val grid = FreqGrid()
    val readings = u.latest?.readings.orEmpty().filter { it.heard }
    if (showEq && readings.isNotEmpty()) {
        val ref = readings.filter { !it.speaker.sub }.map { TheatreJudge.level(it.magDb!!, it.speaker, grid) }.average().takeIf { !it.isNaN() } ?: 0.0
        val curves = readings.map { rd ->
            val sm = grid.smoothOctave(rd.magDb!!, 6.0)
            Curve(rd.speaker.title, DoubleArray(sm.size) { sm[it] - ref }, rd.speaker.ordinal + 1)
        }
        ResponseChart(ChartData(grid.freqs, curves, DoubleArray(grid.size) { Target.HOME_THEATER.dbAt(grid.freqs[it]) }, null, ""), targetLabel = "Home theatre")
    }
    if (showEq) DetailsHeading()
    Fold("Per speaker", summary = "level, distance, from target", startOpen = true, info = ABOUT_RESULT) {
        r.speakers.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.speaker.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = if (s.heard) At.c.text else At.c.muted, maxLines = 1)
                Text(if (s.heard) TheatreJudge.trimText(s.trimDb) else "not heard", Modifier.width(76.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(TheatreJudge.distanceText(s.distanceM), Modifier.width(72.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(s.rmsDb?.let { "%.1f dB".format(it) } ?: "—", Modifier.width(56.dp), style = MaterialTheme.typography.bodySmall, color = At.c.muted, maxLines = 1)
            }
        }
    }
    if (showEq) Fold("EQ per speaker", summary = "for the home theatre, the tv or the pc", info = ABOUT_EQ) {
        r.speakers.filter { it.heard }.forEach { s ->
            Text(s.speaker.title, style = MaterialTheme.typography.titleSmall)
            if (s.eq.isEmpty()) Muted("no change")
            s.eq.sortedBy { it.freqHz }.forEach { f ->
                Text(
                    "%s  %+.1f dB  Q %.1f".format(if (f.freqHz >= 1000) "%.1f kHz".format(f.freqHz / 1000) else "%.0f Hz".format(f.freqHz), f.gainDb, f.q),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Adv {
            r.speakers.filter { it.delayMs != null }.forEach { s -> Muted("${s.speaker.title}: delay %+.2f ms".format(s.delayMs)) }
        }
    }
}

// ---- the two tests (2.15): levels with pink noise, then the system in one pass

@Composable
private fun TwoTests(u: TheatreUi, vm: TheatreViewModel) {
    val sys = u.system?.takeIf { it.speakers.layout == u.layout }
    val r = sys?.speakers
    val lv = u.level
    val level by LiveCapture.level.collectAsState()
    val ring = if (u.playing != null || u.allPlaying) ((level + 60f) / 50f).coerceIn(0f, 1f) else null
    val notes: Map<dev.autotune.dsp.Speaker, String> = when {
        lv != null || (u.levelsDone.isNotEmpty() && sys == null) ->
            u.levelsDone.mapValues { TheatreJudge.trimText(it.value) } + (lv?.change?.let { mapOf(lv.speaker to TheatreJudge.trimText(it)) }.orEmpty())
        r != null && !u.busy -> r.speakers.associate { it.speaker to TheatreJudge.trimText(it.trimDb) }
        else -> emptyMap()
    }
    Panel(pad = 12.dp) {
        SpeakerMap(u.layout, u.playing, level = ring, all = u.allPlaying, notes = notes, silent = r?.speakers?.filter { !it.heard }?.map { it.speaker }?.toSet().orEmpty().takeIf { !u.busy }.orEmpty())
        val line = u.status ?: if (u.pretend) "pretend ${u.layout.title} · really ${u.real ?: "its own speakers"}" else listOfNotNull(u.output, u.method?.title).joinToString(" · ").lowercase()
        Text(line, style = MaterialTheme.typography.labelSmall, color = if (u.pretend && u.status == null) At.c.signal else At.c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    u.error?.let { Alerts(listOf(it), title = "The ${u.device}") }

    when {
        lv != null -> LevelDeck(lv)
        sys != null && !u.busy -> SystemSummary(sys, u)
        u.levelsDone.isNotEmpty() && !u.busy -> LevelsSet(u.levelsDone)
    }

    val method = u.method
    if (method != null && method.layouts.size > 1 && !u.busy) {
        DropSelect(
            method.layouts.map { it.title }, method.layouts.indexOf(u.layout).coerceAtLeast(0), { vm.setLayout(method.layouts[it]) },
            label = "Speakers", icon = Ic.SPEAKER,
            info = "The speakers your home theatre has. ${method.title} from this ${u.device} reaches up to ${method.layouts.first().title}.",
        )
    }

    // the one key: the next speaker while levels are set; stop while the system plays; else what comes next
    when {
        lv != null -> {
            val next = u.layout.speakers.getOrNull(lv.index + 1)
            PinAction("Next speaker", vm::nextLevel, icon = Ic.NEXT, sub = next?.let { "then ${it.title.lowercase()}" } ?: "the last one")
        }
        u.busy -> PinAction("Stop", vm::stop, icon = Ic.STOP, sub = u.status)
        else -> {
            val (k, sub) = systemKey(sys, u.levelsDone.isNotEmpty(), u.layout, u.device)
            PinAction(k, vm::next, icon = if (k.startsWith("Use")) Ic.EQ else if (k.startsWith("Set")) Ic.SPEAKER else Ic.MEASURE, sub = sub)
        }
    }
    if (lv != null) Actions { TextAction("Stop", vm::stop, icon = Ic.STOP) }
    if (!u.busy) Actions {
        val step = systemNext(sys, u.levelsDone.isNotEmpty())
        if (step != SystemStep.LEVELS) TextAction("Set the levels", vm::levelTest, icon = Ic.SPEAKER)
        if (step != SystemStep.SYSTEM) TextAction("Test the system", vm::systemTest, icon = Ic.MEASURE)
        if (sys != null && sys.eq.isNotEmpty() && step != SystemStep.USE_EQ) TextAction("Use the EQ on the ${u.device}", vm::useEq, icon = Ic.EQ)
        if (u.tvEqOn) TextAction("Switch the ${u.device}'s EQ off", vm::tvEqOff, icon = Ic.POWER)
        if (isAdvanced() && u.hasStock) TextAction("Start over (new reference)", vm::startOver, icon = Ic.AGAIN)
        TextAction("Disconnect", vm::disconnect, icon = Ic.CLOSE)
    }
    if (isAdvanced() && !u.busy) {
        var one by rememberSaveable { mutableIntStateOf(0) }
        val sp = u.layout.speakers
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) { DropSelect(sp.map { it.title }, one.coerceAtMost(sp.size - 1), { one = it }, label = "One speaker", icon = Ic.SPEAKER) }
            SecondaryButton("Test", { vm.testOne(sp[one.coerceAtMost(sp.size - 1)]) }, icon = Ic.MEASURE)
        }
    }
    if (sys != null && !u.busy) SystemDetails(sys, u)
}

/** The level test as it runs: the speaker playing noise and, large, how far to turn it. */
@Composable
private fun LevelDeck(lv: dev.autotune.theatre.LevelState) {
    val c = deckColors
    val ch = lv.change
    val name = lv.speaker.title.lowercase()
    RunSheet("Levels", "${lv.index + 1} of ${lv.count}", trailing = { InfoButton("The level test", ABOUT_LEVELS, small = true) }) {
        when {
            lv.ref == null -> ReadingCells(listOf(Reading("…", null, "$name sets the level")))
            ch == null -> ReadingCells(listOf(Reading("…", null, "reading $name")))
            else -> {
                val ok = dev.autotune.dsp.LevelMeter.steady(ch)
                val word = when {
                    ok -> "level · next in ${(dev.autotune.dsp.LevelMeter.STEADY_SECONDS - lv.steady).coerceAtLeast(0)} s"
                    ch > 0 -> "turn $name up"
                    else -> "turn $name down"
                }
                ReadingCells(listOf(Reading(if (ok) "0.0" else "%+.1f".format(ch), "dB", word, if (ok) c.good else c.warn)))
            }
        }
    }
}

/** The levels the last level test ended with. */
@Composable
private fun LevelsSet(done: Map<dev.autotune.dsp.Speaker, Double>) {
    val worst = done.values.maxOfOrNull { kotlin.math.abs(it) } ?: 0.0
    RunSheet("Levels", "set", trailing = { InfoButton("The level test", ABOUT_LEVELS, small = true) }) {
        ReadingCells(listOf(Reading("±%.1f".format(worst), "dB", "left to set", if (worst > dev.autotune.dsp.LevelMeter.OK_DB) deckColors.warn else deckColors.good)))
    }
}

@Composable
private fun SystemSummary(sys: dev.autotune.dsp.PassReport, u: TheatreUi) {
    val c = deckColors
    val r = sys.speakers
    val whenText = u.latest?.whenMillis?.let { java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it)) }
    val notes = r.notes + listOfNotNull(u.real?.takeIf { u.pretend }?.let { "Pretend ${r.layout.title}: really $it. Its readings are for trying the flow, and it is never kept as the reference." })
    RunSheet("System", whenText, trailing = { AlertsIcon(notes) }) {
        ReadingCells(
            listOfNotNull(
                sys.rmsDb?.let { now ->
                    val st = sys.stockRmsDb
                    Reading("%.1f".format(now), "dB", "from target", note = if (st != null && kotlin.math.abs(st - now) > 0.05) "%+.1f vs start".format(now - st) else null)
                },
                Reading("±%.1f".format(r.levelSpreadDb), "dB", "levels", if (r.levelSpreadDb > TheatreJudge.LEVEL_OK_DB) c.warn else c.good),
                Reading("%.2f".format(r.distanceSpreadM), "m", "distance", if (r.distanceSpreadM > TheatreJudge.DISTANCE_OK_M) c.warn else c.good),
                Reading("${r.heardCount}/${r.speakers.size}", null, "heard", if (r.heardCount < r.speakers.size) c.warn else null),
            ),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sys.headline, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            InfoButton("About this result", ABOUT_SYSTEM, small = true)
        }
    }
}

@Composable
private fun SystemDetails(sys: dev.autotune.dsp.PassReport, u: TheatreUi) {
    val grid = FreqGrid()
    val readings = u.latest?.readings.orEmpty().filter { it.heard }
    val ref = readings.filter { !it.speaker.sub }.map { TheatreJudge.level(it.magDb!!, it.speaker, grid) }.average().takeIf { !it.isNaN() } ?: 0.0
    // the graph is always shown: the whole system bold, each speaker under it, against the target
    val curves = buildList {
        sys.systemDb?.let { m ->
            val sm = grid.smoothOctave(m, 6.0)
            val lvl = TheatreJudge.level(m, dev.autotune.dsp.Speaker.FL, grid)
            add(Curve("System", DoubleArray(sm.size) { sm[it] - lvl }, 0))
        }
        readings.forEach { rd ->
            val sm = grid.smoothOctave(rd.magDb!!, 6.0)
            add(Curve(rd.speaker.title, DoubleArray(sm.size) { sm[it] - ref }, rd.speaker.ordinal + 1))
        }
    }
    if (curves.isNotEmpty()) ResponseChart(ChartData(grid.freqs, curves, DoubleArray(grid.size) { Target.HOME_THEATER.dbAt(grid.freqs[it]) }, null, ""), targetLabel = "Home theatre")
    DetailsHeading()
    Details(sys.speakers, u, showEq = false)
    Fold("EQ for every speaker", summary = if (sys.eq.isEmpty()) "no change" else "${sys.eq.size} bands", info = ABOUT_EQ) {
        if (sys.eq.isEmpty()) Muted("no change")
        sys.eq.sortedBy { it.freqHz }.forEach { f ->
            Text(
                "%s  %+.1f dB  Q %.1f".format(if (f.freqHz >= 1000) "%.1f kHz".format(f.freqHz / 1000) else "%.0f Hz".format(f.freqHz), f.gainDb, f.q),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
