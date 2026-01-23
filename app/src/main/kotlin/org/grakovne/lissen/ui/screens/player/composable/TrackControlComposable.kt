package org.grakovne.lissen.ui.screens.player.composable

import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PauseCircleFilled
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.lib.domain.SeekTime
import org.grakovne.lissen.ui.extensions.formatTime
import org.grakovne.lissen.ui.screens.player.composable.common.SeekButton
import org.grakovne.lissen.viewmodel.PlayerViewModel
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
fun TrackControlComposable(
  viewModel: PlayerViewModel,
  settingsViewModel: SettingsViewModel,
  modifier: Modifier = Modifier,
) {
  val isPlaying by viewModel.isPlaying.observeAsState(false)
  val currentTrackIndex by viewModel.currentChapterIndex.observeAsState(0)
  val currentTrackPosition by viewModel.currentChapterPosition.observeAsState(0.0)
  val currentTrackDuration by viewModel.currentChapterDuration.observeAsState(0.0)

  val seekTime by settingsViewModel.seekTime.observeAsState(SeekTime.Default)
  val showNavButtons by settingsViewModel.showPlayerNavButtons.observeAsState(true)

  val book by viewModel.book.observeAsState()
  val chapters = book?.chapters ?: emptyList()

  val view: View = LocalView.current

  var sliderPosition by remember { mutableDoubleStateOf(0.0) }
  var isDragging by remember { mutableStateOf(false) }
  var showRemainingTime by remember { mutableStateOf(false) }

  LaunchedEffect(currentTrackPosition, currentTrackIndex, currentTrackDuration) {
    if (!isDragging) {
      sliderPosition = currentTrackPosition
    }
  }

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
    ) {
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
        colors =
          SliderDefaults.colors(
            thumbColor = colorScheme.primary,
            activeTrackColor = colorScheme.primary,
          ),
        modifier = Modifier.fillMaxWidth(),
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
          )
          Text(
            text =
              if (showRemainingTime) {
                "-" +
                  maxOf(0.0, currentTrackDuration - sliderPosition)
                    .toInt()
                    .formatTime(true)
              } else {
                currentTrackDuration.toInt().formatTime(true)
              },
            style = typography.bodySmall,
            color = colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.clickable { showRemainingTime = !showRemainingTime },
          )
        }
      }

      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(top = 48.dp)
            .align(Alignment.BottomCenter),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (showNavButtons) {
          IconButton(
            onClick = {
              withHaptic(view) { viewModel.previousTrack() }
            },
            enabled = true,
          ) {
            Icon(
              imageVector = Icons.Rounded.SkipPrevious,
              contentDescription = "Previous Track",
              tint = colorScheme.onBackground,
              modifier = Modifier.size(36.dp),
            )
          }
        }

        SeekButton(
          duration = seekTime.rewind.seconds,
          isForward = false,
          onClick = { withHaptic(view) { viewModel.rewind() } },
        )

        IconButton(
          onClick = { withHaptic(view) { viewModel.togglePlayPause() } },
          modifier = Modifier.size(72.dp),
        ) {
          Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = colorScheme.primary,
            modifier = Modifier.fillMaxSize(),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = "Play / Pause",
                tint = colorScheme.onPrimary,
                modifier = Modifier.size(48.dp),
              )
            }
          }
        }

        SeekButton(
          duration = seekTime.forward.seconds,
          isForward = true,
          onClick = { withHaptic(view) { viewModel.forward() } },
        )

        if (showNavButtons) {
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
              contentDescription = "Next Track",
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
}
