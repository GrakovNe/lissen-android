package org.grakovne.lissen.ui.screens.settings.advanced

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.grakovne.lissen.R
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.screens.settings.composable.PlaybackVolumeBoostSettingsComposable
import org.grakovne.lissen.ui.screens.settings.composable.SettingsToggleItem
import org.grakovne.lissen.viewmodel.CachingModelView
import org.grakovne.lissen.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsComposable(
  onBack: () -> Unit,
  navController: AppNavigationService,
) {
  val cachingModelView: CachingModelView = hiltViewModel()
  val viewModel: SettingsViewModel = hiltViewModel()
  val crashReporting by viewModel.crashReporting.observeAsState(true)

  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.settings_screen_advanced_preferences_title),
            style = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colorScheme.onSurface,
          )
        },
        navigationIcon = {
          IconButton(onClick = { onBack() }) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = "Back",
              tint = colorScheme.onSurface,
            )
          }
        },
      )
    },
    modifier =
      Modifier
        .systemBarsPadding()
        .fillMaxHeight(),
    content = { innerPadding ->
      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(innerPadding),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Column(
          modifier =
            Modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState()),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          PlaybackVolumeBoostSettingsComposable(viewModel)

          AdvancedSettingsNavigationItemComposable(
            title = stringResource(R.string.settings_screen_seek_time_title),
            description = stringResource(R.string.settings_screen_seek_time_hint),
            onclick = { navController.showSeekSettings() },
          )

          AdvancedSettingsNavigationItemComposable(
            title = stringResource(R.string.settings_screen_custom_headers_title),
            description = stringResource(R.string.settings_screen_custom_header_hint),
            onclick = { navController.showCustomHeadersSettings() },
          )

          AdvancedSettingsNavigationItemComposable(
            title = stringResource(R.string.settings_screen_internal_connection_url_title),
            description = stringResource(R.string.settings_screen_internal_connection_url_description),
            onclick = { navController.showLocalUrlSettings() },
          )

          SettingsToggleItem(
            title = stringResource(R.string.settings_screen_crash_report_title),
            description = stringResource(R.string.settings_screen_crash_report_description),
            initialState = crashReporting,
          ) { viewModel.preferCrashReporting(it) }

          AdvancedSettingsSimpleItemComposable(
            title = stringResource(R.string.settings_screen_clear_thumbnail_cache_title),
            description = stringResource(R.string.settings_screen_clear_thumbnail_cache_hint),
            onclick = {
              scope.launch { cachingModelView.clearShortTermCache() }
              Toast
                .makeText(
                  context,
                  context.getString(R.string.settings_screen_clear_thumbnail_cache_success_toast),
                  Toast.LENGTH_SHORT,
                ).show()
            },
          )
        }
      }
    },
  )
}
