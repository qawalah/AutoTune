package dev.autotune.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.core.animateFloat
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
            Actions {
                TextAction("Check the key", vm::aiTestKey, enabled = !a.testingKey, icon = Ic.CHECK)
                TextAction("Remove", vm::aiRemoveKey, danger = true, icon = Ic.TRASH)
            }
        } else {
            InfoLabel("Where the key comes from", "Paste a ${a.provider.title} API key from ${a.provider.keyWhere}. It is stored encrypted on this phone and only used to ask for reviews; the requests are charged to that key's account.")
            OutlinedTextField(
                value = key, onValueChange = { key = it.trim() }, label = { Text("${a.provider.title} API key") }, singleLine = true,
                placeholder = { Text(a.provider.keyHint) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (key.isNotBlank()) { vm.aiSaveKey(key); key = "" } }),
                modifier = Modifier.fillMaxWidth().settleIn().bringIntoViewOnFocus(), colors = atFieldColors(), shape = atFieldShape(),
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
        // the device header: the wordmark, what this is, and close
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Wordmark(large = true)
            Text(
                "  ai review" + (a.testName?.let { " · " + dev.autotune.shortTestName(it, "").first.lowercase() } ?: ""), Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, color = At.c.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            InfoButton(
                "AI review",
                "The AI reads this test's measurements and points out what is limiting the result: the measurement itself, the setup and speakers, and the EQ, with what to do next. It can also suggest EQ changes, which the app checks against its own limits before you try them.\n\n" + aiPrivacy(a.provider),
                small = true,
            )
            IconAction(Ic.CLOSE, "Close", vm::aiClose)
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(At.c.line))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!a.hasKey) {
                Panel { AiKeySetup(a, vm) }
                return@Column
            }

            if (a.busy) {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BlinkPilot()
                        Text("  ${a.model.title} is reading the test", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("10 to 40 seconds", style = MaterialTheme.typography.labelSmall, color = At.c.muted)
                }
            }
            a.error?.let { e ->
                Alerts(listOf(e), "The review failed")
                if (!a.busy) Actions { TextAction("Try again", { vm.aiReview(a.review?.aboutFailure == true) }, icon = Ic.AGAIN) }
            }

            val r = a.review
            if (r == null && !a.busy && a.error == null) {
                PinAction("Review this test", { vm.aiReview() }, icon = Ic.AI, enabled = s.runs.isNotEmpty())
                Text("No review yet", style = MaterialTheme.typography.titleMedium, color = At.c.muted)
            }

            if (r != null) {
                val run = r.focusRun?.let { f -> s.runs.getOrNull(f)?.label ?: "Run $f" }
                // the review's display: what it read, then its summary in one sentence, the rest in a bubble
                Panel {
                    Text(
                        listOfNotNull(
                            AiModel.of(r.model).title,
                            SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(r.atMillis)),
                            if (r.aboutFailure) "failed measurement" else run,
                        ).joinToString(" · ").lowercase(),
                        style = MaterialTheme.typography.labelSmall, color = At.c.muted,
                    )
                    if (r.summary.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(firstSentence(r.summary), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 3)
                            if (firstSentence(r.summary).length < r.summary.length) InfoButton("Summary", r.summary, small = true)
                        }
                    }
                    if (a.stale) Alerts(listOf("Runs were added after this review."), "Out of date")
                    r.nextStep?.let { n ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AtIcon(Ic.NEXT, At.c.signal, size = 18.dp)
                            Text("  Next: " + firstSentence(n), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2)
                            if (firstSentence(n).length < n.length) InfoButton("Next", n, small = true)
                        }
                    }
                }
                if (a.stale) PinAction("Review again", { vm.aiReview() }, icon = Ic.AI, enabled = !a.busy)

                if (r.issues.isNotEmpty()) {
                    Group("What it found", divider = false) {
                        r.issues.forEach { IssueRow(it) }
                    }
                }

                if (r.bands.isNotEmpty() || r.eqNote != null) {
                    Group("EQ changes", divider = false, info = r.eqNote) {
                        r.bands.forEach { b ->
                            Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "%+.1f dB".format(b.filter.gainDb), Modifier.widthIn(min = 72.dp), style = MaterialTheme.typography.titleSmall,
                                    color = if (b.filter.gainDb < 0) At.c.text else At.c.signal,
                                )
                                Text("${hz(b.filter.freqHz)} · Q %.1f".format(b.filter.q), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = At.c.muted)
                                b.reason?.let { InfoButton(hz(b.filter.freqHz), it, small = true) }
                            }
                        }
                        val eq = a.eq
                        if (eq != null) {
                            val before = eq.predictedSuggestionDb
                            val after = eq.predictedAiDb
                            if (before != null && after != null) {
                                ReadingCells(
                                    listOf(
                                        Reading("%.1f".format(after), "dB", "with these changes", if (after < before - 0.1) At.c.good else null),
                                        Reading("%.1f".format(before), "dB", "autotune's own"),
                                    ),
                                )
                            }
                            Alerts(
                                listOfNotNull(
                                    if (before != null && after != null && after > before + 0.1) "The app predicts this does worse than AutoTune's own suggestion. Measure it before keeping it." else null,
                                ) + eq.adjusted,
                                "About these changes",
                            )
                            val inUse = s.eqPick == "ai:${r.testId}"
                            if (!inUse) PinAction("Use this EQ", vm::aiUseEq, icon = Ic.EQ)
                            else {
                                StatusLine(true, "In use · ${eq.chain.filters.size} bands")
                                PinAction("Measure it", { vm.aiClose(); vm.tuneGo(TuneStep.APPLY); go(Tab.MEASURE) }, icon = Ic.MEASURE)
                            }
                        } else if (r.bands.isNotEmpty() && r.testId == null) {
                            Muted("Open the test to try these changes.")
                        }
                        a.message?.let { Muted(it) }
                    }
                }
                if (!a.stale && !a.busy) Actions { TextAction("Review again", { vm.aiReview(r.aboutFailure) }, icon = Ic.AGAIN) }
            }

            if (r != null || a.chat.isNotEmpty()) Chat(a, vm)
        }
    }
}

