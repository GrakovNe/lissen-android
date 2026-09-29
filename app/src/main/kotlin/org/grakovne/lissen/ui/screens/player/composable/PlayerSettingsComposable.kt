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
import org.grakovne.lissen.playback.autoskip.AutoSkipConfiguration
import org.grakovne.lissen.ui.components.ApplicationSettingsItemComposable
import org.grakovne.lissen.ui.components.LissenModalBottomSheet
import org.grakovne.lissen.ui.components.SettingsPickerRow
import org.grakovne.lissen.ui.components.SettingsSortOptionRow
import org.grakovne.lissen.ui.components.slider.AutoSkipSlider
import org.grakovne.lissen.ui.extensions.formatTime
import org.grakovne.lissen.ui.extensions.spokenDuration
import org.grakovne.lissen.ui.icons.SkipEdges
import org.grakovne.lissen.ui.navigation.AppNavigationService

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

  // stored once per gesture, when the thumb is released
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

        // not folded while the queue is rebuilt after a pick, only dimmed
        AnimatedVisibility(visible = sortExpanded) {
          Column {
            EpisodeOrderingOption.entries.forEach { option ->
              SettingsSortOptionRow(
                title = option.toLocalizedName(context),
                icon = option.icon(),
                selected = current.option == option,
                direction = current.direction,
                enabled = orderingEnabled,
                modifier = Modifier.testTag("episodeOrderingOption_${option.name}"),
                onSelected = { direction -> onOrderingChanged(EpisodeOrderingConfiguration(option = option, direction = direction)) },
              )
            }
          }
        }
      }

      SettingsPickerRow(
        label = stringResource(R.string.player_settings_auto_skip),
        icon = SkipEdges,
        value = draft.summary(),
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
          // indented like the option rows
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

/** "Intro 01:20 · Outro 02:25", or in words for TalkBack. */
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
