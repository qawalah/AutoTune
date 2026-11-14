package dev.autotune.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
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
import dev.autotune.theatre.CompanionLink
import dev.autotune.theatre.TheatreUi
import dev.autotune.theatre.TheatreViewModel
import dev.autotune.theatre.RunKind
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
fun TheatrePage(pick: Pickers, openId: String? = null, opened: () -> Unit = {}, newName: String? = null, sharer: Sharer? = null, picked: dev.autotune.Equipment? = null) {
    val vm: TheatreViewModel = viewModel()
    val u by vm.ui.collectAsState()
    LaunchedEffect(openId) { openId?.let { vm.selectTest(it); opened() } }
    // from Tune › Set up: a new home theatre once connected, never the last one measured (2.24)
    // 2.33: the remembered device's home theatre opens when there is one; a new one is made from the list's New
    LaunchedEffect(newName) { newName?.let { vm.fromTune(it, picked == dev.autotune.Equipment.PC); opened() } }
    DisposableEffect(Unit) { vm.start(); onDispose { vm.pause() } }
    LaunchedEffect(u.needsMic) { if (u.needsMic) { vm.micGranted(true); pick.permission() } }
    // a test can run past the screen timeout: the screen stays on until it ends (2.31)
    KeepScreenOn(u.busy)

    // the home theatres are managed on their own page over this one; Back returns
    var managing by rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = managing) { managing = false }
    if (managing) { TheatresPage(u, vm, picked) { managing = false }; return }

    // titled by what is connected: PC speakers or a home theatre (2.32: "Home theatre" for a PC too)
    ScreenTitle(dev.autotune.connectTitle(if (u.tv != null) u.device else null, picked), context = u.tv ?: "not connected", contextIcon = Ic.HOME)
    if (u.tv == null) {
        Connect(u, vm, picked)
        // the open home theatre's last results stay readable while nothing is connected; not when Tune's Set up
        // started a new one, which is made as soon as it connects
        if (u.test != null && !u.startingNew) {
            TheatreLine(u, picked) { managing = true }
            LevelsResult(u)
            DistancesResult(u)
            EqResult(u)
            RunsDetails(u, vm)
        }
    } else if (u.threeTests) ThreeTests(u, vm, sharer, picked) { managing = true } else Connected(u, vm)
}

