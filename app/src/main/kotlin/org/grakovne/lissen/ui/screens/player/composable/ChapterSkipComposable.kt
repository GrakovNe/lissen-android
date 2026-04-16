package org.grakovne.lissen.ui.screens.player.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.lib.domain.ChapterSkipConfig

private val PRESET_SECONDS = listOf(5, 10, 15, 30)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterSkipComposable(
  config: ChapterSkipConfig,
  currentChapterPosition: Double,
  currentChapterDuration: Double,
  onConfigChanged: (ChapterSkipConfig) -> Unit,
  onDismissRequest: () -> Unit,
) {
  val view = LocalView.current

  ModalBottomSheet(
    containerColor = colorScheme.background,
    onDismissRequest = onDismissRequest,
    content = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = stringResource(R.string.chapter_skip_title),
          style = typography.bodyLarge,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Enable/Disable toggle
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = stringResource(R.string.chapter_skip_enabled),
            style = typography.bodyMedium,
          )
          Switch(
            checked = config.enabled,
            onCheckedChange = { enabled ->
              withHaptic(view) {
                onConfigChanged(config.copy(enabled = enabled))
              }
            },
          )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        // Intro Skip Section
        SkipSection(
          title = stringResource(R.string.chapter_skip_intro),
          currentSeconds = config.introSeconds,
          onSecondsChanged = { seconds ->
            withHaptic(view) {
              onConfigChanged(config.copy(introSeconds = seconds))
            }
          },
          onSetFromPosition = {
            withHaptic(view) {
              val seconds = currentChapterPosition.toInt().coerceAtLeast(0)
              onConfigChanged(config.copy(introSeconds = seconds))
            }
          },
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        // Outro Skip Section
        SkipSection(
          title = stringResource(R.string.chapter_skip_outro),
          currentSeconds = config.outroSeconds,
          onSecondsChanged = { seconds ->
            withHaptic(view) {
              onConfigChanged(config.copy(outroSeconds = seconds))
            }
          },
          onSetFromPosition = {
            withHaptic(view) {
              val remaining = (currentChapterDuration - currentChapterPosition).toInt().coerceAtLeast(0)
              onConfigChanged(config.copy(outroSeconds = remaining))
            }
          },
        )
      }
    },
  )
}

@Composable
private fun SkipSection(
  title: String,
  currentSeconds: Int,
  onSecondsChanged: (Int) -> Unit,
  onSetFromPosition: () -> Unit,
) {
  var manualInput by remember(currentSeconds) { mutableStateOf("") }

  Column(
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = title,
        style = typography.titleSmall,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text =
          if (currentSeconds > 0) {
            stringResource(R.string.chapter_skip_current_value, currentSeconds)
          } else {
            stringResource(R.string.chapter_skip_disabled)
          },
        style = typography.bodySmall,
        color = colorScheme.onSurfaceVariant,
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Preset buttons
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
      PRESET_SECONDS.forEach { seconds ->
        FilledTonalButton(
          onClick = {
            onSecondsChanged(if (currentSeconds == seconds) 0 else seconds)
          },
          modifier = Modifier.size(52.dp),
          shape = CircleShape,
          colors =
            ButtonDefaults.filledTonalButtonColors(
              containerColor =
                if (currentSeconds == seconds) {
                  colorScheme.primary
                } else {
                  colorScheme.surfaceContainer
                },
              contentColor =
                if (currentSeconds == seconds) {
                  colorScheme.onPrimary
                } else {
                  colorScheme.onSurfaceVariant
                },
            ),
          contentPadding = PaddingValues(0.dp),
        ) {
          Text(
            text = "${seconds}s",
            style =
              if (currentSeconds == seconds) {
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

    Spacer(modifier = Modifier.height(8.dp))

    // Set from current position button
    TextButton(
      onClick = onSetFromPosition,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Icon(
        imageVector = Icons.Outlined.MyLocation,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = stringResource(R.string.chapter_skip_set_from_position),
        style = typography.bodySmall,
      )
    }

    // Manual input
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      OutlinedTextField(
        value = manualInput,
        onValueChange = { value ->
          manualInput = value.filter { it.isDigit() }
        },
        label = {
          Text(
            text = stringResource(R.string.chapter_skip_manual_input),
            style = typography.bodySmall,
          )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier =
          Modifier
            .weight(1f)
            .height(56.dp),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        textStyle = typography.bodyMedium,
      )
      Spacer(modifier = Modifier.width(8.dp))
      FilledTonalButton(
        onClick = {
          val seconds = manualInput.toIntOrNull() ?: 0
          onSecondsChanged(seconds.coerceIn(0, 3600))
          manualInput = ""
        },
        enabled = manualInput.isNotBlank(),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.height(56.dp),
      ) {
        Text(
          text = stringResource(android.R.string.ok),
          style = typography.labelMedium,
        )
      }
    }
  }
}
