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
    TvScreen(u, vm::playEach, vm::setLayout, vm::eqOff)
}

@Composable
fun TvScreen(u: TvUi, playEach: () -> Unit, setLayout: (dev.autotune.dsp.Layout) -> Unit, eqOff: () -> Unit) {
    val c = At.c
    Row(
        Modifier.fillMaxSize().background(c.bg).padding(horizontal = 48.dp, vertical = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Box(Modifier.weight(1.5f).deckPlate().padding(20.dp), contentAlignment = Alignment.Center) {
            SpeakerMap(
                u.layout, u.playing, large = true,
                notes = u.rows.associate { it.speaker to listOf(it.trim, it.distance).filter { t -> t.isNotBlank() && t != "—" }.joinToString("\n") },
                silent = u.rows.filter { !it.heard }.map { it.speaker }.toSet(),
            )
        }
        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Wordmark()
            Text("home theatre", style = MaterialTheme.typography.labelLarge, color = c.muted)
            Spacer(Modifier.height(4.dp))
            val playing = u.playing
            when {
                playing != null -> {
                    Reading(playing.title.lowercase(), "playing", tint = c.signal)
                    val hz = ChannelClip.nowPlaying(u.playT)
                    val what = when {
                        hz != null -> if (hz >= 1000) "%.1f kHz".format(hz / 1000) else "%.0f Hz".format(hz)
                        u.playT < ChannelClip.LEAD_SECONDS -> "starting"
                        u.playT < ChannelClip.LEAD_SECONDS + dev.autotune.dsp.Marker.LENGTH_SEC + 0.1 -> "start beeps · front left"
                        else -> "listening"
                    }
                    Text(what, style = MaterialTheme.typography.titleLarge, color = c.muted)
                }
                u.phone == null -> {
                    Reading(u.code, "code for the phone")
                    Text(
                        listOfNotNull("same wi-fi", u.address).joinToString(" · "),
                        style = MaterialTheme.typography.titleMedium, color = c.muted,
                    )
                }
                else -> {
                    Reading(u.headline?.lowercase() ?: "ready", u.phone.orEmpty().lowercase(), small = u.headline != null)
                }
            }
            Spacer(Modifier.weight(1f))
            u.error?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = c.warn, maxLines = 2) }
            u.probe?.let { p ->
                Text("${p.output.lowercase()} · ${p.method.title.lowercase()} · ${u.layout.title}", style = MaterialTheme.typography.titleSmall, color = c.muted)
            }
            // one key per row, as on the phone
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val first = remember { FocusRequester() }
                TvKey(if (u.playing != null && u.phone == null) "Stop" else "Play each speaker", Ic.PLAY, playEach, Modifier.focusRequester(first))
                LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
                val layouts = u.probe?.method?.layouts.orEmpty()
                if (layouts.size > 1 && u.phone == null) {
                    val next = layouts[(layouts.indexOf(u.layout) + 1) % layouts.size]
                    TvKey(u.layout.title, Ic.SPEAKER, { setLayout(next) })
                }
                if (u.eqOn) TvKey("EQ off", Ic.POWER, eqOff)
            }
        }
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
