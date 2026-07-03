package org.grakovne.lissen.ui.screens.settings.advanced

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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.grakovne.lissen.R
import org.grakovne.lissen.common.restartApplication
import org.grakovne.lissen.common.shareFile
import org.grakovne.lissen.ui.screens.settings.composable.SettingsInfoBanner
import org.grakovne.lissen.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigBackupSettingsScreen(onBack: () -> Unit) {
  val viewModel: SettingsViewModel = hiltViewModel()
  val context = LocalContext.current

  var importSucceeded by remember { mutableStateOf(false) }

  val importFailedToast = stringResource(R.string.import_config_failed_toast)
  val exportFailedToast = stringResource(R.string.export_config_failed_toast)
  val exportChooserTitle = stringResource(R.string.export_config_title)
  val appName = stringResource(R.string.app_name)

  val importLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      val json =
        uri?.let {
          runCatching {
            context.contentResolver.openInputStream(it)?.use { stream -> stream.bufferedReader().readText() }
          }.getOrNull()
        }

      importSucceeded = json != null && viewModel.importSettingsJson(json)

      if (uri != null && !importSucceeded) {
        Toast.makeText(context, importFailedToast, Toast.LENGTH_SHORT).show()
      }
    }

  val onExport = {
    viewModel
      .provideConfigArchive()
      ?.let { context.shareFile(it, "application/json", exportChooserTitle, "$appName settings") }
      ?: Toast.makeText(context, exportFailedToast, Toast.LENGTH_SHORT).show()
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
          IconButton(onClick = onBack) {
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
      ConfigBackupContent(
        modifier = Modifier.padding(innerPadding),
        importSucceeded = importSucceeded,
        onImport = { importLauncher.launch(arrayOf("application/json")) },
        onExport = { onExport() },
        onRestart = { context.restartApplication() },
      )
    },
  )
}

@Composable
private fun ConfigBackupContent(
  importSucceeded: Boolean,
  onImport: () -> Unit,
  onExport: () -> Unit,
  onRestart: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize(),
    verticalArrangement = Arrangement.SpaceBetween,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .weight(1f)
          .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      AdvancedSettingsSimpleItemComposable(
        title = stringResource(R.string.import_config_title),
        description = stringResource(R.string.import_config_description),
        onclick = onImport,
      )

      AdvancedSettingsSimpleItemComposable(
        title = stringResource(R.string.export_config_title),
        description = stringResource(R.string.export_config_description),
        onclick = onExport,
      )
    }

    if (importSucceeded) {
      SettingsInfoBanner(
        icon = Icons.Outlined.Description,
        text = stringResource(R.string.restart_the_app_to_apply_settings_title),
        ctaText = stringResource(R.string.restart_the_app_to_apply_settings_cta),
        onAction = onRestart,
      )
    }
  }
}
