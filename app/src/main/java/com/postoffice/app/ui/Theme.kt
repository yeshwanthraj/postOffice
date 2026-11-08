package com.postoffice.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.postoffice.app.model.HttpMethod

// Harbor: cool slate surfaces with a teal accent.
// To try another variation (e.g. Harbor Deep), change the values in this file only.

private val HarborDark = darkColorScheme(
    primary = Color(0xFF2BB3A3),
    onPrimary = Color(0xFF04241F),
    primaryContainer = Color(0xFF134640),
    onPrimaryContainer = Color(0xFFBFEDE6),
    secondary = Color(0xFF8FB3BD),
    onSecondary = Color(0xFF0E2328),
    secondaryContainer = Color(0xFF1E3238),
    onSecondaryContainer = Color(0xFFD3E7EC),
    tertiary = Color(0xFF8DB4F2),
    background = Color(0xFF141B1F),
    onBackground = Color(0xFFE3EAED),
    surface = Color(0xFF141B1F),
    onSurface = Color(0xFFE3EAED),
    surfaceVariant = Color(0xFF1C252A),
    onSurfaceVariant = Color(0xFF90A2AA),
    surfaceTint = Color(0xFF141B1F),
    surfaceDim = Color(0xFF0F1518),
    surfaceBright = Color(0xFF263238),
    surfaceContainerLowest = Color(0xFF0B1013),
    surfaceContainerLow = Color(0xFF0F1518),
    surfaceContainer = Color(0xFF182126),
    surfaceContainerHigh = Color(0xFF1C252A),
    surfaceContainerHighest = Color(0xFF233037),
    outline = Color(0xFF3A4A52),
    outlineVariant = Color(0xFF26323A),
    error = Color(0xFFEE8E8E),
    onError = Color(0xFF330707),
    errorContainer = Color(0xFF4A1A1A),
    onErrorContainer = Color(0xFFFCDADA),
    inverseSurface = Color(0xFFE3EAED),
    inverseOnSurface = Color(0xFF17242A),
    inversePrimary = Color(0xFF0E7C70),
)

private val HarborLight = lightColorScheme(
    primary = Color(0xFF0E7C70),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFEFEA),
    onPrimaryContainer = Color(0xFF003A33),
    secondary = Color(0xFF4E6A72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCEBEE),
    onSecondaryContainer = Color(0xFF17323A),
    tertiary = Color(0xFF1F5BB5),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF17242A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17242A),
    surfaceVariant = Color(0xFFEEF3F4),
    onSurfaceVariant = Color(0xFF5B6E75),
    surfaceTint = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDDE5E8),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F7F8),
    surfaceContainer = Color(0xFFEEF3F4),
    surfaceContainerHigh = Color(0xFFE9EFF1),
    surfaceContainerHighest = Color(0xFFE3EBEE),
    outline = Color(0xFFB4C3C9),
    outlineVariant = Color(0xFFDCE5E8),
    error = Color(0xFFB02A2A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFCE8E8),
    onErrorContainer = Color(0xFF5C1010),
    inverseSurface = Color(0xFF17242A),
    inverseOnSurface = Color(0xFFE3EAED),
    inversePrimary = Color(0xFF2BB3A3),
)

/** A foreground/background pair for tinted labels (status badges, test results, variables). */
@Immutable
data class Tint(val fg: Color, val bg: Color)

/** App-specific colours that Material's scheme doesn't cover. */
@Immutable
data class AppPalette(
    val get: Color,
    val post: Color,
    val put: Color,
    val patch: Color,
    val delete: Color,
    val otherMethod: Color,
    val success: Tint,
    val redirect: Tint,
    val warning: Tint,
    val error: Tint,
    val neutral: Tint,
    val variableKnown: Tint,
    val variableUnknown: Tint,
    val json: JsonColors,
) {
    fun method(m: HttpMethod): Color = when (m) {
        HttpMethod.GET -> get
        HttpMethod.POST -> post
        HttpMethod.PUT -> put
        HttpMethod.PATCH -> patch
        HttpMethod.DELETE -> delete
        HttpMethod.HEAD, HttpMethod.OPTIONS -> otherMethod
    }

    fun status(code: Int): Tint = when (code) {
        in 200..299 -> success
        in 300..399 -> redirect
        in 400..499 -> warning
        else -> error
    }
}

