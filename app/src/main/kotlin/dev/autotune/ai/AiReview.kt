package dev.autotune.ai

import dev.autotune.Diag
import dev.autotune.SYSTEM_EQ_FIT
import dev.autotune.SavedRun
import dev.autotune.SavedTest
import dev.autotune.suggestedFor
import dev.autotune.dsp.EqChain
import dev.autotune.dsp.EqFilter
import dev.autotune.dsp.EqFitter
import dev.autotune.dsp.Export
import dev.autotune.dsp.FitConfig
import dev.autotune.dsp.FreqGrid
import dev.autotune.dsp.Target
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The AI review: what is sent (a compact summary of one test, never audio), what comes back (issues, actions and
 * optional EQ changes, through a forced tool call so the reply is always structured), and the checks that keep a
 * suggested EQ inside the app's own limits. Free of Android so it can be unit tested.
 */

enum class AiSeverity(val word: String) { PROBLEM("problem"), CHECK("check"), FINE("fine") }

class AiIssue(val severity: AiSeverity, val area: String, val title: String, val detail: String, val action: String?)

/** One band the AI wants added on top of AutoTune's suggestion (a band with the opposite gain removes one). */
class AiBand(val filter: EqFilter, val reason: String?)

class AiReview(
    val testId: String?,
    val model: String,
    val atMillis: Long,
    /** Runs in the test when it was reviewed, and the run it focused on. */
    val runCount: Int,
    val focusRun: Int?,
    /** The review was about a failed measurement. */
    val aboutFailure: Boolean,
    val summary: String,
    val issues: List<AiIssue>,
    val bands: List<AiBand>,
    val eqNote: String?,
    val nextStep: String?,
    /** The tool input exactly as received, kept so the review can be stored and read back. */
    val raw: Map<String, Any?>,
) {
    fun toJson(): String = Diag.json(
        mapOf(
            "v" to 1, "testId" to testId, "model" to model, "at" to atMillis, "runs" to runCount, "focus" to focusRun,
            "failure" to aboutFailure, "review" to raw,
        ),
    )

    companion object {
        /** Reads a review from the tool input of the model's reply. Missing fields become empty, never an error. */
        fun fromTool(
            input: Map<String, Any?>, testId: String?, model: String, at: Long, runs: Int, focus: Int?, failure: Boolean,
        ): AiReview {
            val issues = input["issues"].list().mapNotNull { it.obj() }.mapNotNull { m ->
                val title = m.str("title") ?: return@mapNotNull null
                val sev = when (m.str("severity")?.lowercase()) {
                    "problem" -> AiSeverity.PROBLEM
                    "fine", "good", "ok" -> AiSeverity.FINE
                    else -> AiSeverity.CHECK
                }
                AiIssue(sev, m.str("area") ?: "general", title, m.str("detail") ?: "", m.str("action"))
            }.sortedBy { it.severity.ordinal }
            val bands = input["eq_changes"].list().mapNotNull { it.obj() }.mapNotNull { m ->
                val f = m.num("freq_hz") ?: return@mapNotNull null
                val g = m.num("gain_db") ?: return@mapNotNull null
                val q = m.num("q") ?: 1.0
                AiBand(EqFilter(f, g, q), m.str("reason"))
            }
            return AiReview(
                testId, model, at, runs, focus, failure,
                input.str("summary") ?: "",
                issues, bands, input.str("eq_note"), input.str("next_step"), input,
            )
        }

        fun fromJson(text: String): AiReview? = runCatching {
            val m = Json.parse(text).obj() ?: return null
            val input = m["review"].obj() ?: return null
            fromTool(
                input, m.str("testId"), m.str("model") ?: "", m.num("at")?.toLong() ?: 0L,
                m.num("runs")?.toInt() ?: 0, m.num("focus")?.toInt(), m["failure"] == true,
            )
        }.getOrNull()
    }
}

/** The AI's EQ after the app's checks, with what had to be changed to make it safe. */
class AiEq(
    val chain: EqChain,
    /** RMS distance from the target the focus run is predicted to reach with AutoTune's suggestion and with this EQ. */
    val predictedSuggestionDb: Double?,
    val predictedAiDb: Double?,
    val adjusted: List<String>,
)

object AiEqCheck {
    const val MAX_CHANGES = 6

