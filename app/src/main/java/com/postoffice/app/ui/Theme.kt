package com.postoffice.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.postoffice.app.model.HttpMethod

@Composable
fun PostOfficeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFFFFB68A), secondary = Color(0xFF9CCAFF))
        else -> lightColorScheme(primary = Color(0xFFB94A00), secondary = Color(0xFF00629E))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

val WarningColor = Color(0xFFC77800)
val PassColor = Color(0xFF2E9E5B)
val FailColor = Color(0xFFD03A3A)

fun methodColor(method: HttpMethod): Color = when (method) {
    HttpMethod.GET -> Color(0xFF2E9E5B)
    HttpMethod.POST -> Color(0xFFD08A00)
    HttpMethod.PUT -> Color(0xFF2F74D0)
    HttpMethod.PATCH -> Color(0xFF8A4FD0)
    HttpMethod.DELETE -> Color(0xFFD03A3A)
    HttpMethod.HEAD, HttpMethod.OPTIONS -> Color(0xFF6E7781)
}

fun statusColor(code: Int): Color = when (code) {
    in 200..299 -> Color(0xFF2E9E5B)
    in 300..399 -> Color(0xFF2F74D0)
    in 400..499 -> Color(0xFFD08A00)
    else -> Color(0xFFD03A3A)
}
