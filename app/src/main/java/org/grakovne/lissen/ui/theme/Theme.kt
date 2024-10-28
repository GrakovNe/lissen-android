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

val LightBackGroundColor = Color(0xFFFAFAFA)

private val LightColorScheme = lightColorScheme(
        primary = FoxOrange,
        secondary = Dark,
        tertiary = FoxOrange,
        background = LightBackGroundColor,
        surface = LightBackGroundColor
)

private val DarkColorScheme = darkColorScheme(
        primary = FoxOrange
)

@Composable
fun LissenTheme(
        colorScheme: ColorScheme,
        content: @Composable () -> Unit
) {

    MaterialTheme(
            colorScheme = colorScheme.toColorScheme(isSystemInDarkTheme()),
            content = {
                val systemUiController = rememberSystemUiController()
                val backgroundColor = MaterialTheme.colorScheme.background

                SideEffect {
                    systemUiController.setNavigationBarColor(
                            color = backgroundColor,
                            darkIcons = true
                    )
                    systemUiController.setStatusBarColor(
                            color = backgroundColor,
                            darkIcons = true
                    )
                }

                content()
            }
    )
}

private fun ColorScheme.toColorScheme(systemDarkTheme: Boolean) =
        when (this) {
            ColorScheme.FOLLOW_SYSTEM -> when (systemDarkTheme) {
                true -> DarkColorScheme
                false -> LightColorScheme
            }

            ColorScheme.LIGHT -> LightColorScheme
            ColorScheme.DARK -> DarkColorScheme
        }
