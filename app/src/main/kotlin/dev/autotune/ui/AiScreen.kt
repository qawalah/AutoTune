package dev.autotune.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.autotune.AiUi
import dev.autotune.TuneStep
import dev.autotune.TuneViewModel
import dev.autotune.UiState
import dev.autotune.ai.AiIssue
import dev.autotune.ai.AiModel
import dev.autotune.ai.AiProvider
import dev.autotune.ai.AiSeverity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun aiPrivacy(p: AiProvider) = "Sends this test's numbers to ${p.company} with your own API key: the responses as dB from the target, the EQ of every run, warnings and checks, your notes, the phone model and the mic's name. Never the recordings."

/** The API key and model, used in Settings and on the review screen before a key is set. */
@Composable
fun AiKeySetup(a: AiUi, vm: TuneViewModel) {
    var key by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DropSelect(
            AiProvider.entries.map { it.title }, AiProvider.entries.indexOf(a.provider), { vm.aiSetProvider(AiProvider.entries[it]) },
            label = "AI", details = AiProvider.entries.map { "By ${it.company}, with your own key" }, icon = Ic.AI,
        )
        if (a.hasKey) {
            StatusLine(true, "API key saved: ${a.keyMasked ?: "…"}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton("Check the key", vm::aiTestKey, enabled = !a.testingKey, icon = Ic.CHECK)
                TextAction("Remove", vm::aiRemoveKey, danger = true, icon = Ic.TRASH)
            }
        } else {
            Muted("Paste a ${a.provider.title} API key from ${a.provider.keyWhere}. It is stored encrypted on this phone and only used to ask for reviews; the requests are charged to that key's account.")
            OutlinedTextField(
                value = key, onValueChange = { key = it.trim() }, label = { Text("${a.provider.title} API key") }, singleLine = true,
                placeholder = { Text(a.provider.keyHint) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (key.isNotBlank()) { vm.aiSaveKey(key); key = "" } }),
                modifier = Modifier.fillMaxWidth().bringIntoViewOnFocus(),
            )
            PrimaryButton("Save the key", { vm.aiSaveKey(key); key = "" }, enabled = key.length > 10, icon = Ic.SAVE)
        }
        val models = AiModel.forProvider(a.provider)
        DropSelect(
            models.map { it.title }, models.indexOf(a.model), { vm.aiSetModel(models[it]) },
            label = "Model", details = models.map { it.detail }, icon = Ic.AI,
        )
        a.keyMessage?.let { Muted(it) }
    }
}

