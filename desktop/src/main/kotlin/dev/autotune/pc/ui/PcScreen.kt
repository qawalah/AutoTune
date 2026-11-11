package dev.autotune.pc.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.autotune.dsp.ChannelClip
import dev.autotune.dsp.EqChain
import dev.autotune.dsp.EqFilter
import dev.autotune.dsp.PlayMethod
import dev.autotune.dsp.Speaker
import dev.autotune.pc.ApoState
import dev.autotune.pc.PC_METHODS
import dev.autotune.pc.PcEndpoint
import dev.autotune.pc.PcUi
import dev.autotune.ui.gauge.GaugeColors
import dev.autotune.ui.gauge.MeasureDisplay
import dev.autotune.ui.gauge.sweepText
import dev.autotune.ui.room.ROOM_ASPECT
import dev.autotune.ui.room.Room3d
import dev.autotune.ui.room.RoomColors
import dev.autotune.ui.room.RoomSpeaker
import kotlin.math.log10
import kotlin.math.pow

private const val ABOUT_PHONE =
    "On the phone, open AutoTune: Tune › Connect to the companion app on a TV or PC. Pick this PC and enter the code once.\n\n" +
        "The phone and this PC must be on the same Wi-Fi. If Windows asks whether AutoTune may use the network, allow it " +
        "on private networks. When the PC is not found, the phone can connect by the address shown here (Advanced)."

private const val ABOUT_OUTPUT =
    "The output the speakers are on. The PC plays each speaker's test sound here, and AutoTune's EQ is set on it alone.\n\n" +
        "The EQ runs in Equalizer APO, a free sound processor for Windows: it reaches everything the PC plays on this " +
        "output, with no delay. Install it once, tick this output in its Configurator, restart, then press Allow AutoTune here " +
        "once (Windows asks for an administrator) so AutoTune may write its EQ."

private const val ABOUT_SPEAKERS =
    "Stereo plays front left and right. 5.1 and 7.1 play each speaker on its own channel, for a PC wired to a receiver " +
        "or soundbar over HDMI with Windows set to that many channels (Sound settings › the output › Speaker setup). The " +
        "phone's tests and the EQ follow."

private const val ABOUT_EQ =
    "The EQ the phone fitted, or the one you edited here. It stays on after AutoTune is closed, until you switch it off " +
        "here or on the phone. While a test sound plays it is held off, so the phone reads the speakers as they are.\n\n" +
        "Edit changes the bands on the PC: each one's frequency, gain (−15 to +6 dB) and width (Q). The preamp is set for " +
        "you, so the EQ never clips."

/** The PC's one window, wide: the room and the test on the left; the phone, the output and the EQ on the right. */
@Composable
fun PcScreen(u: PcUi, pc: PcEndpoint?) {
    Column(Modifier.fillMaxSize().background(Pc.body).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header(u)
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1.35f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // the room makes way for the test while one shows
                RoomDeck(u, compact = u.live != null || (u.result != null && u.phone != null))
                TestDeck(u)
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PhoneDeck(u)
                OutputDeck(u, pc)
                EqDeck(u, pc)
            }
        }
    }
}

