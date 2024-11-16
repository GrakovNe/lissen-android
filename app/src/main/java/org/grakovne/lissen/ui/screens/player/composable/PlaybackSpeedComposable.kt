package org.grakovne.lissen.ui.screens.player.composable

import android.annotation.SuppressLint
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight.Companion.SemiBold
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSpeedComposable(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismissRequest: () -> Unit
) {
    var selectedPlaybackSpeed by remember { mutableFloatStateOf(currentSpeed) }

    ModalBottomSheet(
        containerColor = colorScheme.background,
        onDismissRequest = onDismissRequest,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.playback_speed_title),
                    style = typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "${String.format("%.2f", selectedPlaybackSpeed)}x",
                    style = typography.titleLarge.copy(fontWeight = SemiBold)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Slider(
                    value = selectedPlaybackSpeed,
                    onValueChange = { value ->
                        selectedPlaybackSpeed = value
                    },
                    onValueChangeFinished = {
                        val snapThreshold = 0.01f
                        val snappedValue = playbackSpeedPresets
                            .find { kotlin.math.abs(it - selectedPlaybackSpeed) <= snapThreshold }
                            ?: selectedPlaybackSpeed

                        selectedPlaybackSpeed = snappedValue
                        onSpeedChange(snappedValue)
                    },
                    valueRange = 0.5f..3f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = colorScheme.primary,
                        activeTrackColor = colorScheme.primary,
                        inactiveTrackColor = colorScheme.primary.copy(alpha = 0.2f)
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    playbackSpeedPresets
                        .forEach { value ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Button(
                                    onClick = {
                                        selectedPlaybackSpeed = value
                                        onSpeedChange(value)
                                    },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = when (selectedPlaybackSpeed == value) {
                                            true -> colorScheme.primary
                                            else -> colorScheme.surfaceContainer
                                        }
                                    ),
                                    modifier = Modifier.fillMaxSize()
                                ) {}
                                Text(
                                    text = String.format("%.2f", value),
                                    style = when (selectedPlaybackSpeed == value) {
                                        true -> typography.labelMedium.copy(fontWeight = SemiBold)
                                        false -> typography.labelMedium
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    )
}

private val playbackSpeedPresets = listOf(1f, 1.25f, 1.5f, 2f, 3f)
