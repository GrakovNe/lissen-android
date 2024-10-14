package org.grakovne.lissen.ui.screens.player.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.rounded.PauseCircleFilled
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.Replay30
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.ui.extensions.formatFully
import org.grakovne.lissen.viewmodel.PlayerViewModel

@Composable
fun TrackControlComposable(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val isPlaying by viewModel.isPlaying.observeAsState(false)
    val currentPosition by viewModel.currentPosition.observeAsState(0L)
    val currentTrackIndex by viewModel.currentTrackIndex.observeAsState(0)

    val book by viewModel.book.observeAsState()
    val chapters = book?.files ?: emptyList()

    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val duration by remember {
        derivedStateOf {
            book?.files?.getOrNull(currentTrackIndex)?.duration?.toFloat() ?: 0f
        }
    }

    LaunchedEffect(currentPosition) {
        when (isDragging) {
            true -> {}
            false -> {
                sliderPosition = currentPosition.toFloat()
            }
        }
    }

    LaunchedEffect(isDragging) {
        when (isDragging) {
            true -> {}
            false -> viewModel.seekTo(sliderPosition)
        }
    }

    Column(
        modifier = modifier.padding(horizontal = 16.dp)
    ) {

        Slider(
            value = sliderPosition,
            onValueChange = { newPosition ->
                isDragging = true
                sliderPosition = newPosition
            },
            onValueChangeFinished = {
                isDragging = false
            },
            valueRange = 0f..(duration),
            colors = SliderDefaults
                .colors(
                    thumbColor = colorScheme.primary,
                    activeTrackColor = colorScheme.primary
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = currentPosition.toInt().formatFully(),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Text(
                text = "-${maxOf(0f, duration - currentPosition).toInt().formatFully()}",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                if (currentTrackIndex > 0) {
                    viewModel.previousTrack()
                }
            },
            modifier = Modifier
                .weight(1f)
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous",
                tint = colorScheme.onBackground,
                modifier = Modifier.size(36.dp)
            )
        }

        IconButton(
            onClick = {
                viewModel.seekTo(maxOf(0f, currentPosition - 10f))
            },
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Rounded.Replay30,
                contentDescription = "Replay",
                tint = colorScheme.onBackground,
                modifier = Modifier.size(36.dp)
            )
        }

        IconButton(
            onClick = { viewModel.togglePlayPause() },
            modifier = Modifier
                .size(72.dp)
                .weight(1.5f)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.PauseCircleFilled else Icons.Rounded.PlayCircleFilled,
                contentDescription = "Play / Pause toggle",
                tint = colorScheme.primary,
                modifier = Modifier.fillMaxSize()
            )
        }

        IconButton(
            onClick = {
                viewModel.seekTo(minOf(duration, currentPosition + 30f))
            },
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Forward30,
                contentDescription = "Forward",
                tint = colorScheme.onBackground,
                modifier = Modifier.size(36.dp)
            )
        }

        IconButton(
            onClick = {
                if (currentTrackIndex < chapters.size.minus(1)) {
                    viewModel.nextTrack()
                }
            },
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next Track",
                tint = colorScheme.onBackground,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}