@Composable
private fun Header(u: PcUi) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(28.dp)) { drawKnob() }
        Spacer(Modifier.width(10.dp))
        Text("AUTOTUNE", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = MaterialTheme.typography.labelLarge.fontSize * 0.28f), color = Pc.ink)
        Spacer(Modifier.weight(1f))
        Text(u.pcName.lowercase(), style = MaterialTheme.typography.labelSmall, color = Pc.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val roomColors = RoomColors(
    floor = androidx.compose.ui.graphics.Color(0xFFE9E7E1), floorLine = androidx.compose.ui.graphics.Color(0xFFC9C7C0),
    ink = Pc.ink, muted = Pc.muted, face = androidx.compose.ui.graphics.Color(0xFFF3F2EE), side = Pc.key,
    live = Pc.red, onLive = androidx.compose.ui.graphics.Color.White, screen = Pc.ink,
)

private val gauge = GaugeColors(Pc.key, Pc.ink, Pc.muted, Pc.line, Pc.good, Pc.warn, Pc.red, Pc.red)

/** The room as the phone and the TV draw it: the speaker playing lit, with what the phone found under each. */
@Composable
private fun RoomDeck(u: PcUi, compact: Boolean) {
    Deck("Speakers", "${u.layout.title} · ${u.method.title}") {
        val tm = rememberTextMeasurer()
        val inf = rememberInfiniteTransition(label = "rings")
        val phase by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "p")
        val level = u.live?.levelDb?.let { ((it + 60f) / 50f).coerceIn(0f, 1f) }
        val speakers = u.layout.speakers.associateWith { s ->
            val row = u.rows.firstOrNull { it.speaker == s }
            RoomSpeaker(s, row?.let { r -> listOf(r.trim, r.distance).filter { t -> t.isNotBlank() && t != "—" }.joinToString("\n").ifEmpty { null } }, silent = row?.heard == false)
        }
        Canvas(Modifier.fillMaxWidth().then(if (compact) Modifier.height(200.dp) else Modifier.aspectRatio(ROOM_ASPECT))) {
            with(Room3d) {
                drawRoom(
                    u.layout, u.playing, speakers, roomColors, tm,
                    TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    TextStyle(fontFamily = Inter, fontSize = 11.sp),
                    phase = phase, level = level, all = u.allPlaying,
                )
            }
        }
    }
}

/** What is playing, as the phone's display says it, and how far through the sweep (null outside one). */
private fun playingNow(u: PcUi): Pair<String, Float?> {
    val into = when {
        u.what == "sweep" -> dev.autotune.dsp.PassClip.nowPlaying(u.layout, u.playT)?.second
        u.what == null && u.playing != null -> ChannelClip.nowPlaying(u.playT)?.let { hz -> log10(hz / 20.0) / 3.0 * ChannelClip.SWEEP_SECONDS }
        else -> null
    }
    val who = if (u.allPlaying) "every speaker" else u.playing?.title?.lowercase()
    if (into != null) {
        val x = (into / ChannelClip.SWEEP_SECONDS).coerceIn(0.0, 1.0)
        return sweepText(x) + (who?.let { " · $it" } ?: "") to x.toFloat()
    }
    return when {
        u.what == "level noise" -> "Level noise" + (who?.let { " · $it" } ?: "") to null
        u.playT < ChannelClip.LEAD_SECONDS && (u.playing != null || u.what != null) -> "Waking the speakers" to null
        u.what != null || u.playing != null -> "Start beeps" to null
        else -> "Listening" to null
    }
}

/** While the phone records: its measuring display, the same as on the phone. After a test: the result. */
@Composable
private fun TestDeck(u: PcUi) {
    // the phone sends what it hears a few times a second; three seconds without means it stopped
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(u.live) { while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(500) } }
    val live = u.live?.takeIf { now - it.atMillis < 3000 || u.playing != null || u.allPlaying }
    val result = u.result
    when {
        live != null -> Deck("What the phone hears", u.phone) {
            val (headline, sweep) = playingNow(u)
            MeasureDisplay(
                live.levelDb, live.samples, headline, live.status, sweep, waiting = u.playing != null || u.allPlaying || u.what != null,
                gauge, change = live.change, okDb = dev.autotune.dsp.LevelMeter.OK_DB,
            )
        }
        result != null && u.phone != null -> Deck("Result", u.phone) {
            Text(result.headline, style = MaterialTheme.typography.titleMedium, color = Pc.ink, maxLines = 2)
            if (result.readings.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                result.readings.take(3).forEach { (v, unit, name) -> ReadingCell(v + if (unit.isBlank()) "" else " $unit", name, big = false) }
            }
            result.notes.take(3).forEach { Text("· $it", style = MaterialTheme.typography.bodySmall, color = Pc.warn, maxLines = 2) }
            result.next?.let { StatusLine("next: $it") }
        }
        u.phone != null -> Deck("Test", "waiting for the phone") { StatusLine("start a test on the phone: levels, distances or eq") }
        else -> Unit
    }
}

