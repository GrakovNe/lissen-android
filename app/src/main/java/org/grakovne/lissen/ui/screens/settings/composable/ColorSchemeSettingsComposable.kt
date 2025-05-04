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
import org.grakovne.lissen.common.ColorScheme
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
fun ColorSchemeSettingsComposable(viewModel: SettingsViewModel) {
  val context = LocalContext.current
  var colorSchemeExpanded by remember { mutableStateOf(false) }
  val preferredColorScheme by viewModel.preferredColorScheme.observeAsState()

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { colorSchemeExpanded = true }
        .padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(
      modifier = Modifier.weight(1f),
    ) {
      Text(
        text = stringResource(R.string.settings_screen_color_scheme_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
      )
      Text(
        text = preferredColorScheme?.toItem(context)?.name ?: "",
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
      )
    }
  }

  if (colorSchemeExpanded) {
    CommonSettingsItemComposable(
      items =
        listOf(
          ColorScheme.FOLLOW_SYSTEM.toItem(context),
          ColorScheme.LIGHT.toItem(context),
          ColorScheme.DARK.toItem(context),
          ColorScheme.BLACK.toItem(context),
        ),
      selectedItem = preferredColorScheme?.toItem(context),
      onDismissRequest = { colorSchemeExpanded = false },
      onItemSelected = { item ->
        ColorScheme
          .entries
          .find { it.name == item.id }
          ?.let { viewModel.preferColorScheme(it) }
      },
    )
  }
}

private fun ColorScheme.toItem(context: Context): CommonSettingsItem {
  val id = this.name
  val name =
    when (this) {
      ColorScheme.FOLLOW_SYSTEM -> context.getString(R.string.color_scheme_follow_system)
      ColorScheme.LIGHT -> context.getString(R.string.color_scheme_light)
      ColorScheme.DARK -> context.getString(R.string.color_scheme_dark)
      ColorScheme.BLACK -> context.getString(R.string.color_scheme_black)
    }

  return CommonSettingsItem(id, name, null)
}