private val HarborDarkPalette = AppPalette(
    get = Color(0xFF7CC254),
    post = Color(0xFFE3A33B),
    put = Color(0xFF6FA3F2),
    patch = Color(0xFFA98BF0),
    delete = Color(0xFFEE7B7B),
    otherMethod = Color(0xFF8E9FA6),
    success = Tint(Color(0xFF9BCB7E), Color(0x247CC254)),
    redirect = Tint(Color(0xFF8DB4F2), Color(0x244C8DF0)),
    warning = Tint(Color(0xFFE0B56E), Color(0x24E3A33B)),
    error = Tint(Color(0xFFEE8E8E), Color(0x24EF5B5B)),
    neutral = Tint(Color(0xFFA7B6BD), Color(0x248E9FA6)),
    variableKnown = Tint(Color(0xFF4FC7B8), Color(0x2E2BB3A3)),
    variableUnknown = Tint(Color(0xFFEE8E8E), Color(0x2EEF5B5B)),
    json = JsonColors(
        key = Color(0xFF4FC7B8),        // teal, the accent
        string = Color(0xFFE0B56E),     // sand
        number = Color(0xFF8DB4F2),     // soft blue
        boolean = Color(0xFFB9A0F2),    // lavender
        nul = Color(0xFF8E9FA6),        // muted grey, italic
        punctuation = Color(0xFF6F818A),
    ),
)

private val HarborLightPalette = AppPalette(
    get = Color(0xFF3E8E1F),
    post = Color(0xFFA86A05),
    put = Color(0xFF2763C2),
    patch = Color(0xFF7A4BC9),
    delete = Color(0xFFC23030),
    otherMethod = Color(0xFF5B6E75),
    success = Tint(Color(0xFF3B7A1E), Color(0xFFEAF4E3)),
    redirect = Tint(Color(0xFF1F5BB5), Color(0xFFE6EFFC)),
    warning = Tint(Color(0xFF8A5A0B), Color(0xFFFBF0DD)),
    error = Tint(Color(0xFFB02A2A), Color(0xFFFCE8E8)),
    neutral = Tint(Color(0xFF4E6168), Color(0xFFEEF3F4)),
    variableKnown = Tint(Color(0xFF0B6B60), Color(0xFFDDF1EE)),
    variableUnknown = Tint(Color(0xFFB02A2A), Color(0xFFFCE8E8)),
    json = JsonColors(
        key = Color(0xFF0B6B60),
        string = Color(0xFF8A5A0B),
        number = Color(0xFF1F5BB5),
        boolean = Color(0xFF7A4BC9),
        nul = Color(0xFF5B6E75),
        punctuation = Color(0xFF8796A0),
    ),
)

val LocalPalette = staticCompositionLocalOf { HarborDarkPalette }

/** Fixed Harbor palette (no wallpaper-based colours); follows the system light/dark setting. */
@Composable
fun PostOfficeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalPalette provides if (dark) HarborDarkPalette else HarborLightPalette) {
        MaterialTheme(colorScheme = if (dark) HarborDark else HarborLight, content = content)
    }
}

/** Small tinted label, e.g. "200 OK" or "Pass". */
@Composable
fun TintedLabel(text: String, tint: Tint, modifier: Modifier = Modifier, monospace: Boolean = true) {
    Text(
        text,
        color = tint.fg,
        fontWeight = FontWeight.SemiBold,
        fontFamily = if (monospace) FontFamily.Monospace else null,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .background(tint.bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Tab with the accent colour only on the selected item; the rest stay muted. */
@Composable
fun AppTab(selected: Boolean, onClick: () -> Unit, text: @Composable () -> Unit) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = text,
        selectedContentColor = MaterialTheme.colorScheme.primary,
        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
