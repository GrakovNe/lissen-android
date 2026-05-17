package org.grakovne.lissen.ui.screens.settings.advanced

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
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
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.grakovne.lissen.R
import org.grakovne.lissen.common.restartApplication
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.screens.settings.composable.PlaybackVolumeBoostSettingsComposable
import org.grakovne.lissen.ui.screens.settings.composable.SettingsToggleItem
import org.grakovne.lissen.viewmodel.CachingModelView
import org.grakovne.lissen.viewmodel.SettingsViewModel
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsComposable(
  onBack: () -> Unit,
  navController: AppNavigationService,
) {
  val cachingModelView: CachingModelView = hiltViewModel()
  val viewModel: SettingsViewModel = hiltViewModel()

  val crashReporting by viewModel.crashReporting.observeAsState(true)

  val materialYouColorsEnabled by viewModel.materialYouEnabled.observeAsState(false)
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

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SettingsToggleItem(
              stringResource(R.string.settings_screen_material_you_title),
              stringResource(R.string.settings_screen_material_you_description),
              materialYouColorsEnabled,
            ) {
              viewModel.preferMaterialYouColors(it)
            }
          }

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

          AdvancedSettingsSimpleItemComposable(
            title = stringResource(R.string.export_logs_title),
            description = stringResource(R.string.export_logs_description),
            onclick = { shareLogs(context, viewModel) },
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

private fun shareLogs(
  context: Context,
  viewModel: SettingsViewModel,
) {
  val logFile = viewModel.provideLogFileOrNull()

  if (logFile == null) {
    Toast.makeText(context, context.getString(R.string.export_logs_no_logs), Toast.LENGTH_SHORT).show()
    return
  }

  val uri =
    FileProvider.getUriForFile(
      context,
      "${context.packageName}.fileprovider",
      logFile,
    )

  val exportTimestamp = OffsetDateTime.now()
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX")

  val formattedTimestamp = exportTimestamp.format(formatter)

  val sizeKb = logFile.length() / 1024

  val subject =
    "${context.getString(R.string.app_name)} logs • $formattedTimestamp • $sizeKb KB"

  val details =
    buildString {
      appendLine(context.getString(R.string.app_name))
      appendLine(formattedTimestamp)
      appendLine("$sizeKb KB")
    }

  val shareIntent =
    Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"

      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_SUBJECT, subject)
      putExtra(Intent.EXTRA_TEXT, details)

      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

  context.startActivity(Intent.createChooser(shareIntent, "Export logs"))
}
