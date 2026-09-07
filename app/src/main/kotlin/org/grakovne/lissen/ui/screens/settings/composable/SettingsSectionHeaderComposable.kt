package org.grakovne.lissen.ui.screens.settings.composable

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SettingsSectionHeaderComposable(
  title: String,
  topPadding: Dp = 20.dp,
) {
  Text(
    text = title.uppercase(),
    style = typography.labelSmall,
    color = colorScheme.primary,
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(start = 24.dp, end = 24.dp, top = topPadding, bottom = 4.dp),
  )
}
