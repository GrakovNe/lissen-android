package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.grakovne.lissen.R
import org.grakovne.lissen.lib.domain.LibraryType
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
  navController: AppNavigationService,
  settingsModelView: SettingsViewModel = hiltViewModel(),
  libraryViewModel: LibraryViewModel = hiltViewModel(),
) {
  val forceCache by cachingModelView.forceCache.collectAsState(false)
  val hideCompleted by settingsModelView.hideCompleted.collectAsState(false)

  val context = LocalContext.current

  ModalBottomSheet(
    containerColor = colorScheme.background,
    onDismissRequest = onDismissRequest,
    content = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .padding(horizontal = 16.dp),
      ) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
          item {
            LibrarySettingsComposableItem(
              title = context.getString(R.string.show_downloaded_content_only),
              icon = ImageVector.vectorResource(id = R.drawable.available_offline_outline),
              state = forceCache,
              onStateChange = { onForceLocalToggled() },
            )

            if (libraryViewModel.fetchPreferredLibraryType() == LibraryType.LIBRARY) {
              LibrarySettingsComposableItem(
                title = stringResource(R.string.hide_completed_items),
                icon = Icons.Outlined.VisibilityOff,
                state = hideCompleted,
                onStateChange = { onHideCompletedToggled() },
              )
            }

            HorizontalDivider()

            ApplicationSettingsItemComposable(
              onClicked = {
                onDismissRequest()
                navController.showSettings()
              },
            )
          }
        }
      }
    },
  )
}

@Composable
fun LibrarySettingsComposableItem(
  title: String,
  icon: ImageVector,
  state: Boolean,
  onStateChange: (Boolean) -> Unit,
) {
  ListItem(
    modifier = Modifier,
    headlineContent = { Text(text = title) },
    leadingContent = {
      Icon(
        imageVector = icon,
        contentDescription = null,
      )
    },
    trailingContent = {
      Switch(
        checked = state,
        onCheckedChange = onStateChange,
        enabled = true,
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

@Composable
fun ApplicationSettingsItemComposable(onClicked: () -> Unit) {
  ListItem(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { onClicked() },
    leadingContent = {
      Icon(
        imageVector = Icons.Outlined.Settings,
        contentDescription = null,
      )
    },
    headlineContent = {
      Text(
        text = stringResource(R.string.application_settings),
        style = typography.bodyLarge,
      )
    },
    trailingContent = {
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
        contentDescription = null,
      )
    },
  )
}
