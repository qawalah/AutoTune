package dev.autotune.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
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
import dev.autotune.theatre.RunKind
import dev.autotune.theatre.threeKey
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

private const val ABOUT_DIST =
    "The start beeps once, then each speaker's sweep in turn: when each one's sound arrives against front left gives its " +
        "distance setting, the sub's own delay included. Run it at setup, and again only when a speaker moves; the EQ does " +
        "not need it, but wrong distances make the sub and the others cancel."

private const val ABOUT_EQTEST =
    "Every speaker together, as you hear the system, at one seat or averaged over several. It gives one EQ for every " +
        "speaker, fitted from this home theatre's first EQ test so EQs never stack; test again with the EQ on to see what it " +
        "did. The first EQ test plays with AutoTune's own EQ held off."

private const val ABOUT_EQ =
    "An EQ for each speaker, fitted from the first full test so EQs never stack. \"Use the EQ on the TV\" sets it on the " +
        "TV's own sound. It only reaches sound the TV turns into PCM itself: Dolby or DTS passed straight to the home theatre " +
        "(most streaming apps) skips it, so for those, enter the bands in the home theatre's EQ if it has one.\n\n" +
        "\"Use the EQ on the PC\" sets it in Equalizer APO on the PC, one EQ per channel: it reaches everything the PC plays " +
        "on that output, and stays on when AutoTune there is closed."

