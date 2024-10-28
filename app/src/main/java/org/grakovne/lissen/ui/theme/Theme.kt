package org.grakovne.lissen.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import org.grakovne.lissen.common.ColorScheme

val backgroundColor = Color(0xFFFAFAFA)

private val LightColorScheme = lightColorScheme(
        primary = FoxOrange,
        secondary = Dark,
        tertiary = FoxOrange,
        background = backgroundColor,
        surface = backgroundColor
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
            colorScheme = colorScheme.getScheme(),
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

private fun ColorScheme.getScheme(): androidx.compose.material3.ColorScheme {
   return when(this) {
        ColorScheme.FOLLOW_SYSTEM -> LightColorScheme
        ColorScheme.LIGHT ->LightColorScheme
        ColorScheme.DARK -> DarkColorScheme
    }
}
