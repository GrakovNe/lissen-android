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
    tertiaryContainer = LightBackground,
    background = LightBackground,
    surface = LightBackground,
    surfaceContainer = Color(0xFFEEEEEE),
)

private val DarkColorScheme = darkColorScheme(
    primary = FoxOrangeDimmed,
    tertiaryContainer = Color(0xFF1A1A1A),
)

private val BlackColorScheme = darkColorScheme(
    primary = FoxOrangeDimmed,
    background = Black,
    surface = Black,
    tertiaryContainer = Black,
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
        ColorScheme.BLACK -> true
    }

    val colors = when (isDarkTheme) {
        true -> {
            if (colorSchemePreference == ColorScheme.BLACK) {
                BlackColorScheme
            } else {
                DarkColorScheme
            }
        }

        false -> {
            LightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