    /**
     * Applies the AI's [bands] on top of [base] (AutoTune's suggestion), held to the same limits as the app's own fit
     * (cuts to 12 dB, boosts to +3 dB in total, narrow bands only where the route can realise them), and predicts what
     * the focus run would measure with it. The prediction uses the same model as the tuning loop: the run, minus the
     * EQ it was measured with, plus the new EQ.
     */
    fun build(
        base: List<EqFilter>, bands: List<EqFilter>, systemRoute: Boolean,
        run: SavedRun?, measuredWith: List<EqFilter>, target: Target?, grid: FreqGrid = FreqGrid(),
    ): AiEq {
        val notes = ArrayList<String>()
        val cfg = if (systemRoute) SYSTEM_EQ_FIT else FitConfig()
        if (bands.size > MAX_CHANGES) notes += "Only the first $MAX_CHANGES of ${bands.size} changes were used."
        val safe = bands.take(MAX_CHANGES).mapNotNull { b ->
            val f = b.freqHz.coerceIn(20.0, 16000.0)
            val g = b.gainDb.coerceIn(-cfg.maxCutDb, cfg.maxBoostDb)
            val qMax = if (g < 0) cfg.maxCutQ else cfg.maxBoostQ
            val q = b.q.coerceIn(0.4, qMax)
            if (abs(g) < 0.2) return@mapNotNull null
            if (abs(g - b.gainDb) > 0.05) notes += "%s: %+.1f dB held to %+.1f dB.".format(hz(f), b.gainDb, g)
            if (abs(q - b.q) > 0.01) notes += "%s: Q %.1f held to %.1f%s.".format(hz(f), b.q, q, if (systemRoute) " (AutoTune EQ cannot do narrower)" else "")
            EqFilter(f, g, q)
        }
        val full = EqChain.withSafePreamp(EqChain.capBoost(Export.combine(base + safe, cfg)))
        val sug = EqChain.withSafePreamp(base)
        var pSug: Double? = null; var pAi: Double? = null
        if (run != null && target != null && run.magDb.size == grid.size) {
            fun predict(c: List<EqFilter>): Double {
                val m = DoubleArray(grid.size) { i ->
                    val f = grid.freqs[i]
                    run.magDb[i] - measuredWith.sumOf { it.magnitudeDb(f) } + c.sumOf { it.magnitudeDb(f) }
                }
                return EqFitter.score(m, grid, target).first
            }
            pSug = predict(sug.filters); pAi = predict(full.filters)
        }
        return AiEq(full, pSug, pAi, notes)
    }

    private fun hz(f: Double) = if (f >= 1000) "%.1f kHz".format(f / 1000) else "%.0f Hz".format(f)
}

/** Builds what is sent to the model. */
object AiPrompt {
    /** Third-octave points the responses are given at (dB from the target, level-matched like the app's own score). */
    val POINTS = listOf(
        20.0, 25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0, 125.0, 160.0, 200.0, 250.0, 315.0, 400.0, 500.0, 630.0, 800.0,
        1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3150.0, 4000.0, 5000.0, 6300.0, 8000.0, 10000.0, 12500.0, 16000.0,
    )
    private const val MAX_RUNS = 7

