package org.grakovne.lissen.ui.screens.settings.advanced

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jakewharton.processphoenix.ProcessPhoenix
import kotlinx.coroutines.launch
import org.grakovne.lissen.R
import org.grakovne.lissen.common.restartApplication
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

  val collapseOnFling by viewModel.collapseOnFling.observeAsState(false)
  val crashReporting by viewModel.crashReporting.observeAsState(true)
  val bypassSsl by viewModel.bypassSsl.observeAsState(false)

  val softwareCodecsEnabled by viewModel.softwareCodecsEnabled.observeAsState(false)
  val softwareCodecsEnabledOnStart = viewModel.softwareCodecsEnabledOnStart

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

          SettingsToggleItem(
            stringResource(R.string.settings_screen_collapse_on_fling_title),
            stringResource(R.string.settings_screen_collapse_on_fling_description),
            collapseOnFling,
          ) { viewModel.preferCollapseOnFling(it) }

          AdvancedSettingsNavigationItemComposable(
            title = stringResource(R.string.settings_screen_custom_headers_title),
            description = stringResource(R.string.settings_screen_custom_header_hint),
            onclick = { navController.showCustomHeadersSettings() },
          )

          SettingsToggleItem(
            title = stringResource(R.string.settings_screen_bypass_ssl_title),
            description = stringResource(R.string.settings_screen_bypass_ssl_hint),
            initialState = bypassSsl,
          ) { viewModel.preferBypassSsl(it) }

          AdvancedSettingsNavigationItemComposable(
            title = stringResource(R.string.settings_screen_internal_connection_url_title),
            description = stringResource(R.string.settings_screen_internal_connection_url_description),
            onclick = { navController.showLocalUrlSettings() },
          )

          SettingsToggleItem(
            title = stringResource(R.string.settings_screen_software_codecs_enabled_title),
            description = stringResource(R.string.settings_screen_software_codecs_enabled_description),
            initialState = softwareCodecsEnabled,
          ) { viewModel.preferSoftwareCodecsEnabled(it) }

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

        if (softwareCodecsEnabledOnStart != softwareCodecsEnabled) {
          SoftwareCodecsPreferenceBanner()
        }
      }
    },
  )
}

@Composable
fun SoftwareCodecsPreferenceBanner(modifier: Modifier = Modifier) {
  val context = LocalContext.current

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = Icons.Outlined.Memory,
      contentDescription = null,
      tint = colorScheme.primary,
      modifier = Modifier.padding(end = 12.dp),
    )

    Text(
      text = stringResource(R.string.restart_the_app_to_start_using_the_new_codecs_title),
      style =
        typography.bodyMedium.copy(
          color = colorScheme.onSurface,
        ),
      modifier = Modifier.weight(1f),
    )

    TextButton(
      onClick = { context.restartApplication() },
    ) {
      Text(
        text = stringResource(R.string.restart_the_app_to_start_using_the_new_codecs_cta),
        style =
          typography.bodyMedium.copy(
            color = colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
          ),
      )
    }
  }
}
