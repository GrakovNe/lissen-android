package org.grakovne.lissen.ui.screens.settings.advanced

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R

@Composable
fun ClientCertificateSettingItemComposable(
  alias: String?,
  onSelect: () -> Unit,
  onClear: () -> Unit,
) {
  val isConfigured = alias != null
  val description =
    if (isConfigured) {
      stringResource(R.string.settings_screen_client_cert_configured, alias!!)
    } else {
      stringResource(R.string.settings_screen_client_cert_not_configured)
    }

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { onSelect() }
        .padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.settings_screen_client_cert_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = description,
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    if (isConfigured) {
      IconButton(onClick = { onClear() }) {
        Icon(
          imageVector = Icons.Outlined.Close,
          contentDescription = stringResource(R.string.settings_screen_client_cert_clear_action),
        )
      }
    } else {
      IconButton(onClick = { onSelect() }) {
        Icon(
          imageVector = Icons.Outlined.Security,
          contentDescription = stringResource(R.string.settings_screen_client_cert_select_action),
        )
      }
    }
  }
}
