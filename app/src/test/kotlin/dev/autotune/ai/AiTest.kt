package dev.autotune.ai

import dev.autotune.SavedRun
import dev.autotune.SavedTest
import dev.autotune.dsp.EqChain
import dev.autotune.dsp.EqFilter
import dev.autotune.dsp.EqFitter
import dev.autotune.dsp.FreqGrid
import dev.autotune.dsp.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTest {
    private val grid = FreqGrid()
    private val claude = AiClient.of("sk-ant-test", AiModel.SONNET)
    private val gemini = AiClient.of("AIzaTest", AiModel.GEMINI_FLASH)

    @Test
    fun jsonReadsWhatTheApiSends() {
        val v = Json.parse("""{"a":[1,-2.5e1,true,null,"x\"yé\n"],"b":{}}""").obj()!!
        val a = v["a"].list()
        assertEquals(1.0, a[0])
        assertEquals(-25.0, a[1])
        assertEquals(true, a[2])
        assertNull(a[3])
        assertEquals("x\"yé\n", a[4])
        assertTrue(v["b"].obj()!!.isEmpty())
    }

    @Test(expected = Json.ParseError::class)
    fun brokenJsonIsAnError() { Json.parse("""{"a": [1, 2""") }

    @Test
    fun reviewIsReadFromTheToolCallAndSurvivesStoring() {
        val reply = Json.parse(
            """{"content":[{"type":"text","text":"ok"},{"type":"tool_use","name":"report","input":{
              "summary":"Good result.",
              "issues":[{"severity":"check","area":"setup","title":"Sub level","detail":"Hole at 80 Hz","action":"Raise the sub 2 dB"},
                        {"severity":"problem","area":"measurement","title":"Low SNR","detail":"22 dB"},
                        {"severity":"weird","title":"Odd"}],
              "eq_changes":[{"freq_hz":120,"gain_db":-2,"q":1.5,"reason":"hump"}],
              "next_step":"Measure again."}}]}""",
        ).obj()!!
        val input = (claude.reviewInput(reply))!!
        val r = AiReview.fromTool(input, "t1", "claude-sonnet-5-5", 1000L, 3, 2, false)
        assertEquals("Good result.", r.summary)
        assertEquals(3, r.issues.size)
        assertEquals(AiSeverity.PROBLEM, r.issues[0].severity) // problems first
        assertEquals(AiSeverity.CHECK, r.issues.last().severity) // unknown severity reads as "check"
        assertEquals(120.0, r.bands.single().filter.freqHz, 0.0)
        val back = (AiReview.fromJson(r.toJson()))!!
        assertEquals("t1", back.testId)
        assertEquals(2, back.focusRun)
        assertEquals(3, back.issues.size)
        assertEquals(-2.0, back.bands.single().filter.gainDb, 1e-9)
        assertEquals("Measure again.", back.nextStep)
    }

    @Test
    fun textReplyAndErrorsAreReadable() {
        val reply = Json.parse("""{"content":[{"type":"text","text":"Line one"},{"type":"text","text":"Line two"}]}""").obj()!!
        assertEquals("Line one\nLine two", claude.answerText(reply))
        val err = Json.parse("""{"type":"error","error":{"type":"invalid_request_error","message":"max_tokens too big"}}""").obj()
        assertTrue(claude.errorText(400, err).contains("max_tokens too big"))
        assertTrue(claude.errorText(401, null).contains("API key"))
    }

    /** A stock response with a +8 dB hump at 100 Hz on top of the target. */
    private fun humpRun(): Pair<SavedRun, Target> {
        val t = Target.FLAT
        val mag = DoubleArray(grid.size) { i ->
            val f = grid.freqs[i]
            val x = kotlin.math.ln(f / 100.0) / kotlin.math.ln(2.0)
            t.dbAt(f) + 8.0 * kotlin.math.exp(-x * x / 0.08)
        }
        val rms = EqFitter.score(mag, grid, t).first
        return SavedRun(0L, rms, mag, emptyList(), "Stock", complete = true) to t
    }

    @Test
    fun aiBandsAreHeldToTheLimitsAndPredicted() {
        val (run, target) = humpRun()
        val bands = listOf(
            EqFilter(100.0, -8.0, 3.0), // the hump: right
            EqFilter(40.0, 9.0, 6.0), // a huge narrow boost: must be held
        ) + List(6) { EqFilter(1000.0 + it * 100, -0.5, 1.0) } // too many changes
        val eq = AiEqCheck.build(emptyList(), bands, systemRoute = false, run = run, measuredWith = emptyList(), target = target, grid = grid)
        assertTrue(eq.adjusted.any { it.contains("first 6") })
        assertTrue(eq.adjusted.any { it.contains("+9.0 dB held to +3.0 dB") })
        val peak = grid.freqs.maxOf { f -> eq.chain.filters.sumOf { it.magnitudeDb(f) } }
        assertTrue("boost peak $peak", peak <= 3.05)
        assertTrue(eq.chain.filters.filter { it.gainDb > 0 }.all { it.q <= 2.5 })
        val before = (eq.predictedSuggestionDb)!!
        val after = (eq.predictedAiDb)!!
        assertTrue("cutting the hump should help: $before -> $after", after < before - 0.5)
    }

    @Test
    fun systemRouteKeepsBandsWide() {
        val (run, target) = humpRun()
        val eq = AiEqCheck.build(emptyList(), listOf(EqFilter(100.0, -6.0, 5.0)), systemRoute = true, run = run, measuredWith = emptyList(), target = target, grid = grid)
        assertTrue(eq.chain.filters.all { it.q <= 2.0 + 1e-9 })
        assertTrue(eq.adjusted.any { it.contains("AutoTune EQ cannot do narrower") })
    }

    @Test
    fun contextCarriesEveryRunAgainstTheTarget() {
        val (stock, target) = humpRun()
        val run1 = SavedRun(1L, 1.0, stock.magDb, emptyList(), "Run 1", note = "sub +2", applied = listOf(EqFilter(100.0, -6.0, 3.0)), complete = true)
        val test = SavedTest("id", "F-150", 0L, 0, 0, 80, listOf(stock, run1), notes = "driver seat")
        val x = AiPrompt.Extra(
            "1.13.0", "Samsung S23", "UMIK-1", "UNPROCESSED", "umik.txt", "Bluetooth", "AutoTune EQ", true, false,
            "F-150 · Next suggestion", EqChain.withSafePreamp(listOf(EqFilter(100.0, -6.0, 3.0))), emptyList(),
            listOf(Triple("No clipping", "fine", null)), null, listOf("A warning"),
        )
        val json = Json.parse(AiPrompt.context(test, target, "car", "phone", 1, x, grid)).obj()!!
        val runs = json["runs"].list()
        assertEquals(2, runs.size)
        val r0 = runs[0].obj()!!
        assertEquals(AiPrompt.POINTS.size, r0["db_from_target"].list().size)
        // the hump shows at 100 Hz and nowhere far from it
        val at100 = (r0["db_from_target"].list()[AiPrompt.POINTS.indexOf(100.0)] as Double)
        val at2k = (r0["db_from_target"].list()[AiPrompt.POINTS.indexOf(2000.0)] as Double)
        assertTrue("100 Hz $at100", at100 > 5.0)
        assertTrue("2 kHz $at2k", kotlin.math.abs(at2k) < 0.5)
        assertEquals("sub +2", runs[1].obj()!!["note"])
        assertEquals("driver seat", json["test"].obj()!!["notes"])
        assertNotNull(json["autotune_suggestion"].obj())
        assertEquals("fine", json["last_measurement"].obj()!!["checks"].list()[0].obj()!!["state"])
    }

    @Test
    fun contextWithoutATestStillDescribesTheFailure() {
        val x = AiPrompt.Extra(
            "1.13.0", "phone", "the phone's own microphone", null, null, null, "in-app preview", false, false, "none", null,
            emptyList(), listOf(Triple("Start beeps", "failed", "not heard")), "No sweep found", emptyList(),
        )
        val json = Json.parse(AiPrompt.context(null, null, null, null, null, x, grid)).obj()!!
        assertEquals("No sweep found", json["last_measurement"].obj()!!["failed"])
        assertNull(json["runs"])
    }

    @Test
    fun requestBodyIsValidJsonAndForcesTheReportTool() {
        val ctxText = "{\"notes\":\"tab\there, bell\u0007 and \\\"quotes\\\"\"}"
        val body = Json.parse(AiClient.of("sk-ant-test", AiModel.HAIKU).reviewBody(ctxText + "\u0001", "Review this test."))!!.obj()!!
        assertEquals("claude-haiku-5-5", body["model"])
        assertEquals("report", body["tool_choice"].obj()!!["name"])
        assertEquals("report", body["tools"].list()[0].obj()!!["name"])
        val content = body["messages"].list()[0].obj()!!["content"] as String
        assertTrue(content.contains("bell\u0007"))
        assertTrue(content.endsWith("Review this test."))
    }

    @Test
    fun geminiRequestForcesTheReportFunctionAndRepliesAreRead() {
        val body = Json.parse(gemini.reviewBody("{\"a\":1}", "Review this test."))!!.obj()!!
        val cfg = body["toolConfig"].obj()!!["functionCallingConfig"].obj()!!
        assertEquals("ANY", cfg["mode"])
        assertEquals(listOf("report"), cfg["allowedFunctionNames"])
        val decl = body["tools"].list()[0].obj()!!["functionDeclarations"].list()[0].obj()!!
        assertEquals("report", decl["name"])
        assertNotNull(decl["parameters"].obj()!!["properties"].obj()!!["issues"])
        assertNotNull(body["systemInstruction"].obj())
        val reply = Json.parse(
            """{"candidates":[{"content":{"role":"model","parts":[{"text":"thinking","thought":true},
               {"functionCall":{"name":"report","args":{"summary":"Fine.","issues":[],"eq_changes":[],"next_step":"Keep it."}}}]},"finishReason":"STOP"}]}""",
        ).obj()!!
        assertEquals("Fine.", gemini.reviewInput(reply)!!["summary"])
        val answer = Json.parse("""{"candidates":[{"content":{"parts":[{"text":"hidden","thought":true},{"text":"Raise the sub."}]}}]}""").obj()!!
        assertEquals("Raise the sub.", gemini.answerText(answer))
        // the follow-up conversation uses Gemini's role names
        val turns = Json.parse(gemini.answerBody(listOf(AiTurn("user", "q1"), AiTurn("assistant", "a1"), AiTurn("user", "q2"))))!!.obj()!!["contents"].list()
        assertEquals(listOf("user", "model", "user"), turns.map { it.obj()!!["role"] })
    }

    @Test
    fun geminiErrorsAreReadable() {
        val bad = Json.parse("""{"error":{"code":400,"message":"API key not valid.","status":"INVALID_ARGUMENT","details":[{"reason":"API_KEY_INVALID"}]}}""").obj()
        assertTrue(gemini.errorText(400, bad).contains("Google API key was not accepted"))
        assertTrue(gemini.errorText(429, null).contains("quota"))
        val blocked = runCatching { gemini.reviewInput(Json.parse("""{"promptFeedback":{"blockReason":"SAFETY"}}""").obj()!!) }.exceptionOrNull()
        assertTrue(blocked?.message?.contains("SAFETY") == true)
    }

    @Test
    fun modelsBelongToTheirProvider() {
        assertEquals(AiModel.GEMINI_FLASH, AiModel.pick(AiProvider.GOOGLE, "claude-sonnet-5-5"))
        assertEquals(AiModel.OPUS, AiModel.pick(AiProvider.CLAUDE, "claude-opus-5-5"))
        assertTrue(AiProvider.CLAUDE.looksLikeKey("sk-ant-api03-abcdefghijklmnop"))
        assertTrue(!AiProvider.CLAUDE.looksLikeKey("AIzaSyAbcdefghijklmnopqrstuv"))
        assertTrue(AiProvider.GOOGLE.looksLikeKey("AIzaSyAbcdefghijklmnopqrstuv"))
    }
}