@Composable
private fun PhoneDeck(u: PcUi) {
    val phone = u.phone
    Deck("Phone", if (phone == null) "waiting on this wi-fi" else "connected", trailing = { InfoButton("Connect the phone", ABOUT_PHONE) }) {
        if (phone == null) {
            Row(verticalAlignment = Alignment.Bottom) {
                ReadingCell(u.code.toList().joinToString(" "), "code")
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(u.address ?: "no network", style = MaterialTheme.typography.titleMedium, color = Pc.ink)
                    Text("address", style = MaterialTheme.typography.labelSmall, color = Pc.muted)
                }
            }
        } else {
            val busy = u.playing != null || u.allPlaying || u.what != null
            StatusLine(if (busy) playingNow(u).first else phone, dot = if (busy) Pc.red else Pc.good)
        }
    }
}

@Composable
private fun OutputDeck(u: PcUi, pc: PcEndpoint?) {
    val st = u.apo.state
    Deck("Output", if (u.checking) "checking" else null) {
        DropSelect(u.outputs, u.outputs.indexOf(u.output).coerceAtLeast(0), { pc?.pickOutput(it) }, "output", Ic.SPEAKER, enabled = u.playing == null, info = ABOUT_OUTPUT)
        DropSelect(
            PC_METHODS.map { methodName(it) }, PC_METHODS.indexOf(u.method).coerceAtLeast(0), { pc?.setMethod(PC_METHODS[it]) },
            "speakers", Ic.SPEAKER, enabled = u.playing == null, info = ABOUT_SPEAKERS,
        )
        val need = u.method.slots.size
        u.outputChannels?.takeIf { it < need }?.let { StatusLine("this output takes $it channels: set it to ${methodName(u.method)} in windows sound settings", Pc.warn) }
        val tint = when (st) {
            ApoState.READY -> Pc.good
            ApoState.UNKNOWN -> Pc.muted
            else -> Pc.warn
        }
        StatusLine(st.line, tint)
        when (st) {
            ApoState.MISSING -> {
                ActionKey("Get Equalizer APO", Ic.DOWNLOAD, { open("https://sourceforge.net/projects/equalizerapo/") }, main = true)
                ActionKey("Check again", Ic.AGAIN, { pc?.recheck() }, enabled = !u.checking)
            }
            ApoState.NEEDS_SETUP -> ActionKey("Allow AutoTune", Ic.SHIELD, { pc?.allow() }, main = true, enabled = !u.checking)
            ApoState.NOT_ON_OUTPUT -> {
                // the outputs Equalizer APO is already on: one press switches to it
                val elsewhere = u.apo.withApo.filter { a -> !a.equals(u.output, ignoreCase = true) }
                if (elsewhere.isNotEmpty()) StatusLine("equalizer apo is on: " + elsewhere.joinToString(", "))
                elsewhere.take(2).forEach { a -> ActionKey("Use $a", Ic.SPEAKER, { pc?.useApoOutput(a) }, main = elsewhere.first() == a) }
                ActionKey("Open Equalizer APO's Configurator", Ic.OPEN, { pc?.openConfigurator() }, main = elsewhere.isEmpty())
                ActionKey("Check again", Ic.AGAIN, { pc?.recheck() }, enabled = !u.checking)
            }
            else -> Unit
        }
    }
}

private fun methodName(m: PlayMethod) = when (m) {
    PlayMethod.PCM_71 -> "7.1"
    PlayMethod.PCM_51 -> "5.1"
    else -> "2.0 stereo"
}