/** Tune a home theatre (or a PC's speakers) with the TV or PC playing each speaker and the phone listening. A page over the current tab. */
@Composable
fun TheatrePage(pick: Pickers, openId: String? = null, opened: () -> Unit = {}) {
    val vm: TheatreViewModel = viewModel()
    val u by vm.ui.collectAsState()
    LaunchedEffect(openId) { openId?.let { vm.selectTest(it); opened() } }
    DisposableEffect(Unit) { vm.start(); onDispose { vm.pause() } }
    LaunchedEffect(u.needsMic) { if (u.needsMic) { vm.micGranted(true); pick.permission() } }

    ScreenTitle("Home theatre", context = u.tv ?: "not connected", contextIcon = Ic.HOME)
    if (u.tv == null) {
        Connect(u, vm)
        // the open home theatre's last results stay readable while nothing is connected
        if (u.test != null) {
            TestBar(u, vm)
            LevelsResult(u)
            DistancesResult(u)
            EqResult(u)
            RunsDetails(u, vm)
        }
    } else Connected(u, vm)
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
    if (u.threeTests) { ThreeTests(u, vm); return }
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

// ---- the three tests (2.16): levels, distances and the EQ, each on its own, saved as runs of a named test

@Composable
private fun ThreeTests(u: TheatreUi, vm: TheatreViewModel) {
    val lv = u.level
    val level by LiveCapture.level.collectAsState()
    val ring = if (u.playing != null || u.allPlaying) ((level + 60f) / 50f).coerceIn(0f, 1f) else null
    val d = u.distances
    val notes: Map<dev.autotune.dsp.Speaker, String> = buildMap {
        u.levelsDone.forEach { (s, ch) -> put(s, TheatreJudge.trimText(ch)) }
        lv?.change?.let { put(lv.speaker, TheatreJudge.trimText(it)) }
        if (lv == null) d?.speakers?.forEach { r -> put(r.speaker, listOfNotNull(this[r.speaker], TheatreJudge.distanceText(r.distanceM)).joinToString("\n")) }
    }
    TestBar(u, vm)
    Panel(pad = 12.dp) {
        SpeakerMap(u.layout, u.playing, level = ring, all = u.allPlaying, notes = notes, silent = d?.speakers?.filter { !it.heard }?.map { it.speaker }?.toSet().orEmpty().takeIf { !u.busy }.orEmpty())
        val line = u.status ?: if (u.pretend) "pretend ${u.layout.title} · really ${u.real ?: "its own speakers"}" else listOfNotNull(u.output, u.method?.title).joinToString(" · ").lowercase()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(line, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = if (u.pretend && u.status == null) At.c.signal else At.c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // whether the TV's EQ is on, always in view: the pilot dot is lit (red) only while it is
            EqPilot(u.tvEqOn, u.device)
        }
    }
    u.error?.let { Alerts(listOf(it), title = "The ${u.device}") }

    when {
        lv != null -> LevelDeck(lv)
        u.waitingSeat -> RunSheet("EQ test", "seat ${u.seat} of ${u.seats}", trailing = { InfoButton("The EQ test", ABOUT_EQTEST, small = true) }) {
            ReadingCells(listOf(Reading("${u.seat}", null, "move the phone to the next seat, at ear height")))
        }
        // each test with its own keys right under it
        !u.busy -> {
            val t = u.test
            LevelsResult(u)
            Actions { TextAction(if (t?.last(RunKind.LEVELS) == null) "Set the levels" else "Set the levels again", vm::levelTest, icon = Ic.SPEAKER) }
            DistancesResult(u)
            Actions { TextAction(if (t?.last(RunKind.DISTANCES) == null) "Test the distances" else "Test the distances again", vm::distancesTest, icon = Ic.MEASURE) }
            EqResult(u)
            Actions {
                TextAction(if (t?.last(RunKind.EQ) == null) "Test the EQ" else "Test the EQ again", vm::eqTest, icon = Ic.MEASURE)
                if (u.eq?.eq?.isNotEmpty() == true && !u.tvEqOn) TextAction("Use the EQ on the ${u.device}", vm::useEq, icon = Ic.EQ)
                if (u.tvEqOn && u.eq?.eq?.isNotEmpty() == true) TextAction("Check the EQ reaches the speakers", vm::eqCheck, icon = Ic.CHECK)
                if (u.tvEqOn) TextAction("Switch the ${u.device}'s EQ off", vm::tvEqOff, icon = Ic.POWER)
            }
            if (isAdvanced()) {
                DropSelect(
                    listOf("1 seat", "3 seats", "5 seats"), listOf(1, 3, 5).indexOf(u.seats).coerceAtLeast(0), { vm.setSeats(listOf(1, 3, 5)[it]) },
                    label = "EQ test seats", icon = Ic.MIC,
                    info = "The EQ test can average the system over several seats, so the EQ suits the whole sofa rather than one spot. The phone waits at each seat for Next seat.",
                )
            }
        }
    }

    val method = u.method
    if (method != null && method.layouts.size > 1 && !u.busy) {
        DropSelect(
            method.layouts.map { it.title }, method.layouts.indexOf(u.layout).coerceAtLeast(0), { vm.setLayout(method.layouts[it]) },
            label = "Speakers", icon = Ic.SPEAKER,
            info = "The speakers your home theatre has. ${method.title} from this ${u.device} reaches up to ${method.layouts.first().title}.",
        )
    }

    // the one key: next speaker or next seat while a test waits; stop while one plays; else the suggested test
    when {
        lv != null -> {
            val next = u.layout.speakers.getOrNull(lv.index + 1)
            PinAction("Next speaker", vm::nextLevel, icon = Ic.NEXT, sub = next?.let { "then ${it.title.lowercase()}" } ?: "the last one")
        }
        u.waitingSeat -> PinAction("Measure seat ${u.seat}", vm::nextSeat, icon = Ic.MEASURE, sub = "of ${u.seats}")
        u.busy -> PinAction("Stop", vm::stop, icon = Ic.STOP, sub = u.status)
        else -> { val (k, sub) = threeKey(u); PinAction(k, vm::next, icon = when { k.startsWith("Set") -> Ic.SPEAKER; k.startsWith("Use") -> Ic.EQ; else -> Ic.MEASURE }, sub = sub) }
    }
    if (lv != null || u.waitingSeat) Actions { TextAction("Stop", vm::stop, icon = Ic.STOP) }
    if (!u.busy) Actions { TextAction("Disconnect", vm::disconnect, icon = Ic.CLOSE) }
    if (!u.busy) RunsDetails(u, vm)
}

/** "eq on" with a lit pilot dot, or "eq off" with an unlit one: the TV's (or PC's) EQ. */
@Composable
private fun EqPilot(on: Boolean, device: String) {
    Row(Modifier.padding(start = 8.dp).semantics { contentDescription = "$device EQ ${if (on) "on" else "off"}" }, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (on) At.c.signal else At.c.line),
        )
        Text("${device.lowercase()} eq ${if (on) "on" else "off"}", Modifier.padding(start = 5.dp), style = MaterialTheme.typography.labelSmall, color = if (on) At.c.text else At.c.muted)
    }
}

