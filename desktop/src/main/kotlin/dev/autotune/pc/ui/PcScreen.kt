package dev.autotune.pc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.autotune.dsp.ChannelClip
import dev.autotune.dsp.EqChain
import dev.autotune.dsp.Speaker
import dev.autotune.link.ShownRow
import dev.autotune.pc.ApoState
import dev.autotune.pc.PcEndpoint
import dev.autotune.pc.PcUi
import kotlin.math.log10
import kotlin.math.pow

private const val ABOUT_PHONE =
    "On the phone, open AutoTune: Tune › Speakers on a TV or PC. Pick this PC and enter the code once.\n\n" +
        "The phone and this PC must be on the same Wi-Fi. If Windows asks whether AutoTune may use the network, allow it " +
        "on private networks. When the PC is not found, the phone can connect by the address shown here (Advanced)."

private const val ABOUT_OUTPUT =
    "The output the speakers are on. The PC plays each speaker's test sound here, and AutoTune's EQ is set on it alone.\n\n" +
        "The EQ runs in Equalizer APO, a free sound processor for Windows: it reaches everything the PC plays on this " +
        "output, with no delay. Install it once, tick this output in its Configurator, restart, then press Allow AutoTune here " +
        "once (Windows asks for an administrator) so AutoTune may write its EQ."

private const val ABOUT_EQ =
    "The EQ the phone fitted for each speaker, from the first full test so EQs never stack. It stays on after AutoTune is " +
        "closed, until you switch it off here or on the phone. While a test sound plays it is held off, so the phone reads the " +
        "speakers as they are."