@Composable
fun AiScreen(a: AiUi, s: UiState, vm: TuneViewModel, go: (Tab) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(Ic.CLOSE, "Close", vm::aiClose)
            AtIcon(Ic.AI, At.c.signal, Modifier.padding(start = 4.dp, end = 8.dp), 22.dp)
            Column(Modifier.weight(1f)) {
                Text("AI review", style = MaterialTheme.typography.headlineSmall)
                a.testName?.let { Muted(it) }
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!a.hasKey) {
                Text("The AI reads this test's measurements and points out what is limiting the result: the measurement itself, the setup and speakers, and the EQ, with what to do next. It can also suggest EQ changes, which the app checks against its own limits before you try them.", style = MaterialTheme.typography.bodyMedium)
                AiKeySetup(a, vm)
                Muted(aiPrivacy(a.provider))
                return@Column
            }

            if (a.busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = At.c.signal, strokeWidth = 2.5.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("${a.model.title} is reading the test…", style = MaterialTheme.typography.bodyMedium)
                        Muted("Usually 10 to 40 seconds.")
                    }
                }
            }
            a.error?.let { e ->
                Warning(e)
                if (!a.busy) SecondaryButton("Try again", { vm.aiReview(a.review?.aboutFailure == true) }, icon = Ic.AGAIN)
            }

            val r = a.review
            if (r == null && !a.busy && a.error == null) {
                Muted("No review of this test yet.")
                PrimaryButton("Review this test", { vm.aiReview() }, Modifier.fillMaxWidth(), enabled = s.runs.isNotEmpty(), icon = Ic.AI)
            }

            if (r != null) {
                val run = r.focusRun?.let { f -> s.runs.getOrNull(f)?.label ?: "Run $f" }
                Muted(
                    listOfNotNull(
                        AiModel.of(r.model).title,
                        SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(r.atMillis)),
                        if (r.aboutFailure) "about the failed measurement" else run?.let { "after $it" },
                    ).joinToString(" · "),
                )
                if (a.stale) {
                    Warning("Runs were added after this review.")
                    PrimaryButton("Review again", { vm.aiReview() }, Modifier.fillMaxWidth(), enabled = !a.busy, icon = Ic.AI)
                }
                if (r.summary.isNotBlank()) Text(r.summary, style = MaterialTheme.typography.bodyLarge)

                if (r.issues.isNotEmpty()) {
                    Group("What it found", divider = false) {
                        r.issues.forEach { IssueRow(it) }
                    }
                }

                if (r.bands.isNotEmpty() || r.eqNote != null) {
                    Group("EQ") {
                        r.eqNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        r.bands.forEach { b ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    "%+.1f dB  %s  Q %.1f".format(b.filter.gainDb, hz(b.filter.freqHz), b.filter.q),
                                    Modifier.widthIn(min = 150.dp), style = MaterialTheme.typography.bodyMedium, fontFamily = AtFigures,
                                )
                                b.reason?.let { Text(it, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, color = At.c.muted) }
                            }
                        }
                        val eq = a.eq
                        if (eq != null) {
                            val before = eq.predictedSuggestionDb
                            val after = eq.predictedAiDb
                            if (before != null && after != null) {
                                Panel(pad = 12.dp) {
                                    Text("%.1f → %.1f dB".format(before, after), style = MaterialTheme.typography.titleMedium, fontFamily = AtFigures)
                                    Muted("Predicted distance from the target for ${run ?: "the run"}: AutoTune's suggestion → with these changes")
                                }
                                if (after > before + 0.1) Warning("The app predicts this does worse than AutoTune's own suggestion. Measure it before keeping it.")
                            }
                            eq.adjusted.forEach { Muted(it) }
                            val inUse = s.eqPick == "ai:${r.testId}"
                            if (!inUse) PrimaryButton("Use this EQ", vm::aiUseEq, Modifier.fillMaxWidth(), icon = Ic.EQ)
                            else StatusLine(true, "In use: ${eq.chain.filters.size} bands, preamp %.1f dB".format(eq.chain.preampDb))
                            if (inUse) SecondaryButton("Measure it", { vm.aiClose(); vm.tuneGo(TuneStep.APPLY); go(Tab.MEASURE) }, Modifier.fillMaxWidth(), icon = Ic.MEASURE)
                        } else if (r.bands.isNotEmpty() && r.testId == null) {
                            Muted("Open the test to try these changes.")
                        }
                        a.message?.let { Muted(it) }
                    }
                }

                r.nextStep?.let { n ->
                    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(At.c.panelHigh).padding(12.dp), verticalAlignment = Alignment.Top) {
                        AtIcon(Ic.NEXT, At.c.signal, Modifier.padding(top = 1.dp, end = 10.dp), 20.dp)
                        Column {
                            Text("Next", style = MaterialTheme.typography.labelMedium, color = At.c.muted)
                            Text(n, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                if (!a.stale && !a.busy) TextAction("Review again", { vm.aiReview(r.aboutFailure) }, icon = Ic.AGAIN)
            }

            if (r != null || a.chat.isNotEmpty()) Chat(a, vm)
            Muted(aiPrivacy(a.provider))
        }
    }
}

@Composable
private fun IssueRow(i: AiIssue) {
    val (icon, tint) = when (i.severity) {
        AiSeverity.PROBLEM -> Ic.ALERT to At.c.bad
        AiSeverity.CHECK -> Ic.INFO to At.c.warn
        AiSeverity.FINE -> Ic.DONE to At.c.good
    }
    Row(verticalAlignment = Alignment.Top) {
        AtIcon(icon, tint, Modifier.padding(top = 2.dp, end = 10.dp), 20.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(i.title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall)
                Text("  ${i.area}", style = MaterialTheme.typography.labelSmall, color = At.c.muted)
            }
            if (i.detail.isNotBlank()) Text(i.detail, style = MaterialTheme.typography.bodyMedium)
            i.action?.let { act ->
                Row(verticalAlignment = Alignment.Top) {
                    AtIcon(Ic.NEXT, At.c.signal, Modifier.padding(top = 2.dp, end = 6.dp), 16.dp)
                    Text(act, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Follow-up questions about the review, answered with the test data in view. */
@Composable
private fun Chat(a: AiUi, vm: TuneViewModel) {
    var q by rememberSaveable { mutableStateOf("") }
    Group("Ask about this test") {
        a.chat.forEach { t ->
            val mine = t.role == "user"
            Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                Text(
                    t.text,
                    Modifier.widthIn(max = 320.dp).clip(MaterialTheme.shapes.medium)
                        .background(if (mine) At.c.panelHigh else At.c.panel).padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (a.asking) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), color = At.c.signal, strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
                Muted("Thinking…")
            }
        }
        if (a.chat.isEmpty()) Muted("For example: why is 63 Hz still high? Should I change the crossover? Is my mic in the right place?")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = q, onValueChange = { q = it }, placeholder = { Text("Ask a question") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (q.isNotBlank() && !a.asking) { vm.aiAsk(q); q = "" } }),
                modifier = Modifier.weight(1f).bringIntoViewOnFocus(), maxLines = 4,
            )
            IconAction(Ic.SEND, "Send") { if (q.isNotBlank() && !a.asking) { vm.aiAsk(q); q = "" } }
        }
    }
}

private fun hz(f: Double) = if (f >= 1000) "%.1f kHz".format(f / 1000) else "%.0f Hz".format(f)
