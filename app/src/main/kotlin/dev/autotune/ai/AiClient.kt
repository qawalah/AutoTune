package dev.autotune.ai

import dev.autotune.Diag
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/** Who answers: Anthropic's Claude or Google's Gemini, each with the user's own key. */
enum class AiProvider(val title: String, val company: String, val keyHint: String, val keyWhere: String) {
    CLAUDE("Claude", "Anthropic", "sk-ant-…", "console.anthropic.com > Settings > API keys"),
    GOOGLE("Google Gemini", "Google", "AIza…", "aistudio.google.com > Get API key"),
    ;

    /** A rough shape check before a key is stored, so a pasted wrong thing is caught at once. */
    fun looksLikeKey(k: String): Boolean = when (this) {
        CLAUDE -> k.startsWith("sk-") && k.length >= 20
        GOOGLE -> k.length >= 20 && k.none { it.isWhitespace() }
    }

    companion object { fun of(name: String?) = entries.firstOrNull { it.name == name } ?: CLAUDE }
}

/** The models the user can pick for each provider, the recommended one first. */
enum class AiModel(val provider: AiProvider, val id: String, val title: String, val detail: String) {
    SONNET(AiProvider.CLAUDE, "claude-sonnet-5-5", "Claude Sonnet", "Balanced: thorough and quick (recommended)"),
    OPUS(AiProvider.CLAUDE, "claude-opus-5-5", "Claude Opus", "Most careful, slower and costs more"),
    HAIKU(AiProvider.CLAUDE, "claude-haiku-5-5", "Claude Haiku", "Fastest and cheapest, less detail"),
    GEMINI_FLASH(AiProvider.GOOGLE, "gemini-3.8-flash", "Gemini 3.8 Flash", "Balanced: thorough and quick (recommended)"),
    GEMINI_PRO(AiProvider.GOOGLE, "gemini-4-argon", "Gemini 4 Argon", "Google's most careful model, slower and costs more"),
    GEMINI_LITE(AiProvider.GOOGLE, "gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Fastest and cheapest, less detail"),
    ;

    companion object {
        fun of(id: String?): AiModel = entries.firstOrNull { it.id == id } ?: SONNET
        fun forProvider(p: AiProvider) = entries.filter { it.provider == p }
        /** The saved model if it belongs to [p], otherwise that provider's recommended one. */
        fun pick(p: AiProvider, savedId: String?) = entries.firstOrNull { it.id == savedId && it.provider == p } ?: forProvider(p).first()
    }
}

class AiException(message: String) : Exception(message)

/** One message of a conversation: role "user" or "assistant", plain text. */
class AiTurn(val role: String, val text: String)

/**
 * One request style per provider. Blocking: call it from a background dispatcher.
 * Nothing but the JSON summary built by [AiPrompt] (and the user's questions) is sent.
 */
abstract class AiClient(protected val key: String, protected val model: AiModel) {
    /** A structured review: the model is made to call the report tool, whose arguments are returned. */
    fun review(context: String, ask: String): Map<String, Any?> =
        reviewInput(post(reviewBody(context, ask))) ?: throw AiException("The reply had no review in it. Try again.")

    /** A plain-text answer to [turns] (alternating user/assistant, starting and ending with the user). */
    fun answer(turns: List<AiTurn>): String = answerText(post(answerBody(turns))) ?: throw AiException("The reply was empty. Try again.")

    /** Cheapest possible request, to check the key. */
    fun ping() { post(pingBody()) }

    internal abstract fun reviewBody(context: String, ask: String): String
    internal abstract fun answerBody(turns: List<AiTurn>): String
    protected abstract fun pingBody(): String
    protected abstract fun url(): String
    protected abstract fun headers(): Map<String, String>
    abstract fun reviewInput(reply: Map<String, Any?>): Map<String, Any?>?
    abstract fun answerText(reply: Map<String, Any?>): String?
    abstract fun errorText(code: Int, json: Map<String, Any?>?): String

    private val who get() = model.provider.title

    private fun post(body: String): Map<String, Any?> {
        val c = try { URL(url()).openConnection() as HttpURLConnection } catch (e: IOException) { throw AiException("Could not reach $who: ${e.message}") }
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 20_000
            c.readTimeout = 150_000
            c.doOutput = true
            c.setRequestProperty("content-type", "application/json")
            headers().forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            val json = runCatching { Json.parse(text).obj() }.getOrNull()
            if (code !in 200..299) throw AiException(errorText(code, json))
            return json ?: throw AiException("$who sent a reply the app could not read.")
        } catch (e: AiException) {
            throw e
        } catch (e: UnknownHostException) {
            throw AiException("No internet connection.")
        } catch (e: SocketTimeoutException) {
            throw AiException("$who took too long to answer. Try again, or pick a faster model in Settings.")
        } catch (e: IOException) {
            throw AiException("Could not reach $who: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            c.disconnect()
        }
    }

    companion object {
        fun of(key: String, model: AiModel): AiClient = when (model.provider) {
            AiProvider.CLAUDE -> ClaudeClient(key, model)
            AiProvider.GOOGLE -> GeminiClient(key, model)
        }
    }
}

/** Anthropic Messages API; the review is a forced tool call. */
class ClaudeClient(key: String, model: AiModel) : AiClient(key, model) {
    override fun url() = "https://api.anthropic.com/v1/messages"
    override fun headers() = mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01")

    override fun reviewBody(context: String, ask: String): String = Diag.json(
        mapOf(
            "model" to model.id,
            "max_tokens" to 3000,
            "system" to AiPrompt.SYSTEM,
            "tools" to listOf(AiPrompt.TOOL),
            "tool_choice" to mapOf("type" to "tool", "name" to "report"),
            "messages" to listOf(mapOf("role" to "user", "content" to "Test data:\n$context\n\n$ask")),
        ),
    )

