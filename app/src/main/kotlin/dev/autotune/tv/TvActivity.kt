package dev.autotune.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.autotune.ThemeMode
import dev.autotune.dsp.ChannelClip
import dev.autotune.dsp.Layout
import dev.autotune.ui.At
import dev.autotune.ui.AtIcon
import dev.autotune.ui.AutoTuneTheme
import dev.autotune.ui.Ic
import dev.autotune.ui.SpeakerMap
import dev.autotune.ui.Wordmark
import dev.autotune.ui.deckPlate

/** AutoTune on an Android TV or Google TV: plays each speaker for the phone, shows what it found, holds the TV's EQ. */
class TvActivity : ComponentActivity() {
    private val vm: TvViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // the code and the map stay up while the phone works
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { AutoTuneTheme(ThemeMode.DARK) { TvScreen(vm) } }
    }
}

@Composable
fun TvScreen(vm: TvViewModel) {
    val u by vm.ui.collectAsState()
    TvScreen(u, vm::playEach, vm::setLayout, vm::eqOff, vm::setPretend)
}

@Composable
fun TvScreen(
    u: TvUi, playEach: () -> Unit, setLayout: (dev.autotune.dsp.Layout) -> Unit, eqOff: () -> Unit,
    setPretend: (Layout?) -> Unit = {},
) {
    val c = At.c
    val now = System.currentTimeMillis()
    val live = u.live?.takeIf { now - it.atMillis < 3000 || u.playing != null || u.allPlaying }
    val result = u.result?.takeIf { live == null && u.playing == null && !u.allPlaying }
    Row(
        Modifier.fillMaxSize().background(c.bg).padding(horizontal = 48.dp, vertical = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        // the room, and under it what the phone hears while it tests, or the result's chart after
        Column(Modifier.weight(1.45f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.fillMaxWidth().deckPlate().padding(20.dp), contentAlignment = Alignment.Center) {
                // drawn in the deck's own colours, so the room sits light on its light plate
                dev.autotune.ui.DeckColors {
                    SpeakerMap(
                        u.layout, u.playing, large = true, all = u.allPlaying,
                        level = live?.let { ((it.levelDb + 60f) / 50f).coerceIn(0f, 1f) },
                        notes = u.rows.associate { it.speaker to listOf(it.trim, it.distance).filter { t -> t.isNotBlank() && t != "—" }.joinToString("\n") },
                        silent = u.rows.filter { !it.heard }.map { it.speaker }.toSet(),
                    )
                }
            }
            when {
                // the phone's measuring display, the same drawing and movement, larger
                live != null -> Column(Modifier.fillMaxWidth().weight(1f).deckPlate().padding(24.dp)) {
                    val (headline, sweep) = tvPlaying(u)
                    dev.autotune.ui.gauge.MeasureDisplay(
                        live.levelDb, live.samples.takeIf { it.isNotEmpty() } ?: live.wave.map { it * 2f - 1f }.toFloatArray(),
                        headline, live.status, sweep, waiting = u.playing != null || u.allPlaying || u.what != null,
                        dev.autotune.ui.gauge.GaugeColors(c.bg, c.text, c.muted, c.line, c.good, c.warn, c.bad, c.signal),
                        animate = dev.autotune.ui.animationsOn(), scale = 1.25f, change = live.change, okDb = dev.autotune.dsp.LevelMeter.OK_DB,
                    )
                }
                result != null && result.response.size == dev.autotune.dsp.Rta.CENTERS.size -> Box(Modifier.fillMaxWidth().weight(1f).deckPlate().padding(12.dp)) {
                    dev.autotune.ui.ResponseChart(
                        dev.autotune.ChartData(
                            dev.autotune.dsp.Rta.CENTERS, listOf(dev.autotune.Curve("System", result.response.toDoubleArray(), 0)),
                            result.target.takeIf { it.size == result.response.size }?.toDoubleArray() ?: DoubleArray(result.response.size), null, "",
                        ),
                        height = 260.dp, targetLabel = "Target",
                    )
                }
                else -> Spacer(Modifier.weight(1f))
            }
        }
        // the right side in decks, one job each: who is connected, what is happening, the EQ, the speakers
        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Wordmark()
                Spacer(Modifier.weight(1f))
                Text(u.tvName.lowercase(), style = MaterialTheme.typography.titleSmall, color = c.muted, maxLines = 1)
            }
            // the phone: the code until one pairs, then the phone itself with a lit pilot
            TvDeck {
                if (u.phone == null) {
                    Text("pair the phone", style = MaterialTheme.typography.labelLarge, color = c.muted)
                    Text(u.code.toList().joinToString(" "), style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp, lineHeight = 70.sp, fontWeight = FontWeight.Light), color = c.text)
                    Text("code · same wi-fi" + (u.address?.let { " · " + dev.autotune.link.addressText(it, u.port) } ?: ""), style = MaterialTheme.typography.titleSmall, color = c.muted)
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(c.signal))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u.phone, style = MaterialTheme.typography.headlineSmall, color = c.text, maxLines = 1)
                        Text("phone connected", style = MaterialTheme.typography.titleSmall, color = c.muted)
                    }
                    AtIcon(Ic.CHECK, c.signal, size = 32.dp)
                }
            }
            // what is happening: the test playing, or the last result
            val playing = u.playing
            when {
                playing != null || u.allPlaying || u.what != null -> TvDeck(accent = true) {
                    Text("playing", style = MaterialTheme.typography.labelLarge, color = c.signal)
                    Text(if (u.allPlaying) "Every speaker" else playing?.title ?: "Test", style = MaterialTheme.typography.displaySmall, color = c.text, maxLines = 1)
                    Text(tvPlaying(u).first, style = MaterialTheme.typography.titleMedium, color = c.muted, maxLines = 1)
                }
                result != null && u.phone != null -> TvDeck {
                    Text(result.headline, style = MaterialTheme.typography.headlineSmall, color = c.text, maxLines = 2)
                    if (result.readings.isNotEmpty()) dev.autotune.ui.ReadingCells(result.readings.map { (v, unit, name) -> dev.autotune.ui.Reading(v, unit.ifBlank { null }, name) })
                    result.notes.take(2).forEach { Text(it, style = MaterialTheme.typography.bodyLarge, color = c.warn, maxLines = 2) }
                }
                else -> Unit
            }
            u.error?.let { TvDeck { Text(it, style = MaterialTheme.typography.bodyLarge, color = c.warn, maxLines = 3) } }
            Spacer(Modifier.weight(1f))
            // the EQ's state, large: lit while it plays; the key switches it off
            TvEqKey(u.eqOn, eqOff, note = if (u.eqOn && u.probe?.method == dev.autotune.dsp.PlayMethod.DOLBY_51) "stereo · dolby from apps passes by it" else null)
            // the speakers: one line on the output, then the keys
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                u.probe?.let { p ->
                    val line = if (u.pretend != null) "pretend ${u.pretend.title} · really ${u.real}" else "${p.output.lowercase()} · ${p.method.title.lowercase()}"
                    Text(line, style = MaterialTheme.typography.titleSmall, color = if (u.pretend != null) c.signal else c.muted, maxLines = 1)
                }
                val first = remember { FocusRequester() }
                TvKey(if (u.playing != null && u.phone == null) "Stop" else "Play each speaker", Ic.PLAY, playEach, Modifier.focusRequester(first))
                LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
                val layouts = u.probe?.method?.layouts.orEmpty()
                if (layouts.size > 1 && u.phone == null) {
                    val next = layouts[(layouts.indexOf(u.layout) + 1) % layouts.size]
                    TvKey("Speakers: ${u.layout.title}", Ic.SPEAKER, { setLayout(next) })
                }
                // a phone or tablet standing in for a bigger system, to try the whole flow without one
                if (u.probe?.method == dev.autotune.dsp.PlayMethod.STEREO && u.phone == null) {
                    val options = listOf(null, Layout.L21, Layout.L31, Layout.L51, Layout.L71)
                    val next = options[(options.indexOf(u.pretend) + 1) % options.size]
                    TvKey(if (u.pretend == null) "Pretend: off" else "Pretend: ${u.pretend.title}", Ic.SPEAKER, { setPretend(next) })
                }
            }
        }
    }
}