@Composable
private fun Connect(u: TheatreUi, vm: TheatreViewModel, picked: dev.autotune.Equipment? = null) {
    var tvIdx by rememberSaveable { mutableIntStateOf(0) }
    val tvName = u.found.getOrNull(tvIdx.coerceAtMost((u.found.size - 1).coerceAtLeast(0)))
    var code by rememberSaveable(tvName) { mutableStateOf(tvName?.let { u.codes[it] }.orEmpty()) }
    var address by rememberSaveable { mutableStateOf("") }
    // Simple offers the address once nothing has been found for a few seconds (2.32: a dead end before). Counted by the
    // page's own clock, a second per tick (2.33: wall-clock time did not move with the walk harness's clock, so the
    // field never came there; on a phone the two run together)
    var lookingMs by androidx.compose.runtime.remember { mutableStateOf(0L) }
    val none = u.found.isEmpty()
    LaunchedEffect(none) {
        lookingMs = 0L
        while (none) { kotlinx.coroutines.delay(1000); lookingMs += 1000 }
    }
    val showAddress = dev.autotune.offersAddress(isAdvanced(), none, lookingMs)
    // the remembered TV or PC is connected again by itself (2.25): say so while it is
    val remembered = CompanionLink.rememberedName(androidx.compose.ui.platform.LocalContext.current)
    val sub = when {
        (u.connecting || u.reconnecting) && remembered != null -> "connecting to $remembered"
        u.found.isEmpty() -> "looking on this wi-fi"
        else -> "${u.found.size} found"
    }
    // the device by its name once known, else what Set up picked; the kind remembered must match what was picked
    val rememberedPc = CompanionLink.rememberedKind(androidx.compose.ui.platform.LocalContext.current) == dev.autotune.link.Msg.KIND_PC
    val device = dev.autotune.connectDevice(remembered?.takeIf { picked == null || (picked == dev.autotune.Equipment.PC) == rememberedPc }, picked)
    RunSheet("Find ${device.removePrefix("the ")}", sub, trailing = { InfoButton("Find ${device.removePrefix("the ")}", ABOUT_TV + "\n\n" + GET_AUTOTUNE, small = true) }) {
        // three facts that tick themselves, in place of a bare "Nothing found yet" (2.33)
        dev.autotune.connectSteps(device, !none, code.length).forEach { (text, done) -> StatusTick(done, text) }
        if (!none) DropSelect(u.found, tvIdx.coerceAtMost(u.found.size - 1), { tvIdx = it }, label = "TV or PC", icon = Ic.HOME)
        OutlinedTextField(
            value = code, onValueChange = { v -> code = v.filter { it.isDigit() }.take(4) },
            label = { Text("Code on its screen") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().settleIn().bringIntoViewOnFocus(), colors = atFieldColors(), shape = atFieldShape(),
        )
        if (showAddress) {
            OutlinedTextField(
                value = address, onValueChange = { address = it.trim() },
                label = { Text("Address (when it is not found)") }, placeholder = { Text("192.168.1.20") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().settleIn().bringIntoViewOnFocus(), colors = atFieldColors(), shape = atFieldShape(),
            )
        }
    }
    u.error?.let { Alerts(listOf(it), title = "Connecting") }
    val byAddress = address.isNotBlank() && showAddress
    PinAction(
        if (u.connecting) "Connecting" else "Connect", icon = Ic.SEND,
        enabled = !u.connecting && code.length == 4 && (byAddress || tvName != null),
        onClick = { if (byAddress) vm.connectAddress(address, code) else vm.connect(tvIdx, code) },
        sub = tvName?.lowercase() ?: "the tv or pc",
    )
}

@Composable
private fun StatusTick(done: Boolean, text: String) {
    Row(Modifier.heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (done) AtIcon(Ic.CHECK, At.c.good, size = 18.dp) else Box(Modifier.size(8.dp).clip(CircleShape).background(At.c.line))
        }
        Text(text, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium, color = if (done) At.c.text else At.c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Connected(u: TheatreUi, vm: TheatreViewModel) {
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
        ResponseChart(ChartData(grid.freqs, curves, DoubleArray(grid.size) { u.target.dbAt(grid.freqs[it]) }, null, ""), targetLabel = u.target.name)
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
private fun ThreeTests(u: TheatreUi, vm: TheatreViewModel, sharer: Sharer?, picked: dev.autotune.Equipment?, manage: () -> Unit) {
    // before the first EQ test only the device, the speakers, the room and the three tests (2.33); the EQ key, the
    // flavour, Refine and the runs come with the first EQ
    val hasEq = dev.autotune.theatreShowsEq(u.test?.last(RunKind.EQ) != null, u.tvEqOn)
    val lv = u.level
    val level by LiveCapture.level.collectAsState()
    val ring = if (u.playing != null || u.allPlaying) ((level + 60f) / 50f).coerceIn(0f, 1f) else null
    val d = u.distances
    val notes: Map<dev.autotune.dsp.Speaker, String> = buildMap {
        u.levelsDone.forEach { (s, ch) -> put(s, TheatreJudge.trimText(ch)) }
        lv?.change?.let { put(lv.speaker, TheatreJudge.trimText(it)) }
        if (lv == null) d?.speakers?.forEach { r -> put(r.speaker, listOfNotNull(this[r.speaker], TheatreJudge.distanceText(r.distanceM)).joinToString("\n")) }
    }
    DeviceDeck(u, vm)
    // the speakers decide every test: chosen up here, beside the device (2.32: it sat near the bottom)
    val method = u.method
    if (method != null && method.layouts.size > 1 && !u.busy) {
        DropSelect(
            method.layouts.map { it.title }, method.layouts.indexOf(u.layout).coerceAtLeast(0), { vm.setLayout(method.layouts[it]) },
            label = "Speakers", icon = Ic.SPEAKER,
            info = "The speakers your home theatre has. ${method.title} from this ${u.device} reaches up to ${method.layouts.first().title}.",
        )
    }
    TheatreLine(u, picked, manage)
    Panel(pad = 12.dp) {
        SpeakerMap(u.layout, u.playing, level = ring, all = u.allPlaying, notes = notes, silent = d?.speakers?.filter { !it.heard }?.map { it.speaker }?.toSet().orEmpty().takeIf { !u.busy }.orEmpty())
        // under the room only what is happening (the output is in the device's deck above)
        val line = u.status ?: if (u.pretend) "pretend ${u.layout.title} · really ${u.real ?: "its own speakers"}" else null
        line?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = if (u.pretend && u.status == null) At.c.signal else At.c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
    // whether the TV's EQ is on, as a big key you can't miss: on sends the EQ, off switches it off
    if (hasEq) EqKey(u, vm)
    // what plays on top of the EQ there, in both modes; never measured
    val flavours = dev.autotune.Flavour.entries
    if (hasEq) DropSelect(
        flavours.map { it.title }, flavours.indexOf(u.flavour), { vm.setFlavour(flavours[it]) }, label = "Flavour", enabled = !u.busy,
        details = flavours.map { it.detail }, icons = flavours.map { flavourIcon(it) },
        info = "A flavour plays on top of the EQ on the ${u.device}. It only plays: every test that plays through the ${u.device}'s EQ gets the EQ alone, and the flavour comes back after it. The widget can change it too.",
    )
    u.error?.let { Alerts(listOf(it), title = "The ${u.device}") }

    when {
        lv != null -> LevelDeck(lv)
        u.waitingSeat -> RunSheet("EQ test", "seat ${u.seat} of ${u.seats}", trailing = { InfoButton("The EQ test", ABOUT_EQTEST, small = true) }) {
            ReadingCells(listOf(Reading("${u.seat}", null, "move the phone to the next seat, at ear height")))
        }
        // the three tests side by side, each with its own key inside its card: you choose, nothing is pushed
        !u.busy -> {
            LevelsResult(u) { TextAction("Set the levels", vm::levelTest, icon = Ic.SPEAKER) }
            DistancesResult(u) { TextAction("Test the distances", vm::distancesTest, icon = Ic.MEASURE) }
            EqResult(u) {
                // what the EQ is fitted to, in both modes, before the first EQ test too (owner, 2026-11-12)
                TheatreTargetPick(u, vm)
                TextAction("Test the EQ", vm::eqTest, icon = Ic.MEASURE)
                if (u.tvEqOn && u.eq?.eq?.isNotEmpty() == true) TextAction("Check the EQ reaches the speakers", vm::eqCheck, icon = Ic.CHECK)
                if (isAdvanced()) {
                    DropSelect(
                        listOf("1 seat", "3 seats", "5 seats"), listOf(1, 3, 5).indexOf(u.seats).coerceAtLeast(0), { vm.setSeats(listOf(1, 3, 5)[it]) },
                        label = "EQ test seats", icon = Ic.MIC,
                        info = "The EQ test can average the system over several seats, so the EQ suits the whole sofa rather than one spot. The phone waits at each seat for Next seat.",
                    )
                }
            }
            // what the phone's Refine step offers, once there is an EQ to refine
            if (u.eq?.eq?.isNotEmpty() == true) RefineDeck(u, vm, sharer)
        }
    }

    // a key is pinned only while a test runs: next speaker or next seat while it waits, stop while it plays
    when {
        lv != null -> {
            val next = u.layout.speakers.getOrNull(lv.index + 1)
            PinAction("Next speaker", vm::nextLevel, icon = Ic.NEXT, sub = next?.let { "then ${it.title.lowercase()}" } ?: "the last one")
        }
        u.waitingSeat -> PinAction("Measure seat ${u.seat}", vm::nextSeat, icon = Ic.MEASURE, sub = "of ${u.seats}")
        u.busy -> PinAction("Stop", vm::stop, icon = Ic.STOP, sub = u.status)
        else -> Unit
    }
    if (lv != null || u.waitingSeat) Actions { TextAction("Stop", vm::stop, icon = Ic.STOP) }
    if (!u.busy && hasEq) RunsDetails(u, vm)
}

/**
 * The TV's (or PC's) EQ as one big latching key: "EQ on" with the pilot lit red while the EQ plays there, "EQ off"
 * otherwise. Pressing it sends the EQ from the last EQ test, or switches it off.
 */
@Composable
private fun EqKey(u: TheatreUi, vm: TheatreViewModel) {
    val c = At.c
    val has = u.eq?.eq?.isNotEmpty() == true
    val enabled = !u.busy && (u.tvEqOn || has)
    val on = u.tvEqOn
    val shape = MaterialTheme.shapes.medium
    Row(
        Modifier.fillMaxWidth().heightIn(min = 76.dp).offset(y = if (on) 2.dp else 0.dp).clip(shape)
            .background(if (on) androidx.compose.ui.graphics.lerp(c.panelHigh, c.line, 0.55f) else c.panelHigh)
            .border(1.dp, if (on) c.text else c.line, shape)
            .toggleable(value = on, enabled = enabled, role = androidx.compose.ui.semantics.Role.Switch) { want -> if (want) vm.useEq() else vm.tvEqOff() }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(if (on) c.signal else c.muted.copy(alpha = 0.4f)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (on) "EQ on" else "EQ off", style = MaterialTheme.typography.headlineMedium, color = if (enabled || on) c.text else c.muted)
            Text(
                when {
                    on -> "on the ${u.device.lowercase()} · press for off"
                    has -> "press to use it on the ${u.device.lowercase()}"
                    else -> "no eq yet · test the eq first"
                },
                style = MaterialTheme.typography.labelSmall, color = c.muted, maxLines = 1,
            )
        }
    }
}

/** The TV or PC connected: its name and output with a lit pilot, and Disconnect, in one deck at the top. */
@Composable
private fun DeviceDeck(u: TheatreUi, vm: TheatreViewModel) {
    val c = At.c
    Panel(pad = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(c.signal))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(u.tv.orEmpty(), style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "connected · " + if (u.pretend) "pretend ${u.layout.title}" else listOfNotNull(u.output, u.method?.title).joinToString(" · ").lowercase(),
                    style = MaterialTheme.typography.labelSmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (!u.busy) androidx.compose.material3.TextButton(onClick = vm::disconnect) {
                AtIcon(Ic.CLOSE, c.text, size = 18.dp)
                Spacer(Modifier.width(6.dp))
                Text("Disconnect", style = MaterialTheme.typography.labelLarge, color = c.text)
            }
        }
    }
}

/** Which home theatre is open, as one line: tapping it opens the home theatres' own page. */
@Composable
private fun TheatreLine(u: TheatreUi, picked: dev.autotune.Equipment?, manage: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(MaterialTheme.shapes.medium)
            .clickable(enabled = !u.busy, role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Home theatres", onClick = manage)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AtIcon(Ic.HOME, At.c.muted, size = 20.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            // what is tuned: a PC's speakers are not a home theatre (2.33)
            Text(theatreWord(u, picked).lowercase(), style = MaterialTheme.typography.labelSmall, color = At.c.muted)
            Text(u.test?.name ?: "none yet", style = MaterialTheme.typography.titleMedium, color = At.c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AtIcon(Ic.CHEVRON, At.c.muted, size = 18.dp)
    }
}

/** "Home theatre" or "PC speakers": what is tuned, by the device connected, else what Set up picked (2.33). */
private fun theatreWord(u: TheatreUi, picked: dev.autotune.Equipment?): String =
    dev.autotune.connectTitle(if (u.tv != null) u.device else null, picked).let { if (it == "TV or PC") "Home theatre" else it }

/**
 * The home theatres on their own page: pick one, start a new one, rename or delete the open one. Connected, only that
 * device's are listed; not connected, they are grouped under each device's name (2.33).
 */
@Composable
private fun TheatresPage(u: TheatreUi, vm: TheatreViewModel, picked: dev.autotune.Equipment?, back: () -> Unit) {
    var naming by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val word = theatreWord(u, picked)
    val groups = dev.autotune.theatreGroups(u.entries, u.tv)
    val shown = groups.sumOf { it.second.size }
    ScreenTitle(dev.autotune.theatreListTitle(word == "PC speakers"), context = u.tv ?: "$shown", contextIcon = Ic.HOME)
    groups.filter { it.second.isNotEmpty() }.forEach { (device, list) ->
        Panel(pad = 4.dp) {
            if (device != null || u.tv == null) Text(
                (device ?: "no tv or pc yet").lowercase(), Modifier.padding(start = 12.dp, top = 8.dp),
                style = MaterialTheme.typography.labelSmall, color = At.c.muted,
            )
            list.forEach { e ->
                val open = e.id == u.test?.id
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.medium)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { vm.selectTest(e.id); back() }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (open) At.c.signal else Color.Transparent))
                    Spacer(Modifier.width(12.dp))
                    Text(e.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = At.c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    val one = word.lowercase()
    Actions {
        TextAction("New", { naming = "" }, icon = Ic.PLUS)
        if (u.test != null) {
            TextAction("Rename ${u.test.name}", { renaming = true }, icon = Ic.EDIT)
            TextAction("Delete ${u.test.name}", { deleting = true }, icon = Ic.TRASH, danger = true)
        }
        TextAction("Back", back, icon = Ic.BACK)
    }
    naming?.let { text ->
        NameDialog("New: $one", text, onDone = { n -> vm.newTest(n); naming = null; back() }, onCancel = { naming = null })
    }
    if (renaming) NameDialog("Rename", u.test?.name.orEmpty(), onDone = { n -> vm.renameTest(n); renaming = false }, onCancel = { renaming = false })
    if (deleting) androidx.compose.material3.AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("Delete ${u.test?.name ?: one}?") },
        text = { Text("Every run of it is deleted, and its EQ if it was the last one sent.") },
        confirmButton = { DialogKey("Delete", { vm.deleteTest(); deleting = false }, danger = true) },
        dismissButton = { DialogKey("Keep it", { deleting = false }) },
    )
}

@Composable
private fun NameDialog(title: String, start: String, onDone: (String) -> Unit, onCancel: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(start) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, placeholder = { Text("Living room 5.1") }, modifier = Modifier.fillMaxWidth(), colors = atFieldColors(), shape = atFieldShape()) },
        confirmButton = { DialogKey("Save", { onDone(text) }) },
        dismissButton = { DialogKey("Cancel", onCancel) },
    )
}

private fun whenText(ms: Long?): String? = ms?.let { java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it)) }

