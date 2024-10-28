package org.grakovne.lissen.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@Composable
fun LissenTheme(
        colorScheme: ColorScheme,
    content: @Composable () -> Unit
) {

    LaunchedEffect(colorScheme) {
        println(colorScheme)
    }

    val systemUiController = rememberSystemUiController()

    systemUiController.setNavigationBarColor(
            color = backgroundColor,
            darkIcons = true
    )

    systemUiController.setStatusBarColor(
            color = backgroundColor,
            darkIcons = true
    )

    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
