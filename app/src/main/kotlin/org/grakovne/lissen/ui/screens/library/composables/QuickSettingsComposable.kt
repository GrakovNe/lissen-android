package org.grakovne.lissen.ui.screens.library.composables

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.grakovne.lissen.R
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.common.LibraryOrderingOption
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.ui.components.ApplicationSettingsItemComposable
import org.grakovne.lissen.ui.components.LissenModalBottomSheet
import org.grakovne.lissen.ui.components.SettingsOptionRow
import org.grakovne.lissen.ui.components.SettingsPickerRow
import org.grakovne.lissen.ui.components.SettingsSortOptionRow
import org.grakovne.lissen.ui.components.SettingsToggleRow
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.viewmodel.CachingModelView
import org.grakovne.lissen.viewmodel.LibrarySettingsViewModel
import org.grakovne.lissen.viewmodel.LibraryViewModel

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
  librarySettingsViewModel: LibrarySettingsViewModel = hiltViewModel(),
  libraryViewModel: LibraryViewModel = hiltViewModel(),
) {
  val forceCache by cachingModelView.forceCache.collectAsState(false)
  val hideCompleted by librarySettingsViewModel.hideCompleted.collectAsState(false)
  val grouping by librarySettingsViewModel.libraryGrouping.collectAsState(LibraryGrouping.NONE)
  val ordering by librarySettingsViewModel.preferredLibraryOrdering.collectAsState()
  val context = LocalContext.current
  val isLibrary = libraryViewModel.fetchPreferredLibraryType() == LibraryType.LIBRARY

  var groupingExpanded by remember { mutableStateOf(false) }
  var sortExpanded by remember { mutableStateOf(false) }

  LissenModalBottomSheet(
    containerColor = colorScheme.surface,
    scrollable = false,
    onDismissRequest = onDismissRequest,
  ) {
    Column(
      modifier =
        Modifier
          .testTag("librarySettingsSheet")
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
    ) {
      SettingsToggleRow(
        title = stringResource(R.string.show_downloaded_content_only),
        icon = Icons.Outlined.CloudOff,
        checked = forceCache,
        onClick = onForceLocalToggled,
      )

      // a row the library type does not support is hidden, not dimmed
      if (isLibrary) {
        SettingsToggleRow(
          title = stringResource(R.string.hide_completed_items),
          icon = Icons.Outlined.VisibilityOff,
          checked = hideCompleted,
          onClick = onHideCompletedToggled,
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      HorizontalDivider(
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 8.dp),
      )

      Spacer(modifier = Modifier.height(4.dp))

      if (isLibrary) {
        SettingsPickerRow(
          label = stringResource(R.string.library_quick_settings_grouping_title),
          icon = Icons.Outlined.Workspaces,
          value = grouping.toLocalizedName(context),
          expanded = groupingExpanded,
          onClick = { groupingExpanded = !groupingExpanded },
        )

        AnimatedVisibility(visible = groupingExpanded) {
          Column {
            LibraryGrouping.entries.forEach { option ->
              SettingsOptionRow(
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

      // grouped by author, genre or narrator the library is always sorted by name, so there is nothing to pick
      if (isLibrary.not() || grouping !in NAME_SORTED_GROUPINGS) {
        SettingsPickerRow(
          label = stringResource(R.string.library_quick_settings_sort_title),
          icon = Icons.AutoMirrored.Outlined.Sort,
          value = ordering.option.toLocalizedName(context),
          expanded = sortExpanded,
          onClick = { sortExpanded = !sortExpanded },
        )

        AnimatedVisibility(visible = sortExpanded) {
          Column {
            LibraryOrderingOption.entries.forEach { option ->
              SettingsSortOptionRow(
                title = option.toLocalizedName(context),
                icon = option.icon(),
                selected = ordering.option == option,
                direction = ordering.direction,
                onSelected = { direction ->
                  librarySettingsViewModel.preferLibraryOrdering(LibraryOrderingConfiguration(option = option, direction = direction))
                  onSortingChanged()
                },
              )
            }
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

private val NAME_SORTED_GROUPINGS = setOf(LibraryGrouping.AUTHOR, LibraryGrouping.GENRE, LibraryGrouping.NARRATOR)

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
    LibraryGrouping.GENRE -> Icons.Outlined.TheaterComedy
    LibraryGrouping.NARRATOR -> Icons.Outlined.MicNone
  }

private fun LibraryGrouping.toLocalizedName(context: Context): String =
  when (this) {
    LibraryGrouping.NONE -> context.getString(R.string.library_grouping_disabled)
    LibraryGrouping.SERIES -> context.getString(R.string.library_grouping_series)
    LibraryGrouping.AUTHOR -> context.getString(R.string.library_grouping_author)
    LibraryGrouping.GENRE -> context.getString(R.string.library_grouping_genre)
    LibraryGrouping.NARRATOR -> context.getString(R.string.library_grouping_narrator)
  }
