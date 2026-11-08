package com.postoffice.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.postoffice.app.model.unresolvedNames
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.postoffice.app.model.ApiRequest
import com.postoffice.app.model.ResponseState
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Adaptive layout:
 *  - wide tablet landscape (>= 1200dp): History | Request | Response side by side
 *  - tablet (>= 720dp): History | Request stacked over Response
 *  - narrow: history in a drawer, Request over Response
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val palette = LocalPalette.current
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showEnvironments by rememberSaveable { mutableStateOf(false) }

    val variableScope = remember(state.envs) { state.envs.scope() }
    val unresolved = remember(state.request, variableScope) { state.request.unresolvedNames(variableScope) }

    if (showEnvironments) {
        CompositionLocalProvider(LocalVariableScope provides variableScope) {
            EnvironmentManagerScreen(vm, state.envs, onBack = { showEnvironments = false })
        }
        return
    }

    CompositionLocalProvider(LocalVariableScope provides variableScope) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val showSidebar = maxWidth >= 720.dp
        // Narrower history on smaller tablets / split screen so the URL bar keeps room.
        val sidebarWidth = if (maxWidth >= 1000.dp) 300.dp else 240.dp
        val sideBySide = maxWidth - sidebarWidth >= 900.dp

        val history: @Composable (Modifier) -> Unit = { mod ->
            HistoryPane(
                items = state.history,
                onSelect = { vm.loadFromHistory(it); scope.launch { drawer.close() } },
                onDelete = vm::deleteHistory,
                onClear = vm::clearHistory,
                modifier = mod,
            )
        }

        val workspace: @Composable () -> Unit = {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("PostOffice", fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            if (!showSidebar) IconButton(onClick = { scope.launch { drawer.open() } }) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "History")
                            }
                        },
                        actions = {
                            EnvironmentPicker(state.envs, onSelect = vm::setActiveEnvironment, onManage = { showEnvironments = true })
                            VariablesQuickLook(state.envs, onManage = { showEnvironments = true })
                            IconButton(onClick = vm::newRequest) { Icon(Icons.Default.Add, contentDescription = "New request") }
                        },
                    )
                },
            ) { padding ->
                Column(
                    Modifier
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                        .fillMaxSize(),
                ) {
                    UrlBar(
                        request = state.request,
                        loading = state.response is ResponseState.Loading,
                        onChange = vm::updateRequest,
                        onSend = vm::send,
                        onCancel = vm::cancel,
                        modifier = Modifier.fillMaxWidth().padding(bottom = if (unresolved.isEmpty()) 12.dp else 0.dp),
                    )
                    if (unresolved.isNotEmpty()) {
                        val where = state.envs.active?.name?.let { "$it or Globals" } ?: "Globals (no environment selected)"
                        val names = unresolved.joinToString(", ") { "{{$it}}" }
                        Row(Modifier.padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.WarningAmber, null, tint = palette.warning.fg, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "$names ${if (unresolved.size == 1) "isn't" else "aren't"} defined in $where. Sent as written.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.warning.fg,
                            )
                        }
                    }
                    if (sideBySide) {
                        Row(Modifier.fillMaxSize()) {
                            RequestTabs(state.request, vm::updateRequest, Modifier.weight(1f).fillMaxHeight())
                            VerticalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            ResponseViewer(state.response, state.report, Modifier.weight(1f).fillMaxHeight().padding(bottom = 16.dp))
                        }
                    } else {
                        RequestTabs(state.request, vm::updateRequest, Modifier.weight(1f).fillMaxWidth())
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        ResponseViewer(state.response, state.report, Modifier.weight(1f).fillMaxWidth().padding(bottom = 16.dp))
                    }
                }
            }
        }

        if (showSidebar) {
            Row(Modifier.fillMaxSize()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.width(sidebarWidth).fillMaxHeight()) {
                    // The sidebar isn't inside the Scaffold, so it must keep clear of the status/navigation bars itself.
                    history(Modifier.fillMaxSize().systemBarsPadding())
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                workspace()
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawer,
                drawerContent = {
                    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow) { history(Modifier.fillMaxSize()) }
                },
            ) { workspace() }
        }
    }
    }
}

@Composable
private fun HistoryPane(
    items: List<ApiRequest>,
    onSelect: (ApiRequest) -> Unit,
    onDelete: (ApiRequest) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Column(modifier.padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("History", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (items.isNotEmpty()) IconButton(onClick = onClear) {
                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear history")
            }
        }
        if (items.isEmpty()) {
            Text(
                "Requests you send will show up here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(item) }
                        .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        item.method.name,
                        color = palette.method(item.method),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(56.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(item.url, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        if (item.timestamp > 0) Text(
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onDelete(item) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.padding(8.dp))
    }
}
