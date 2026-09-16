package org.grakovne.lissen.ui.screens.player.composable

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.common.EpisodeOrdering
import org.grakovne.lissen.common.EpisodeSortKey
import org.grakovne.lissen.common.selectKey
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.ui.components.LissenModalBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemSettingsSheet(
  ordering: EpisodeOrdering,
  onOrderingSelected: (EpisodeOrdering) -> Unit,
  onDismissRequest: () -> Unit,
) {
  val view = LocalView.current
  var sortExpanded by remember { mutableStateOf(false) }

  val ascendingDescription = stringResource(R.string.episode_sort_direction_ascending)
  val descendingDescription = stringResource(R.string.episode_sort_direction_descending)

  LissenModalBottomSheet(
    containerColor = colorScheme.background,
    onDismissRequest = onDismissRequest,
    content = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier
              .fillMaxWidth()
              .height(56.dp)
              .clickable(role = Role.Button) {
                withHaptic(view) { sortExpanded = !sortExpanded }
              }.padding(horizontal = 24.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.Sort,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
          )

          Spacer(modifier = Modifier.width(16.dp))

          Text(
            text = stringResource(R.string.player_sort_section),
            style = typography.bodyLarge,
            color = colorScheme.onSurface,
            modifier = Modifier.weight(1f),
          )

          Text(
            text = stringResource(ordering.key.shortLabelRes),
            style = typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )

          Spacer(modifier = Modifier.width(16.dp))

          Icon(
            imageVector =
              when (sortExpanded) {
                true -> Icons.Filled.KeyboardArrowUp
                false -> Icons.Filled.KeyboardArrowDown
              },
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
          )
        }

        AnimatedVisibility(visible = sortExpanded) {
          Column {
            SortOptions.forEach { option ->
              val isSelected = option.key == ordering.key

              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics {
                      selected = isSelected
                      stateDescription =
                        when {
                          isSelected.not() -> ""
                          ordering.ascending -> ascendingDescription
                          else -> descendingDescription
                        }
                    }.clickable(role = Role.Button) {
                      withHaptic(view) { onOrderingSelected(ordering.selectKey(option.key)) }
                    }.padding(start = 40.dp, end = 24.dp),
              ) {
                Icon(
                  imageVector = option.icon,
                  contentDescription = null,
                  tint =
                    when (isSelected) {
                      true -> colorScheme.onSurface
                      false -> colorScheme.onSurfaceVariant
                    },
                  modifier = Modifier.size(24.dp),
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                  text = stringResource(option.labelRes),
                  style = typography.bodyLarge,
                  color =
                    when (isSelected) {
                      true -> colorScheme.onSurface
                      false -> colorScheme.onSurfaceVariant
                    },
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )

                if (isSelected) {
                  Icon(
                    imageVector =
                      when (ordering.ascending) {
                        true -> Icons.Filled.ArrowUpward
                        false -> Icons.Filled.ArrowDownward
                      },
                    contentDescription = null,
                    tint = colorScheme.primary,
                  )
                }
              }
            }
          }
        }
      }
    },
  )
}

private data class SortOption(
  val key: EpisodeSortKey,
  @StringRes val labelRes: Int,
  val icon: ImageVector,
)

private val SortOptions =
  listOf(
    SortOption(EpisodeSortKey.PUBLISHED_AT, R.string.episode_sort_published_at, Icons.Outlined.CalendarToday),
    SortOption(EpisodeSortKey.TITLE, R.string.episode_sort_title, Icons.Outlined.SortByAlpha),
    SortOption(EpisodeSortKey.SEASON, R.string.episode_sort_season, Icons.Outlined.Layers),
    SortOption(EpisodeSortKey.EPISODE, R.string.episode_sort_episode, Icons.Outlined.Tag),
    SortOption(EpisodeSortKey.FILENAME, R.string.episode_sort_filename, Icons.Outlined.Description),
  )

@get:StringRes
private val EpisodeSortKey.shortLabelRes: Int
  get() =
    when (this) {
      EpisodeSortKey.PUBLISHED_AT -> R.string.episode_sort_published_at_short
      EpisodeSortKey.TITLE -> R.string.episode_sort_title_short
      EpisodeSortKey.SEASON -> R.string.episode_sort_season_short
      EpisodeSortKey.EPISODE -> R.string.episode_sort_episode_short
      EpisodeSortKey.FILENAME -> R.string.episode_sort_filename_short
    }
