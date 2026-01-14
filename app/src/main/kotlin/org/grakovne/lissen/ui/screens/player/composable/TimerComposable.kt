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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.lib.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.lib.domain.DurationTimerOption
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.lib.domain.TimerOption
import org.grakovne.lissen.ui.components.slider.SleepTimerSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerComposable(
  currentOption: TimerOption?,
  libraryType: LibraryType,
  onOptionSelected: (TimerOption?) -> Unit,
  onDismissRequest: () -> Unit,
) {
  val view = LocalView.current
  val context = LocalContext.current

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
          text = stringResource(R.string.timer_title),
          style = typography.bodyLarge,
        )

        SleepTimerSlider(
          libraryType = libraryType,
          context = context,
          option = currentOption,
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(vertical = 16.dp),
          onUpdate = {
            onOptionSelected(it)
          },
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          OptionPresets.forEach { value ->
            FilledTonalButton(
              onClick = {
                withHaptic(view) {
                  onOptionSelected(value)
                }
              },
              modifier = Modifier.size(56.dp),
              shape = CircleShape,
              colors =
                ButtonDefaults.filledTonalButtonColors(
                  containerColor =
                    if (currentOption.isSame(value)) {
                      colorScheme.primary
                    } else {
                      colorScheme.surfaceContainer
                    },
                  contentColor =
                    if (currentOption.isSame(value)) {
                      colorScheme.onPrimary
                    } else {
                      colorScheme.onSurfaceVariant
                    },
                ),
              contentPadding = PaddingValues(0.dp),
            ) {
              if (value == null) {
                val fontSize = typography.labelMedium.fontSize
                val iconSize = with(LocalDensity.current) { fontSize.toDp() } * 1.5f

                Icon(
                  imageVector = Icons.Outlined.Close,
                  contentDescription = null,
                  modifier = Modifier.size(iconSize),
                )
              } else {
                Text(
                  text = value.duration.toString(),
                  style =
                    if (currentOption.isSame(value)) {
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
    },
  )
}

private fun TimerOption?.isSame(that: TimerOption?) =
  when (this) {
    CurrentEpisodeTimerOption -> that == CurrentEpisodeTimerOption
    is DurationTimerOption -> that is DurationTimerOption && that.duration == this.duration
    null -> that == null
  }

private val OptionPresets =
  listOf(
    null,
    DurationTimerOption(10),
    DurationTimerOption(15),
    DurationTimerOption(30),
    DurationTimerOption(60),
  )
