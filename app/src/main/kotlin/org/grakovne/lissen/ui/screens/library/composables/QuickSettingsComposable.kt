package org.grakovne.lissen.ui.screens.library.composables

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.grakovne.lissen.R
import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.common.LibraryOrderingDirection
import org.grakovne.lissen.common.LibraryOrderingDirection.ASCENDING
import org.grakovne.lissen.common.LibraryOrderingDirection.DESCENDING
import org.grakovne.lissen.common.LibraryOrderingOption
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.domain.LibraryType
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
  onGroupBySeriesToggled: () -> Unit,
  onSortingChanged: () -> Unit,
  navController: AppNavigationService,
  settingsModelView: SettingsViewModel = hiltViewModel(),
  libraryViewModel: LibraryViewModel = hiltViewModel(),
) {
  val forceCache by cachingModelView.forceCache.collectAsState(false)
  val hideCompleted by settingsModelView.hideCompleted.collectAsState(false)
  val groupBySeries by settingsModelView.groupBySeries.collectAsState(false)
  val ordering by settingsModelView.preferredLibraryOrdering.collectAsState()
  val context = LocalContext.current

  ModalBottomSheet(
    containerColor = colorScheme.surface,
    onDismissRequest = onDismissRequest,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    Column(
      modifier =
        Modifier
          .testTag("librarySettingsSheet")
          .fillMaxWidth(),
    ) {
      SectionHeader(stringResource(R.string.library_quick_settings_filters_title))

      ToggleRow(
        title = stringResource(R.string.show_downloaded_content_only),
        icon = Icons.Outlined.CloudOff,
        checked = forceCache,
        onClick = { onForceLocalToggled() },
      )

      if (libraryViewModel.fetchPreferredLibraryType() == LibraryType.LIBRARY) {
        ToggleRow(
          title = stringResource(R.string.hide_completed_items),
          icon = Icons.Outlined.VisibilityOff,
          checked = hideCompleted,
          onClick = { onHideCompletedToggled() },
        )

        ToggleRow(
          title = stringResource(R.string.group_by_series),
          icon = Icons.Outlined.Layers,
          checked = groupBySeries,
          onClick = { onGroupBySeriesToggled() },
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      HorizontalDivider(
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 8.dp),
      )

      Spacer(modifier = Modifier.height(4.dp))

      SectionHeader(stringResource(R.string.library_quick_settings_sort_title))

      LibraryOrderingOption.entries.forEach { option ->
        val isSelected = ordering.option == option
        SortOptionRow(
          title = option.toLocalizedName(context),
          icon = option.icon(),
          direction = if (isSelected) ordering.direction else null,
          onClick = {
            val newDirection =
              if (isSelected) {
                when (ordering.direction) {
                  ASCENDING -> DESCENDING
                  DESCENDING -> ASCENDING
                }
              } else {
                ASCENDING
              }
            settingsModelView.preferLibraryOrdering(
              LibraryOrderingConfiguration(option = option, direction = newDirection),
            )
            onSortingChanged()
          },
        )
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
  onClick: () -> Unit,
) {
  val view = LocalView.current
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { withHaptic(view) { onClick() } }
        .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      modifier = Modifier.size(20.dp),
      tint = colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = title,
      style = typography.bodyLarge,
      color = colorScheme.onSurface,
      modifier = Modifier.weight(1f),
    )
    Switch(
      checked = checked,
      onCheckedChange = null,
      colors =
        SwitchDefaults.colors(
          uncheckedTrackColor = colorScheme.surface,
          checkedBorderColor = colorScheme.onSurface,
          checkedThumbColor = colorScheme.onSurface,
          checkedTrackColor = colorScheme.surface,
        ),
    )
  }
}

@Composable
private fun SortOptionRow(
  title: String,
  icon: ImageVector,
  direction: LibraryOrderingDirection?,
  onClick: () -> Unit,
) {
  val view = LocalView.current
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { withHaptic(view) { onClick() } }
        .padding(horizontal = 16.dp, vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      modifier = Modifier.size(20.dp),
      tint = colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = title,
      style = typography.bodyLarge,
      color = colorScheme.onSurface,
      modifier = Modifier.weight(1f),
    )
    if (direction != null) {
      Icon(
        imageVector =
          when (direction) {
            ASCENDING -> Icons.Outlined.ArrowUpward
            DESCENDING -> Icons.Outlined.ArrowDownward
          },
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
