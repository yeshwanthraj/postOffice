package com.postoffice.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.postoffice.app.model.EnvironmentState
import com.postoffice.app.model.KeyValue

private const val GLOBALS = "__globals__"

// ---------------------------------------------------------------- top bar

@Composable
fun EnvironmentPicker(envs: EnvironmentState, onSelect: (String?) -> Unit, onManage: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { open = true }) {
            Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                envs.active?.name ?: "No environment",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp),
                color = if (envs.active == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("No environment") },
                trailingIcon = { if (envs.activeId == null) Icon(Icons.Default.Check, null) },
                onClick = { onSelect(null); open = false },
            )
            envs.environments.forEach { env ->
                DropdownMenuItem(
                    text = { Text(env.name) },
                    trailingIcon = { if (env.id == envs.activeId) Icon(Icons.Default.Check, null) },
                    onClick = { onSelect(env.id); open = false },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Manage environments") },
                leadingIcon = { Icon(Icons.Default.Settings, null) },
                onClick = { onManage(); open = false },
            )
        }
    }
}

/** Eye button: read-only look at the variables in effect, without leaving the request. */
@Composable
fun VariablesQuickLook(envs: EnvironmentState, onManage: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.Visibility, contentDescription = "Show variables") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(Modifier.widthIn(min = 280.dp, max = 440.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                val active = envs.active
                val envKeys = active?.variables.orEmpty().filter { it.isActive }.map { it.key.trim() }.toSet()
                QuickLookSection(active?.name ?: "No environment selected", active?.variables.orEmpty(), emptySet())
                Spacer(Modifier.size(12.dp))
                QuickLookSection("Globals", envs.globals, envKeys)
                TextButton(onClick = { open = false; onManage() }, modifier = Modifier.align(Alignment.End)) { Text("Manage") }
            }
        }
    }
}

@Composable
private fun QuickLookSection(title: String, rows: List<KeyValue>, overridden: Set<String>) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    val active = rows.filter { it.isActive }
    if (active.isEmpty()) {
        Text("No variables", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    active.forEach { v ->
        val isOverridden = v.key.trim() in overridden
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                v.key, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.4f),
                textDecoration = if (isOverridden) TextDecoration.LineThrough else null,
            )
            Text(
                if (v.secret) "••••••••" else v.value,
                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.6f),
                color = if (isOverridden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ---------------------------------------------------------------- manager screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvironmentManagerScreen(vm: MainViewModel, envs: EnvironmentState, onBack: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf(envs.activeId ?: GLOBALS) }
    var showDetailOnPhone by rememberSaveable { mutableStateOf(false) }
    // If the selected environment was deleted, fall back to Globals.
    if (selected != GLOBALS && envs.environments.none { it.id == selected }) selected = GLOBALS

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane = maxWidth >= 720.dp
        val inDetail = !twoPane && showDetailOnPhone
        BackHandler { if (inDetail) showDetailOnPhone = false else onBack() }

        val select: (String) -> Unit = { selected = it; showDetailOnPhone = true }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Environments", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { if (inDetail) showDetailOnPhone = false else onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { padding ->
            Row(Modifier.padding(padding).fillMaxSize()) {
                if (twoPane || !inDetail) {
                    Surface(tonalElevation = if (twoPane) 1.dp else 0.dp, modifier = if (twoPane) Modifier.width(280.dp).fillMaxHeight() else Modifier.fillMaxSize()) {
                        EnvironmentList(
                            envs = envs,
                            selected = selected.takeIf { twoPane },
                            showChevrons = !twoPane,
                            onSelect = select,
                            onCreate = { select(vm.createEnvironment()) },
                        )
                    }
                }
                if (twoPane) VerticalDivider()
                if (twoPane || inDetail) {
                    EnvironmentDetail(
                        vm = vm,
                        envs = envs,
                        selectedId = selected,
                        onSelect = { selected = it },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun EnvironmentList(
    envs: EnvironmentState,
    selected: String?,
    showChevrons: Boolean,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        ListRow(
            title = "Globals",
            subtitle = "Used by every environment",
            leading = { Icon(Icons.Default.Public, null) },
            selected = selected == GLOBALS,
            active = false,
            chevron = showChevrons,
            onClick = { onSelect(GLOBALS) },
        )
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        if (envs.environments.isEmpty()) {
            Text(
                "Create an environment for each place you call, like Development, Staging and Production.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        envs.environments.forEach { env ->
            val count = env.variables.count { it.isActive }
            ListRow(
                title = env.name.ifBlank { "Untitled" },
                subtitle = if (count == 1) "1 variable" else "$count variables",
                leading = null,
                selected = selected == env.id,
                active = env.id == envs.activeId,
                chevron = showChevrons,
                onClick = { onSelect(env.id) },
            )
        }
        TextButton(onClick = onCreate, modifier = Modifier.padding(horizontal = 8.dp)) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("New environment")
        }
    }
}

@Composable
private fun ListRow(
    title: String,
    subtitle: String,
    leading: (@Composable () -> Unit)?,
    selected: Boolean,
    active: Boolean,
    chevron: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Row(
            Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (active) {
                Text("Active", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun EnvironmentDetail(
    vm: MainViewModel,
    envs: EnvironmentState,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val env = envs.environments.firstOrNull { it.id == selectedId }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (env == null) {
            Text("Globals", style = MaterialTheme.typography.headlineSmall)
            SectionHint("Globals apply to every request. If the active environment has a variable with the same name, the environment's value is used.")
            KeyValueEditor(
                rows = envs.globals,
                onChange = { vm.updateVariables(null, it) },
                keyLabel = "Variable",
                showSecretToggle = true,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = env.name,
                    onValueChange = { vm.renameEnvironment(env.id, it) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).widthIn(max = 360.dp),
                )
                if (env.id == envs.activeId) {
                    AssistChip(onClick = { vm.setActiveEnvironment(null) }, label = { Text("Active") }, leadingIcon = { Icon(Icons.Default.Check, null) })
                } else {
                    FilledTonalButton(onClick = { vm.setActiveEnvironment(env.id) }) { Text("Set active") }
                }
                IconButton(onClick = { vm.duplicateEnvironment(env.id)?.let(onSelect) }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Duplicate environment")
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete environment")
                }
            }
            SectionHint("Use these in any request field as {{name}}. They override Globals with the same name. Tap the lock to hide a value.")
            KeyValueEditor(
                rows = env.variables,
                onChange = { vm.updateVariables(env.id, it) },
                keyLabel = "Variable",
                showSecretToggle = true,
            )
        }
    }

    if (confirmDelete && env != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${env.name.ifBlank { "this environment" }}?") },
            text = { Text("Its ${env.variables.count { it.isActive }} variables will be deleted too. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteEnvironment(env.id); onSelect(GLOBALS) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
