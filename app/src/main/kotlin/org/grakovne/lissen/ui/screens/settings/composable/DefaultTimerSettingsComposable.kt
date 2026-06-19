package org.grakovne.lissen.ui.screens.settings.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.domain.DurationTimerOption
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.ui.screens.player.composable.TimerComposable
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
fun DefaultTimerSettingsComposable(viewModel: SettingsViewModel) {
  var timerExpanded by remember { mutableStateOf(false) }
  val defaultTimerOption by viewModel.defaultTimerOption.collectAsState()
  val preferredLibrary by viewModel.preferredLibrary.collectAsState()

  val context = LocalContext.current
  val libraryType = preferredLibrary?.type ?: LibraryType.LIBRARY

  val timerDescription =
    when (val opt = defaultTimerOption) {
      null -> {
        stringResource(R.string.timer_option_disabled)
      }

      CurrentEpisodeTimerOption -> {
        when (libraryType) {
          LibraryType.PODCAST -> stringResource(R.string.timer_option_after_current_episode)
          else -> stringResource(R.string.timer_option_after_current_chapter)
        }
      }

      is DurationTimerOption -> {
        context.resources.getQuantityString(
          R.plurals.timer_option_after_time,
          opt.duration,
          opt.duration,
        )
      }
    }

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { timerExpanded = true }
        .padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.settings_screen_default_sleep_timer_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
      )
      Text(
        text = timerDescription,
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
      )
    }
  }

  if (timerExpanded) {
    TimerComposable(
      currentOption = defaultTimerOption,
      libraryType = libraryType,
      onOptionSelected = { viewModel.saveDefaultTimerOption(it) },
      onDismissRequest = { timerExpanded = false },
    )
  }
}