/** The PC's one window: who is connected (or the code), the speakers, the output and its EQ. */
@Composable
fun PcScreen(u: PcUi, pc: PcEndpoint?) {
    Column(
        Modifier.fillMaxSize().background(Pc.body).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Header(u)
        PhoneDeck(u)
        OutputDeck(u, pc)
        EqDeck(u, pc)
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
            SpeakerPair(u.playing, u.rows)
            val now = u.playing?.let { sp ->
                val hz = ChannelClip.nowPlaying(u.playT)
                "${sp.title} · " + when {
                    hz != null -> if (hz >= 1000) "sweep %.1f khz".format(hz / 1000) else "sweep %.0f hz".format(hz)
                    u.playT < ChannelClip.LEAD_SECONDS -> "starting"
                    else -> "start beeps"
                }
            }
            StatusLine(now ?: phone, dot = if (u.playing != null) Pc.red else null)
            u.headline?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Pc.ink, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** Front left and right as two cabinets; the one playing is lit red, each with what the phone found under it. */
@Composable
private fun SpeakerPair(playing: Speaker?, rows: List<ShownRow>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        for (s in listOf(Speaker.FL, Speaker.FR)) {
            val lit = s == playing
            val row = rows.firstOrNull { it.speaker == s }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp, 84.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val ink = if (lit) Pc.red else if (row != null && !row.heard) Pc.line else Pc.ink
                        val w = size.width
                        val h = size.height
                        drawRoundRect(ink, Offset(w * 0.12f, 0f), Size(w * 0.76f, h), androidx.compose.ui.geometry.CornerRadius(6f), style = Stroke(2.5f))
                        drawCircle(ink, w * 0.2f, Offset(w / 2, h * 0.66f), style = Stroke(2.5f))
                        drawCircle(ink, w * 0.08f, Offset(w / 2, h * 0.24f), style = Stroke(2.5f))
                        if (lit) for (k in 1..2) drawCircle(Pc.red.copy(alpha = 0.35f / k), w * (0.2f + 0.12f * k), Offset(w / 2, h * 0.66f), style = Stroke(2f))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(s.short, style = MaterialTheme.typography.titleSmall, color = if (lit) Pc.red else Pc.ink)
                if (row != null) Text(
                    (if (row.heard) "${row.trim} · ${row.distance}" else "not heard").lowercase(),
                    style = MaterialTheme.typography.labelSmall, color = Pc.muted, maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun OutputDeck(u: PcUi, pc: PcEndpoint?) {
    val st = u.apo.state
    Deck("Output", if (u.checking) "checking" else null) {
        DropSelect(u.outputs, u.outputs.indexOf(u.output).coerceAtLeast(0), { pc?.pickOutput(it) }, "output", Ic.SPEAKER, enabled = u.playing == null, info = ABOUT_OUTPUT)
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
                ActionKey("Open Equalizer APO's Configurator", Ic.OPEN, { u.apo.configDir?.let { configurator(it) } }, main = true)
                ActionKey("Check again", Ic.AGAIN, { pc?.recheck() }, enabled = !u.checking)
            }
            else -> Unit
        }
    }
}

@Composable
private fun EqDeck(u: PcUi, pc: PcEndpoint?) {
    val sub = if (!u.hasEq) null else listOfNotNull(u.eqFrom?.let { "from $it" }, u.eqWhen?.let { PcEndpoint.stamp(it) }).joinToString(" · ")
    Deck("EQ", sub, trailing = { InfoButton("The EQ", ABOUT_EQ) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val pre = u.chains.values.minOfOrNull { it.preampDb }
                if (pre == null) Text("none yet · the phone sends it", style = MaterialTheme.typography.bodyMedium, color = Pc.muted)
                else ReadingCell("%.1f".format(pre), "preamp, db", big = false, tint = if (u.eqOn) Pc.ink else Pc.muted)
            }
            LatchKey(u.eqOn, { pc?.setEqOn(it) }, enabled = u.hasEq)
        }
        if (u.hasEq) EqCurve(u.chains, u.eqOn)
        val problem = u.error ?: if (u.hasEq) u.apo.problem(u.output) else null
        Alerts(listOfNotNull(problem))
    }
}

/** Each speaker's EQ from 20 Hz to 20 kHz, ±12 dB: front left in ink, front right in blue; dashed while it is off. */
@Composable
private fun EqCurve(chains: Map<Speaker, EqChain>, on: Boolean) {
    Column {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val w = size.width
            val h = size.height
            fun x(f: Double) = (log10(f / 20.0) / 3.0 * w).toFloat()
            fun y(db: Double) = (h / 2 - db.coerceIn(-12.0, 12.0) / 12.0 * h / 2).toFloat()
            for (f in listOf(100.0, 1000.0, 10000.0)) drawLine(Pc.line.copy(alpha = 0.6f), Offset(x(f), 0f), Offset(x(f), h), 1f)
            for (db in listOf(-6.0, 6.0)) drawLine(Pc.line.copy(alpha = 0.4f), Offset(0f, y(db)), Offset(w, y(db)), 1f)
            drawLine(Pc.line, Offset(0f, y(0.0)), Offset(w, y(0.0)), 1f)
            for ((s, c) in chains) {
                val ink = if (s == Speaker.FR) Pc.second else Pc.ink
                val path = Path()
                for (k in 0..160) {
                    val f = 20.0 * 1000.0.pow(k / 160.0)
                    val db = c.responseDb(f)
                    if (k == 0) path.moveTo(x(f), y(db)) else path.lineTo(x(f), y(db))
                }
                drawPath(path, if (on) ink else ink.copy(alpha = 0.5f), style = Stroke(2f, pathEffect = if (on) null else PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
            }
        }
        // each label under its line: 100 Hz, 1 kHz and 10 kHz
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp)) {
            val w = maxWidth
            Text("20 hz", style = MaterialTheme.typography.labelSmall, color = Pc.muted)
            for ((f, l) in listOf(100.0 to "100", 1000.0 to "1k", 10000.0 to "10k")) {
                Text(l, Modifier.padding(start = w * (log10(f / 20.0) / 3.0).toFloat() - 6.dp), style = MaterialTheme.typography.labelSmall, color = Pc.muted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (s in chains.keys.sortedBy { it.ordinal }) Row(verticalAlignment = Alignment.CenterVertically) {
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

/** Equalizer APO's Configurator sits beside its config folder; Windows asks for an administrator to open it. */
private fun configurator(configDir: String) {
    runCatching { java.awt.Desktop.getDesktop().open(java.io.File(java.io.File(configDir).parentFile, "Configurator.exe")) }
}