    override fun answerBody(turns: List<AiTurn>): String = Diag.json(
        mapOf(
            "model" to model.id,
            "max_tokens" to 1200,
            "system" to AiPrompt.FOLLOW_UP,
            "messages" to turns.map { mapOf("role" to it.role, "content" to it.text) },
        ),
    )

    override fun pingBody() = Diag.json(mapOf("model" to model.id, "max_tokens" to 1, "messages" to listOf(mapOf("role" to "user", "content" to "Hi"))))

    override fun reviewInput(reply: Map<String, Any?>): Map<String, Any?>? =
        reply["content"].list().mapNotNull { it.obj() }.firstOrNull { it["type"] == "tool_use" }?.get("input").obj()

    override fun answerText(reply: Map<String, Any?>): String? =
        reply["content"].list().mapNotNull { it.obj() }.filter { it["type"] == "text" }.mapNotNull { it.str("text") }
            .joinToString("\n").trim().ifBlank { null }

    override fun errorText(code: Int, json: Map<String, Any?>?): String {
        val msg = json?.get("error").obj()?.str("message")
        return when (code) {
            401 -> "The Claude API key was not accepted. Check it in Settings > AI assistant."
            403 -> "This API key may not use that model. ${msg ?: ""}".trim()
            404 -> "That model is not available to this key. Pick another one in Settings > AI assistant."
            429 -> "Too many requests or the key's limit is reached. Wait a minute and try again."
            529, 503 -> "Claude is busy right now. Try again in a moment."
            400 -> "Claude could not take the request: ${msg ?: "bad request"}"
            else -> "Claude answered with an error ($code)${msg?.let { ": $it" } ?: ""}"
        }
    }
}

/** Google Gemini API (generateContent); the review is a forced function call. */
class GeminiClient(key: String, model: AiModel) : AiClient(key, model) {
    override fun url() = "https://generativelanguage.googleapis.com/v1beta/models/${model.id}:generateContent"
    override fun headers() = mapOf("x-goog-api-key" to key)

    private fun system(text: String) = mapOf("parts" to listOf(mapOf("text" to text)))

    override fun reviewBody(context: String, ask: String): String {
        val tool = AiPrompt.TOOL
        return Diag.json(
            mapOf(
                "systemInstruction" to system(AiPrompt.SYSTEM),
                "contents" to listOf(mapOf("role" to "user", "parts" to listOf(mapOf("text" to "Test data:\n$context\n\n$ask")))),
                "tools" to listOf(
                    mapOf("functionDeclarations" to listOf(mapOf("name" to tool["name"], "description" to tool["description"], "parameters" to tool["input_schema"]))),
                ),
                "toolConfig" to mapOf("functionCallingConfig" to mapOf("mode" to "ANY", "allowedFunctionNames" to listOf("report"))),
                // thinking counts against this limit on current Gemini models: leave room for it and the review
                "generationConfig" to mapOf("maxOutputTokens" to 12000),
            ),
        )
    }

    override fun answerBody(turns: List<AiTurn>): String = Diag.json(
        mapOf(
            "systemInstruction" to system(AiPrompt.FOLLOW_UP),
            "contents" to turns.map { mapOf("role" to if (it.role == "assistant") "model" else "user", "parts" to listOf(mapOf("text" to it.text))) },
            "generationConfig" to mapOf("maxOutputTokens" to 6000),
        ),
    )

    override fun pingBody() = Diag.json(
        mapOf("contents" to listOf(mapOf("role" to "user", "parts" to listOf(mapOf("text" to "Hi")))), "generationConfig" to mapOf("maxOutputTokens" to 16)),
    )

    private fun parts(reply: Map<String, Any?>): List<Map<String, Any?>> {
        val cand = reply["candidates"].list().firstOrNull().obj()
        if (cand == null) {
            val blocked = reply["promptFeedback"].obj()?.str("blockReason")
            throw AiException(if (blocked != null) "Gemini declined the request ($blocked)." else "Gemini sent no answer. Try again.")
        }
        val out = cand["content"].obj()?.get("parts").list().mapNotNull { it.obj() }
        if (out.isEmpty() && cand.str("finishReason") == "MAX_TOKENS") throw AiException("Gemini ran out of room before answering. Try again or pick another model.")
        return out
    }

    override fun reviewInput(reply: Map<String, Any?>): Map<String, Any?>? =
        parts(reply).firstNotNullOfOrNull { it["functionCall"].obj()?.takeIf { f -> f["name"] == "report" }?.get("args").obj() }

    override fun answerText(reply: Map<String, Any?>): String? =
        parts(reply).filter { it["thought"] != true }.mapNotNull { it.str("text") }.joinToString("\n").trim().ifBlank { null }

    override fun errorText(code: Int, json: Map<String, Any?>?): String {
        val err = json?.get("error").obj()
        val msg = err?.str("message")
        val reason = err?.get("details").list().mapNotNull { it.obj()?.str("reason") }.firstOrNull()
        return when {
            reason == "API_KEY_INVALID" || code == 401 -> "The Google API key was not accepted. Check it in Settings > AI assistant."
            code == 403 -> "This Google key may not use the Gemini API. ${msg ?: ""}".trim()
            code == 404 -> "That Gemini model is not available to this key. Pick another one in Settings > AI assistant."
            code == 429 -> "The key's Gemini quota is used up for now. Wait a minute and try again."
            code == 500 || code == 503 -> "Gemini is busy right now. Try again in a moment."
            code == 400 -> "Gemini could not take the request: ${msg ?: "bad request"}"
            else -> "Gemini answered with an error ($code)${msg?.let { ": $it" } ?: ""}"
        }
    }
}
