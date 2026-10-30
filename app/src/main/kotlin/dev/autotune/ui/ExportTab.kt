package dev.autotune.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import dev.autotune.EqChoice
import dev.autotune.TargetKind
import dev.autotune.TuneViewModel
import dev.autotune.UiState

/** Export: pick the test and run (or saved EQ), pick the file type, save or share. Nothing else. */
@Composable
fun ExportTab(s: UiState, vm: TuneViewModel, go: (Tab) -> Unit) {
    val now = listOfNotNull(
        s.exportChain?.takeIf { it.filters.isNotEmpty() }?.let { EqChoice("current", "Now", TargetKind.HAND, "The EQ in use", s.eqSource) },
    )
    val choices = now + s.eqChoices.filter { it.key != "own" }
    var key by rememberSaveable { mutableStateOf(if (choices.any { it.key == s.eqPick && it.key != "own" }) s.eqPick else choices.firstOrNull()?.key ?: "") }
    var format by rememberSaveable { mutableIntStateOf(0) }

    ScreenTitle("Export", "Save an EQ as a file for your EQ app")

    if (choices.isEmpty()) {
        Panel {
            Text("Nothing to export yet", style = MaterialTheme.typography.titleLarge)
            Text("Tune a system, or build an EQ on the EQ tab.", style = MaterialTheme.typography.bodyMedium, color = At.c.muted)
            PrimaryButton("Open EQ", { go(Tab.EQ) }, icon = Ic.EQ)
        }
        return
    }
    Panel {
        GroupedPick("Which EQ", choices, key, { key = it }, "Pick an EQ")
        DropSelect(
            listOf("Poweramp, Equalizer APO", "Wavelet", "Both", "Both, plus the test's report and measurements"), format, { format = it },
            label = "For", icon = Ic.FILE,
            details = listOf("ParametricEQ.txt: the exact bands", "GraphicEQ.txt: the curve", "Both files", "Two text files and a CSV of every run"),
        )
        runCatching { vm.eqSummary(key) }.getOrNull()?.let { Muted(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Save to Downloads", { vm.exportEq(key, format, share = false) }, icon = Ic.SAVE)
            SecondaryButton("Share", { vm.exportEq(key, format, share = true) }, icon = Ic.SHARE)
        }
        s.status?.takeIf { it.startsWith("Saved") || it.startsWith("Choose") || it.startsWith("Could") || it.startsWith("That") }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
