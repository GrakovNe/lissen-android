package org.grakovne.lissen.ui.screens.player.composable

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.common.EpisodeOrderingConfiguration
import org.grakovne.lissen.common.EpisodeOrderingOption
import org.grakovne.lissen.common.LibraryOrderingDirection.ASCENDING
import org.grakovne.lissen.common.LibraryOrderingDirection.DESCENDING
import org.grakovne.lissen.ui.components.LissenModalBottomSheet
import org.grakovne.lissen.ui.components.SettingsOptionRow

/**
 * Sort options of the podcast episode list, opened from the sort icon next to the list title.
 * Tapping an option selects it ascending; tapping the selected one flips the direction.
 */
@Composable
fun EpisodeOrderingComposable(
  current: EpisodeOrderingConfiguration?,
  onOrderingChanged: (EpisodeOrderingConfiguration) -> Unit,
  onDismissRequest: () -> Unit,
) {
  val context = LocalContext.current
  val ordering = current ?: EpisodeOrderingConfiguration.default

  LissenModalBottomSheet(
    containerColor = colorScheme.surface,
    scrollable = false,
    onDismissRequest = onDismissRequest,
  ) {
    Column(
      modifier =
        Modifier
          .testTag("episodeOrderingSheet")
          .fillMaxWidth()
          .padding(bottom = 8.dp),
    ) {
      Text(
        text = stringResource(R.string.library_quick_settings_sort_title),
        style = typography.bodyLarge,
        color = colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
      )

      Spacer(modifier = Modifier.height(4.dp))

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
          modifier = Modifier.testTag("episodeOrderingOption_${option.name}"),
          onClick = {
            val newDirection =
              when {
                !isSelected -> ASCENDING
                ordering.direction == ASCENDING -> DESCENDING
                else -> ASCENDING
              }
            onOrderingChanged(EpisodeOrderingConfiguration(option = option, direction = newDirection))
          },
        )
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

fun EpisodeOrderingOption.toLocalizedName(context: Context): String =
  when (this) {
    EpisodeOrderingOption.PUBLISHED_AT -> context.getString(R.string.episode_ordering_published_at_option)
    EpisodeOrderingOption.TITLE -> context.getString(R.string.settings_screen_library_ordering_title_option)
    EpisodeOrderingOption.SEASON -> context.getString(R.string.episode_ordering_season_option)
    EpisodeOrderingOption.EPISODE -> context.getString(R.string.episode_ordering_episode_option)
    EpisodeOrderingOption.FILE_NAME -> context.getString(R.string.episode_ordering_file_name_option)
  }