    val SYSTEM = """
You are the measurement and tuning assistant inside AutoTune, an Android app that measures car and home-theatre
speakers with a microphone and fits a parametric EQ. You get one test as JSON: the setup, every run's response as dB
from the chosen target curve (1/6-octave smoothed, level-matched over 250-2500 Hz, positive = too loud), the EQ that was
in effect for each run, warnings, the measuring checks, resonances and AutoTune's current suggestion.

How AutoTune works (so your advice fits it):
- Run 0 is the stock run (no EQ). Every later run is measured with an EQ in effect; the next suggestion is a closed-loop
  fit from the stock run and the EQ in effect. "rms_db" is the RMS distance from the target, 30 Hz-12 kHz. Lower is better.
  Repeat runs with nothing changed show how repeatable a position is (0.3-0.8 dB apart is normal; more means the mic moved
  or the system is unstable).
- Limits: cuts down to -12 dB, boosts at most +3 dB in total at any frequency, at most 10 bands. AutoTune EQ (the phone's
  own 31-band system effect) only realises cuts with Q <= 2 and boosts with Q <= 1.4. A safe preamp is added automatically.
- Routes: "AutoTune EQ" (phone system effect), "equipment" (user sets the EQ on a head unit/DSP/AVR), "in-app" (sweep
  played through the EQ inside the app only, a preview).
- The phone's own microphone is uncalibrated and rolls off in the deep bass and the top octave; a USB measurement mic with
  its calibration file is accurate. Bluetooth adds latency, can resample and can change the level between runs.
- Narrow dips (cancellations, nulls) cannot be fixed with boost; cars have comb filtering above about 500 Hz that moves
  with the head, so only broad trends there are worth correcting; deep bass is dominated by cabin/room gain.

Your job: find what limits this result and say what to do next.
- Measurement quality: low signal-to-noise, clipping, missed start beeps, an EQ that had no effect, mixed calibrations,
  mic not at the listening position, level changes between runs, a damaged treble with the system EQ, unrepeatable runs.
- Setup and speakers: polarity or time-alignment hints (a deep notch around the crossover, a missing band), a subwoofer
  level/phase problem (60-120 Hz hole or hump), a tweeter that is too hot or too dull, fader/balance, a wrong target.
- The EQ: over-correction, boosting into nulls, bands fighting each other, oscillation between runs, a result already
  as good as it will get (say so and recommend keeping it).
Only propose EQ changes ("eq_changes") when the data clearly supports them; they are ADDED on top of AutoTune's current
suggestion (to weaken or remove a suggested band, add one at the same frequency and Q with the opposite gain). Keep to
the limits above, prefer cuts and broad bands, never boost a narrow dip, and use at most 6 changes. An empty list is a
fine answer.
Be concrete and short: plain words, numbers with units, one action per issue, no filler. Do not invent data that is not
in the JSON; if something cannot be told from it, say what to measure to find out.
""".trimIndent()

    val FOLLOW_UP = SYSTEM + "\n\nNow answer the user's follow-up question about this test in a few short sentences or a short list. Plain text, no markdown headings."

