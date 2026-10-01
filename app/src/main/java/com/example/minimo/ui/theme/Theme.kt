package com.example.minimo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Palette inspired by iOS system colors. It is fixed (no wallpaper-based dynamic color) so the glass looks the same
// on every phone.
private val LightColors = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E8FF),
    onPrimaryContainer = Color(0xFF00255C),
    secondary = Color(0xFF5856D6),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E2FA),
    onSecondaryContainer = Color(0xFF1B1A52),
    tertiary = Color(0xFF34C759),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF111114),
    surface = Color(0xFFF2F2F7),
    onSurface = Color(0xFF111114),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF5F5F66),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    outline = Color(0xFFC6C6C8),
    outlineVariant = Color(0xFFE0E0E4),
    error = Color(0xFFD70015),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE1DF),
    onErrorContainer = Color(0xFF5C0008),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF0A3A75),
    onPrimaryContainer = Color(0xFFD6E8FF),
    secondary = Color(0xFF7D7AFF),
    onSecondary = Color(0xFF14134A),
    secondaryContainer = Color(0xFF2C2B66),
    onSecondaryContainer = Color(0xFFE3E2FA),
    tertiary = Color(0xFF30D158),
    background = Color(0xFF050507),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF050507),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF2C2C30),
    onSurfaceVariant = Color(0xFFA1A1A8),
    surfaceContainer = Color(0xFF1C1C20),
    surfaceContainerHigh = Color(0xFF26262B),
    outline = Color(0xFF48484D),
    outlineVariant = Color(0xFF38383D),
    error = Color(0xFFFF6961),
    onError = Color(0xFF3B0004),
    errorContainer = Color(0xFF5C1410),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Colors only the glass components use. */
data class GlassColors(
    /** Top and bottom of the translucent fill of a glass surface. */
    val fillTop: Color,
    val fillBottom: Color,
    /** Fill when there is nothing blurred behind (older phones): more opaque so the text stays legible. */
    val fillSolidTop: Color,
    val fillSolidBottom: Color,
    /** The bright edge where light hits the glass, and the faint opposite edge. */
    val rimLight: Color,
    val rimShade: Color,
    val shadow: Color,
    /** Positive / warning accents (secured, reachable) that read on both themes. */
    val success: Color,
    val warning: Color,
    /** Three soft color blobs drawn behind everything. */
    val blobA: Color,
    val blobB: Color,
    val blobC: Color,
    val isDark: Boolean,
)

private val LightGlass = GlassColors(
    fillTop = Color.White.copy(alpha = 0.62f),
    fillBottom = Color.White.copy(alpha = 0.40f),
    fillSolidTop = Color.White.copy(alpha = 0.90f),
    fillSolidBottom = Color.White.copy(alpha = 0.78f),
    rimLight = Color.White.copy(alpha = 0.95f),
    rimShade = Color(0xFF8E8E93).copy(alpha = 0.28f),
    shadow = Color(0xFF1B2A4A),
    success = Color(0xFF1B8A3C),
    warning = Color(0xFFB45F00),
    blobA = Color(0xFF7FB8FF).copy(alpha = 0.55f),
    blobB = Color(0xFFC4A3FF).copy(alpha = 0.40f),
    blobC = Color(0xFF8FE3D0).copy(alpha = 0.40f),
    isDark = false,
)

private val DarkGlass = GlassColors(
    fillTop = Color.White.copy(alpha = 0.14f),
    fillBottom = Color.White.copy(alpha = 0.06f),
    fillSolidTop = Color(0xFF2A2A30).copy(alpha = 0.94f),
    fillSolidBottom = Color(0xFF1E1E23).copy(alpha = 0.90f),
    rimLight = Color.White.copy(alpha = 0.50f),
    rimShade = Color.White.copy(alpha = 0.10f),
    shadow = Color.Black,
    success = Color(0xFF30D158),
    warning = Color(0xFFFFB340),
    blobA = Color(0xFF0A84FF).copy(alpha = 0.40f),
    blobB = Color(0xFF7D4DFF).copy(alpha = 0.32f),
    blobC = Color(0xFF00B3A4).copy(alpha = 0.26f),
    isDark = true,
)

val LocalGlassColors = staticCompositionLocalOf { LightGlass }

/** Glass colors of the current theme. */
val MaterialTheme.glass: GlassColors
    @Composable
    @ReadOnlyComposable
    get() = LocalGlassColors.current

private val Rounded = FontFamily.SansSerif

private val MinimoTypography = Typography(
    displaySmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp),
    headlineLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Rounded, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

@Composable
fun MinimoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(
        LocalGlassColors provides if (darkTheme) DarkGlass else LightGlass,
        // Nothing here sits on a Material Surface, so say what the default text color is.
        LocalContentColor provides colorScheme.onSurface,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MinimoTypography,
            content = content,
        )
    }
}
