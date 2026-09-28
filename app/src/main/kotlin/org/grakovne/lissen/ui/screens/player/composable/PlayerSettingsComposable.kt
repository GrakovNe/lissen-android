package org.grakovne.lissen.ui.screens.player.composable

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.grakovne.lissen.ui.components.ApplicationSettingsItemComposable
import org.grakovne.lissen.ui.components.LissenModalBottomSheet
import org.grakovne.lissen.ui.components.SettingsOptionRow
import org.grakovne.lissen.ui.components.SettingsPickerRow
import org.grakovne.lissen.ui.components.slider.IntroOutroSlider
import org.grakovne.lissen.ui.extensions.formatTime
import org.grakovne.lissen.ui.extensions.spokenDuration
import org.grakovne.lissen.ui.icons.SkipEdges
import org.grakovne.lissen.ui.navigation.AppNavigationService

/**
 * The player's counterpart of the library quick settings, in the same sheet layout.
 *
 * Episode ordering (podcasts only): tapping an option selects it ascending, tapping it again flips
 * the direction; the picker is inert while the queue is rebuilt. Auto-skip: a ruler with the intro
 * and outro lengths, for now a UI-only mock whose values live in the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsComposable(
  ordering: EpisodeOrderingConfiguration?,
  orderingVisible: Boolean,
  orderingEnabled: Boolean,
  onOrderingChanged: (EpisodeOrderingConfiguration) -> Unit,
  onDismissRequest: () -> Unit,
  navController: AppNavigationService,
) {
  val context = LocalContext.current
  val current = ordering ?: EpisodeOrderingConfiguration.default

  var sortExpanded by remember { mutableStateOf(false) }

  // UI-only mock: the values live in the sheet until the playback side exists
  var skipExpanded by remember { mutableStateOf(false) }
  var introSeconds by remember { mutableIntStateOf(0) }
  var outroSeconds by remember { mutableIntStateOf(0) }

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
      if (orderingVisible) {
        SettingsPickerRow(
          label = stringResource(R.string.library_quick_settings_sort_title),
          icon = Icons.AutoMirrored.Outlined.Sort,
          value = current.option.toLocalizedName(context),
          expanded = sortExpanded,
          enabled = orderingEnabled,
          modifier = Modifier.testTag("episodeOrderingPicker"),
          onClick = { sortExpanded = !sortExpanded },
        )

        AnimatedVisibility(visible = sortExpanded && orderingEnabled) {
          Column {
            EpisodeOrderingOption.entries.forEach { option ->
              val isSelected = current.option == option
              SettingsOptionRow(
                title = option.toLocalizedName(context),
                icon = option.icon(),
                selected = isSelected,
                trailing =
                  when (current.direction) {
                    ASCENDING -> Icons.Outlined.ArrowUpward
                    DESCENDING -> Icons.Outlined.ArrowDownward
                  },
                modifier = Modifier.testTag("episodeOrderingOption_${option.name}"),
                onClick = {
                  val newDirection =
                    when {
                      !isSelected -> ASCENDING
                      current.direction == ASCENDING -> DESCENDING
                      else -> ASCENDING
                    }
                  onOrderingChanged(EpisodeOrderingConfiguration(option = option, direction = newDirection))
                },
              )
            }
          }
        }
      }

      SettingsPickerRow(
        label = stringResource(R.string.player_settings_auto_skip),
        icon = SkipEdges,
        value = skipSummary(introSeconds, outroSeconds),
        expanded = skipExpanded,
        modifier = Modifier.testTag("introOutroPicker"),
        onClick = { skipExpanded = !skipExpanded },
      )

      AnimatedVisibility(visible = skipExpanded) {
        IntroOutroSlider(
          introSeconds = introSeconds,
          outroSeconds = outroSeconds,
          stateDescription = skipSummary(introSeconds, outroSeconds, spoken = true),
          modifier =
            Modifier
              .padding(horizontal = 16.dp, vertical = 8.dp)
              .testTag("introOutroSlider"),
          onUpdate = { intro, outro ->
            introSeconds = intro
            outroSeconds = outro
          },
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      HorizontalDivider(
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 8.dp),
      )

      ApplicationSettingsItemComposable(
        onClicked = {
          onDismissRequest()
          navController.showSettings()
        },
      )
    }
  }
}

/** "Intro 01:20 · Outro 02:25", or the same read out in words for accessibility. */
@Composable
private fun skipSummary(
  introSeconds: Int,
  outroSeconds: Int,
  spoken: Boolean = false,
): String {
  val intro = stringResource(R.string.player_settings_intro_value, if (spoken) spokenDuration(introSeconds) else introSeconds.formatTime())
  val outro = stringResource(R.string.player_settings_outro_value, if (spoken) spokenDuration(outroSeconds) else outroSeconds.formatTime())
  return when {
    introSeconds == 0 && outroSeconds == 0 -> stringResource(R.string.player_settings_skip_disabled)
    outroSeconds == 0 -> intro
    introSeconds == 0 -> outro
    else -> if (spoken) "$intro, $outro" else "$intro \u00b7 $outro"
  }
}

private fun EpisodeOrderingOption.icon(): ImageVector =
  when (this) {
    EpisodeOrderingOption.PUBLISHED_AT -> Icons.Outlined.CalendarToday
    EpisodeOrderingOption.TITLE -> Icons.Outlined.SortByAlpha
    EpisodeOrderingOption.SEASON -> Icons.Outlined.Layers
    EpisodeOrderingOption.EPISODE -> Icons.Outlined.Tag
    EpisodeOrderingOption.FILE_NAME -> Icons.AutoMirrored.Outlined.InsertDriveFile
  }

private fun EpisodeOrderingOption.toLocalizedName(context: Context): String =
  when (this) {
    EpisodeOrderingOption.PUBLISHED_AT -> context.getString(R.string.episode_ordering_published_at_option)
    EpisodeOrderingOption.TITLE -> context.getString(R.string.settings_screen_library_ordering_title_option)
    EpisodeOrderingOption.SEASON -> context.getString(R.string.episode_ordering_season_option)
    EpisodeOrderingOption.EPISODE -> context.getString(R.string.episode_ordering_episode_option)
    EpisodeOrderingOption.FILE_NAME -> context.getString(R.string.episode_ordering_file_name_option)
  }
