package org.grakovne.lissen.ui.screens.settings.advanced

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.grakovne.lissen.R
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.ui.components.LissenModalBottomSheet
import org.grakovne.lissen.ui.components.slider.SeekTimeSlider
import org.grakovne.lissen.ui.screens.settings.composable.DisableableTimeBottomSheet
import org.grakovne.lissen.ui.screens.settings.composable.SettingsTopAppBar
import org.grakovne.lissen.viewmodel.PlaybackSettingsViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SeekSettingsScreen(onBack: () -> Unit) {
  val viewModel: PlaybackSettingsViewModel = hiltViewModel()
  val preferredSeekTime by viewModel.seekTime.collectAsState()
  val rewindOnPause by viewModel.rewindOnPause.collectAsState()
  val context = LocalContext.current

  var rewindExpanded by remember { mutableStateOf(false) }
  var forwardExpanded by remember { mutableStateOf(false) }
  var rewindOnPauseExpanded by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      SettingsTopAppBar(
        title = stringResource(R.string.settings_screen_seek_time_title),
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
          title = stringResource(R.string.rewind_interval),
          description = context.seconds(preferredSeekTime.rewind),
          onclick = { rewindExpanded = true },
        )

        AdvancedSettingsSimpleItemComposable(
          title = stringResource(R.string.forward_interval),
          description = context.seconds(preferredSeekTime.forward),
          onclick = { forwardExpanded = true },
        )

        AdvancedSettingsSimpleItemComposable(
          title = stringResource(R.string.rewind_on_pause_title),
          description = rewindOnPause?.let { context.seconds(it) } ?: stringResource(R.string.rewind_on_pause_disabled),
          onclick = { rewindOnPauseExpanded = true },
        )
      }
    },
  )

  if (rewindExpanded) {
    SeekTimeBottomSheet(
      title = stringResource(R.string.rewind_interval),
      currentSeconds = preferredSeekTime.rewind,
      onDismissRequest = { rewindExpanded = false },
      onUpdate = { viewModel.preferRewind(it) },
    )
  }

  if (forwardExpanded) {
    SeekTimeBottomSheet(
      title = stringResource(R.string.forward_interval),
      currentSeconds = preferredSeekTime.forward,
      onDismissRequest = { forwardExpanded = false },
      onUpdate = { viewModel.preferForward(it) },
    )
  }

  if (rewindOnPauseExpanded) {
    DisableableTimeBottomSheet(
      title = stringResource(R.string.rewind_on_pause_title),
      seconds = rewindOnPause,
      presets = rewindOnPausePresets,
      secondsLabel = R.plurals.seek_interval_seconds,
      offLabel = R.string.rewind_on_pause_disabled,
      onDismissRequest = { rewindOnPauseExpanded = false },
      onUpdate = { viewModel.preferRewindOnPause(it) },
    )
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SeekTimeBottomSheet(
  title: String,
  currentSeconds: Int,
  onDismissRequest: () -> Unit,
  onUpdate: (Int) -> Unit,
) {
  val view: View = LocalView.current
  val context = LocalContext.current
  var selectedSeconds by remember { mutableIntStateOf(currentSeconds) }

  LissenModalBottomSheet(
    containerColor = colorScheme.background,
    onDismissRequest = onDismissRequest,
    content = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = title,
          style = typography.bodyLarge,
        )

        SeekTimeSlider(
          context = context,
          seconds = selectedSeconds,
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(vertical = 16.dp),
          onUpdate = {
            selectedSeconds = it
            onUpdate(it)
          },
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          seekTimePresets.forEach { preset ->
            FilledTonalButton(
              onClick = {
                withHaptic(view) {
                  selectedSeconds = preset
                  onUpdate(preset)
                }
              },
              modifier = Modifier.size(56.dp),
              shape = CircleShape,
              colors =
                ButtonDefaults.filledTonalButtonColors(
                  containerColor =
                    if (selectedSeconds == preset) colorScheme.primary else colorScheme.surfaceContainer,
                  contentColor =
                    if (selectedSeconds == preset) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                ),
              contentPadding = PaddingValues(0.dp),
            ) {
              Text(
                text = "$preset",
                style =
                  if (selectedSeconds == preset) {
                    typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                  } else {
                    typography.labelMedium
                  },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    },
  )
}

private fun Context.seconds(seconds: Int): String = resources.getQuantityString(R.plurals.seek_interval_seconds, seconds, seconds)

private val seekTimePresets = listOf(5, 10, 15, 30, 60)
private val rewindOnPausePresets = listOf(1, 3, 5, 10)
