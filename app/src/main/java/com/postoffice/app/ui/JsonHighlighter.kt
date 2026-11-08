package com.postoffice.app.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle

enum class JsonToken { KEY, STRING, NUMBER, BOOLEAN, NULL, PUNCTUATION }

data class JsonSpan(val start: Int, val end: Int, val token: JsonToken)

@Immutable
data class JsonColors(
    val key: Color,
    val string: Color,
    val number: Color,
    val boolean: Color,
    val nul: Color,
    val punctuation: Color,
)

/**
 * A forgiving single-pass JSON tokenizer for colouring. It never throws: anything it
 * doesn't recognise is left uncoloured, so truncated or slightly invalid bodies still render.
 */
object JsonHighlighter {

    /** Bodies larger than this are shown plain to keep scrolling smooth. */
    const val MAX_CHARS = 400_000

    fun looksLikeJson(text: String, contentType: String?): Boolean {
        if (contentType?.contains("json", ignoreCase = true) == true) return true
        val first = text.firstOrNull { !it.isWhitespace() }
        return first == '{' || first == '['
    }

    fun tokenize(text: String): List<JsonSpan> {
        val spans = ArrayList<JsonSpan>()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                c == '"' -> {
                    val start = i
                    i++
                    while (i < n && text[i] != '"') {
                        if (text[i] == '\\') i++ // skip escaped char
                        i++
                    }
                    i = minOf(i + 1, n) // include closing quote
                    var j = i
                    while (j < n && text[j].isWhitespace()) j++
                    val isKey = j < n && text[j] == ':'
                    spans += JsonSpan(start, i, if (isKey) JsonToken.KEY else JsonToken.STRING)
                }
                c == '-' || c.isDigit() -> {
                    val start = i
                    i++
                    while (i < n && (text[i].isDigit() || text[i] in ".eE+-")) i++
                    spans += JsonSpan(start, i, JsonToken.NUMBER)
                }
                text.startsWith("true", i) -> { spans += JsonSpan(i, i + 4, JsonToken.BOOLEAN); i += 4 }
                text.startsWith("false", i) -> { spans += JsonSpan(i, i + 5, JsonToken.BOOLEAN); i += 5 }
                text.startsWith("null", i) -> { spans += JsonSpan(i, i + 4, JsonToken.NULL); i += 4 }
                c in "{}[],:" -> { spans += JsonSpan(i, i + 1, JsonToken.PUNCTUATION); i++ }
                else -> i++
            }
        }
        return spans
    }

    fun highlight(text: String, colors: JsonColors): AnnotatedString = buildAnnotatedString {
        append(text)
        val styles = mapOf(
            JsonToken.KEY to SpanStyle(color = colors.key),
            JsonToken.STRING to SpanStyle(color = colors.string),
            JsonToken.NUMBER to SpanStyle(color = colors.number),
            JsonToken.BOOLEAN to SpanStyle(color = colors.boolean),
            JsonToken.NULL to SpanStyle(color = colors.nul, fontStyle = FontStyle.Italic),
            JsonToken.PUNCTUATION to SpanStyle(color = colors.punctuation),
        )
        tokenize(text).forEach { addStyle(styles.getValue(it.token), it.start, it.end) }
    }
}