/** A deck on the TV: the phone's light plate with its hairline; [accent] rims it in the accent while something plays. */
@Composable
private fun TvDeck(accent: Boolean = false, content: @Composable () -> Unit) {
    val c = At.c
    Column(
        Modifier.fillMaxWidth().deckPlate().then(if (accent) Modifier.border(2.dp, c.signal, MaterialTheme.shapes.medium) else Modifier).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { dev.autotune.ui.DeckColors { content() } }
}

/** The TV's EQ as one big latching key: "EQ on" with the pilot lit, or "EQ off". Pressing it switches it off. */
@Composable
private fun TvEqKey(on: Boolean, off: () -> Unit, note: String?) {
    var focused by remember { mutableStateOf(false) }
    val c = At.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 84.dp).clip(MaterialTheme.shapes.medium)
            .background(if (on) androidx.compose.ui.graphics.lerp(c.panelHigh, c.signal, 0.12f) else c.panelHigh)
            .border(3.dp, if (focused) c.signal else if (on) c.signal.copy(alpha = 0.6f) else c.line, MaterialTheme.shapes.medium)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = on, role = Role.Switch, onClick = off)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(16.dp).clip(CircleShape).background(if (on) c.signal else c.line))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(if (on) "EQ on" else "EQ off", style = MaterialTheme.typography.headlineMedium, color = c.text)
            Text(note ?: if (on) "press to switch it off" else "the phone sends it", style = MaterialTheme.typography.titleSmall, color = c.muted, maxLines = 1)
        }
    }
}

