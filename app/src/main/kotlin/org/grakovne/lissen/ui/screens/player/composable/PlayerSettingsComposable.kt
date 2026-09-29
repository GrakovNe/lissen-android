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
import org.grakovne.lissen.playback.autoskip.AutoSkipConfiguration
import org.grakovne.lissen.ui.components.ApplicationSettingsItemComposable
import org.grakovne.lissen.ui.components.LissenModalBottomSheet
import org.grakovne.lissen.ui.components.SettingsOptionRow
import org.grakovne.lissen.ui.components.SettingsPickerRow
import org.grakovne.lissen.ui.components.slider.AutoSkipSlider
import org.grakovne.lissen.ui.extensions.formatTime
import org.grakovne.lissen.ui.extensions.spokenDuration
import org.grakovne.lissen.ui.icons.SkipEdges
import org.grakovne.lissen.ui.navigation.AppNavigationService

/**
 * The player's counterpart of the library quick settings, in the same sheet layout.
 *
 * Episode ordering (podcasts only): tapping an option selects it ascending, tapping it again flips
 * the direction; the picker is inert while the queue is rebuilt. Auto-skip: a ruler with the intro
 * and outro lengths of the item, stored once per gesture when the thumb is released.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsComposable(
  ordering: EpisodeOrderingConfiguration?,
  orderingVisible: Boolean,
  orderingEnabled: Boolean,
  onOrderingChanged: (EpisodeOrderingConfiguration) -> Unit,
  autoSkip: AutoSkipConfiguration,
  onAutoSkipChanged: (AutoSkipConfiguration) -> Unit,
  onDismissRequest: () -> Unit,
  navController: AppNavigationService,
) {
  val context = LocalContext.current
  val current = ordering ?: EpisodeOrderingConfiguration.default

  var sortExpanded by remember { mutableStateOf(false) }

  var skipExpanded by remember { mutableStateOf(false) }

  // the ruler edits a local copy and hands it over when the thumb is released
  var draft by remember(autoSkip) { mutableStateOf(autoSkip) }

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

        // kept open while the queue is rebuilt after a pick: dimmed, not folded under the finger
        AnimatedVisibility(visible = sortExpanded) {
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
                trailingDescription =
                  when (current.direction) {
                    ASCENDING -> stringResource(R.string.episode_ordering_ascending)
                    DESCENDING -> stringResource(R.string.episode_ordering_descending)
                  },
                enabled = orderingEnabled,
                modifier = Modifier.testTag("episodeOrderingOption_${option.name}"),
                onClick = {
                  onOrderingChanged(
                    EpisodeOrderingConfiguration(option = option, direction = if (isSelected) current.direction.opposite else ASCENDING),
                  )
                },
              )
            }
          }
        }
      }

      SettingsPickerRow(
        label = stringResource(R.string.player_settings_auto_skip),
        icon = SkipEdges,
        value = draft.summary(),
        // where the title leaves no room for the words, the two numbers alone
        compactValue = draft.compactSummary(),
        expanded = skipExpanded,
        modifier = Modifier.testTag("autoSkipPicker"),
        onClick = { skipExpanded = !skipExpanded },
      )

      AnimatedVisibility(visible = skipExpanded) {
        AutoSkipSlider(
          introSeconds = draft.introSeconds,
          outroSeconds = draft.outroSeconds,
          stateDescription = draft.summary(spoken = true),
          // nested one level in like the option rows: nothing of the ruler, its value labels
          // included, is drawn left of where their icons start or right of where their arrows end
          modifier =
            Modifier
              .padding(horizontal = 24.dp, vertical = 8.dp)
              .testTag("autoSkipSlider"),
          onUpdate = { intro, outro -> draft = AutoSkipConfiguration(introSeconds = intro, outroSeconds = outro) },
          onUpdateFinished = { if (draft != autoSkip) onAutoSkipChanged(draft) },
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

/** "Intro 01:20 · Outro 02:25", or the same read out in words for accessibility; what is not skipped is not mentioned. */
@Composable
private fun AutoSkipConfiguration.summary(spoken: Boolean = false): String {
  val intro =
    introSeconds
      .takeIf {
        it > 0
      }?.let { stringResource(R.string.player_settings_intro_value, if (spoken) spokenDuration(it) else it.formatTime()) }
  val outro =
    outroSeconds
      .takeIf {
        it > 0
      }?.let { stringResource(R.string.player_settings_outro_value, if (spoken) spokenDuration(it) else it.formatTime()) }

  return listOfNotNull(intro, outro).joinToString(if (spoken) ", " else SUMMARY_SEPARATOR).ifEmpty {
    stringResource(R.string.player_settings_skip_disabled)
  }
}

/** The two numbers alone, for a row whose title leaves no room for the words. */
private fun AutoSkipConfiguration.compactSummary(): String? =
  "${introSeconds.formatTime()}$SUMMARY_SEPARATOR${outroSeconds.formatTime()}".takeIf { enabled }

private const val SUMMARY_SEPARATOR = " · "

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