/** Which home theatre this is: a drop-down of the tests, New test, and in Advanced, rename and delete. */
@Composable
private fun TestBar(u: TheatreUi, vm: TheatreViewModel) {
    var naming by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val ids = u.tests.map { it.first }
    if (u.tests.isNotEmpty()) {
        DropSelect(
            u.tests.map { it.second }, ids.indexOf(u.test?.id).coerceAtLeast(0), { vm.selectTest(ids[it]) },
            enabled = !u.busy, label = "Home theatre", icon = Ic.HOME,
            info = "Each home theatre keeps every run of its three tests, so you can look back and compare. Name one per room or system.",
        )
    }
    if (!u.busy) Actions {
        TextAction("New home theatre", { naming = "" }, icon = Ic.PLUS)
        if (isAdvanced() && u.test != null) {
            TextAction("Rename", { renaming = true }, icon = Ic.EDIT)
            TextAction("Delete this home theatre", { deleting = true }, icon = Ic.TRASH, danger = true)
        }
    }
    naming?.let { text ->
        NameDialog("New home theatre", text, onDone = { n -> vm.newTest(n); naming = null }, onCancel = { naming = null })
    }
    if (renaming) NameDialog("Rename", u.test?.name.orEmpty(), onDone = { n -> vm.renameTest(n); renaming = false }, onCancel = { renaming = false })
    if (deleting) androidx.compose.material3.AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("Delete ${u.test?.name ?: "this home theatre"}?") },
        text = { Text("Every run of it is deleted.") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { vm.deleteTest(); deleting = false }) { Text("Delete", color = At.c.bad) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { deleting = false }) { Text("Keep it") } },
    )
}

@Composable
private fun NameDialog(title: String, start: String, onDone: (String) -> Unit, onCancel: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(start) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, placeholder = { Text("Living room 5.1") }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onDone(text) }) { Text("Save") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

private fun whenText(ms: Long?): String? = ms?.let { java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it)) }

@Composable
private fun LevelsResult(u: TheatreUi) {
    val run = u.test?.last(RunKind.LEVELS)
    val c = deckColors
    RunSheet("Levels", whenText(run?.whenMillis) ?: "not set yet", trailing = { InfoButton("The level test", ABOUT_LEVELS, small = true) }) {
        if (run == null) Muted("Pink noise, one speaker at a time: set each level on the home theatre.")
        else {
            val worst = run.levels.values.maxOfOrNull { kotlin.math.abs(it) } ?: 0.0
            ReadingCells(listOf(Reading("±%.1f".format(worst), "dB", "left to set", if (worst > dev.autotune.dsp.LevelMeter.OK_DB) c.warn else c.good)))
        }
    }
}

