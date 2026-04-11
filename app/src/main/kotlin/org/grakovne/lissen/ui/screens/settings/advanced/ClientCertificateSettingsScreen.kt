package org.grakovne.lissen.ui.screens.settings.advanced

import android.os.Handler
import android.os.Looper
import android.security.KeyChain
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.grakovne.lissen.R
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ClientCertificateSettingsScreen(onBack: () -> Unit) {
  val viewModel: SettingsViewModel = hiltViewModel()
  val clientCertAlias by viewModel.clientCertAlias.collectAsState(initial = null)
  val activity = LocalActivity.current
  val applicationContext = LocalContext.current.applicationContext

  val cancelledToast = stringResource(R.string.settings_screen_client_cert_picker_cancelled_toast)

  val onChooseCertificate: () -> Unit = {
    activity?.let { act ->
      KeyChain.choosePrivateKeyAlias(
        act,
        { selectedAlias ->
          if (selectedAlias != null) {
            viewModel.saveClientCertAlias(selectedAlias)
          } else {
            Handler(Looper.getMainLooper()).post {
              Toast.makeText(applicationContext, cancelledToast, Toast.LENGTH_SHORT).show()
            }
          }
        },
        null,
        null,
        null,
        -1,
        clientCertAlias,
      )
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.settings_screen_client_cert_title),
            style = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
          )
        },
        navigationIcon = {
          IconButton(onClick = { onBack() }) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = "Back",
            )
          }
        },
      )
    },
    modifier =
      Modifier
        .systemBarsPadding()
        .fillMaxHeight(),
  ) { innerPadding ->
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      when (val alias = clientCertAlias) {
        null -> EmptyStateContent()
        else -> SelectedCertificateContent(alias = alias)
      }

      Spacer(modifier = Modifier.height(8.dp))

      Button(
        onClick = onChooseCertificate,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(text = stringResource(R.string.settings_screen_client_cert_choose_action))
      }

      OutlinedButton(
        onClick = { viewModel.clearClientCertAlias() },
        enabled = clientCertAlias != null,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(text = stringResource(R.string.settings_screen_client_cert_remove_action))
      }
    }
  }
}

@Composable
private fun EmptyStateContent() {
  Text(
    text = stringResource(R.string.settings_screen_client_cert_empty_state_title),
    style = typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
  )
  Text(
    text = stringResource(R.string.settings_screen_client_cert_empty_state_description),
    style = typography.bodyMedium,
    color = colorScheme.onSurfaceVariant,
  )
  Text(
    text = stringResource(R.string.settings_screen_client_cert_install_help_title),
    style = typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    modifier = Modifier.padding(top = 8.dp),
  )
  Text(
    text = stringResource(R.string.settings_screen_client_cert_install_help_description),
    style = typography.bodyMedium,
    color = colorScheme.onSurfaceVariant,
  )
}

@Composable
private fun SelectedCertificateContent(alias: String) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      text = stringResource(R.string.settings_screen_client_cert_selected_label),
      style = typography.labelMedium,
      color = colorScheme.onSurfaceVariant,
    )
    Text(
      text = alias,
      style = typography.titleMedium,
    )
  }
}
