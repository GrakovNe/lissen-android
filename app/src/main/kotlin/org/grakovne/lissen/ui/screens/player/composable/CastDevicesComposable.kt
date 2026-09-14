package org.grakovne.lissen.ui.screens.player.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.playback.cast.CastSessionState
import org.grakovne.lissen.ui.components.LissenModalBottomSheet
import org.grakovne.lissen.ui.screens.common.RequestLocalNetworkPermission
import org.grakovne.lissen.viewmodel.CastViewModel

@Composable
fun CastDevicesComposable(
  castViewModel: CastViewModel,
  onDismissRequest: () -> Unit,
) {
  val devices by castViewModel.devices.collectAsState()
  val discovering by castViewModel.discovering.collectAsState()
  val session by castViewModel.session.collectAsState()

  RequestLocalNetworkPermission { castViewModel.startDiscovery() }

  DisposableEffect(Unit) {
    castViewModel.startDiscovery()
    onDispose { castViewModel.stopDiscovery() }
  }

  LissenModalBottomSheet(
    containerColor = colorScheme.background,
    scrollable = false,
    onDismissRequest = onDismissRequest,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
          .padding(bottom = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = stringResource(R.string.cast_sheet_title),
          style = typography.bodyLarge,
        )

        if (discovering) {
          Spacer(modifier = Modifier.width(12.dp))
          CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = colorScheme.primary,
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
          DeviceRow(
            icon = Icons.Outlined.PhoneAndroid,
            title = stringResource(R.string.cast_this_device),
            subtitle = null,
            selected = session == null,
            onClick = {
              castViewModel.disconnect()
              onDismissRequest()
            },
          )
          HorizontalDivider(color = colorScheme.outlineVariant)
        }

        items(devices, key = { it.id }) { device ->
          val current = session?.takeIf { it.device.id == device.id }

          DeviceRow(
            icon = Icons.Outlined.Speaker,
            title = device.name,
            subtitle =
              when (current?.state) {
                CastSessionState.CONNECTING -> {
                  stringResource(R.string.cast_connecting)
                }

                CastSessionState.FAILED -> {
                  current.message?.let { "${stringResource(R.string.cast_connection_failed)}: $it" }
                    ?: stringResource(R.string.cast_connection_failed)
                }

                else -> {
                  listOfNotNull(stringResource(R.string.cast_protocol_dlna), device.model ?: device.host).joinToString(" · ")
                }
              },
            selected = current?.state == CastSessionState.CONNECTED,
            connecting = current?.state == CastSessionState.CONNECTING,
            onClick = {
              castViewModel.connect(device)
              onDismissRequest()
            },
          )
          HorizontalDivider(color = colorScheme.outlineVariant)
        }

        if (devices.isEmpty()) {
          item {
            Box(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .padding(vertical = 24.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = stringResource(if (discovering) R.string.cast_searching else R.string.cast_no_devices),
                style = typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DeviceRow(
  icon: ImageVector,
  title: String,
  subtitle: String?,
  selected: Boolean,
  connecting: Boolean = false,
  onClick: () -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(vertical = 12.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = if (selected) colorScheme.primary else colorScheme.onSurface,
    )

    Spacer(modifier = Modifier.width(16.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = if (selected) colorScheme.primary else colorScheme.onSurface,
      )

      subtitle?.let {
        Text(
          text = it,
          style = typography.bodySmall,
          color = colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    when {
      connecting -> {
        CircularProgressIndicator(
          modifier = Modifier.size(18.dp),
          strokeWidth = 2.dp,
          color = colorScheme.primary,
        )
      }

      selected -> {
        Icon(
          imageVector = Icons.Outlined.Check,
          contentDescription = null,
          tint = colorScheme.primary,
        )
      }
    }
  }
}
