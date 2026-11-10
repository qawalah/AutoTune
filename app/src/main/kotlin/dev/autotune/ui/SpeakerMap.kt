package dev.autotune.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import dev.autotune.dsp.Layout
import dev.autotune.dsp.Speaker
import dev.autotune.ui.room.ROOM_ASPECT
import dev.autotune.ui.room.Room3d
import dev.autotune.ui.room.RoomColors
import dev.autotune.ui.room.RoomSpeaker

/**
 * The room as seen from just behind the seat ([Room3d]): the screen at the far end, each speaker of [layout] a small
 * cabinet on the floor. The speaker [playing] lights in the accent with rings spreading over the floor, wider with
 * [level] (0..1, the mic's level on the phone; null pulses evenly). [notes] puts a short line under a speaker
 * ("+1.5 dB"; a new line for each line of it); speakers in [silent] are drawn faint. [large] is for the TV.
 */
@Composable
fun SpeakerMap(
    layout: Layout, playing: Speaker?, modifier: Modifier = Modifier, notes: Map<Speaker, String> = emptyMap(),
    silent: Set<Speaker> = emptySet(), level: Float? = null, large: Boolean = false, all: Boolean = false,
) {
    val c = At.c
    val tm = rememberTextMeasurer()
    val moving = animationsOn()
    val phase = if (moving && (playing != null || all)) {
        rememberInfiniteTransition(label = "rings").animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "ring").value
    } else 0.35f
    val label = MaterialTheme.typography.labelSmall
    val name = TextStyle(fontFamily = label.fontFamily, fontSize = if (large) 18.sp else 12.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
    val note = TextStyle(fontFamily = label.fontFamily, fontSize = if (large) 15.sp else 11.sp, fontFeatureSettings = "tnum")
    val colors = RoomColors(
        // a light, warm rug: the deck's own colour with a touch of the accent, never a dark slab
        floor = lerp(c.panelHigh, c.signal, if (c.dark) 0.10f else 0.07f), floorLine = lerp(c.line, c.signal, 0.25f), ink = c.text, muted = c.muted,
        face = c.panel, side = c.panelHigh, live = c.signal, onLive = c.onSignal, screen = c.text, tag = c.panel,
    )
    val speakers = layout.speakers.associateWith { s -> RoomSpeaker(s, notes[s], s in silent) }
    val describe = buildString {
        append("Room, ${layout.title}")
        if (all) append(", every speaker playing") else playing?.let { append(", ${it.title.lowercase()} playing") }
    }
    Canvas(modifier.fillMaxWidth().aspectRatio(ROOM_ASPECT).semantics { contentDescription = describe }) {
        with(Room3d) { drawRoom(layout, playing, speakers, colors, tm, name, note, phase, level, all) }
    }
}
