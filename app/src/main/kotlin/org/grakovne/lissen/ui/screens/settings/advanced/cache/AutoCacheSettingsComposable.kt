package org.grakovne.lissen.ui.screens.settings.advanced.cache

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.CurrentItemDownloadOption
import org.grakovne.lissen.domain.DownloadOption
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.NumberItemDownloadOption
import org.grakovne.lissen.domain.RemainingItemsDownloadOption
import org.grakovne.lissen.domain.makeId
import org.grakovne.lissen.ui.screens.common.ChaptersCountStepper
import org.grakovne.lissen.ui.screens.common.makeText
import org.grakovne.lissen.ui.screens.settings.composable.CommonSettingsItem
import org.grakovne.lissen.viewmodel.SettingsViewModel

private const val MIN_CHAPTERS_COUNT = 1
private const val DEFAULT_CHAPTERS_COUNT = 5

@Composable
fun AutoCacheSettingsComposable(viewModel: SettingsViewModel) {
  val context = LocalContext.current
  var autoCacheExpanded by remember { mutableStateOf(false) }
  val preferredDownloadOption by viewModel.preferredAutoDownloadOption.collectAsState()

  val preferredLibrary by viewModel.preferredLibrary.collectAsState()
  val libraryType = preferredLibrary?.type ?: LibraryType.LIBRARY

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
    AutoCacheOptionsSheet(
      selectedOption = preferredDownloadOption,
      libraryType = libraryType,
      onOptionSelected = { viewModel.preferAutoDownloadOption(it) },
      onDismissRequest = { autoCacheExpanded = false },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoCacheOptionsSheet(
  selectedOption: DownloadOption?,
  libraryType: LibraryType,
  onOptionSelected: (DownloadOption?) -> Unit,
  onDismissRequest: () -> Unit,
) {
  val context = LocalContext.current

  var count by rememberSaveable {
    mutableIntStateOf(
      (selectedOption as? NumberItemDownloadOption)?.itemsNumber?.coerceAtLeast(MIN_CHAPTERS_COUNT)
        ?: DEFAULT_CHAPTERS_COUNT,
    )
  }

  val isNumberSelected = selectedOption is NumberItemDownloadOption

  ModalBottomSheet(
    containerColor = colorScheme.background,
    onDismissRequest = onDismissRequest,
    content = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .padding(horizontal = 16.dp),
      ) {
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
          item {
            AutoCacheOptionRow(
              text = stringResource(R.string.downloads_menu_download_option_disable),
              selected = selectedOption == null,
              onClick = { onOptionSelected(null) },
            )
            HorizontalDivider()
          }

          item {
            AutoCacheOptionRow(
              text = CurrentItemDownloadOption.makeText(context, libraryType),
              selected = selectedOption is CurrentItemDownloadOption,
              onClick = { onOptionSelected(CurrentItemDownloadOption) },
            )
            HorizontalDivider()
          }

          item {
            AutoCacheOptionRow(
              text =
                when (libraryType) {
                  LibraryType.LIBRARY -> stringResource(R.string.downloads_menu_download_option_next_chapters_label)
                  LibraryType.PODCAST -> stringResource(R.string.downloads_menu_download_option_next_episodes_label)
                  LibraryType.UNKNOWN -> stringResource(R.string.downloads_menu_download_option_next_items_label)
                },
              selected = isNumberSelected,
              onClick = { onOptionSelected(NumberItemDownloadOption(count)) },
              trailingContent = {
                ChaptersCountStepper(
                  count = count,
                  minCount = MIN_CHAPTERS_COUNT,
                  numberColor = colorScheme.onBackground,
                  onCountChanged = {
                    count = it
                    if (isNumberSelected) {
                      onOptionSelected(NumberItemDownloadOption(it))
                    }
                  },
                )
              },
            )
            HorizontalDivider()
          }

          item {
            AutoCacheOptionRow(
              text = RemainingItemsDownloadOption.makeText(context, libraryType),
              selected = selectedOption is RemainingItemsDownloadOption,
              onClick = { onOptionSelected(RemainingItemsDownloadOption) },
            )
          }
        }
      }
    },
  )
}

@Composable
private fun AutoCacheOptionRow(
  text: String,
  selected: Boolean,
  onClick: () -> Unit,
  trailingContent: (@Composable () -> Unit)? = null,
) {
  ListItem(
    headlineContent = {
      Row { Text(text) }
    },
    trailingContent = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
      ) {
        trailingContent?.invoke()
        if (selected) {
          Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
          )
        }
      }
    },
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(
          indication = null,
          interactionSource = remember { MutableInteractionSource() },
          onClick = onClick,
        ),
  )
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
