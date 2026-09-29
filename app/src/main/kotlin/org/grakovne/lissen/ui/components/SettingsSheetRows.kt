package org.grakovne.lissen.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.common.LibraryOrderingDirection
import org.grakovne.lissen.common.LibraryOrderingDirection.ASCENDING
import org.grakovne.lissen.common.LibraryOrderingDirection.DESCENDING
import org.grakovne.lissen.common.withHaptic

@Composable
fun SettingsToggleRow(
  title: String,
  icon: ImageVector,
  checked: Boolean,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  val view = LocalView.current
  val contentColor = colorScheme.onSurface.copy(alpha = if (enabled) 1f else SETTINGS_DISABLED_ALPHA)
  Row(
    modifier =
      modifier
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

/** The value gives way to [compactValue] when the label would wrap. */
@Composable
fun SettingsPickerRow(
  label: String,
  icon: ImageVector,
  value: String,
  expanded: Boolean,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  compactValue: String? = null,
  onClick: () -> Unit,
) {
  val view = LocalView.current
  val labelColor = colorScheme.onSurface.copy(alpha = if (enabled) 1f else SETTINGS_DISABLED_ALPHA)
  val valueColor = colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else SETTINGS_DISABLED_ALPHA)
  // decided once per row, not per value, or the row would flip between its forms on every change
  var compact by remember(compactValue != null) { mutableStateOf(false) }
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
      onTextLayout = { if (it.lineCount > 1 && compactValue != null) compact = true },
    )
    Text(
      text = if (compact) compactValue ?: value else value,
      style = typography.bodyMedium,
      color = valueColor,
      textAlign = TextAlign.End,
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
  trailingDescription: String? = null,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  val view = LocalView.current
  val alpha = if (enabled) 1f else SETTINGS_DISABLED_ALPHA
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton) { withHaptic(view) { onClick() } }
        .padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      modifier = Modifier.size(20.dp),
      tint = colorScheme.onSurfaceVariant.copy(alpha = alpha),
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = title,
      style = typography.bodyLarge,
      color = (if (selected) colorScheme.onSurface else colorScheme.onSurfaceVariant).copy(alpha = alpha),
      modifier = Modifier.weight(1f),
    )
    if (selected) {
      Icon(
        imageVector = trailing,
        contentDescription = trailingDescription,
        modifier = Modifier.size(20.dp),
        tint = colorScheme.onSurface.copy(alpha = alpha),
      )
    }
  }
}

/** Tapping the selected option flips its direction, tapping another one picks it ascending. */
@Composable
fun SettingsSortOptionRow(
  title: String,
  icon: ImageVector,
  selected: Boolean,
  direction: LibraryOrderingDirection,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  onSelected: (LibraryOrderingDirection) -> Unit,
) {
  SettingsOptionRow(
    title = title,
    icon = icon,
    selected = selected,
    trailing =
      when (direction) {
        ASCENDING -> Icons.Outlined.ArrowUpward
        DESCENDING -> Icons.Outlined.ArrowDownward
      },
    trailingDescription =
      when (direction) {
        ASCENDING -> stringResource(R.string.episode_ordering_ascending)
        DESCENDING -> stringResource(R.string.episode_ordering_descending)
      },
    modifier = modifier,
    enabled = enabled,
    onClick = { onSelected(if (selected) direction.opposite else ASCENDING) },
  )
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

private const val SETTINGS_DISABLED_ALPHA = 0.38f
