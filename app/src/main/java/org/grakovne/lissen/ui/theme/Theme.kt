package org.grakovne.lissen.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.grakovne.lissen.common.ColorScheme

private val LightColorScheme = lightColorScheme(
    primary = FoxOrange,
    secondary = Dark,
    tertiary = FoxOrange,
    background = LightBackground,
    surface = LightBackground,
    surfaceContainer = Color(0xFFEEEEEE),
)

private val DarkColorScheme = darkColorScheme(
    primary = FoxOrange,
)

@Composable
fun LissenTheme(
    colorSchemePreference: ColorScheme,
    content: @Composable () -> Unit,
) {
    val isDarkTheme = when (colorSchemePreference) {
        ColorScheme.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        ColorScheme.LIGHT -> false
        ColorScheme.DARK -> true
    }

    val colors = if (isDarkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
