package org.grakovne.lissen.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import org.grakovne.lissen.common.ColorScheme

//val LightBackGroundColor = Color(0xFFFAFAFA)

private val LightColorScheme = lightColorScheme(
        primary = FoxOrange,
        secondary = Dark,
        tertiary = FoxOrange,
        background = Color.Black,
        surface = Color.Black
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

    val systemUiController = rememberSystemUiController()
    val backgroundColor = colors.background

    SideEffect {
        systemUiController.setNavigationBarColor(
                color = Color.Black,
                darkIcons = !isDarkTheme
        )
        systemUiController.setStatusBarColor(
                color = Color.Black,
                darkIcons = !isDarkTheme
        )
    }

    MaterialTheme(
            colorScheme = colors,
            content = content
    )
}