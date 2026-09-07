package org.grakovne.lissen.ui.screens.settings.advanced.cache

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.StoragePath
import org.grakovne.lissen.ui.screens.settings.composable.CommonSettingsItem
import org.grakovne.lissen.ui.screens.settings.composable.CommonSettingsItemComposable
import org.grakovne.lissen.ui.screens.settings.composable.ConfirmationBottomSheetComposable
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
fun DownloadStorageSettingsComposable(viewModel: SettingsViewModel) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var storageExpanded by remember { mutableStateOf(false) }
  var pendingStorage by remember { mutableStateOf<StoragePath?>(null) }
  val downloadStorage by viewModel.downloadStorage.collectAsState()
  val downloadStoragePath by viewModel.downloadStoragePath.collectAsState()
  val availableStorages by viewModel.availableStorages.collectAsState()
  val clearing by viewModel.downloadStorageClearing.collectAsState()

  LaunchedEffect(storageExpanded) { viewModel.fetchDownloadStorages() }

  val selectedStorage = downloadStoragePath ?: availableStorages.firstOrNull()
  val enabled =
    clearing.not() &&
      availableStorages.isNotEmpty() &&
      (availableStorages.size > 1 || availableStorages.single() != selectedStorage)

  val switchStorage: (StoragePath) -> Unit = { storage ->
    scope.launch {
      if (viewModel.preferDownloadStorage(storage).not()) {
        Toast
          .makeText(
            context,
            context.getString(R.string.download_settings_storage_switch_failed_toast),
            Toast.LENGTH_SHORT,
          ).show()
      }
    }
  }

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(enabled = enabled) { storageExpanded = true }
        .padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(
      modifier = Modifier.weight(1f),
    ) {
      Text(
        text = stringResource(R.string.download_settings_storage_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
        color =
          when (enabled) {
            true -> colorScheme.onBackground
            false -> colorScheme.onBackground.copy(alpha = 0.4f)
          },
      )
      Text(
        text = downloadStorage?.name.orEmpty(),
        style = typography.bodyMedium,
        color =
          when (enabled) {
            true -> colorScheme.onSurfaceVariant
            false -> colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
          },
      )
    }
  }

  if (storageExpanded) {
    val items =
      remember(availableStorages, selectedStorage) {
        val available = availableStorages.map { it.toItem() }

        when (selectedStorage == null || available.any { it.id == selectedStorage.path }) {
          true -> available
          false -> available + selectedStorage.toUnavailableItem(context)
        }
      }

    CommonSettingsItemComposable(
      items = items,
      selectedItem = items.find { it.id == selectedStorage?.path },
      onDismissRequest = { storageExpanded = false },
      onItemSelected = { item ->
        storageExpanded = false

        availableStorages
          .find { it.path == item.id }
          ?.takeIf { it != selectedStorage }
          ?.let { storage ->
            when (storage == downloadStorage) {
              true -> switchStorage(storage)
              false -> pendingStorage = storage
            }
          }
      },
    )
  }

  pendingStorage?.let { storage ->
    ConfirmationBottomSheetComposable(
      message = stringResource(R.string.download_settings_storage_confirmation_message),
      confirmLabel = stringResource(R.string.download_settings_storage_confirm),
      onConfirm = {
        pendingStorage = null
        switchStorage(storage)
      },
      onDismissRequest = { pendingStorage = null },
    )
  }
}

private fun StoragePath.toItem(): CommonSettingsItem =
  CommonSettingsItem(
    id = path,
    name = name,
    icon = null,
  )

private fun StoragePath.toUnavailableItem(context: Context): CommonSettingsItem =
  CommonSettingsItem(
    id = path,
    name = name,
    icon = null,
    description = context.getString(R.string.download_settings_storage_unavailable),
    enabled = false,
  )