@Composable
private fun EqDeck(u: PcUi, pc: PcEndpoint?) {
    var editing by remember { mutableStateOf(false) }
    val sub = if (!u.hasEq) null else listOfNotNull(u.eqFrom?.let { "from $it" }, u.eqWhen?.let { PcEndpoint.stamp(it) }).joinToString(" · ")
    Deck("EQ", sub, trailing = { InfoButton("The EQ", ABOUT_EQ) }) {
        val pre = u.chains.values.minOfOrNull { it.preampDb }
        BigLatch(u.eqOn, { pc?.setEqOn(it) }, if (!u.hasEq) "none yet · the phone sends it, or edit one" else "preamp %.1f db".format(pre ?: 0.0), enabled = u.hasEq)
        if (u.hasEq && !editing) EqCurve(u.chains, u.eqOn)
        val problem = u.error ?: if (u.hasEq) u.apo.problem(u.output) else null
        Alerts(listOfNotNull(problem))
        if (editing) EqEditor(u, onDone = { c -> pc?.setChains(c); editing = false }, onCancel = { editing = false })
        else ActionKey(if (u.hasEq) "Edit the EQ" else "Make an EQ here", Ic.EDIT, { editing = true }, enabled = u.playing == null)
    }
}

/** One band as typed: kept as text until saved, so a half-typed number is never lost. */
private class BandText(f: String, g: String, q: String) {
    var f by mutableStateOf(f); var g by mutableStateOf(g); var q by mutableStateOf(q)
    fun band(): EqFilter? {
        val fv = f.replace(',', '.').toDoubleOrNull() ?: return null
        val gv = g.replace(',', '.').replace('−', '-').toDoubleOrNull() ?: return null
        val qv = q.replace(',', '.').toDoubleOrNull() ?: return null
        return EqFilter(fv.coerceIn(20.0, 20000.0), gv.coerceIn(-15.0, 6.0), qv.coerceIn(0.1, 20.0))
    }
}

/**
 * The EQ's bands, editable: one speaker's (or every speaker's at once, when they share one EQ). Save writes it to
 * Equalizer APO with a preamp that keeps it from clipping.
 */
