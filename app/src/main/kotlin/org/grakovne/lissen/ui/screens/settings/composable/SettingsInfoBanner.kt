package org.grakovne.lissen.ui.screens.settings.composable

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsInfoBanner(
  icon: ImageVector,
  text: String,
  ctaText: String,
  onAction: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = colorScheme.primary,
      modifier = Modifier.padding(end = 12.dp),
    )

    Text(
      text = text,
      style = typography.bodyMedium.copy(color = colorScheme.onSurface),
      modifier = Modifier.weight(1f),
    )

    TextButton(onClick = onAction) {
      Text(
        text = ctaText,
        style =
          typography.bodyMedium.copy(
            color = colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
          ),
      )
    }
  }
}
