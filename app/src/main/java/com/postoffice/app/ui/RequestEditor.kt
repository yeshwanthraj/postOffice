package com.postoffice.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.postoffice.app.model.ApiKeyLocation
import com.postoffice.app.model.ApiRequest
import com.postoffice.app.model.AuthConfig
import com.postoffice.app.model.AuthType
import com.postoffice.app.model.BodyType
import com.postoffice.app.model.HttpMethod
import com.postoffice.app.net.HttpEngine

@Composable
fun UrlBar(
    request: ApiRequest,
    loading: Boolean,
    onChange: ((ApiRequest) -> ApiRequest) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Close the keyboard on send so it doesn't cover the response.
    val send = { focus.clearFocus(); keyboard?.hide(); onSend() }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        DropdownSelector(
            selected = request.method,
            options = HttpMethod.entries,
            label = { it.name },
            color = { palette.method(it) },
            onSelect = { m -> onChange { it.copy(method = m) } },
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = request.url,
            onValueChange = { v -> onChange { it.copy(url = v) } },
            placeholder = { Text("https://api.example.com/v1/resource", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            visualTransformation = rememberVariableHighlighter(),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedBorderColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Send,
            ),
            keyboardActions = KeyboardActions(onSend = { if (!loading) send() }),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        if (loading) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.heightIn(min = 56.dp),
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onError)
                Spacer(Modifier.width(8.dp))
                Text("Cancel")
            }
        } else {
            Button(onClick = send, modifier = Modifier.heightIn(min = 56.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Send")
            }
        }
    }
}

private enum class RequestTab(val title: String) {
    PARAMS("Params"), AUTH("Auth"), HEADERS("Headers"), BODY("Body"), SCRIPTS("Scripts")
}

@Composable
fun RequestTabs(
    request: ApiRequest,
    onChange: ((ApiRequest) -> ApiRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier) {
        ScrollableTabRow(
            selectedTabIndex = selected,
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
        ) {
            RequestTab.entries.forEachIndexed { i, tab ->
                val count = when (tab) {
                    RequestTab.PARAMS -> request.params.count { it.isActive }
                    RequestTab.HEADERS -> request.headers.count { it.isActive }
                    RequestTab.AUTH -> if (request.auth.type != AuthType.NONE) 1 else 0
                    RequestTab.BODY -> if (request.bodyType != BodyType.NONE && request.method.allowsBody) 1 else 0
                    RequestTab.SCRIPTS -> listOf(request.preRequestScript, request.postResponseScript).count { it.isNotBlank() }
                }
                val showNumber = tab == RequestTab.PARAMS || tab == RequestTab.HEADERS || tab == RequestTab.SCRIPTS
                AppTab(
                    selected = selected == i,
                    onClick = { selected = i },
                    text = {
                        if (count > 0) {
                            BadgedBox(badge = { Badge { Text(if (showNumber) "$count" else "") } }) {
                                Text(tab.title)
                            }
                        } else {
                            Text(tab.title)
                        }
                    },
                )
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
        ) {
            when (RequestTab.entries[selected]) {
                RequestTab.PARAMS -> {
                    SectionHint("Query parameters are URL-encoded and appended to the URL.")
                    KeyValueEditor(request.params, { rows -> onChange { it.copy(params = rows) } }, "Parameter")
                }
                RequestTab.HEADERS -> {
                    SectionHint("Headers set by Authorization and Body (e.g. Authorization, Content-Type) are added automatically; a header here with the same name overrides Content-Type.")
                    KeyValueEditor(request.headers, { rows -> onChange { it.copy(headers = rows) } }, "Header")
                }
                RequestTab.AUTH -> AuthEditor(request.auth) { a -> onChange { it.copy(auth = a) } }
                RequestTab.BODY -> BodyEditor(request, onChange)
                RequestTab.SCRIPTS -> ScriptsEditor(request, onChange)
            }
        }
    }
}

@Composable
private fun AuthEditor(auth: AuthConfig, onChange: (AuthConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            AuthType.entries.forEachIndexed { i, type ->
                SegmentedButton(
                    selected = auth.type == type,
                    onClick = { onChange(auth.copy(type = type)) },
                    shape = SegmentedButtonDefaults.itemShape(i, AuthType.entries.size),
                ) { Text(type.label) }
            }
        }
        when (auth.type) {
            AuthType.NONE -> SectionHint("This request does not use any authorization.")
            AuthType.BEARER -> {
                SecretField("Token", auth.bearerToken) { onChange(auth.copy(bearerToken = it)) }
                SectionHint("Sent as  Authorization: Bearer <token>")
            }
            AuthType.BASIC -> {
                OutlinedTextField(
                    value = auth.username,
                    onValueChange = { onChange(auth.copy(username = it)) },
                    label = { Text("Username") },
                    singleLine = true,
                    keyboardOptions = TechnicalKeyboard,
                    modifier = Modifier.fillMaxWidth(),
                )
                SecretField("Password", auth.password) { onChange(auth.copy(password = it)) }
                SectionHint("Sent as  Authorization: Basic base64(username:password)")
            }
            AuthType.API_KEY -> {
                OutlinedTextField(
                    value = auth.apiKeyName,
                    onValueChange = { onChange(auth.copy(apiKeyName = it)) },
                    label = { Text("Key name") },
                    singleLine = true,
                    keyboardOptions = TechnicalKeyboard,
                    modifier = Modifier.fillMaxWidth(),
                )
                SecretField("Value", auth.apiKeyValue) { onChange(auth.copy(apiKeyValue = it)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Add to", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(12.dp))
                    ApiKeyLocation.entries.forEach { loc ->
                        FilterChip(
                            selected = auth.apiKeyLocation == loc,
                            onClick = { onChange(auth.copy(apiKeyLocation = loc)) },
                            label = { Text(loc.label) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Toggle visibility")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BodyEditor(request: ApiRequest, onChange: ((ApiRequest) -> ApiRequest) -> Unit) {
    if (!request.method.allowsBody) {
        SectionHint("${request.method.name} requests don't send a body. Pick POST, PUT, PATCH, DELETE or OPTIONS to add one.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            BodyType.entries.forEach { type ->
                FilterChip(
                    selected = request.bodyType == type,
                    onClick = { onChange { it.copy(bodyType = type) } },
                    label = { Text(type.label) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        when (request.bodyType) {
            BodyType.NONE -> SectionHint("No body will be sent.")
            BodyType.FORM_URLENCODED ->
                KeyValueEditor(request.formBody, { rows -> onChange { it.copy(formBody = rows) } }, "Field")
            else -> {
                if (request.bodyType == BodyType.JSON) {
                    val invalid = request.rawBody.isNotBlank() && HttpEngine.formatJson(request.rawBody) == null
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            HttpEngine.formatJson(request.rawBody)?.let { pretty -> onChange { it.copy(rawBody = pretty) } }
                        }) { Text("Beautify") }
                        if (invalid) {
                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Text("Invalid JSON", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                OutlinedTextField(
                    value = request.rawBody,
                    onValueChange = { v -> onChange { it.copy(rawBody = v) } },
                    placeholder = { Text(if (request.bodyType == BodyType.JSON) "{\n  \"key\": \"value\"\n}" else "Request body") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    visualTransformation = rememberVariableHighlighter(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                    minLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
                SectionHint("Content-Type: ${request.bodyType.defaultContentType} (override it in Headers)")
            }
        }
    }
}
