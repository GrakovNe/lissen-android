package org.grakovne.lissen.ui.screens.player.composable

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import org.grakovne.lissen.R
import org.grakovne.lissen.common.EpisodeOrderingConfiguration
import org.grakovne.lissen.common.EpisodeOrderingOption
import org.grakovne.lissen.common.LibraryOrderingDirection.ASCENDING
import org.grakovne.lissen.common.LibraryOrderingDirection.DESCENDING
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.ui.components.LissenModalBottomSheet
import org.grakovne.lissen.ui.components.SettingsActionRow
import org.grakovne.lissen.ui.components.SettingsOptionRow
import org.grakovne.lissen.ui.components.SettingsPickerHeaderRow

@Composable
fun PlayerSettingsComposable(
  libraryType: LibraryType,
  episodeOrdering: EpisodeOrderingConfiguration?,
  onEpisodeOrderingChanged: (EpisodeOrderingConfiguration) -> Unit,
  onMarkAsFinished: () -> Unit,
  onDismissRequest: () -> Unit,
) {
  val context = LocalContext.current
  val view = LocalView.current

  var sortExpanded by remember { mutableStateOf(false) }

  LissenModalBottomSheet(
    containerColor = colorScheme.surface,
    scrollable = false,
    onDismissRequest = onDismissRequest,
  ) {
    Column(
      modifier =
        Modifier
          .testTag("playerSettingsSheet")
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
    ) {
      when (libraryType) {
        LibraryType.PODCAST -> {
          val ordering = episodeOrdering ?: EpisodeOrderingConfiguration.default

          SettingsPickerHeaderRow(
            label = stringResource(R.string.library_quick_settings_sort_title),
            icon = Icons.AutoMirrored.Outlined.Sort,
            value = ordering.option.toLocalizedName(context),
            expanded = sortExpanded,
            modifier = Modifier.testTag("playerSortingHeader"),
            onClick = { withHaptic(view) { sortExpanded = !sortExpanded } },
          )

          AnimatedVisibility(visible = sortExpanded) {
            Column {
              EpisodeOrderingOption.entries.forEach { option ->
                val isSelected = ordering.option == option
                SettingsOptionRow(
                  title = option.toLocalizedName(context),
                  icon = option.icon(),
                  selected = isSelected,
                  trailing =
                    when (ordering.direction) {
                      ASCENDING -> Icons.Outlined.ArrowUpward
                      DESCENDING -> Icons.Outlined.ArrowDownward
                    },
                  modifier = Modifier.testTag("playerSortingOption_${option.name}"),
                  onClick = {
                    val newDirection =
                      when {
                        !isSelected -> ASCENDING
                        ordering.direction == ASCENDING -> DESCENDING
                        else -> ASCENDING
                      }
                    onEpisodeOrderingChanged(EpisodeOrderingConfiguration(option = option, direction = newDirection))
                  },
                )
              }
            }
          }
        }

        LibraryType.LIBRARY, LibraryType.UNKNOWN -> {
          SettingsActionRow(
            title = stringResource(R.string.player_settings_mark_as_finished),
            icon = Icons.Outlined.DoneAll,
            modifier = Modifier.testTag("playerMarkAsFinished"),
            onClick = onMarkAsFinished,
          )
        }
      }
    }
  }
}

private fun EpisodeOrderingOption.icon(): ImageVector =
  when (this) {
    EpisodeOrderingOption.PUBLISHED_AT -> Icons.Outlined.CalendarToday
    EpisodeOrderingOption.TITLE -> Icons.Outlined.SortByAlpha
    EpisodeOrderingOption.SEASON -> Icons.Outlined.Layers
    EpisodeOrderingOption.EPISODE -> Icons.Outlined.Tag
    EpisodeOrderingOption.FILE_NAME -> Icons.Outlined.InsertDriveFile
  }

private fun EpisodeOrderingOption.toLocalizedName(context: Context): String =
  when (this) {
    EpisodeOrderingOption.PUBLISHED_AT -> context.getString(R.string.episode_ordering_published_at_option)
    EpisodeOrderingOption.TITLE -> context.getString(R.string.settings_screen_library_ordering_title_option)
    EpisodeOrderingOption.SEASON -> context.getString(R.string.episode_ordering_season_option)
    EpisodeOrderingOption.EPISODE -> context.getString(R.string.episode_ordering_episode_option)
    EpisodeOrderingOption.FILE_NAME -> context.getString(R.string.episode_ordering_file_name_option)
  }
