package org.grakovne.lissen.ui.screens.settings.advanced

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import org.grakovne.lissen.channel.common.DEFAULT_USER_AGENT
import org.grakovne.lissen.common.restartApplication
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.screens.settings.composable.PlaybackVolumeBoostSettingsComposable
import org.grakovne.lissen.ui.screens.settings.composable.SettingsInfoBanner
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
  val activityLoggingEnabled by viewModel.activityLoggingEnabled.observeAsState(true)
  val activityLoggingEnabledOnStart = viewModel.activityLoggingEnabledOnStart

  val userAgent by viewModel.userAgent.observeAsState("")

  var showUserAgentSheet by remember { mutableStateOf(false) }

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

          SettingsToggleItem(
            title = stringResource(R.string.settings_screen_activity_logging_title),
            description = stringResource(R.string.settings_screen_activity_logging_description),
            initialState = activityLoggingEnabled,
          ) { viewModel.preferActivityLoggingEnabled(it) }

          AdvancedSettingsSimpleItemComposable(
            title = stringResource(R.string.settings_screen_user_agent_title),
            description = stringResource(R.string.settings_screen_user_agent_hint),
            onclick = { showUserAgentSheet = true },
          )

          AdvancedSettingsSimpleItemComposable(
            title = stringResource(R.string.export_logs_title),
            description = stringResource(R.string.export_logs_description),
            enabled = activityLoggingEnabledOnStart,
            onclick = { shareLogs(context, viewModel) },
          )
        }

        if (showUserAgentSheet) {
          UserAgentBottomSheet(
            initialValue = userAgent,
            defaultValue = DEFAULT_USER_AGENT,
            onValueChange = { viewModel.updateUserAgent(it) },
            onRestoreDefault = { viewModel.resetUserAgent() },
            onDismiss = { showUserAgentSheet = false },
          )
        }

        val codecsChanged = softwareCodecsEnabledOnStart != softwareCodecsEnabled
        val loggingChanged = activityLoggingEnabledOnStart != activityLoggingEnabled

        when {
          codecsChanged && loggingChanged -> RestartRequiredPreferenceBanner()
          codecsChanged -> SoftwareCodecsPreferenceBanner()
          loggingChanged -> ActivityLoggingPreferenceBanner()
        }
      }
    },
  )
}

@Composable
fun RestartRequiredPreferenceBanner(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  SettingsInfoBanner(
    icon = Icons.Outlined.Settings,
    text = stringResource(R.string.restart_the_app_to_apply_settings_title),
    ctaText = stringResource(R.string.restart_the_app_to_apply_settings_cta),
    onAction = { context.restartApplication() },
    modifier = modifier,
  )
}

@Composable
fun SoftwareCodecsPreferenceBanner(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  SettingsInfoBanner(
    icon = Icons.Outlined.Memory,
    text = stringResource(R.string.restart_the_app_to_start_using_the_new_codecs_title),
    ctaText = stringResource(R.string.restart_the_app_to_start_using_the_new_codecs_cta),
    onAction = { context.restartApplication() },
    modifier = modifier,
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
  val archiveFile = viewModel.provideLogArchiveOrNull()

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserAgentBottomSheet(
  initialValue: String,
  defaultValue: String,
  onValueChange: (String) -> Unit,
  onRestoreDefault: () -> Unit,
  onDismiss: () -> Unit,
) {
  var text by remember { mutableStateOf(initialValue) }

  ModalBottomSheet(
    containerColor = colorScheme.background,
    onDismissRequest = onDismiss,
    content = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = stringResource(R.string.settings_screen_user_agent_title),
          style = typography.bodyLarge,
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
          value = text,
          onValueChange = {
            text = it
            onValueChange(it)
          },
          modifier = Modifier.fillMaxWidth(),
          minLines = 3,
          maxLines = 5,
        )

        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(top = 16.dp)
              .clickable {
                text = defaultValue
                onRestoreDefault()
              },
          horizontalArrangement = Arrangement.Center,
        ) {
          Text(
            text = stringResource(R.string.settings_screen_user_agent_restore_default),
            style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colorScheme.error,
          )
        }
      }
    },
  )
}