    /** The tool the model must call: forces a structured reply. */
    val TOOL: Map<String, Any?> = mapOf(
        "name" to "report",
        "description" to "Report the review of this AutoTune test to the user.",
        "input_schema" to mapOf(
            "type" to "object",
            "properties" to mapOf(
                "summary" to mapOf("type" to "string", "description" to "Two or three sentences: how good the result is and the main thing holding it back."),
                "issues" to mapOf(
                    "type" to "array",
                    "description" to "Problems found and things worth checking, most important first. Include a 'fine' item when something important checked out.",
                    "items" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "severity" to mapOf("type" to "string", "enum" to listOf("problem", "check", "fine")),
                            "area" to mapOf("type" to "string", "enum" to listOf("measurement", "setup", "speakers", "eq", "target")),
                            "title" to mapOf("type" to "string", "description" to "A few words."),
                            "detail" to mapOf("type" to "string", "description" to "What the data shows, with numbers."),
                            "action" to mapOf("type" to "string", "description" to "One concrete thing to do."),
                        ),
                        "required" to listOf("severity", "area", "title", "detail"),
                    ),
                ),
                "eq_changes" to mapOf(
                    "type" to "array",
                    "description" to "Bands added on top of AutoTune's current suggestion. Empty when the suggestion is fine.",
                    "items" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "freq_hz" to mapOf("type" to "number"),
                            "gain_db" to mapOf("type" to "number"),
                            "q" to mapOf("type" to "number"),
                            "reason" to mapOf("type" to "string"),
                        ),
                        "required" to listOf("freq_hz", "gain_db", "q"),
                    ),
                ),
                "eq_note" to mapOf("type" to "string", "description" to "One sentence on AutoTune's suggestion and your changes."),
                "next_step" to mapOf("type" to "string", "description" to "The single most useful next step."),
            ),
            "required" to listOf("summary", "issues", "eq_changes", "next_step"),
        ),
    )

    /** What the user's screen knows besides the saved test. */
    class Extra(
        val appVersion: String,
        val device: String,
        val mic: String,
        val micSource: String?,
        val calibration: String?,
        val output: String?,
        val route: String,
        val systemEqOn: Boolean,
        val systemEqDamagedBefore: Boolean,
        val eqInUse: String,
        val eqInUseChain: EqChain?,
        val sliderChanges: List<EqFilter>,
        /** The measuring checks of the last reading: name, state (fine/check/failed/not done), detail. */
        val checks: List<Triple<String, String, String?>>,
        val failure: String?,
        val lastWarnings: List<String>,
    )

    /** One test (or none, for a failure before a test exists) as compact JSON. */
    fun context(test: SavedTest?, target: Target?, kind: String?, source: String?, focus: Int?, x: Extra, grid: FreqGrid = FreqGrid()): String {
        val m = LinkedHashMap<String, Any?>()
        m["app"] = "AutoTune ${x.appVersion}"
        m["setup"] = mapOf(
            "phone" to x.device, "mic" to x.mic, "mic_source" to x.micSource, "calibration" to (x.calibration ?: "none"),
            "output" to x.output, "eq_route" to x.route, "autotune_eq_on" to x.systemEqOn,
            "system_eq_damaged_treble_before" to x.systemEqDamagedBefore,
        )
        if (x.failure != null || x.checks.isNotEmpty()) {
            m["last_measurement"] = mapOf(
                "failed" to x.failure,
                "checks" to x.checks.map { (n, s, d) -> mapOf("check" to n, "state" to s, "detail" to d) },
                "warnings" to x.lastWarnings,
            )
        }
        if (test != null && test.runs.isNotEmpty()) {
            m["test"] = mapOf(
                "name" to test.name, "system" to kind, "target" to target?.name, "source" to source,
                "gate_ms" to test.gateMs, "notes" to test.notes.ifBlank { null }, "runs" to test.runs.size,
            )
            if (target != null) m["target_curve_db"] = POINTS.map { r1(target.dbAt(it)) }
            m["points_hz"] = POINTS
            val keep = (listOf(0) + test.runs.indices.drop(1).toList().takeLast(MAX_RUNS - 1) + listOfNotNull(focus)).distinct().sorted()
            if (keep.size < test.runs.size) m["runs_left_out"] = test.runs.size - keep.size
            m["runs"] = keep.mapNotNull { i -> test.runs.getOrNull(i)?.let { r -> runJson(i, r, target, grid) } }
            m["resonances"] = test.resonances.take(8).map { r ->
                mapOf("freq_hz" to r1(r[0]), "prominence_db" to r1(r[1]), "q" to r1(r[2]), "t60_ms" to r.getOrNull(3)?.takeIf { it.isFinite() }?.roundToInt(), "t60_at_least" to (r.getOrNull(4) == 1.0))
            }
            val f = focus?.takeIf { it in test.runs.indices } ?: test.runs.lastIndex
            val sug = runCatching { suggestedFor(test.runs[f]) }.getOrNull()
            m["focus_run"] = f
            m["autotune_suggestion"] = sug?.let { s ->
                mapOf("after_run" to f, "bands" to bands(s.filters), "preamp_db" to r1(s.preampDb), "converged" to test.runs[f].converged)
            }
        }
        m["eq_in_use"] = mapOf(
            "what" to x.eqInUse, "bands" to x.eqInUseChain?.let { bands(it.filters) }, "preamp_db" to x.eqInUseChain?.preampDb?.let(::r1),
            "slider_changes" to bands(x.sliderChanges).ifEmpty { null },
        )
        m["band_format"] = "[freq_hz, gain_db, q]"
        return Diag.json(m)
    }

    private fun runJson(i: Int, r: SavedRun, target: Target?, grid: FreqGrid): Map<String, Any?> {
        val err = target?.takeIf { r.magDb.size == grid.size }?.let { t ->
            val s = grid.smoothOctave(r.magDb, 6.0)
            val off = EqFitter.offsetDb(r.magDb, grid, t)
            POINTS.map { f ->
                val k = grid.indexAtOrAbove(f)
                r1(s[k] - t.dbAt(grid.freqs[k]) - off)
            }
        }
        return mapOf(
            "run" to i, "label" to r.label, "rms_db" to r2(r.rmsDb), "note" to r.note.ifBlank { null },
            "eq_in_effect" to bands(r.eq).ifEmpty { null },
            "realised_by_31_band_eq" to r.appliedParam?.let { r.applied.size },
            "calibration" to r.calibration, "converged" to r.converged.takeIf { it },
            "warnings" to r.warnings.ifEmpty { null },
            "db_from_target" to err,
        )
    }

    private fun bands(f: List<EqFilter>) = f.map { listOf(r1(it.freqHz), r1(it.gainDb), r2(it.q)) }
    private fun r1(v: Double) = Math.round(v * 10) / 10.0
    private fun r2(v: Double) = Math.round(v * 100) / 100.0
}
