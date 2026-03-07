package org.grakovne.lissen.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.grakovne.lissen.common.ColorScheme
import org.grakovne.lissen.ui.theme.LissenTheme

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun AppEdgeToEdgePreview() {
  LissenTheme(
    colorSchemePreference = ColorScheme.LIGHT,
    materialYouEnabled = false,
  ) {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center,
    ) {
      Text("Edge to Edge UI Preview")
    }
  }
}
