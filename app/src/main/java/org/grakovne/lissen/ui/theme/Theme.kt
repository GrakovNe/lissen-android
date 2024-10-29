package org.grakovne.lissen.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import org.grakovne.lissen.common.ColorScheme

private val LightColorScheme = lightColorScheme(
    primary = FoxOrange,
    secondary = Dark,
    tertiary = FoxOrange,
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFEEEEEE)
)

private val DarkColorScheme = darkColorScheme(
    primary = FoxOrange
)

@Composable
fun LissenTheme(
    colorSchemePreference: ColorScheme,
    content: @Composable () -> Unit
) {
    val isDarkTheme = when (colorSchemePreference) {
        ColorScheme.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        ColorScheme.LIGHT -> false
        ColorScheme.DARK -> true
    }

    val colors = if (isDarkTheme) DarkColorScheme else LightColorScheme
    val itemAccented = if (isDarkTheme) Color(0xFF444444) else Color(0xFFEEEEEE)
    val systemUiController = rememberSystemUiController()

    SideEffect {
        systemUiController.setNavigationBarColor(
            color = colors.background,
            darkIcons = !isDarkTheme
        )
        systemUiController.setStatusBarColor(
            color = colors.background,
            darkIcons = !isDarkTheme
        )
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
