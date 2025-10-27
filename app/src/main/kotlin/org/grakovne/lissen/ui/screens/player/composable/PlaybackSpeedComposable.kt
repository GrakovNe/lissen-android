package org.grakovne.lissen.ui.screens.player.composable

import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.ui.PlaybackSpeedSlider
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSpeedComposable(
  currentSpeed: Float,
  onSpeedChange: (Float) -> Unit,
  onDismissRequest: () -> Unit,
) {
  val view: View = LocalView.current
  var selectedPlaybackSpeed by remember { mutableFloatStateOf(currentSpeed) }

  ModalBottomSheet(
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
          text = stringResource(R.string.playback_speed_title),
          style = typography.bodyLarge,
        )

        PlaybackSpeedSlider(
          speed = selectedPlaybackSpeed,
          speedRange = (0.5f..3f),
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(vertical = 16.dp),
          onSpeedUpdate = {
            selectedPlaybackSpeed = it
            onSpeedChange(it)
          },
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          playbackSpeedPresets.forEach { value ->
            FilledTonalButton(
              onClick = {
                withHaptic(view) {
                  selectedPlaybackSpeed = value
                  onSpeedChange(value)
                }
              },
              modifier = Modifier.size(56.dp),
              shape = CircleShape,
              colors =
                ButtonDefaults.filledTonalButtonColors(
                  containerColor =
                    if (selectedPlaybackSpeed ==
                      value
                    ) {
                      colorScheme.primary
                    } else {
                      colorScheme.surfaceContainer
                    },
                  contentColor =
                    if (selectedPlaybackSpeed ==
                      value
                    ) {
                      colorScheme.onPrimary
                    } else {
                      colorScheme.onSurfaceVariant
                    },
                ),
              contentPadding = PaddingValues(0.dp),
            ) {
              Text(
                text = String.format(Locale.US, "%.1f", value),
                style =
                  if (selectedPlaybackSpeed == value) {
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

private val playbackSpeedPresets = listOf(1f, 1.2f, 1.5f, 2f, 3f)
