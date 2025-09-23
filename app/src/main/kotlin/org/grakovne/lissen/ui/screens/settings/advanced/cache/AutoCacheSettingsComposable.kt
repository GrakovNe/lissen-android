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
import androidx.lifecycle.map
import org.grakovne.lissen.R
import org.grakovne.lissen.lib.domain.CurrentItemDownloadOption
import org.grakovne.lissen.lib.domain.DownloadOption
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.lib.domain.NumberItemDownloadOption
import org.grakovne.lissen.lib.domain.RemainingItemsDownloadOption
import org.grakovne.lissen.lib.domain.makeDownloadOption
import org.grakovne.lissen.lib.domain.makeId
import org.grakovne.lissen.ui.screens.common.makeText
import org.grakovne.lissen.ui.screens.settings.composable.CommonSettingsItem
import org.grakovne.lissen.ui.screens.settings.composable.CommonSettingsItemComposable
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
fun AutoCacheSettingsComposable(viewModel: SettingsViewModel) {
  val context = LocalContext.current
  var autoCacheExpanded by remember { mutableStateOf(false) }
  val preferredDownloadOption by viewModel.preferredAutoDownloadOption.observeAsState()

  val libraryType by viewModel
    .preferredLibrary
    .map { it?.type ?: LibraryType.LIBRARY }
    .observeAsState(LibraryType.LIBRARY)

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { autoCacheExpanded = true }
        .padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(
      modifier = Modifier.weight(1f),
    ) {
      Text(
        text = stringResource(R.string.settings_download_automatically_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
      )
      Text(
        text = preferredDownloadOption.toSettingsItem(context, libraryType).name,
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
      )
    }
  }

  if (autoCacheExpanded) {
    CommonSettingsItemComposable(
      items = DownloadOptions.map { it.toSettingsItem(context, libraryType) },
      selectedItem = preferredDownloadOption.toSettingsItem(context, libraryType),
      onDismissRequest = { autoCacheExpanded = false },
      onItemSelected = {
        it
          .id
          .makeDownloadOption()
          .let { viewModel.preferAutoDownloadOption(it) }
      },
    )
  }
}

private fun DownloadOption?.toSettingsItem(
  context: Context,
  libraryType: LibraryType,
): CommonSettingsItem =
  CommonSettingsItem(
    id = this.makeId(),
    name = this.makeText(context, libraryType),
    icon = null,
  )

private val DownloadOptions =
  listOf(
    null,
    CurrentItemDownloadOption,
    NumberItemDownloadOption(5),
    NumberItemDownloadOption(10),
    RemainingItemsDownloadOption,
  )
