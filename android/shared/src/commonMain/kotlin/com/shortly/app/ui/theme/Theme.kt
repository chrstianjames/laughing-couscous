package com.shortly.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.shortly.app.platform.ApplySystemBarsStyle

val Pink = Color(0xFFFF2E7D)
val PinkDark = Color(0xFFC51162)
val Teal = Color(0xFF00E5FF)
val Purple = Color(0xFFB83BFF)

private val DarkColors = darkColorScheme(
    primary = Pink,
    onPrimary = Color.White,
    primaryContainer = PinkDark,
    secondary = Teal,
    onSecondary = Color.Black,
    tertiary = Purple,
    background = Color(0xFF0A0A0C),
    onBackground = Color.White,
    surface = Color(0xFF141418),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E1E24),
    onSurfaceVariant = Color(0xFFBBBBBB),
    error = Color(0xFFFF5252),
    outline = Color(0xFF2A2A32)
)

private val LightColors = lightColorScheme(
    primary = Pink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD6E3),
    secondary = Color(0xFF0097A7),
    onSecondary = Color.White,
    tertiary = Purple,
    background = Color(0xFFFBFBFC),
    onBackground = Color(0xFF0A0A0C),
    surface = Color.White,
    onSurface = Color(0xFF0A0A0C),
    surfaceVariant = Color(0xFFF2F2F5),
    onSurfaceVariant = Color(0xFF555555),
    error = Color(0xFFD32F2F),
    outline = Color(0xFFDDDDE2)
)

@Composable
fun ShortlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    // Edge-to-edge system bars on Android; no-op on the web.
    ApplySystemBarsStyle(darkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
