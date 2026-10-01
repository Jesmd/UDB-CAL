package com.example.minimo.ui.theme

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E5F74),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBFE9F8),
    onPrimaryContainer = Color(0xFF001F28),
    secondary = Color(0xFF4C616B),
    tertiary = Color(0xFF5B5B7E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FCEE2),
    onPrimary = Color(0xFF003543),
    primaryContainer = Color(0xFF004D61),
    onPrimaryContainer = Color(0xFFBFE9F8),
    secondary = Color(0xFFB3CAD5),
    tertiary = Color(0xFFC4C3EB),
)

@Composable
fun MinimoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
