package org.grakovne.lissen.ui.screens.player.composable

import android.view.View
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PauseCircleFilled
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.ui.extensions.formatTime
import org.grakovne.lissen.ui.extensions.spokenDuration
import org.grakovne.lissen.ui.screens.player.composable.common.provideForwardIcon
import org.grakovne.lissen.ui.screens.player.composable.common.provideReplayIcon
import org.grakovne.lissen.viewmodel.PlayerViewModel
import org.grakovne.lissen.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackControlComposable(
  viewModel: PlayerViewModel,
  settingsViewModel: SettingsViewModel,
  modifier: Modifier = Modifier,
) {
  val isPlaying by viewModel.isPlaying.collectAsState()
  val currentTrackIndex by viewModel.currentChapterIndex.collectAsState()
  val currentTrackPosition by viewModel.currentChapterPosition.collectAsState()
  val currentTrackDuration by viewModel.currentChapterDuration.collectAsState()

  val seekTime by settingsViewModel.seekTime.collectAsState()

  val book by viewModel.book.collectAsState()
  val chapters = book?.chapters ?: emptyList()

  val view: View = LocalView.current

  var sliderPosition by remember { mutableDoubleStateOf(0.0) }
  var isDragging by remember { mutableStateOf(false) }

  LaunchedEffect(currentTrackPosition, currentTrackIndex, currentTrackDuration) {
    if (!isDragging) {
      sliderPosition = currentTrackPosition
    }
  }

  Column(
    modifier =
      modifier
        .testTag("trackControls")
        .fillMaxWidth()
        .padding(horizontal = 12.dp),
  ) {
    val positionLabel = stringResource(R.string.a11y_playback_position)
    val spokenPosition =
      stringResource(
        R.string.a11y_position_of,
        spokenDuration(sliderPosition.toInt()),
        spokenDuration(currentTrackDuration.toInt()),
      )

    Column(
      modifier = Modifier.fillMaxWidth(),
    ) {
      val sliderInteractionSource = remember { MutableInteractionSource() }
      val sliderColors =
        SliderDefaults.colors(
          thumbColor = colorScheme.primary,
          activeTrackColor = colorScheme.primary,
          inactiveTrackColor = colorScheme.primary.copy(alpha = 0.24f),
        )

      Slider(
        value = sliderPosition.toFloat(),
        onValueChange = { newPosition ->
          isDragging = true
          sliderPosition = newPosition.toDouble()
        },
        onValueChangeFinished = {
          isDragging = false
          viewModel.seekTo(sliderPosition)
        },
        valueRange = 0f..currentTrackDuration.toFloat(),
        interactionSource = sliderInteractionSource,
        thumb = {
          SliderDefaults.Thumb(
            interactionSource = sliderInteractionSource,
            thumbSize = DpSize(20.dp, 20.dp),
            colors = sliderColors,
          )
        },
        track = { sliderState ->
          SliderDefaults.Track(
            sliderState = sliderState,
            modifier = Modifier.height(4.dp),
            colors = sliderColors,
            thumbTrackGapSize = 0.dp,
            trackInsideCornerSize = 0.dp,
            drawStopIndicator = null,
          )
        },
        modifier =
          Modifier
            .fillMaxWidth()
            .semantics {
              contentDescription = positionLabel
              stateDescription = spokenPosition
            },
      )
    }

    Box(
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .offset(y = (-4).dp)
              .padding(horizontal = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = sliderPosition.toInt().formatTime(true),
            style = typography.bodySmall,
            color = colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.clearAndSetSemantics {},
          )
          Text(
            text =
              maxOf(0.0, currentTrackDuration - sliderPosition)
                .toInt()
                .formatTime(true),
            style = typography.bodySmall,
            color = colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.clearAndSetSemantics {},
          )
        }
      }

      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .align(Alignment.BottomCenter),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = {
            withHaptic(view) { viewModel.previousTrack() }
          },
          enabled = true,
        ) {
          Icon(
            imageVector = Icons.Rounded.SkipPrevious,
            contentDescription = stringResource(R.string.a11y_previous_track),
            tint = colorScheme.onBackground,
            modifier = Modifier.size(36.dp),
          )
        }

        IconButton(
          onClick = { withHaptic(view) { viewModel.rewind() } },
        ) {
          Icon(
            imageVector = provideReplayIcon(seekTime),
            contentDescription = stringResource(R.string.a11y_rewind_seconds, seekTime.rewind),
            tint = colorScheme.onBackground,
            modifier = Modifier.size(48.dp),
          )
        }

        IconButton(
          onClick = { withHaptic(view) { viewModel.togglePlayPause() } },
          modifier = Modifier.size(72.dp),
        ) {
          Icon(
            imageVector = if (isPlaying) Icons.Rounded.PauseCircleFilled else Icons.Rounded.PlayCircleFilled,
            contentDescription = if (isPlaying) stringResource(R.string.a11y_pause) else stringResource(R.string.a11y_play),
            tint = colorScheme.primary,
            modifier = Modifier.fillMaxSize(),
          )
        }

        IconButton(
          onClick = { withHaptic(view) { viewModel.forward() } },
        ) {
          Icon(
            imageVector = provideForwardIcon(seekTime),
            contentDescription = stringResource(R.string.a11y_fast_forward_seconds, seekTime.forward),
            tint = colorScheme.onBackground,
            modifier = Modifier.size(48.dp),
          )
        }

        IconButton(
          onClick = {
            if (currentTrackIndex < chapters.size - 1) {
              withHaptic(view) { viewModel.nextTrack() }
            }
          },
          enabled = currentTrackIndex < chapters.size - 1,
        ) {
          Icon(
            imageVector = Icons.Rounded.SkipNext,
            contentDescription = stringResource(R.string.a11y_next_track),
            tint =
              if (currentTrackIndex < chapters.size - 1) {
                colorScheme.onBackground
              } else {
                colorScheme.onBackground.copy(
                  alpha = 0.3f,
                )
              },
            modifier = Modifier.size(36.dp),
          )
        }
      }
    }
  }
}
