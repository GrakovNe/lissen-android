package org.grakovne.lissen.ui.screens.settings.advanced.cache

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.common.NetworkTypeAutoCache
import org.grakovne.lissen.ui.screens.settings.composable.CommonSettingsItem
import org.grakovne.lissen.ui.screens.settings.composable.CommonSettingsItemComposable
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
fun NetworkTypeAutoCacheSettingsComposable(viewModel: SettingsViewModel) {
  val context = LocalContext.current
  var networkTypeExpanded by remember { mutableStateOf(false) }
  val preferredDownloadOption by viewModel.preferredAutoDownloadOption.observeAsState()
  val preferredNetworkType by viewModel.preferredAutoDownloadNetworkType.observeAsState()

  val enabled = preferredDownloadOption != null

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(enabled = preferredDownloadOption != null) { networkTypeExpanded = true }
        .padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(
      modifier = Modifier.weight(1f),
    ) {
      Text(
        text = stringResource(R.string.download_settings_network_type_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
        color =
          when (enabled) {
            true -> colorScheme.onBackground
            false -> colorScheme.onBackground.copy(alpha = 0.4f)
          },
      )
      Text(
        text = preferredNetworkType?.toItem(context)?.name ?: "",
        style = typography.bodyMedium,
        color =
          when (enabled) {
            true -> colorScheme.onSurfaceVariant
            false -> colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
          },
      )
    }
  }

  if (networkTypeExpanded) {
    CommonSettingsItemComposable(
      items =
        listOf(
          NetworkTypeAutoCache.WIFI_ONLY.toItem(context),
          NetworkTypeAutoCache.WIFI_OR_CELLULAR.toItem(context),
        ),
      selectedItem = preferredNetworkType?.toItem(context),
      onDismissRequest = { networkTypeExpanded = false },
      onItemSelected = { item ->
        NetworkTypeAutoCache
          .entries
          .find { it.name == item.id }
          ?.let { viewModel.preferAutoDownloadNetworkType(it) }
      },
    )
  }
}

private fun NetworkTypeAutoCache.toItem(context: Context): CommonSettingsItem {
  val id = this.name
  val name =
    when (this) {
      NetworkTypeAutoCache.WIFI_ONLY -> context.getString(R.string.wifi_only_settings_option)
      NetworkTypeAutoCache.WIFI_OR_CELLULAR -> context.getString(R.string.wifi_or_cellular_settings_option)
    }

  return CommonSettingsItem(id, name, null)
}
