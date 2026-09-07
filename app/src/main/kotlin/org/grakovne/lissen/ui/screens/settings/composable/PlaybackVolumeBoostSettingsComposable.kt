package org.grakovne.lissen.ui.screens.settings.composable

import android.view.View
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import org.grakovne.lissen.ui.components.LissenModalBottomSheet
import org.grakovne.lissen.ui.components.slider.VolumeBoostSlider
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
fun PlaybackVolumeBoostSettingsComposable(viewModel: SettingsViewModel) {
  var volumeBoostExpanded by remember { mutableStateOf(false) }
  val preferredDb by viewModel.preferredPlaybackVolumeBoost.collectAsState()

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { volumeBoostExpanded = true }
        .padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.volume_boost_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
      )
      Text(
        text = if (preferredDb == 0) stringResource(R.string.volume_boost_disabled) else "$preferredDb dB",
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
      )
    }
  }

  if (volumeBoostExpanded) {
    VolumeBoostBottomSheet(
      currentDb = preferredDb,
      onDismissRequest = { volumeBoostExpanded = false },
      onUpdate = { viewModel.preferPlaybackVolumeBoost(it) },
    )
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun VolumeBoostBottomSheet(
  currentDb: Int,
  onDismissRequest: () -> Unit,
  onUpdate: (Int) -> Unit,
) {
  val view: View = LocalView.current
  var selectedDb by remember { mutableIntStateOf(currentDb) }

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
          text = stringResource(R.string.volume_boost_title),
          style = typography.bodyLarge,
        )

        VolumeBoostSlider(
          db = selectedDb,
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(vertical = 16.dp),
          onUpdate = {
            selectedDb = it
            onUpdate(it)
          },
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          volumeBoostPresets.forEach { preset ->
            FilledTonalButton(
              onClick = {
                withHaptic(view) {
                  selectedDb = preset
                  onUpdate(preset)
                }
              },
              modifier = Modifier.size(56.dp),
              shape = CircleShape,
              colors =
                ButtonDefaults.filledTonalButtonColors(
                  containerColor =
                    if (selectedDb == preset) colorScheme.primary else colorScheme.surfaceContainer,
                  contentColor =
                    if (selectedDb == preset) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                ),
              contentPadding = PaddingValues(0.dp),
            ) {
              Text(
                text = "$preset",
                style =
                  if (selectedDb == preset) {
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

private val volumeBoostPresets = listOf(0, 3, 6, 12, 20)
