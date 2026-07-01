package org.grakovne.lissen.ui.screens.settings.advanced

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.grakovne.lissen.viewmodel.SettingsViewModel
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigBackupSettingsScreen(onBack: () -> Unit) {
  val viewModel: SettingsViewModel = hiltViewModel()
  val context = LocalContext.current

  var importSucceeded by remember { mutableStateOf(false) }

  val importFailedToast = stringResource(R.string.import_config_failed_toast)

  val importLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri == null) return@rememberLauncherForActivityResult

      val json =
        runCatching {
          context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
        }.getOrNull()

      importSucceeded = json != null && viewModel.importSettingsJson(json)

      if (!importSucceeded) {
        Toast.makeText(context, importFailedToast, Toast.LENGTH_SHORT).show()
      }
    }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.config_backup_title),
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
          AdvancedSettingsSimpleItemComposable(
            title = stringResource(R.string.import_config_title),
            description = stringResource(R.string.import_config_description),
            onclick = { importLauncher.launch(arrayOf("application/json")) },
          )

          AdvancedSettingsSimpleItemComposable(
            title = stringResource(R.string.export_config_title),
            description = stringResource(R.string.export_config_description),
            onclick = { shareConfig(context, viewModel) },
          )
        }

        if (importSucceeded) {
          SettingsInfoBanner(
            icon = Icons.Outlined.Description,
            text = stringResource(R.string.restart_the_app_to_apply_settings_title),
            ctaText = stringResource(R.string.restart_the_app_to_apply_settings_cta),
            onAction = { context.restartApplication() },
          )
        }
      }
    },
  )
}

private fun shareConfig(
  context: Context,
  viewModel: SettingsViewModel,
) {
  val archiveFile = File(context.cacheDir, "lissen-settings.json")

  val written = runCatching { archiveFile.writeText(viewModel.exportSettingsJson()) }.isSuccess

  if (!written) {
    Toast.makeText(context, context.getString(R.string.export_config_failed_toast), Toast.LENGTH_SHORT).show()
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
    "${context.getString(R.string.app_name)} settings • $formattedTimestamp • $sizeKb KB"

  val details =
    buildString {
      appendLine(context.getString(R.string.app_name))
      appendLine(formattedTimestamp)
      appendLine("$sizeKb KB")
    }

  val shareIntent =
    Intent(Intent.ACTION_SEND).apply {
      type = "application/json"

      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_SUBJECT, subject)
      putExtra(Intent.EXTRA_TEXT, details)

      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

  context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.export_config_title)))
}
