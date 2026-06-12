package org.grakovne.lissen.ui.screens.library.composables

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.grakovne.lissen.R
import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.common.LibraryOrderingDirection.ASCENDING
import org.grakovne.lissen.common.LibraryOrderingDirection.DESCENDING
import org.grakovne.lissen.common.LibraryOrderingOption
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.viewmodel.CachingModelView
import org.grakovne.lissen.viewmodel.LibraryViewModel
import org.grakovne.lissen.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySettingsComposable(
  cachingModelView: CachingModelView = hiltViewModel(),
  onDismissRequest: () -> Unit,
  onForceLocalToggled: () -> Unit,
  onHideCompletedToggled: () -> Unit,
  onSortingChanged: () -> Unit,
  navController: AppNavigationService,
  settingsModelView: SettingsViewModel = hiltViewModel(),
  libraryViewModel: LibraryViewModel = hiltViewModel(),
) {
  val forceCache by cachingModelView.forceCache.collectAsState(false)
  val hideCompleted by settingsModelView.hideCompleted.collectAsState(false)
  val ordering by settingsModelView.preferredLibraryOrdering.observeAsState(LibraryOrderingConfiguration.default)
  val context = LocalContext.current

  ModalBottomSheet(
    containerColor = colorScheme.background,
    onDismissRequest = onDismissRequest,
  ) {
    Column(
      modifier =
        Modifier
          .testTag("librarySettingsSheet")
          .fillMaxWidth()
          .padding(bottom = 24.dp),
    ) {
      SectionHeader(stringResource(R.string.library_quick_settings_filters_title))

      SheetRow(
        title = stringResource(R.string.show_downloaded_content_only),
        onClick = { onForceLocalToggled() },
        trailingContent = {
          Switch(
            checked = forceCache,
            onCheckedChange = null,
            colors =
              SwitchDefaults.colors(
                uncheckedTrackColor = colorScheme.background,
                checkedBorderColor = colorScheme.onSurface,
                checkedThumbColor = colorScheme.onSurface,
                checkedTrackColor = colorScheme.background,
              ),
          )
        },
      )

      if (libraryViewModel.fetchPreferredLibraryType() == LibraryType.LIBRARY) {
        SheetRow(
          title = stringResource(R.string.hide_completed_items),
          onClick = { onHideCompletedToggled() },
          trailingContent = {
            Switch(
              checked = hideCompleted,
              onCheckedChange = null,
              colors =
                SwitchDefaults.colors(
                  uncheckedTrackColor = colorScheme.background,
                  checkedBorderColor = colorScheme.onSurface,
                  checkedThumbColor = colorScheme.onSurface,
                  checkedTrackColor = colorScheme.background,
                ),
            )
          },
        )
      }

      Spacer(modifier = Modifier.height(8.dp))
      HorizontalDivider()
      Spacer(modifier = Modifier.height(8.dp))

      SectionHeader(stringResource(R.string.settings_screen_library_ordering_title))

      LibraryOrderingOption.entries.forEach { option ->
        val isSelected = ordering.option == option
        SheetRow(
          title = option.toLocalizedName(context),
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
          titleColor = if (isSelected) colorScheme.onSurface else colorScheme.onSurfaceVariant,
          trailingContent =
            if (isSelected) {
              {
                Icon(
                  imageVector =
                    when (ordering.direction) {
                      ASCENDING -> Icons.Outlined.ArrowUpward
                      DESCENDING -> Icons.Outlined.ArrowDownward
                    },
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                  tint = colorScheme.onSurface,
                )
              }
            } else {
              null
            },
        )
      }

      Spacer(modifier = Modifier.height(8.dp))
      HorizontalDivider()

      TextButton(
        onClick = {
          onDismissRequest()
          navController.showSettings()
        },
        modifier =
          Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 4.dp),
      ) {
        Text(
          text = stringResource(R.string.application_settings),
          style = typography.labelLarge,
        )
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
          contentDescription = null,
          modifier = Modifier.padding(start = 4.dp).size(12.dp),
        )
      }
    }
  }
}

@Composable
private fun SectionHeader(title: String) {
  Text(
    text = title,
    style = typography.labelSmall,
    color = colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
  )
}

@Composable
private fun SheetRow(
  title: String,
  onClick: () -> Unit,
  titleColor: androidx.compose.ui.graphics.Color = colorScheme.onSurface,
  trailingContent: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { onClick() }
        .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = title,
      style = typography.bodyMedium,
      color = titleColor,
    )
    trailingContent?.invoke()
  }
}

private fun LibraryOrderingOption.toLocalizedName(context: Context): String =
  when (this) {
    LibraryOrderingOption.TITLE -> context.getString(R.string.settings_screen_library_ordering_title_option)
    LibraryOrderingOption.AUTHOR -> context.getString(R.string.settings_screen_library_ordering_author_option)
    LibraryOrderingOption.CREATED_AT -> context.getString(R.string.settings_screen_library_ordering_creation_date_option)
    LibraryOrderingOption.UPDATED_AT -> context.getString(R.string.settings_screen_library_ordering_modification_date_option)
  }
