package org.grakovne.lissen.ui.screens.settings.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.channel.audiobookshelf.HostType
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsComposable(
  navController: AppNavigationService,
  viewModel: SettingsViewModel,
) {
  var connectionInfoExpanded by remember { mutableStateOf(false) }

  val localUrls by viewModel.localUrls.observeAsState(emptyList())
  val host by viewModel.host.observeAsState()

  LaunchedEffect(Unit) {
    viewModel.refreshConnectionInfo()
  }

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { connectionInfoExpanded = true }
        .padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.settings_screen_server_connection),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
      )

      host?.let {
        Text(
          text = it.url,
          style = typography.bodyMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    IconButton(
      onClick = {
        navController.showLogin()
        viewModel.logout()
      },
    ) {
      Icon(
        imageVector = Icons.Outlined.Delete,
        contentDescription = "Logout",
      )
    }
  }

  if (connectionInfoExpanded) {
    ModalBottomSheet(
      containerColor = MaterialTheme.colorScheme.background,
      onDismissRequest = { connectionInfoExpanded = false },
      content = {
        Column(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(bottom = 16.dp)
              .padding(horizontal = 16.dp),
        ) {
          viewModel.username.value?.let {
            InfoRow(
              label = stringResource(R.string.settings_screen_connected_as_title),
              value = it,
            )

            HorizontalDivider()
          }

          viewModel.serverVersion.value?.let {
            InfoRow(
              label = stringResource(R.string.settings_screen_server_version),
              value = it,
            )
          }

          if (localUrls.isNotEmpty() && host != null) {
            val connectionType =
              when (host?.type) {
                HostType.INTERNAL -> stringResource(R.string.settings_screen_connection_local)
                HostType.EXTERNAL -> stringResource(R.string.settings_screen_connection_external)
                else -> ""
              }

            HorizontalDivider()

            InfoRow(
              label = stringResource(R.string.settings_screen_connection_type),
              value = connectionType,
            )
          }
        }
      },
    )
  }
}

@Composable
fun InfoRow(
  label: String,
  value: String,
) {
  ListItem(
    headlineContent = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(text = label)
        Text(text = value)
      }
    },
  )
}
