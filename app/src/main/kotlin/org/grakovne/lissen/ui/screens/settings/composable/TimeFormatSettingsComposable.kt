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
import org.grakovne.lissen.common.TimeFormat
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
fun TimeFormatSettingsComposable(viewModel: SettingsViewModel) {
  val context = LocalContext.current
  var timeFormatExpanded by remember { mutableStateOf(false) }
  val preferredTimeFormat by viewModel.preferredTimeFormat.observeAsState()

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { timeFormatExpanded = true }
        .padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(
      modifier = Modifier.weight(1f),
    ) {
      Text(
        text = stringResource(R.string.time_format_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
      )
      Text(
        text = preferredTimeFormat?.toItem(context)?.name ?: "",
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
      )
    }
  }

  if (timeFormatExpanded) {
    CommonSettingsItemComposable(
      items =
        listOf(
          TimeFormat.MM_SS.toItem(context),
          TimeFormat.HH_MM_SS.toItem(context),
        ),
      selectedItem = preferredTimeFormat?.toItem(context),
      onDismissRequest = { timeFormatExpanded = false },
      onItemSelected = { item ->
        TimeFormat
          .entries
          .find { it.name == item.id }
          ?.let { viewModel.preferTimeFormat(it) }
      },
    )
  }
}

private fun TimeFormat.toItem(context: Context): CommonSettingsItem {
  val id = this.name
  val name =
    when (this) {
      TimeFormat.MM_SS -> context.getString(R.string.time_format_mm_ss)
      TimeFormat.HH_MM_SS -> context.getString(R.string.time_format_hh_mm_ss)
    }

  return CommonSettingsItem(id, name, null)
}
