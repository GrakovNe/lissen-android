package org.grakovne.lissen.ui.screens.settings.composable

import android.view.View
import androidx.annotation.PluralsRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.ui.components.LissenModalBottomSheet
import org.grakovne.lissen.ui.components.slider.OptionalSecondsSlider

/** Picks a number of seconds or off, like the sleep timer sheet: a slider and presets, the first of which is off. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun OptionalSecondsBottomSheet(
  title: String,
  seconds: Int?,
  presets: List<Int>,
  @PluralsRes secondsLabel: Int,
  onDismissRequest: () -> Unit,
  onUpdate: (Int?) -> Unit,
) {
  val view: View = LocalView.current
  val context = LocalContext.current
  var selected by remember { mutableStateOf(seconds) }

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

        OptionalSecondsSlider(
          context = context,
          seconds = selected,
          secondsLabel = secondsLabel,
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(vertical = 16.dp),
          onUpdate = {
            selected = it
            onUpdate(it)
          },
        )

        // as many presets as the setting has: the buttons share the width
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          (listOf(null) + presets).forEach { preset ->
            FilledTonalButton(
              onClick = {
                withHaptic(view) {
                  selected = preset
                  onUpdate(preset)
                }
              },
              modifier =
                Modifier
                  .weight(1f)
                  .aspectRatio(1f),
              shape = CircleShape,
              colors =
                ButtonDefaults.filledTonalButtonColors(
                  containerColor =
                    if (selected == preset) colorScheme.primary else colorScheme.surfaceContainer,
                  contentColor =
                    if (selected == preset) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                ),
              contentPadding = PaddingValues(0.dp),
            ) {
              if (preset == null) {
                val fontSize = typography.labelMedium.fontSize
                val iconSize = with(LocalDensity.current) { fontSize.toDp() } * 1.5f

                Icon(
                  imageVector = Icons.Outlined.Close,
                  contentDescription = null,
                  modifier = Modifier.size(iconSize),
                )
              } else {
                Text(
                  text = "$preset",
                  style =
                    if (selected == preset) {
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
