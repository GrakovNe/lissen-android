package org.grakovne.lissen.ui.screens.settings.advanced

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
import org.grakovne.lissen.common.AudioFocusLossPolicy
import org.grakovne.lissen.ui.screens.settings.composable.CommonSettingsItem
import org.grakovne.lissen.ui.screens.settings.composable.CommonSettingsItemComposable
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
fun AudioFocusLossPolicyComposable(viewModel: SettingsViewModel) {
  val context = LocalContext.current
  var expanded by remember { mutableStateOf(false) }
  val policy by viewModel.audioFocusLossPolicy.observeAsState(AudioFocusLossPolicy.LOWER_VOLUME)

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { expanded = true }
        .padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.settings_screen_audio_focus_loss_policy_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
        color = colorScheme.onBackground,
      )
      Text(
        text = policy.toItem(context).name,
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
      )
    }
  }

  if (expanded) {
    CommonSettingsItemComposable(
      items =
        listOf(
          AudioFocusLossPolicy.PAUSE.toItem(context),
          AudioFocusLossPolicy.LOWER_VOLUME.toItem(context),
        ),
      selectedItem = policy.toItem(context),
      onDismissRequest = { expanded = false },
      onItemSelected = { item ->
        AudioFocusLossPolicy
          .entries
          .find { it.name == item.id }
          ?.let { viewModel.preferAudioFocusLossPolicy(it) }
        expanded = false
      },
    )
  }
}

private fun AudioFocusLossPolicy.toItem(context: Context): CommonSettingsItem {
  val name =
    when (this) {
      AudioFocusLossPolicy.PAUSE -> context.getString(R.string.settings_screen_audio_focus_loss_policy_option_pause)
      AudioFocusLossPolicy.LOWER_VOLUME -> context.getString(R.string.settings_screen_audio_focus_loss_policy_option_lower_volume)
    }
  return CommonSettingsItem(this.name, name, null)
}
