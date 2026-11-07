package com.postoffice.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.postoffice.app.model.ApiRequest

private data class Snippet(val title: String, val code: String)

private val PRE_SNIPPETS = listOf(
    Snippet("Set an environment variable", """pm.environment.set("name", "value");"""),
    Snippet("Add a request header", """pm.request.headers.upsert("X-Request-Id", pm.uuid());"""),
    Snippet(
        "Timestamp and UUID",
        """
        pm.variables.set("timestamp", Date.now().toString());
        pm.variables.set("requestId", pm.uuid());
        """.trimIndent(),
    ),
    Snippet(
        "HMAC signature",
        """
        const ts = Date.now().toString();
        const signature = pm.crypto.hmacSha256(pm.environment.get("secret"), ts);
        pm.request.headers.upsert("X-Timestamp", ts);
        pm.request.headers.upsert("X-Signature", signature);
        """.trimIndent(),
    ),
    Snippet("Log the request URL", """console.log("URL:", pm.variables.replaceIn(pm.request.url));"""),
)

private val POST_SNIPPETS = listOf(
    Snippet(
        "Status is 200",
        """
        pm.test("Status is 200", () => {
          pm.response.to.have.status(200);
        });
        """.trimIndent(),
    ),
    Snippet(
        "Save token from JSON",
        """
        const body = pm.response.json();
        pm.environment.set("token", body.token);
        """.trimIndent(),
    ),
    Snippet(
        "Response time below 500 ms",
        """
        pm.test("Responds within 500 ms", () => {
          pm.expect(pm.response.responseTime).to.be.below(500);
        });
        """.trimIndent(),
    ),
    Snippet(
        "JSON has a property",
        """
        pm.test("Body has an id", () => {
          pm.expect(pm.response.json()).to.have.property("id");
        });
        """.trimIndent(),
    ),
    Snippet(
        "Header is present",
        """
        pm.test("Content-Type is JSON", () => {
          pm.expect(pm.response.headers.get("Content-Type")).to.include("json");
        });
        """.trimIndent(),
    ),
)

private fun appendSnippet(script: String, snippet: String): String = when {
    script.isBlank() -> snippet + "\n"
    script.endsWith("\n\n") -> script + snippet + "\n"
    script.endsWith("\n") -> script + "\n" + snippet + "\n"
    else -> script + "\n\n" + snippet + "\n"
}

@Composable
fun ScriptsEditor(request: ApiRequest, onChange: ((ApiRequest) -> ApiRequest) -> Unit) {
    var post by rememberSaveable { mutableStateOf(false) }
    val script = if (post) request.postResponseScript else request.preRequestScript
    val setScript: (String) -> Unit = { s ->
        onChange { if (post) it.copy(postResponseScript = s) else it.copy(preRequestScript = s) }
    }
    val snippets = if (post) POST_SNIPPETS else PRE_SNIPPETS

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 360.dp)) {
            listOf("Pre-request" to false, "Post-response" to true).forEachIndexed { i, (label, isPost) ->
                val hasCode = (if (isPost) request.postResponseScript else request.preRequestScript).isNotBlank()
                SegmentedButton(
                    selected = post == isPost,
                    onClick = { post = isPost },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                ) { Text(if (hasCode) "$label •" else label) }
            }
        }
        SectionHint(
            if (post) "Runs after the response arrives. Use pm.response, pm.test and pm.expect; results show in Tests and Console."
            else "Runs before the request is sent. It can change pm.request and set variables. If it fails, the request isn't sent."
        )

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 600.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CodeEditor(script, setScript, Modifier.weight(1f))
                    Column(Modifier.width(190.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Snippets", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        snippets.forEach { s ->
                            OutlinedButton(onClick = { setScript(appendSnippet(script, s.code)) }, modifier = Modifier.fillMaxWidth()) {
                                Text(s.title, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        if (script.isNotEmpty()) TextButton(onClick = { setScript("") }) { Text("Clear script") }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        snippets.forEach { s -> AssistChip(onClick = { setScript(appendSnippet(script, s.code)) }, label = { Text(s.title) }) }
                    }
                    CodeEditor(script, setScript, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Monospace editor with a line-number gutter. Lines don't wrap; long lines scroll sideways. */
@Composable
private fun CodeEditor(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
    val lineCount = value.count { it == '\n' } + 1
    Surface(
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.heightIn(min = 240.dp),
    ) {
        Row {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.heightIn(min = 240.dp)) {
                Text(
                    (1..maxOf(lineCount, 10)).joinToString("\n"),
                    style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp).widthIn(min = 20.dp),
                )
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                val minWidth = maxWidth
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = style,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.widthIn(min = minWidth).padding(horizontal = 10.dp, vertical = 10.dp),
                        decorationBox = { inner ->
                            Box {
                                if (value.isEmpty()) {
                                    Text("// Write JavaScript here, or tap a snippet", style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                                }
                                inner()
                            }
                        },
                    )
                }
            }
        }
    }
}