@Composable
private fun LevelsResult(u: TheatreUi, keys: (@Composable () -> Unit)? = null) {
    val run = u.test?.last(RunKind.LEVELS)
    val c = deckColors
    // numbered, so a first-time user sees an order; nothing is pushed (owner: no nagging about levels)
    RunSheet("1 Levels", whenText(run?.whenMillis) ?: "not set yet", trailing = { InfoButton("The level test", ABOUT_LEVELS, small = true) }) {
        if (run == null) Muted("Pink noise, one speaker at a time: set each level on the home theatre.")
        else {
            val worst = run.levels.values.maxOfOrNull { kotlin.math.abs(it) } ?: 0.0
            ReadingCells(listOf(Reading("±%.1f".format(worst), "dB", "left to set", if (worst > dev.autotune.dsp.LevelMeter.OK_DB) c.warn else c.good)))
        }
        keys?.let { Actions { it() } }
    }
}

@Composable
private fun DistancesResult(u: TheatreUi, keys: (@Composable () -> Unit)? = null) {
    val d = u.distances
    val run = u.test?.last(RunKind.DISTANCES)
    val c = deckColors
    RunSheet("2 Distances", whenText(run?.whenMillis) ?: "not tested yet", trailing = { if (d != null) AlertsIcon(d.notes) else InfoButton("The distances test", ABOUT_DIST, small = true) }) {
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
        keys?.let { Actions { it() } }
    }
}

