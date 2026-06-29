package org.grakovne.lissen.ui.screens.library.composables

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.grakovne.lissen.R
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.common.LibraryOrderingDirection.ASCENDING
import org.grakovne.lissen.common.LibraryOrderingDirection.DESCENDING
import org.grakovne.lissen.common.LibraryOrderingOption
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.ui.components.LissenToggle
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.viewmodel.CachingModelView
import org.grakovne.lissen.viewmodel.LibraryViewModel
import org.grakovne.lissen.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSettingsComposable(
  cachingModelView: CachingModelView = hiltViewModel(),
  onDismissRequest: () -> Unit,
  onForceLocalToggled: () -> Unit,
  onHideCompletedToggled: () -> Unit,
  onGroupingSelected: (LibraryGrouping) -> Unit,
  onSortingChanged: () -> Unit,
  navController: AppNavigationService,
  settingsModelView: SettingsViewModel = hiltViewModel(),
  libraryViewModel: LibraryViewModel = hiltViewModel(),
) {
  val forceCache by cachingModelView.forceCache.collectAsState(false)
  val hideCompleted by settingsModelView.hideCompleted.collectAsState(false)
  val grouping by settingsModelView.libraryGrouping.collectAsState(LibraryGrouping.NONE)
  val ordering by settingsModelView.preferredLibraryOrdering.collectAsState()
  val context = LocalContext.current
  val view = LocalView.current
  val isLibrary = libraryViewModel.fetchPreferredLibraryType() == LibraryType.LIBRARY

  var groupingExpanded by remember { mutableStateOf(false) }
  var sortExpanded by remember { mutableStateOf(false) }

  ModalBottomSheet(
    containerColor = colorScheme.surface,
    onDismissRequest = onDismissRequest,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    Column(
      modifier =
        Modifier
          .testTag("librarySettingsSheet")
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
    ) {
      ToggleRow(
        title = stringResource(R.string.show_downloaded_content_only),
        icon = Icons.Outlined.CloudOff,
        checked = forceCache,
        onClick = { onForceLocalToggled() },
      )

      ToggleRow(
        title = stringResource(R.string.hide_completed_items),
        icon = Icons.Outlined.VisibilityOff,
        checked = isLibrary && hideCompleted,
        enabled = isLibrary,
        onClick = { onHideCompletedToggled() },
      )

      Spacer(modifier = Modifier.height(8.dp))

      HorizontalDivider(
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 8.dp),
      )

      Spacer(modifier = Modifier.height(4.dp))

      if (isLibrary) {
        PickerHeaderRow(
          label = stringResource(R.string.library_quick_settings_grouping_title),
          icon = Icons.Outlined.Workspaces,
          value = grouping.toLocalizedName(context),
          expanded = groupingExpanded,
          onClick = { withHaptic(view) { groupingExpanded = !groupingExpanded } },
        )

        AnimatedVisibility(visible = groupingExpanded) {
          Column {
            LibraryGrouping.entries.forEach { option ->
              OptionRow(
                title = option.toLocalizedName(context),
                icon = option.icon(),
                selected = grouping == option,
                trailing = Icons.Outlined.Check,
                onClick = { onGroupingSelected(option) },
              )
            }
          }
        }
      }

      val sortLocked = isLibrary && grouping == LibraryGrouping.AUTHOR
      val displayedSortOption = if (sortLocked) LibraryOrderingOption.AUTHOR else ordering.option

      PickerHeaderRow(
        label = stringResource(R.string.library_quick_settings_sort_title),
        icon = Icons.AutoMirrored.Outlined.Sort,
        value = displayedSortOption.toLocalizedName(context),
        expanded = sortExpanded,
        enabled = !sortLocked,
        onClick = { withHaptic(view) { sortExpanded = !sortExpanded } },
      )

      AnimatedVisibility(visible = sortExpanded && !sortLocked) {
        Column {
          LibraryOrderingOption.entries.forEach { option ->
            val isSelected = ordering.option == option
            OptionRow(
              title = option.toLocalizedName(context),
              icon = option.icon(),
              selected = isSelected,
              trailing =
                when (ordering.direction) {
                  ASCENDING -> Icons.Outlined.ArrowUpward
                  DESCENDING -> Icons.Outlined.ArrowDownward
                },
              onClick = {
                val newDirection =
                  when {
                    !isSelected -> ASCENDING
                    ordering.direction == ASCENDING -> DESCENDING
                    else -> ASCENDING
                  }
                settingsModelView.preferLibraryOrdering(
                  LibraryOrderingConfiguration(option = option, direction = newDirection),
                )
                onSortingChanged()
              },
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      HorizontalDivider(
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 8.dp),
      )

      ApplicationSettingsItemComposable(
        onClicked = {
          onDismissRequest()
          navController.showSettings()
        },
      )
    }
  }
}

@Composable
private fun SectionHeader(title: String) {
  Text(
    text = title,
    style = typography.labelMedium,
    color = colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
  )
}

@Composable
private fun ToggleRow(
  title: String,
  icon: ImageVector,
  checked: Boolean,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  val view = LocalView.current
  val contentColor = colorScheme.onSurface.copy(alpha = if (enabled) 1f else DISABLED_ALPHA)
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
private fun PickerHeaderRow(
  label: String,
  icon: ImageVector,
  value: String,
  expanded: Boolean,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  val view = LocalView.current
  val labelColor = colorScheme.onSurface.copy(alpha = if (enabled) 1f else DISABLED_ALPHA)
  val valueColor = colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else DISABLED_ALPHA)
  Row(
    modifier =
      Modifier
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
private fun OptionRow(
  title: String,
  icon: ImageVector,
  selected: Boolean,
  trailing: ImageVector,
  onClick: () -> Unit,
) {
  val view = LocalView.current
  Row(
    modifier =
      Modifier
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

private const val DISABLED_ALPHA = 0.38f

private fun LibraryOrderingOption.icon(): ImageVector =
  when (this) {
    LibraryOrderingOption.TITLE -> Icons.Outlined.SortByAlpha
    LibraryOrderingOption.AUTHOR -> Icons.Outlined.Person
    LibraryOrderingOption.CREATED_AT -> Icons.Outlined.CalendarToday
    LibraryOrderingOption.UPDATED_AT -> Icons.Outlined.Update
  }

private fun LibraryOrderingOption.toLocalizedName(context: Context): String =
  when (this) {
    LibraryOrderingOption.TITLE -> context.getString(R.string.settings_screen_library_ordering_title_option)
    LibraryOrderingOption.AUTHOR -> context.getString(R.string.settings_screen_library_ordering_author_option)
    LibraryOrderingOption.CREATED_AT -> context.getString(R.string.settings_screen_library_ordering_creation_date_option)
    LibraryOrderingOption.UPDATED_AT -> context.getString(R.string.settings_screen_library_ordering_modification_date_option)
  }

private fun LibraryGrouping.icon(): ImageVector =
  when (this) {
    LibraryGrouping.NONE -> Icons.AutoMirrored.Outlined.List
    LibraryGrouping.SERIES -> Icons.Outlined.CollectionsBookmark
    LibraryGrouping.AUTHOR -> Icons.Outlined.Person
  }

private fun LibraryGrouping.toLocalizedName(context: Context): String =
  when (this) {
    LibraryGrouping.NONE -> context.getString(R.string.library_grouping_disabled)
    LibraryGrouping.SERIES -> context.getString(R.string.library_grouping_series)
    LibraryGrouping.AUTHOR -> context.getString(R.string.library_grouping_author)
  }
