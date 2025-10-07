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
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.ui.screens.settings.composable.CommonSettingsItem
import org.grakovne.lissen.ui.screens.settings.composable.CommonSettingsMultiItemComposable
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
fun LibraryTypeAutoCacheSettingsComposable(
  viewModel: SettingsViewModel,
  enabled: Boolean,
) {
  val context = LocalContext.current
  var libraryTypeExpanded by remember { mutableStateOf(false) }
  val preferredDownloadOption by viewModel.preferredAutoDownloadOption.observeAsState()
  val preferredLibraryTypes by viewModel.preferredAutoDownloadLibraryTypes.observeAsState(LibraryType.meaningfulTypes)
  val libraryTypesState = LibraryType.meaningfulTypes.map { it to preferredLibraryTypes.contains(it) }

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
        text = stringResource(R.string.download_settings_library_type_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
        color =
          when (enabled) {
            true -> colorScheme.onBackground
            false -> colorScheme.onBackground.copy(alpha = 0.4f)
          },
      )
      Text(
        text =
          preferredLibraryTypes
            ?.map { it.toItem(context) }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { it.name }
            ?.lowercase()
            ?.replaceFirstChar { it.uppercase() }
            ?: context.getString(R.string.download_settings_library_type_no_items_selected),
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
      items = libraryTypesState.map { (type, state) -> type.toItem(context) to state },
      onDismissRequest = { libraryTypeExpanded = false },
      onItemChanged = { id, state ->
        LibraryType
          .entries
          .find { it.name == id }
          ?.let { viewModel.changeAutoDownloadLibraryType(it, state) }
      },
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
