package org.grakovne.lissen.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.common.withHaptic

/**
 * Rows shared by the quick-settings sheets (library screen, player screen).
 */
@Composable
fun SettingsToggleRow(
  title: String,
  icon: ImageVector,
  checked: Boolean,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  val view = LocalView.current
  val contentColor = colorScheme.onSurface.copy(alpha = if (enabled) 1f else SETTINGS_DISABLED_ALPHA)
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .then(if (enabled) Modifier.clickable { withHaptic(view) { onClick() } } else Modifier)
        .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      modifier = Modifier.size(20.dp),
      tint = contentColor,
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = title,
      style = typography.bodyLarge,
      color = contentColor,
      modifier = Modifier.weight(1f),
    )
    LissenToggle(checked = checked, enabled = enabled)
  }
}

@Composable
fun SettingsPickerHeaderRow(
  label: String,
  icon: ImageVector,
  value: String,
  expanded: Boolean,
  enabled: Boolean = true,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val view = LocalView.current
  val labelColor = colorScheme.onSurface.copy(alpha = if (enabled) 1f else SETTINGS_DISABLED_ALPHA)
  val valueColor = colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else SETTINGS_DISABLED_ALPHA)
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .then(if (enabled) Modifier.clickable { withHaptic(view) { onClick() } } else Modifier)
        .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      modifier = Modifier.size(20.dp),
      tint = labelColor,
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = label,
      style = typography.bodyLarge,
      color = labelColor,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = value,
      style = typography.bodyMedium,
      color = valueColor,
    )
    Spacer(modifier = Modifier.width(4.dp))
    Icon(
      imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
      contentDescription = null,
      modifier = Modifier.size(20.dp),
      tint = valueColor,
    )
  }
}

@Composable
fun SettingsOptionRow(
  title: String,
  icon: ImageVector,
  selected: Boolean,
  trailing: ImageVector,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val view = LocalView.current
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable { withHaptic(view) { onClick() } }
        .padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      modifier = Modifier.size(20.dp),
      tint = colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = title,
      style = typography.bodyLarge,
      color = if (selected) colorScheme.onSurface else colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    if (selected) {
      Icon(
        imageVector = trailing,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = colorScheme.onSurface,
      )
    }
  }
}

@Composable
fun SettingsActionRow(
  title: String,
  icon: ImageVector,
  modifier: Modifier = Modifier,
  contentColor: Color = colorScheme.onSurface,
  onClick: () -> Unit,
) {
  val view = LocalView.current
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable { withHaptic(view) { onClick() } }
        .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      modifier = Modifier.size(20.dp),
      tint = contentColor,
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = title,
      style = typography.bodyLarge,
      color = contentColor,
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
fun ApplicationSettingsItemComposable(onClicked: () -> Unit) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .testTag("appSettingsItem")
        .clickable { onClicked() }
        .padding(horizontal = 16.dp, vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = Icons.Outlined.Settings,
      contentDescription = null,
      modifier = Modifier.size(20.dp),
      tint = colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = stringResource(R.string.application_settings),
      style = typography.bodyLarge,
      color = colorScheme.onSurface,
      modifier = Modifier.weight(1f),
    )
    Icon(
      imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
      contentDescription = null,
      modifier = Modifier.size(16.dp),
      tint = colorScheme.onSurfaceVariant,
    )
  }
}

const val SETTINGS_DISABLED_ALPHA = 0.38f
