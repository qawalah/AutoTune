package dev.autotune.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.autotune.TuneViewModel
import dev.autotune.UiState
import dev.autotune.dsp.Tone
import kotlin.math.abs

/**
 * Live's equaliser, for listening and adjusting at the same time: one fader per band for its gain, and for the band
 * picked, a slider for its frequency. The picked band's pitch can be played with the tone generator beside it. The
 * changes are heard at once while AutoTune EQ is on, and drawn in the spectrum above. It edits the EQ in use as exact
 * bands (the EQ tab's "Your own bands"); Reset goes back to the suggestion.
 */
@Composable
internal fun LiveEqStrip(s: UiState, vm: TuneViewModel, pick: Pickers?, modifier: Modifier = Modifier) {
    val bands = s.editFilters
    var selected by remember { mutableIntStateOf(-1) }
    // the pick lets go after a few seconds of nothing, like the EQ tab's faders; a band that is gone is no longer picked
    var touched by remember { mutableIntStateOf(0) }
    LaunchedEffect(selected, touched) {
        if (selected >= 0) { kotlinx.coroutines.delay(8000); selected = -1 }
    }
    if (bands != null && selected >= bands.size) selected = -1
    Column(modifier, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("EQ", style = MaterialTheme.typography.labelLarge)
            LatchKey(
                s.systemEqOn, { if (it) pick?.notifications(); vm.setSystemEq(it) }, "AutoTune EQ",
            )
            val sel = bands?.getOrNull(selected)
            when {
                bands == null -> {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        SecondaryButton("Tune bands", { vm.liveEqStart() }, icon = Ic.EQ)
                    }
                }
                sel != null -> {
                    // the picked band's frequency, slid while listening
                    Text(
                        Tone.text(sel.freqHz), Modifier.width(76.dp),
                        style = MaterialTheme.typography.titleSmall, fontFamily = AtFigures,
                    )
                    Slider(
                        value = Tone.fractionOf(sel.freqHz).toFloat(),
                        onValueChange = { selected = vm.liveSlide(selected, Tone.freqAt(it.toDouble())); touched++ },
                        modifier = Modifier.weight(1f), colors = sliderColors(),
                    )
                }
                else -> Text(
                    "tap a band to slide its frequency", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall, color = At.c.muted,
                )
            }
            if (bands != null) IconAction(Ic.UNDO, "Reset the bands to the suggested EQ") { selected = -1; vm.editReset() }
        }
        if (bands != null) {
            val scroll = rememberScrollState()
            CompositionLocalProvider(LocalFaderHeight provides 84.dp) {
                Row(Modifier.fillMaxWidth().horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    bands.forEachIndexed { i, f ->
                        Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            val g = f.gainDb
                            Text(
                                if (abs(g) < 0.05) "0" else "%+.1f".format(g).replace('-', '−'),
                                style = MaterialTheme.typography.labelSmall, fontFamily = AtFigures, maxLines = 1,
                                color = if (g < -0.05) At.c.bad else if (g > 0.05) At.c.signal else At.c.muted,
                            )
                            VSlider(
                                g.coerceIn(-12.0, 12.0), { vm.editSet(i, f.copy(gainDb = it)) }, "${freqLabel(f.freqHz)} Hz",
                                selected = selected == i, onSelect = { selected = i; touched++ }, onTouch = { touched++ },
                            )
                            Text(
                                freqLabel(f.freqHz), style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                color = if (selected == i) At.c.signal else At.c.muted,
                            )
                        }
                    }
                }
            }
        }
    }
}
