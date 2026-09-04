package org.grakovne.lissen.ui.screens.player.composable

import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.lib.domain.ChapterSkipConfig
import org.grakovne.lissen.ui.components.slider.ChapterSkipSlider

private val PRESET_SECONDS = listOf(5, 10, 15, 30)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterSkipComposable(
  config: ChapterSkipConfig,
  onConfigChanged: (ChapterSkipConfig) -> Unit,
  onDismissRequest: () -> Unit,
) {
  val view: View = LocalView.current
  val latestConfig by rememberUpdatedState(config)

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
                onConfigChanged(latestConfig.copy(enabled = enabled))
              }
            },
          )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        // Intro Skip Section
        SkipSliderSection(
          title = stringResource(R.string.chapter_skip_intro),
          currentSeconds = config.introSeconds,
          onSecondsChanged = { seconds ->
            withHaptic(view) {
              onConfigChanged(latestConfig.copy(introSeconds = seconds))
            }
          },
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        // Outro Skip Section
        SkipSliderSection(
          title = stringResource(R.string.chapter_skip_outro),
          currentSeconds = config.outroSeconds,
          onSecondsChanged = { seconds ->
            withHaptic(view) {
              onConfigChanged(latestConfig.copy(outroSeconds = seconds))
            }
          },
        )
      }
    },
  )
}

@Composable
private fun SkipSliderSection(
  title: String,
  currentSeconds: Int,
  onSecondsChanged: (Int) -> Unit,
) {
  val view = LocalView.current

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = title,
      style = typography.titleSmall,
      fontWeight = FontWeight.Bold,
    )

    ChapterSkipSlider(
      seconds = currentSeconds,
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(vertical = 16.dp),
      onUpdate = { onSecondsChanged(it) },
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
      PRESET_SECONDS.forEach { seconds ->
        FilledTonalButton(
          onClick = {
            withHaptic(view) {
              onSecondsChanged(if (currentSeconds == seconds) 0 else seconds)
            }
          },
          modifier = Modifier.size(56.dp),
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
  }
}