/** What the TV is playing now as the phone's display says it ("Sweeping 734 Hz"), and how far through the sweep. */
private fun tvPlaying(u: TvUi): Pair<String, Float?> {
    val into = when {
        u.what == "sweep" -> dev.autotune.dsp.PassClip.nowPlaying(u.layout, u.playT, u.kind)?.second
        u.what == null && u.playing != null -> ChannelClip.nowPlaying(u.playT)?.let { hz -> Math.log10(hz / 20.0) / 3.0 * ChannelClip.SWEEP_SECONDS }
        else -> null
    }
    val who = if (u.allPlaying) "every speaker" else u.playing?.title?.lowercase()
    if (into != null) {
        val x = (into / ChannelClip.SWEEP_SECONDS).coerceIn(0.0, 1.0)
        return dev.autotune.ui.gauge.sweepText(x) + (who?.let { " · $it" } ?: "") to x.toFloat()
    }
    return when {
        u.what == "level noise" -> "Level noise" + (who?.let { " · $it" } ?: "") to null
        u.playT < ChannelClip.LEAD_SECONDS && (u.playing != null || u.what != null) -> "Waking the speakers" to null
        u.playT < ChannelClip.LEAD_SECONDS + dev.autotune.dsp.Marker.LENGTH_SEC + 0.1 && u.what != null -> "Start beeps" to null
        u.what != null || u.playing != null -> "Get ready" to null
        else -> "Listening" to null
    }
}

@Composable
private fun Reading(value: String, name: String, tint: androidx.compose.ui.graphics.Color? = null, small: Boolean = false) {
    Column {
        Text(
            value, style = MaterialTheme.typography.displayLarge.copy(fontSize = if (small) 36.sp else 72.sp, lineHeight = if (small) 44.sp else 80.sp, fontWeight = FontWeight.Light),
            color = tint ?: At.c.text, maxLines = 2,
        )
        Text(name, style = MaterialTheme.typography.titleMedium, color = At.c.muted, maxLines = 1)
    }
}

/** A key for the remote: graphite, with a ring in the accent while it has the focus. */
@Composable
private fun TvKey(text: String, icon: Ic, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val c = At.c
    Row(
        modifier.fillMaxWidth(0.8f).heightIn(min = 56.dp).clip(MaterialTheme.shapes.medium).background(c.panelHigh)
            .border(3.dp, if (focused) c.signal else c.line, MaterialTheme.shapes.medium)
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (focused) c.signal else c.line))
        Spacer(Modifier.width(12.dp))
        AtIcon(icon, c.text, size = 22.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = c.text)
    }
}