@Composable
private fun EqResult(u: TheatreUi, keys: (@Composable () -> Unit)? = null) {
    val e = u.eq
    val run = u.test?.last(RunKind.EQ)
    val c = deckColors
    val notes = listOfNotNull(
        run?.system?.fatal, u.real?.takeIf { run?.pretend == true }?.let { "Pretend: really $it. Never kept as the reference." },
        ("The ${u.device}'s EQ is on, but this test plays Dolby, which passes by it: the reading is the system without it. " +
            "Check the EQ reaches the speakers plays through it.").takeIf { u.tvEqOn && u.method == dev.autotune.dsp.PlayMethod.DOLBY_51 && run != null },
    )
    RunSheet("3 EQ", whenText(run?.whenMillis) ?: "not tested yet", trailing = { if (notes.isNotEmpty()) AlertsIcon(notes) else InfoButton("The EQ test", ABOUT_EQTEST, small = true) }) {
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
        keys?.let { Actions { it() } }
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
        ResponseChart(ChartData(grid.freqs, curves, DoubleArray(grid.size) { u.target.dbAt(grid.freqs[it]) }, null, ""), targetLabel = u.target.name)
    }
    DetailsHeading()
    Fold("EQ runs", summary = "${eqRuns.size} saved", startOpen = false, info = "Every EQ test of this home theatre, newest first. Tick runs to draw them together on the graph; the first one is the reference every EQ is fitted from. Levels and distances keep their own cards.") {
        t.runs.filter { it.kind == RunKind.EQ }.asReversed().forEach { r ->
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                if (r.kind == RunKind.EQ && r.system?.heard == true) androidx.compose.material3.Checkbox(r.whenMillis in u.compare || (u.compare.isEmpty() && r === eqRuns.lastOrNull()), { vm.toggleCompare(r.whenMillis) })
                else Spacer(Modifier.width(48.dp))
                Column(Modifier.weight(1f)) {
                    Text((whenText(r.whenMillis) ?: "EQ test") + (if (r === t.eqStock) " · reference" else "") + (r.eqInUse?.let { " · EQ $it" } ?: ""), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Muted(listOfNotNull("${r.seats} ${if (r.seats == 1) "seat" else "seats"}", r.note.ifBlank { null }, if (r.pretend) "pretend" else null).joinToString(" · "))
                }
            }
        }
        var note by rememberSaveable(t.id) { mutableStateOf(t.last(RunKind.EQ)?.note.orEmpty()) }
        if (t.last(RunKind.EQ) != null) OutlinedTextField(
            value = note, onValueChange = { note = it; vm.setNote(RunKind.EQ, it) }, singleLine = true,
            label = { Text("Note on the last EQ run") }, modifier = Modifier.fillMaxWidth().settleIn().bringIntoViewOnFocus(), colors = atFieldColors(), shape = atFieldShape(),
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

private const val ABOUT_REFINE =
    "The EQ the last EQ test suggests, fitted from this home theatre's first EQ test so EQs never stack.\n\n" +
        "Use it on the TV or PC with the EQ key above, or set the bands by hand in the home theatre's own EQ (or its app), " +
        "with the preamp first. The files are for Equalizer APO (ParametricEQ.txt) and graphic EQ apps (GraphicEQ.txt).\n\n" +
        "Then test the EQ again, with the EQ on, to see what it did."

/**
 * Refine on the home-theatre page (owner, 2026-11-12: "I don't see refine options when connected to companion"): the
 * suggested EQ's bands to set by hand and its files; Advanced adds the target and the taste, kept per home theatre,
 * which refit the EQ (and resend it when it is on). The EQ key above stays the way to use it there.
 */
@Composable
private fun RefineDeck(u: TheatreUi, vm: TheatreViewModel, sharer: Sharer?) {
    val eq = u.eq?.eq?.sortedBy { it.freqHz }.orEmpty()
    val chain = dev.autotune.dsp.EqChain(eq, dev.autotune.dsp.EqChain.withSafePreamp(eq).preampDb)
    var bands by rememberSaveable(u.test?.id) { mutableStateOf(false) }
    Group("Refine", info = ABOUT_REFINE) {
        Muted("${eq.size} bands · preamp %.1f dB".format(chain.preampDb) + if (u.tvEqOn) " · on the ${u.device}" else "")
        if (isAdvanced()) {
            // the target itself is on the EQ card above
            DropSelect(BASS_STEPS.map(::db), BASS_STEPS.indexOf(u.bassDb).let { if (it < 0) 1 else it }, { vm.setTaste(BASS_STEPS[it], u.trebleDb) }, label = "Bass to taste", icon = Ic.BASS)
            DropSelect(TREBLE_STEPS.map(::db), TREBLE_STEPS.indexOf(u.trebleDb).let { if (it < 0) 3 else it }, { vm.setTaste(u.bassDb, TREBLE_STEPS[it]) }, label = "Treble to taste", icon = Ic.TREBLE)
        }
        Actions {
            TextAction(if (bands) "Hide the bands" else "Set it by hand on the home theatre", { bands = !bands }, icon = Ic.EDIT)
        }
        if (bands) BandTable(filterLines(chain, null), chain.preampDb, compared = false)
        if (sharer != null) Actions {
            TextAction("Share the EQ files", { vm.eqFiles()?.let { sharer.files(it, "text/plain", "Share the EQ files") } }, icon = Ic.SHARE)
        }
    }
}

/**
 * The home theatre's target, the room curves only (it is a home theatre or a PC's speakers): kept per home theatre;
 * a change refits the EQ and resends it while it is on. On the EQ card in Simple too: unlike a car, where Simple
 * keeps Harman car without asking, the owner wants to choose it here (2026-11-12).
 */
@Composable
private fun TheatreTargetPick(u: TheatreUi, vm: TheatreViewModel) {
    val kinds = Target.ALL.map { dev.autotune.targetKind(it) }
    val idxs = dev.autotune.equipmentTargets(dev.autotune.Equipment.HOME, kinds)
    val cur = idxs.indexOfFirst { Target.ALL[it].name == u.targetName }.coerceAtLeast(0)
    DropSelect(
        idxs.map { Target.ALL[it].name }, cur, { vm.setTarget(Target.ALL[idxs[it]].name) }, label = "Target sound",
        icons = idxs.map { kindIcon(kinds[it]) }, details = idxs.map { targetDetail(Target.ALL[it]) }, enabled = !u.busy && u.test != null,
        info = "The sound the EQ aims for. Home theatre is a gentle room curve; Harman room has more bass. Each home theatre keeps its own; changing it refits the EQ (and sends it again while it is on).",
    )
}

