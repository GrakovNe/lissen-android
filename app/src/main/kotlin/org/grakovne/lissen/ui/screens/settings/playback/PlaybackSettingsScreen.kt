package org.grakovne.lissen.ui.screens.settings.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.grakovne.lissen.R
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.screens.settings.advanced.AdvancedSettingsNavigationItemComposable
import org.grakovne.lissen.ui.screens.settings.composable.PlaybackVolumeBoostSettingsComposable
import org.grakovne.lissen.ui.screens.settings.composable.SettingsToggleItem
import org.grakovne.lissen.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettingsScreen(
  onBack: () -> Unit,
  navController: AppNavigationService,
) {
  val viewModel: SettingsViewModel = hiltViewModel()
  val showPlayerNavButtons by viewModel.showPlayerNavButtons.observeAsState(true)
  val shakeToResetTimer by viewModel.shakeToResetTimer.observeAsState(false)

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Playback preferences",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
          )
        },
        navigationIcon = {
          IconButton(onClick = { onBack() }) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = "Back",
              tint = MaterialTheme.colorScheme.onSurface,
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
            .padding(innerPadding),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Column(
          modifier =
            Modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState()),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          SettingsToggleItem(
            title = "Next/previous buttons",
            description = "Show next and previous track buttons in the player",
            initialState = showPlayerNavButtons,
            onCheckedChange = { viewModel.preferShowPlayerNavButtons(it) },
          )

          SettingsToggleItem(
            title = "Shake to reset sleep timer",
            description = "Shake device to reset the sleep timer",
            initialState = shakeToResetTimer,
            onCheckedChange = { viewModel.preferShakeToResetTimer(it) },
          )

          AdvancedSettingsNavigationItemComposable(
            title = stringResource(R.string.smart_rewind_title),
            description = stringResource(R.string.smart_rewind_subtitle),
            onclick = { navController.showSmartRewindSettings() },
          )

          PlaybackVolumeBoostSettingsComposable(viewModel)

          AdvancedSettingsNavigationItemComposable(
            title = stringResource(R.string.settings_screen_seek_time_title),
            description = stringResource(R.string.settings_screen_seek_time_hint),
            onclick = { navController.showSeekSettings() },
          )
        }
      }
    },
  )
}
