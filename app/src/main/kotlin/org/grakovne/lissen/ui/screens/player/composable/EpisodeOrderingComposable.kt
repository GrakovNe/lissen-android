package org.grakovne.lissen.ui.screens.player.composable

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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

/**
 * Sort options of the podcast episode list, laid out exactly like the downloads sheet:
 * centered title, list items with a leading icon and dividers in between. Tapping an option selects it
 * ascending, tapping the selected one flips the direction; the arrow on the selected row shows
 * the current direction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeOrderingComposable(
  current: EpisodeOrderingConfiguration?,
  onOrderingChanged: (EpisodeOrderingConfiguration) -> Unit,
  onDismissRequest: () -> Unit,
) {
  val context = LocalContext.current
  val ordering = current ?: EpisodeOrderingConfiguration.default

  LissenModalBottomSheet(
    containerColor = colorScheme.background,
    scrollable = false,
    onDismissRequest = onDismissRequest,
    content = {
      Column(
        modifier =
          Modifier
            .testTag("episodeOrderingSheet")
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = stringResource(R.string.library_quick_settings_sort_title),
          style = typography.bodyLarge,
        )

        Spacer(modifier = Modifier.height(8.dp))

        EpisodeOrderingOption.entries.forEachIndexed { index, option ->
          val isSelected = ordering.option == option

          ListItem(
            leadingContent = {
              Icon(
                imageVector = option.icon(),
                contentDescription = null,
                tint = colorScheme.onBackground,
                modifier = Modifier.size(24.dp),
              )
            },
            headlineContent = {
              Text(
                text = option.toLocalizedName(context),
                style = typography.bodyMedium,
                color = colorScheme.onBackground,
              )
            },
            trailingContent = {
              if (isSelected) {
                Icon(
                  imageVector =
                    when (ordering.direction) {
                      ASCENDING -> Icons.Outlined.ArrowUpward
                      DESCENDING -> Icons.Outlined.ArrowDownward
                    },
                  contentDescription = null,
                  tint = colorScheme.onBackground,
                  modifier = Modifier.size(20.dp),
                )
              }
            },
            modifier =
              Modifier
                .fillMaxWidth()
                .testTag("episodeOrderingOption_${option.name}")
                .clickable {
                  val newDirection =
                    when {
                      !isSelected -> ASCENDING
                      ordering.direction == ASCENDING -> DESCENDING
                      else -> ASCENDING
                    }
                  onOrderingChanged(EpisodeOrderingConfiguration(option = option, direction = newDirection))
                },
          )

          if (index < EpisodeOrderingOption.entries.lastIndex) {
            HorizontalDivider()
          }
        }
      }
    },
  )
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
