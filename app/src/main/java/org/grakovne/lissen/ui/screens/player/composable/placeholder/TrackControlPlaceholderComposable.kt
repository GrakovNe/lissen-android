package org.grakovne.lissen.ui.screens.player.composable.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forward30
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.ui.extensions.formatFully

@Composable
fun TrackControlPlaceholderComposable(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Slider(
                value = 0f,
                onValueChange = { newPosition ->
                },
                onValueChangeFinished = {
                },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = 0.formatFully(),
                        style = typography.bodySmall,
                        color = colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                    Text(
                        text = 0.formatFully(),
                        style = typography.bodySmall,
                        color = colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }

                Spacer(modifier = Modifier.height(64.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
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

                IconButton(
                    onClick = { },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Replay10,
                        contentDescription = "Rewind",
                        tint = colorScheme.onBackground,
                        modifier = Modifier.size(48.dp),
                    )
                }

                IconButton(
                    onClick = { },
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircleFilled,
                        contentDescription = "Play / Pause",
                        tint = colorScheme.primary,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                IconButton(
                    onClick = { },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Forward30,
                        contentDescription = "Forward",
                        tint = colorScheme.onBackground,
                        modifier = Modifier.size(48.dp),
                    )
                }

                IconButton(
                    onClick = {},
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next Track",
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
    }
}
