package com.postoffice.app.ui

import com.postoffice.app.ui.JsonToken.BOOLEAN
import com.postoffice.app.ui.JsonToken.KEY
import com.postoffice.app.ui.JsonToken.NULL
import com.postoffice.app.ui.JsonToken.NUMBER
import com.postoffice.app.ui.JsonToken.PUNCTUATION
import com.postoffice.app.ui.JsonToken.STRING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonHighlighterTest {

    private fun tokens(text: String) = JsonHighlighter.tokenize(text).map { text.substring(it.start, it.end) to it.token }

    @Test fun classifiesEveryKindOfToken() {
        val json = """{ "id": -12.5e3, "name" : "a \"quoted\" {x}", "ok": true, "off": false, "none": null, "list": [1, "two"] }"""
        assertEquals(
            listOf(
                "{" to PUNCTUATION,
                "\"id\"" to KEY, ":" to PUNCTUATION, "-12.5e3" to NUMBER, "," to PUNCTUATION,
                "\"name\"" to KEY, ":" to PUNCTUATION, "\"a \\\"quoted\\\" {x}\"" to STRING, "," to PUNCTUATION,
                "\"ok\"" to KEY, ":" to PUNCTUATION, "true" to BOOLEAN, "," to PUNCTUATION,
                "\"off\"" to KEY, ":" to PUNCTUATION, "false" to BOOLEAN, "," to PUNCTUATION,
                "\"none\"" to KEY, ":" to PUNCTUATION, "null" to NULL, "," to PUNCTUATION,
                "\"list\"" to KEY, ":" to PUNCTUATION, "[" to PUNCTUATION, "1" to NUMBER, "," to PUNCTUATION,
                "\"two\"" to STRING, "]" to PUNCTUATION,
                "}" to PUNCTUATION,
            ),
            tokens(json),
        )
    }

    @Test fun truncatedBodyDoesNotThrowOrOverrun() {
        val text = """{"a": "unterminated"""
        val spans = JsonHighlighter.tokenize(text)
        assertTrue(spans.all { it.end <= text.length })
        assertEquals(STRING, spans.last().token)
    }

    @Test fun detectsJson() {
        assertTrue(JsonHighlighter.looksLikeJson("  [1]", null))
        assertTrue(JsonHighlighter.looksLikeJson("anything", "application/problem+json"))
        assertFalse(JsonHighlighter.looksLikeJson("<html>", "text/html"))
    }
}