/** A pilot light that blinks while the AI works. */
@Composable
private fun BlinkPilot() {
    val alpha = if (animationsOn()) {
        val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "ai")
        inf.animateFloat(0.3f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "a").value
    } else 1f
    Box(Modifier.size(12.dp).clip(androidx.compose.foundation.shape.CircleShape).background(At.c.signal.copy(alpha = alpha)))
}

@Composable
private fun IssueRow(i: AiIssue) {
    // a pilot light for how much it matters, the title, and everything else in its bubble
    val tint = when (i.severity) {
        AiSeverity.PROBLEM -> At.c.bad
        AiSeverity.CHECK -> At.c.warn
        AiSeverity.FINE -> At.c.good
    }
    Row(Modifier.settleIn().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(end = 12.dp).size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tint))
        Column(Modifier.weight(1f)) {
            Text(i.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            i.action?.let { Text(firstSentence(it), style = MaterialTheme.typography.bodySmall, color = At.c.muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
        }
        val more = listOfNotNull(i.detail.takeIf { it.isNotBlank() }, i.action?.let { "What to do: $it" }, "Area: ${i.area}").joinToString("\n\n")
        InfoButton(i.title, more, small = true)
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = q, onValueChange = { q = it }, placeholder = { Text("e.g. why is 63 Hz still high?") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (q.isNotBlank() && !a.asking) { vm.aiAsk(q); q = "" } }),
                modifier = Modifier.weight(1f).bringIntoViewOnFocus(), maxLines = 4, colors = atFieldColors(), shape = atFieldShape(),
            )
            IconAction(Ic.SEND, "Send") { if (q.isNotBlank() && !a.asking) { vm.aiAsk(q); q = "" } }
        }
    }
}

private fun hz(f: Double) = if (f >= 1000) "%.1f kHz".format(f / 1000) else "%.0f Hz".format(f)
