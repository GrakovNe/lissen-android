package org.grakovne.lissen.ui.screens.settings.advanced

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.ui.screens.settings.composable.DefaultTimerSettingsComposable
import org.grakovne.lissen.ui.screens.settings.composable.OptionalSecondsBottomSheet
import org.grakovne.lissen.ui.screens.settings.composable.SettingsTopAppBar
import org.grakovne.lissen.viewmodel.LibrarySettingsViewModel
import org.grakovne.lissen.viewmodel.PlaybackSettingsViewModel

@Composable
fun SleepTimerSettingsScreen(onBack: () -> Unit) {
  val viewModel: PlaybackSettingsViewModel = hiltViewModel()
  val librarySettingsViewModel: LibrarySettingsViewModel = hiltViewModel()
  val libraryType by librarySettingsViewModel.preferredLibraryType.collectAsState()

  SleepTimerSettingsScreenContent(
    viewModel = viewModel,
    libraryType = libraryType,
    onBack = onBack,
  )
}

@Composable
internal fun SleepTimerSettingsScreenContent(
  viewModel: PlaybackSettingsViewModel,
  libraryType: LibraryType,
  onBack: () -> Unit,
) {
  val fade by viewModel.sleepTimerFade.collectAsState()
  val context = LocalContext.current

  var durationExpanded by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      SettingsTopAppBar(
        title = stringResource(R.string.sleep_timer_settings_title),
        onBack = onBack,
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
        AdvancedSettingsSimpleItemComposable(
          title = stringResource(R.string.sleep_timer_fade_title),
          description =
            fade
              ?.let { context.resources.getQuantityString(R.plurals.fade_duration_seconds, it, it) }
              ?: stringResource(R.string.setting_disabled),
          onclick = { durationExpanded = true },
        )

        DefaultTimerSettingsComposable(viewModel, libraryType)
      }
    },
  )

  if (durationExpanded) {
    OptionalSecondsBottomSheet(
      title = stringResource(R.string.sleep_timer_fade_title),
      seconds = fade,
      presets = fadeTimePresets,
      secondsLabel = R.plurals.fade_duration_seconds,
      onDismissRequest = { durationExpanded = false },
      onUpdate = { viewModel.preferSleepTimerFade(it) },
    )
  }
}

private val fadeTimePresets = listOf(5, 10, 15, 30, 60)
