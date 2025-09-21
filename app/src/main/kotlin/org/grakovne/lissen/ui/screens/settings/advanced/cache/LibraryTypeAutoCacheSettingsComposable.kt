package org.grakovne.lissen.ui.screens.settings.advanced.cache

import android.content.Context
import android.util.Log
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
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.ui.screens.settings.composable.CommonSettingsItem
import org.grakovne.lissen.ui.screens.settings.composable.CommonSettingsMultiItemComposable
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
fun LibraryTypeAutoCacheSettingsComposable(viewModel: SettingsViewModel) {
  val context = LocalContext.current
  var libraryTypeExpanded by remember { mutableStateOf(false) }
  val preferredDownloadOption by viewModel.preferredAutoDownloadOption.observeAsState()
  val preferredNetworkType by remember { mutableStateOf(LibraryType.LIBRARY) }

  val enabled = preferredDownloadOption != null

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(enabled = preferredDownloadOption != null) { libraryTypeExpanded = true }
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

  if (libraryTypeExpanded) {
    CommonSettingsMultiItemComposable(
      items =
        listOf(
          LibraryType.LIBRARY.toItem(context) to true,
          LibraryType.PODCAST.toItem(context) to true,
        ),
      onDismissRequest = { libraryTypeExpanded = false },
      onItemChanged = { f, ff -> Log.d("HERE", "$f changed to $ff") },
    )
  }
}

private fun LibraryType.toItem(context: Context): CommonSettingsItem {
  val id = this.name
  val name =
    when (this) {
      LibraryType.LIBRARY -> context.getString(R.string.library_type_library)
      LibraryType.PODCAST -> context.getString(R.string.library_type_podcast)
      LibraryType.UNKNOWN -> context.getString(R.string.library_type_unknown)
    }

  return CommonSettingsItem(id, name, null)
}
