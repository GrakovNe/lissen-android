package org.grakovne.lissen.ui.screens.settings.advanced

import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.grakovne.lissen.R
import org.grakovne.lissen.common.restartApplication
import org.grakovne.lissen.ui.screens.settings.composable.SettingsInfoBanner
import org.grakovne.lissen.ui.screens.settings.composable.SettingsToggleItem
import org.grakovne.lissen.viewmodel.CachingModelView
import org.grakovne.lissen.viewmodel.SettingsViewModel
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsComposable(onBack: () -> Unit) {
  val cachingModelView: CachingModelView = hiltViewModel()
  val viewModel: SettingsViewModel = hiltViewModel()

  val crashReporting by viewModel.crashReporting.collectAsState()
  val activityLoggingEnabled by viewModel.activityLoggingEnabled.collectAsState()
  val activityLoggingEnabledOnStart = viewModel.activityLoggingEnabledOnStart

  val context = LocalContext.current

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
          SettingsToggleItem(
            title = stringResource(R.string.settings_screen_crash_report_title),
            description = stringResource(R.string.settings_screen_crash_report_description),
            initialState = crashReporting,
          ) { viewModel.preferCrashReporting(it) }

          SettingsToggleItem(
            title = stringResource(R.string.settings_screen_activity_logging_title),
            description = stringResource(R.string.settings_screen_activity_logging_description),
            initialState = activityLoggingEnabled,
          ) { viewModel.preferActivityLoggingEnabled(it) }

          AdvancedSettingsSimpleItemComposable(
            title = stringResource(R.string.export_logs_title),
            description = stringResource(R.string.export_logs_description),
            enabled = activityLoggingEnabledOnStart,
            onclick = { shareLogs(context, viewModel) },
          )

          ClearThumbnailCacheComposable(cachingModelView = cachingModelView)
        }

        val loggingChanged = activityLoggingEnabledOnStart != activityLoggingEnabled

        if (loggingChanged) {
          ActivityLoggingPreferenceBanner()
        }
      }
    },
  )
}

@Composable
fun ActivityLoggingPreferenceBanner(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  SettingsInfoBanner(
    icon = Icons.Outlined.Description,
    text = stringResource(R.string.restart_the_app_to_apply_logging_changes_title),
    ctaText = stringResource(R.string.restart_the_app_to_apply_logging_changes_cta),
    onAction = { context.restartApplication() },
    modifier = modifier,
  )
}

private fun shareLogs(
  context: Context,
  viewModel: SettingsViewModel,
) {
  val archiveFile = viewModel.provideLogArchive()

  if (archiveFile == null) {
    Toast.makeText(context, context.getString(R.string.export_logs_no_logs), Toast.LENGTH_SHORT).show()
    return
  }

  val uri =
    FileProvider.getUriForFile(
      context,
      "${context.packageName}.fileprovider",
      archiveFile,
    )

  val exportTimestamp = OffsetDateTime.now()
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX")

  val formattedTimestamp = exportTimestamp.format(formatter)

  val sizeKb = archiveFile.length() / 1024

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
      type = "application/zip"

      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_SUBJECT, subject)
      putExtra(Intent.EXTRA_TEXT, details)

      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

  context.startActivity(Intent.createChooser(shareIntent, "Export logs"))
}
