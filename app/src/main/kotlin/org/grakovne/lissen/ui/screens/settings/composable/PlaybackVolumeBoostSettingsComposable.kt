package org.grakovne.lissen.ui.screens.settings.composable

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.common.PlaybackVolumeBoost
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
fun PlaybackVolumeBoostSettingsComposable(viewModel: SettingsViewModel) {
  val context = LocalContext.current
  var volumeBoostExpanded by remember { mutableStateOf(false) }
  val preferredPlaybackVolumeBoost by viewModel.preferredPlaybackVolumeBoost.observeAsState()

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { volumeBoostExpanded = true }
        .padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(
      modifier = Modifier.weight(1f),
    ) {
      Text(
        text = stringResource(R.string.volume_boost_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
      )
      Text(
        text = preferredPlaybackVolumeBoost?.toItem(context)?.name ?: "",
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
      )
    }
  }

  if (volumeBoostExpanded) {
    CommonSettingsItemComposable(
      items =
        listOf(
          PlaybackVolumeBoost.DISABLED.toItem(context),
          PlaybackVolumeBoost.LOW.toItem(context),
          PlaybackVolumeBoost.MEDIUM.toItem(context),
          PlaybackVolumeBoost.HIGH.toItem(context),
          PlaybackVolumeBoost.MAX.toItem(context),
        ),
      selectedItem = preferredPlaybackVolumeBoost?.toItem(context),
      onDismissRequest = { volumeBoostExpanded = false },
      onItemSelected = { item ->
        PlaybackVolumeBoost
          .entries
          .find { it.name == item.id }
          ?.let { viewModel.preferPlaybackVolumeBoost(it) }
      },
    )
  }
}

private fun PlaybackVolumeBoost.toItem(context: Context): CommonSettingsItem {
  val id = this.name
  val name =
    when (this) {
      PlaybackVolumeBoost.DISABLED -> context.getString(R.string.volume_boost_disabled)
      PlaybackVolumeBoost.LOW -> context.getString(R.string.volume_boost_low)
      PlaybackVolumeBoost.MEDIUM -> context.getString(R.string.volume_boost_medium)
      PlaybackVolumeBoost.HIGH -> context.getString(R.string.volume_boost_high)
      PlaybackVolumeBoost.MAX -> context.getString(R.string.volume_boost_max)
    }

  return CommonSettingsItem(id, name, null)
}
