package org.grakovne.lissen.ui.screens.settings.composable

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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.lib.domain.SmartRewindDuration
import org.grakovne.lissen.lib.domain.SmartRewindInactivityThreshold
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
fun PlaybackSmartRewindSettingsComposable(viewModel: SettingsViewModel) {
  val smartRewindEnabled by viewModel.smartRewindEnabled.observeAsState(false)
  val smartRewindThreshold by viewModel.smartRewindThreshold.observeAsState(SmartRewindInactivityThreshold.ONE_HOUR)
  val smartRewindDuration by viewModel.smartRewindDuration.observeAsState(SmartRewindDuration.ONE_MINUTE)

  var thresholdExpanded by remember { mutableStateOf(false) }
  var durationExpanded by remember { mutableStateOf(false) }

  Column(modifier = Modifier.fillMaxWidth()) {
    // 1. Toggle
    SettingsToggleItem(
      title = stringResource(R.string.smart_rewind_title),
      description = stringResource(R.string.smart_rewind_subtitle),
      initialState = smartRewindEnabled,
      onCheckedChange = { viewModel.preferSmartRewindEnabled(it) },
    )

    // 2. Threshold Dropdown
    SmartRewindDropdownItem(
      title = stringResource(R.string.smart_rewind_threshold_title),
      value = getThresholdLabel(smartRewindThreshold),
      enabled = smartRewindEnabled,
      onClick = { thresholdExpanded = true },
    )

    // 3. Duration Dropdown
    SmartRewindDropdownItem(
      title = stringResource(R.string.smart_rewind_duration_title),
      value = getDurationLabel(smartRewindDuration),
      enabled = smartRewindEnabled,
      onClick = { durationExpanded = true },
    )
  }

  // Threshold Bottom Sheet
  if (thresholdExpanded) {
    CommonSettingsItemComposable(
      title = stringResource(R.string.smart_rewind_threshold_title),
      items =
        SmartRewindInactivityThreshold.entries.map {
          CommonSettingsItem(it.name, getThresholdLabel(it), null)
        },
      selectedItem = CommonSettingsItem(smartRewindThreshold.name, getThresholdLabel(smartRewindThreshold), null),
      onDismissRequest = { thresholdExpanded = false },
      onItemSelected = { item ->
        SmartRewindInactivityThreshold.values().find { it.name == item.id }?.let {
          viewModel.preferSmartRewindThreshold(it)
        }
        thresholdExpanded = false
      },
    )
  }

  // Duration Bottom Sheet
  if (durationExpanded) {
    CommonSettingsItemComposable(
      title = stringResource(R.string.smart_rewind_duration_title),
      items =
        SmartRewindDuration.entries.map {
          CommonSettingsItem(it.name, getDurationLabel(it), null)
        },
      selectedItem = CommonSettingsItem(smartRewindDuration.name, getDurationLabel(smartRewindDuration), null),
      onDismissRequest = { durationExpanded = false },
      onItemSelected = { item ->
        SmartRewindDuration.values().find { it.name == item.id }?.let {
          viewModel.preferSmartRewindDuration(it)
        }
        durationExpanded = false
      },
    )
  }
}

@Composable
private fun getThresholdLabel(threshold: SmartRewindInactivityThreshold): String =
  when (threshold) {
    SmartRewindInactivityThreshold.THIRTY_MINUTES -> stringResource(R.string.smart_rewind_threshold_30_min)
    SmartRewindInactivityThreshold.ONE_HOUR -> stringResource(R.string.smart_rewind_threshold_1_hour)
    SmartRewindInactivityThreshold.ONE_DAY -> stringResource(R.string.smart_rewind_threshold_1_day)
  }

@Composable
private fun getDurationLabel(duration: SmartRewindDuration): String =
  when (duration) {
    SmartRewindDuration.THIRTY_SECONDS -> stringResource(R.string.smart_rewind_duration_30_sec)
    SmartRewindDuration.ONE_MINUTE -> stringResource(R.string.smart_rewind_duration_1_min)
    SmartRewindDuration.FIVE_MINUTES -> stringResource(R.string.smart_rewind_duration_5_min)
  }

@Composable
private fun SmartRewindDropdownItem(
  title: String,
  value: String,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .let { if (enabled) it.clickable { onClick() } else it }
        .padding(horizontal = 24.dp, vertical = 12.dp)
        .alpha(if (enabled) 1f else 0.5f),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
        color = colorScheme.onSurface,
      )
      Text(
        text = value,
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
      )
    }
  }
}
