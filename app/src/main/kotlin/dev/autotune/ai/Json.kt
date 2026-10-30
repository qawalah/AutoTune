package dev.autotune.ai

/**
 * A small JSON reader (objects become Map<String, Any?>, arrays List<Any?>, numbers Double), so the AI replies can be
 * read in plain Kotlin and unit tested without Android's org.json. Writing uses [dev.autotune.Diag.json].
 */
object Json {
    class ParseError(msg: String) : RuntimeException(msg)

    fun parse(text: String): Any? {
        val p = P(text)
        p.ws()
        val v = p.value()
        p.ws()
        if (p.i != text.length) throw ParseError("Unexpected text at ${p.i}")
        return v
    }

    private class P(val s: String) {
        var i = 0

        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }

        fun value(): Any? {
            if (i >= s.length) throw ParseError("Unexpected end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw ParseError("Unexpected '$c' at $i")
            }
        }

        private fun lit(w: String, v: Any?): Any? {
            if (!s.startsWith(w, i)) throw ParseError("Expected $w at $i")
            i += w.length
            return v
        }

        private fun num(): Double {
            val a = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            return s.substring(a, i).toDoubleOrNull() ?: throw ParseError("Bad number at $a")
        }

        private fun str(): String {
            i++ // opening quote
            val b = StringBuilder()
            while (true) {
                if (i >= s.length) throw ParseError("Unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return b.toString()
                    '\\' -> {
                        if (i >= s.length) throw ParseError("Bad escape")
                        when (val e = s[i++]) {
                            'n' -> b.append('\n'); 't' -> b.append('\t'); 'r' -> b.append('\r')
                            'b' -> b.append('\b'); 'f' -> b.append('\u000C')
                            'u' -> {
                                if (i + 4 > s.length) throw ParseError("Bad unicode escape")
                                b.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4
                            }
                            else -> b.append(e) // \" \\ \/
                        }
                    }
                    else -> b.append(c)
                }
            }
        }

        private fun arr(): List<Any?> {
            i++
            val out = ArrayList<Any?>()
            ws()
            if (i < s.length && s[i] == ']') { i++; return out }
            while (true) {
                ws(); out += value(); ws()
                if (i >= s.length) throw ParseError("Unterminated array")
                when (s[i++]) { ',' -> continue; ']' -> return out; else -> throw ParseError("Expected , or ] at ${i - 1}") }
            }
        }

        private fun obj(): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            ws()
            if (i < s.length && s[i] == '}') { i++; return out }
            while (true) {
                ws()
                if (i >= s.length || s[i] != '"') throw ParseError("Expected a key at $i")
                val k = str(); ws()
                if (i >= s.length || s[i++] != ':') throw ParseError("Expected : at ${i - 1}")
                ws(); out[k] = value(); ws()
                if (i >= s.length) throw ParseError("Unterminated object")
                when (s[i++]) { ',' -> continue; '}' -> return out; else -> throw ParseError("Expected , or } at ${i - 1}") }
            }
        }
    }
}

// small typed accessors for parsed JSON
@Suppress("UNCHECKED_CAST")
internal fun Any?.obj(): Map<String, Any?>? = this as? Map<String, Any?>
internal fun Any?.list(): List<Any?> = (this as? List<Any?>) ?: emptyList()
internal fun Map<String, Any?>.str(k: String): String? = (this[k] as? String)?.takeIf { it.isNotBlank() }
internal fun Map<String, Any?>.num(k: String): Double? = (this[k] as? Number)?.toDouble()?.takeIf { it.isFinite() }
