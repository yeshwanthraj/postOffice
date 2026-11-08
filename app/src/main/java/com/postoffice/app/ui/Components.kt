package com.postoffice.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.postoffice.app.model.KeyValue
import com.postoffice.app.model.Templates
import com.postoffice.app.model.VariableScope

/** For URLs, header names/values, tokens: no autocorrect, suggestions or auto-capitalisation. */
val TechnicalKeyboard = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Ascii,
)

/** The variables currently in effect, used to colour `{{name}}` in text fields. */
val LocalVariableScope = compositionLocalOf { VariableScope() }

/** Colours `{{name}}` with [known] when [scope] defines it and [unknown] when it doesn't. Text length is unchanged. */
class VariableHighlighter(
    private val scope: VariableScope,
    private val known: SpanStyle,
    private val unknown: SpanStyle,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (!text.text.contains("{{")) return TransformedText(text, OffsetMapping.Identity)
        val styled = buildAnnotatedString {
            append(text)
            Templates.PATTERN.findAll(text.text).forEach { m ->
                addStyle(if (scope.lookup(m.groupValues[1]) != null) known else unknown, m.range.first, m.range.last + 1)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    override fun equals(other: Any?) =
        other is VariableHighlighter && other.scope === scope && other.known == known && other.unknown == unknown
    override fun hashCode() = System.identityHashCode(scope)
}

@Composable
fun rememberVariableHighlighter(): VisualTransformation {
    val scope = LocalVariableScope.current
    val palette = LocalPalette.current
    return remember(scope, palette) {
        VariableHighlighter(
            scope,
            known = SpanStyle(color = palette.variableKnown.fg, background = palette.variableKnown.bg, fontWeight = FontWeight.Medium),
            unknown = SpanStyle(color = palette.variableUnknown.fg, background = palette.variableUnknown.bg, fontWeight = FontWeight.Medium),
        )
    }
}

/**
 * Editable table of key/value rows. A blank row is always kept at the end so the
 * user can just start typing a new entry. With [showSecretToggle], each row gets a
 * lock button that masks its value.
 */
@Composable
fun KeyValueEditor(
    rows: List<KeyValue>,
    onChange: (List<KeyValue>) -> Unit,
    keyLabel: String = "Key",
    valueLabel: String = "Value",
    modifier: Modifier = Modifier,
    showSecretToggle: Boolean = false,
) {
    val highlighter = rememberVariableHighlighter()
    val mask = remember { PasswordVisualTransformation() }
    fun commit(list: List<KeyValue>) {
        val trimmed = list.dropLastWhile { it.key.isEmpty() && it.value.isEmpty() }
        onChange(trimmed + KeyValue())
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEachIndexed { index, row ->
            val isTrailingBlank = index == rows.lastIndex && row.key.isEmpty() && row.value.isEmpty()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = row.enabled,
                    enabled = !isTrailingBlank,
                    onCheckedChange = { checked -> commit(rows.map { if (it.id == row.id) it.copy(enabled = checked) else it }) },
                )
                OutlinedTextField(
                    value = row.key,
                    onValueChange = { v -> commit(rows.map { if (it.id == row.id) it.copy(key = v) else it }) },
                    label = { Text(keyLabel) },
                    singleLine = true,
                    visualTransformation = highlighter,
                    keyboardOptions = TechnicalKeyboard,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = row.value,
                    onValueChange = { v -> commit(rows.map { if (it.id == row.id) it.copy(value = v) else it }) },
                    label = { Text(valueLabel) },
                    singleLine = true,
                    visualTransformation = if (row.secret) mask else highlighter,
                    keyboardOptions = TechnicalKeyboard,
                    modifier = Modifier.weight(1.4f),
                )
                if (showSecretToggle) {
                    IconToggleButton(
                        checked = row.secret,
                        enabled = !isTrailingBlank,
                        onCheckedChange = { s -> commit(rows.map { if (it.id == row.id) it.copy(secret = s) else it }) },
                    ) {
                        Icon(
                            if (row.secret) Icons.Filled.Lock else Icons.Outlined.LockOpen,
                            contentDescription = if (row.secret) "Secret: value hidden" else "Mark as secret",
                            tint = if (row.secret) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(
                    onClick = { commit(rows.filterNot { it.id == row.id }) },
                    enabled = !isTrailingBlank,
                ) { Icon(Icons.Outlined.Delete, contentDescription = "Remove row") }
            }
        }
        TextButton(onClick = { onChange(rows + KeyValue()) }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Add row")
        }
    }
}

/** Compact button that opens a menu of [options]. */
@Composable
fun <T> DropdownSelector(
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    color: (T) -> Color? = { null },
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        FilledTonalButton(
            onClick = { open = true },
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.heightIn(min = 56.dp),
        ) {
            Text(
                label(selected),
                color = color(selected) ?: MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = {
                        Text(label(opt), color = color(opt) ?: Color.Unspecified, fontWeight = FontWeight.SemiBold)
                    },
                    onClick = { onSelect(opt); open = false },
                )
            }
        }
    }
}

@Composable
fun SectionHint(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