@Composable
private fun EqEditor(u: PcUi, onDone: (Map<Speaker, EqChain>) -> Unit, onCancel: () -> Unit) {
    val speakers = u.method.slots.filterNotNull()
    val same = u.chains.values.map { c -> c.filters }.distinct().size <= 1
    // "every speaker" first when they share one EQ (or have none yet)
    val choices: List<Speaker?> = (if (same) listOf<Speaker?>(null) else emptyList()) + speakers
    var pick by remember { mutableIntStateOf(0) }
    val edits = remember { HashMap<Speaker?, List<BandText>>() }
    fun bandsOf(s: Speaker?): List<BandText> {
        val chain = if (s == null) u.chains.values.firstOrNull() else u.chains[s]
        return chain?.filters.orEmpty().map { BandText("%.0f".format(it.freqHz), "%.1f".format(it.gainDb), "%.2f".format(it.q)) }
    }
    val who = choices.getOrNull(pick)
    val rows = remember(who) { mutableStateListOf<BandText>().apply { addAll(edits[who] ?: bandsOf(who)) } }
    LaunchedEffect(rows.toList()) { edits[who] = rows.toList() }

    DropSelect(choices.map { it?.title ?: "every speaker" }, pick, { pick = it }, "edit", Ic.SPEAKER)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (h in listOf("frequency, hz", "gain, db", "q")) Text(h, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Pc.muted)
        Spacer(Modifier.width(40.dp))
    }
    rows.forEachIndexed { i, b ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val bad = b.band() == null
            for ((value, set) in listOf<Pair<String, (String) -> Unit>>(b.f to { v -> b.f = v }, b.g to { v -> b.g = v }, b.q to { v -> b.q = v })) {
                OutlinedTextField(
                    value, set, Modifier.weight(1f), singleLine = true, isError = bad,
                    textStyle = MaterialTheme.typography.bodyMedium, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            Box(Modifier.size(40.dp).clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Remove band") { rows.removeAt(i) }, contentAlignment = Alignment.Center) { PcIcon(Ic.CLOSE, Pc.muted, 18.dp) }
        }
    }
    if (rows.size < 10) ActionKey("Add a band", Ic.PLUS, { rows.add(BandText("1000", "0.0", "1.00")) })
    val allOk = edits.values.all { l -> l.all { it.band() != null } } && rows.all { it.band() != null }
    ActionKey("Save to Equalizer APO", Ic.CHECK, {
        edits[who] = rows.toList()
        val out = LinkedHashMap<Speaker, EqChain>()
        // start from what is there, then lay each edited choice over it
        for (s in speakers) u.chains[s]?.let { out[s] = it }
        edits[null]?.let { l -> val c = EqChain.withSafePreamp(l.mapNotNull { it.band() }); for (s in speakers) out[s] = c }
        for ((s, l) in edits) if (s != null) out[s] = EqChain.withSafePreamp(l.mapNotNull { it.band() })
        onDone(out.filterValues { it.filters.isNotEmpty() })
    }, main = true, enabled = allOk)
    ActionKey("Cancel", Ic.CLOSE, onCancel)
}


/** Each speaker's EQ from 20 Hz to 20 kHz, ±12 dB: front left in ink, front right in blue; dashed while it is off. */
@Composable
private fun EqCurve(chains: Map<Speaker, EqChain>, on: Boolean) {
    Column {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val w = size.width
            val h = size.height
            fun x(f: Double) = (log10(f / 20.0) / 3.0 * w).toFloat()
            fun y(db: Double) = (h / 2 - db.coerceIn(-12.0, 12.0) / 12.0 * h / 2).toFloat()
            for (f in listOf(100.0, 1000.0, 10000.0)) drawLine(Pc.line.copy(alpha = 0.6f), Offset(x(f), 0f), Offset(x(f), h), 1f)
            for (db in listOf(-6.0, 6.0)) drawLine(Pc.line.copy(alpha = 0.4f), Offset(0f, y(db)), Offset(w, y(db)), 1f)
            drawLine(Pc.line, Offset(0f, y(0.0)), Offset(w, y(0.0)), 1f)
            val shown = chains.entries.distinctBy { it.value.filters }
            for ((s, c) in shown) {
                val ink = if (s == Speaker.FR && shown.size > 1) Pc.second else Pc.ink
                val path = Path()
                for (k in 0..160) {
                    val f = 20.0 * 1000.0.pow(k / 160.0)
                    val db = c.responseDb(f)
                    if (k == 0) path.moveTo(x(f), y(db)) else path.lineTo(x(f), y(db))
                }
                drawPath(path, if (on) ink else ink.copy(alpha = 0.5f), style = Stroke(2f, pathEffect = if (on) null else PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
            }
        }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp)) {
            val w = maxWidth
            Text("20 hz", style = MaterialTheme.typography.labelSmall, color = Pc.muted)
            for ((f, l) in listOf(100.0 to "100", 1000.0 to "1k", 10000.0 to "10k")) {
                Text(l, Modifier.padding(start = w * (log10(f / 20.0) / 3.0).toFloat() - 6.dp), style = MaterialTheme.typography.labelSmall, color = Pc.muted)
            }
        }
        val shown = chains.entries.distinctBy { it.value.filters }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (shown.size == 1 && chains.size > 1) Text("every speaker", style = MaterialTheme.typography.labelSmall, color = Pc.muted)
            else for (s in chains.keys.sortedBy { it.ordinal }) Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp, 2.dp).background(if (s == Speaker.FR) Pc.second else Pc.ink))
                Spacer(Modifier.width(6.dp))
                Text(s.title.lowercase(), style = MaterialTheme.typography.labelSmall, color = Pc.muted)
            }
        }
    }
}

private fun open(url: String) {
    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
}