@Composable
private fun DistancesResult(u: TheatreUi) {
    val d = u.distances
    val run = u.test?.last(RunKind.DISTANCES)
    val c = deckColors
    RunSheet("Distances", whenText(run?.whenMillis) ?: "not tested yet", trailing = { if (d != null) AlertsIcon(d.notes) else InfoButton("The distances test", ABOUT_DIST, small = true) }) {
        if (d == null) Muted("One sweep per speaker. Run it at setup, and again when a speaker moves.")
        else {
            ReadingCells(listOf(
                // nothing heard: no distance to show, never a green zero
                if (d.heardCount < 2) Reading("—", null, "off by")
                else Reading("%.2f".format(d.spreadM), "m", "off by", if (d.spreadM > TheatreJudge.DISTANCE_OK_M) c.warn else c.good),
                Reading("${d.heardCount}/${d.speakers.size}", null, "heard", if (d.heardCount < d.speakers.size) c.warn else null),
            ))
            Text(d.headline, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EqResult(u: TheatreUi) {
    val e = u.eq
    val run = u.test?.last(RunKind.EQ)
    val c = deckColors
    val notes = listOfNotNull(
        run?.system?.fatal, u.real?.takeIf { run?.pretend == true }?.let { "Pretend: really $it. Never kept as the reference." },
        ("The ${u.device}'s EQ is on, but this test plays Dolby, which passes by it: the reading is the system without it. " +
            "Check the EQ reaches the speakers plays through it.").takeIf { u.tvEqOn && u.method == dev.autotune.dsp.PlayMethod.DOLBY_51 && run != null },
    )
    RunSheet("EQ", whenText(run?.whenMillis) ?: "not tested yet", trailing = { if (notes.isNotEmpty()) AlertsIcon(notes) else InfoButton("The EQ test", ABOUT_EQTEST, small = true) }) {
        if (e == null) Muted("Every speaker together: one EQ for all of them.")
        else {
            ReadingCells(listOfNotNull(
                e.rmsDb?.let { now ->
                    val st = e.stockRmsDb
                    Reading("%.1f".format(now), "dB", "from target", if (e.worth) c.warn else c.good, note = if (st != null && kotlin.math.abs(st - now) > 0.05) "%+.1f vs start".format(now - st) else null)
                },
                Reading("${e.seats}", null, if (e.seats == 1) "seat" else "seats"),
            ))
            Text(e.headline, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            u.eqReached?.let { r ->
                Text(
                    when { r < 0 -> "eq check: not heard"; r >= 0.6 -> "eq check: it reaches the speakers (%.0f%%)".format(r * 100); else -> "eq check: it does not reach the speakers (%.0f%%)".format(r * 100) },
                    style = MaterialTheme.typography.labelSmall, color = if (r >= 0.6) c.good else c.warn, maxLines = 1,
                )
            }
        }
    }
}

/** The graph (the EQ runs ticked, against the target), every run with its note, the distances and the one EQ. */
@Composable
private fun RunsDetails(u: TheatreUi, vm: TheatreViewModel) {
    val t = u.test ?: return
    val grid = FreqGrid()
    val eqRuns = t.runs.filter { it.kind == RunKind.EQ && it.system?.heard == true }
    val shown = eqRuns.filter { it.whenMillis in u.compare }.ifEmpty { listOfNotNull(eqRuns.lastOrNull()) }
    if (shown.isNotEmpty()) {
        val curves = shown.mapIndexed { k, r ->
            val m = r.system!!.magDb!!
            val sm = grid.smoothOctave(m, 6.0)
            val ref = TheatreJudge.level(m, dev.autotune.dsp.Speaker.FL, grid)
            Curve(whenText(r.whenMillis) ?: "EQ test", DoubleArray(sm.size) { sm[it] - ref }, if (r === t.eqStock) 0 else k + 1)
        }
        ResponseChart(ChartData(grid.freqs, curves, DoubleArray(grid.size) { Target.HOME_THEATER.dbAt(grid.freqs[it]) }, null, ""), targetLabel = "Home theatre")
    }
    DetailsHeading()
    Fold("Runs", summary = "${t.runs.size} saved", startOpen = false, info = "Every run of this home theatre's tests, newest first. Tick EQ runs to draw them together on the graph; the first EQ run is the reference every EQ is fitted from.") {
        t.runs.asReversed().forEach { r ->
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                if (r.kind == RunKind.EQ && r.system?.heard == true) androidx.compose.material3.Checkbox(r.whenMillis in u.compare || (u.compare.isEmpty() && r === eqRuns.lastOrNull()), { vm.toggleCompare(r.whenMillis) })
                else Spacer(Modifier.width(48.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.kind.title + (if (r === t.eqStock) " · reference" else "") + (r.eqInUse?.let { " · EQ $it" } ?: ""), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Muted(listOfNotNull(whenText(r.whenMillis), r.note.ifBlank { null }, if (r.pretend) "pretend" else null).joinToString(" · "))
                }
            }
        }
        var note by rememberSaveable(t.id, u.focus) { mutableStateOf(t.last(u.focus)?.note.orEmpty()) }
        if (t.last(u.focus) != null) OutlinedTextField(
            value = note, onValueChange = { note = it; vm.setNote(u.focus, it) }, singleLine = true,
            label = { Text("Note on the last ${u.focus.title.lowercase()} run") }, modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
        )
    }
    u.distances?.let { d ->
        Fold("Distances per speaker", summary = "against front left", info = ABOUT_DIST) {
            d.speakers.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.speaker.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = if (s.heard) At.c.text else At.c.muted, maxLines = 1)
                    Text(if (s.heard) TheatreJudge.distanceText(s.distanceM) else "not heard", Modifier.width(88.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(s.delayMs?.let { "%+.2f ms".format(it) } ?: "—", Modifier.width(72.dp), style = MaterialTheme.typography.bodySmall, color = At.c.muted, maxLines = 1)
                }
            }
        }
    }
    u.eq?.let { e ->
        Fold("EQ for every speaker", summary = if (e.eq.isEmpty()) "no change" else "${e.eq.size} bands", info = ABOUT_EQ) {
            if (e.eq.isEmpty()) Muted("no change")
            e.eq.sortedBy { it.freqHz }.forEach { f ->
                Text("%s  %+.1f dB  Q %.1f".format(if (f.freqHz >= 1000) "%.1f kHz".format(f.freqHz / 1000) else "%.0f Hz".format(f.freqHz), f.gainDb, f.q), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
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

