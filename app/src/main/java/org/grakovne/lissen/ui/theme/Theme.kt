package org.grakovne.lissen.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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
    val view = LocalView.current
    val window = (view.context as? Activity)?.window

    val isDarkTheme = when (colorSchemePreference) {
        ColorScheme.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        ColorScheme.LIGHT -> false
        ColorScheme.DARK -> true
        ColorScheme.BLACK -> true
    }

    SideEffect {
        window?.let {
            WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !isDarkTheme
        }
    }

    val colors = when (isDarkTheme) {
        true -> {
            if (colorSchemePreference == ColorScheme.BLACK) {
                BlackColorScheme
            } else {
                DarkColorScheme
            }
        }
        false -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
