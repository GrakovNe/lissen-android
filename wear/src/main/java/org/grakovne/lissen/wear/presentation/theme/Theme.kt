package org.grakovne.lissen.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

@Composable
fun LissenTheme(
  content: @Composable () -> Unit
) {
  val dynamicColorScheme = dynamicColorScheme(LocalContext.current)

  MaterialTheme(
    content = content,
    colorScheme = dynamicColorScheme ?: ColorScheme()
  )
}
