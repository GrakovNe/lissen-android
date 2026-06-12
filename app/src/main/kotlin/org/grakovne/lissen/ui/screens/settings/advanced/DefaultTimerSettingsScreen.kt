package org.grakovne.lissen.ui.screens.settings.advanced

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.domain.DurationTimerOption
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.ui.screens.player.composable.TimerComposable
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DefaultTimerSettingsScreen(onBack: () -> Unit) {
  val viewModel: SettingsViewModel = hiltViewModel()
  val defaultTimerOption by viewModel.defaultTimerOption.observeAsState()
  val preferredLibrary by viewModel.preferredLibrary.observeAsState()

  var timerExpanded by remember { mutableStateOf(false) }

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

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.settings_screen_default_sleep_timer_title),
            style = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
          )
        },
        navigationIcon = {
          IconButton(onClick = { onBack() }) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = "Back",
            )
          }
        },
      )
    },
    modifier =
      Modifier
        .systemBarsPadding()
        .fillMaxHeight(),
    content = { innerPadding ->
      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
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
      }
    },
  )

  if (timerExpanded) {
    TimerComposable(
      currentOption = defaultTimerOption,
      libraryType = libraryType,
      onOptionSelected = { viewModel.saveDefaultTimerOption(it) },
      onDismissRequest = { timerExpanded = false },
    )
  }
}
