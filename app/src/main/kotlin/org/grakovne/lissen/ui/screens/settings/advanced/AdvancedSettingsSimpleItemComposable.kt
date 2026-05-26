package org.grakovne.lissen.ui.screens.settings.advanced

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun AdvancedSettingsSimpleItemComposable(
  title: String,
  description: String,
  enabled: Boolean = true,
  onclick: () -> Unit,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .let { if (enabled) it.clickable { onclick() } else it }
        .padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(
      modifier = Modifier.weight(1f),
    ) {
      Text(
        text = title,
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = if (enabled) colorScheme.onBackground else colorScheme.onBackground.copy(alpha = 0.4f),
      )
      Text(
        text = description,
        style = typography.bodyMedium,
        color = if (enabled) colorScheme.onSurfaceVariant else colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
