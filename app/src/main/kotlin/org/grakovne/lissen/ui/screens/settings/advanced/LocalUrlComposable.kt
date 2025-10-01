package org.grakovne.lissen.ui.screens.settings.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.lib.domain.connection.LocalUrl

@Composable
fun LocalUrlComposable(
  enabled: Boolean,
  url: LocalUrl,
  onChanged: (LocalUrl) -> Unit,
  onDelete: (LocalUrl) -> Unit,
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp),
  ) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .background(colorScheme.background),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
        modifier = Modifier.weight(1f),
      ) {
        OutlinedTextField(
          value = url.ssid,
          enabled = enabled,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
          onValueChange = { onChanged(url.copy(ssid = it, route = url.route)) },
          label = { Text(stringResource(R.string.local_url_hint_ssid_name)) },
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(bottom = 12.dp),
        )

        OutlinedTextField(
          enabled = enabled,
          value = url.route,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
          onValueChange = { onChanged(url.copy(ssid = url.ssid, route = it)) },
          label = { Text(stringResource(R.string.hint_server_url_input)) },
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth(),
        )
      }

      IconButton(
        enabled = enabled,
        onClick = { onDelete(url) },
      ) {
        Icon(
          imageVector = Icons.Default.DeleteOutline,
          contentDescription = null,
          tint =
            when (enabled) {
              true -> colorScheme.error
              false -> colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
          modifier = Modifier.size(32.dp),
        )
      }
    }
  }
}
