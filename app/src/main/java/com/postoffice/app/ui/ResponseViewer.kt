package com.postoffice.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.postoffice.app.model.ApiResponse
import com.postoffice.app.model.ResponseState
import com.postoffice.app.net.HttpEngine
import com.postoffice.app.script.LogLevel
import com.postoffice.app.script.LogLine
import com.postoffice.app.script.TestResult
import com.postoffice.app.script.TestStatus

@Composable
fun ResponseViewer(state: ResponseState, report: RunReport, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Response", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        when (state) {
            ResponseState.Idle -> Placeholder("Send a request to see the response here.")
            ResponseState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is ResponseState.Error -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                        if (state.durationMs > 0) Text("after ${state.durationMs} ms", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (report.logs.isNotEmpty()) {
                    Text("Console", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    ConsoleList(report.logs)
                }
            }
            is ResponseState.Success -> ResponseContent(state.response, report)
        }
    }
}

@Composable
private fun Placeholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ResponseContent(resp: ApiResponse, report: RunReport) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var wrap by rememberSaveable { mutableStateOf(true) }
    val clipboard = LocalClipboardManager.current
    val passed = report.tests.count { it.status == TestStatus.PASS }
    val counted = report.tests.count { it.status != TestStatus.SKIP }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "${resp.code} ${resp.message}".trim(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(statusColor(resp.code), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Text("${resp.durationMs} ms", style = MaterialTheme.typography.labelLarge)
        Text(formatBytes(resp.sizeBytes), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.weight(1f))
        IconToggleButton(checked = wrap, onCheckedChange = { wrap = it }) {
            Icon(
                Icons.AutoMirrored.Filled.WrapText, contentDescription = "Wrap lines",
                tint = if (wrap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { clipboard.setText(AnnotatedString(resp.body)) }) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy body")
        }
    }
    Text(
        resp.finalUrl,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.padding(vertical = 4.dp),
    )
    ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Body") })
        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Headers (${resp.headers.size})") })
        Tab(
            selected = tab == 2, onClick = { tab = 2 },
            text = {
                if (report.tests.isEmpty()) Text("Tests")
                else Text("Tests ($passed/$counted)", color = if (passed == counted) PassColor else FailColor)
            },
        )
        Tab(
            selected = tab == 3, onClick = { tab = 3 },
            text = {
                val errors = report.logs.any { it.level == LogLevel.ERROR }
                Text(
                    if (report.logs.isEmpty()) "Console" else "Console (${report.logs.size})",
                    color = if (errors) FailColor else Color.Unspecified,
                )
            },
        )
    }
    when (tab) {
        0 -> {
            if (resp.body.isEmpty()) {
                Placeholder("Empty body")
            } else {
                val scroll = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .let { if (wrap) it else it.horizontalScroll(rememberScrollState()) }
                    .padding(top = 8.dp)
                SelectionContainer(scroll) {
                    Column {
                        if (resp.truncated) {
                            Text(
                                "Showing first ${formatBytes(HttpEngine.MAX_DISPLAY_BYTES)} of ${formatBytes(resp.sizeBytes)}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(resp.body, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, softWrap = wrap)
                    }
                }
            }
        }
        1 -> LazyColumn(Modifier.fillMaxSize()) {
            items(resp.headers) { (name, value) ->
                SelectionContainer {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(name, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.4f))
                        Text(value, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.6f))
                    }
                }
                HorizontalDivider()
            }
        }
        2 -> if (report.tests.isEmpty()) {
            Placeholder("No tests ran. Add pm.test(...) to the post-response script.")
        } else {
            TestList(report.tests)
        }
        else -> if (report.logs.isEmpty()) {
            Placeholder("Nothing logged. Use console.log(...) in a script.")
        } else {
            ConsoleList(report.logs)
        }
    }
}

@Composable
private fun TestList(tests: List<TestResult>) {
    LazyColumn(Modifier.fillMaxSize().padding(top = 4.dp)) {
        items(tests) { t ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (label, color) = when (t.status) {
                        TestStatus.PASS -> "Pass" to PassColor
                        TestStatus.FAIL -> "Fail" to FailColor
                        TestStatus.SKIP -> "Skip" to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        label,
                        color = color,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .widthIn(min = 32.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(t.name, style = MaterialTheme.typography.bodyMedium)
                }
                t.error?.let {
                    SelectionContainer {
                        Text(
                            it,
                            color = FailColor,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 58.dp, top = 2.dp),
                        )
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun ConsoleList(logs: List<LogLine>) {
    SelectionContainer {
        LazyColumn(Modifier.fillMaxSize().padding(top = 4.dp)) {
            items(logs) { line ->
                val color = when (line.level) {
                    LogLevel.ERROR -> FailColor
                    LogLevel.WARN -> WarningColor
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(
                        line.phase.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(40.dp),
                    )
                    Text(line.message, color = color, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
}
