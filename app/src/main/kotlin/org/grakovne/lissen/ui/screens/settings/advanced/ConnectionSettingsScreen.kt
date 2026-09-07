package org.grakovne.lissen.ui.screens.settings.advanced

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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.grakovne.lissen.R
import org.grakovne.lissen.channel.common.DEFAULT_USER_AGENT
import org.grakovne.lissen.ui.components.LissenModalBottomSheet
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.screens.settings.composable.DisconnectServerComposable
import org.grakovne.lissen.ui.screens.settings.composable.ServerInfoComposable
import org.grakovne.lissen.ui.screens.settings.composable.SettingsToggleItem
import org.grakovne.lissen.ui.screens.settings.composable.SettingsTopAppBar
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ConnectionSettingsScreen(
  onBack: () -> Unit,
  navController: AppNavigationService,
) {
  val viewModel: SettingsViewModel = hiltViewModel()
  val host by viewModel.host.collectAsState()
  val bypassSsl by viewModel.bypassSsl.collectAsState()

  val userAgent by viewModel.userAgent.collectAsState()
  var userAgentExpanded by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      SettingsTopAppBar(
        title = stringResource(R.string.connection_settings_title),
        onBack = onBack,
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
          .padding(innerPadding),
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
        if (host?.url?.isNotEmpty() == true) {
          ServerInfoComposable(navController, viewModel)
        }

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
          title = stringResource(R.string.settings_screen_client_cert_title),
          description = stringResource(R.string.settings_screen_client_cert_hint),
          onclick = { navController.showClientCertificateSettings() },
        )

        AdvancedSettingsNavigationItemComposable(
          title = stringResource(R.string.settings_screen_internal_connection_url_title),
          description = stringResource(R.string.settings_screen_internal_connection_url_description),
          onclick = { navController.showLocalUrlSettings() },
        )

        AdvancedSettingsSimpleItemComposable(
          title = stringResource(R.string.settings_screen_user_agent_title),
          description = stringResource(R.string.settings_screen_user_agent_hint),
          onclick = { userAgentExpanded = true },
        )
      }

      DisconnectServerComposable(navController, viewModel)
    }

    if (userAgentExpanded) {
      UserAgentBottomSheet(
        initialValue = userAgent,
        defaultValue = DEFAULT_USER_AGENT,
        onValueChange = { viewModel.updateUserAgent(it) },
        onRestoreDefault = { viewModel.resetUserAgent() },
        onDismiss = { userAgentExpanded = false },
      )
    }
  }
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

  LissenModalBottomSheet(
    containerColor = colorScheme.background,
    onDismissRequest = onDismiss,
    content = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
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
              .clickable {
                text = defaultValue
                onRestoreDefault()
              }.padding(vertical = 16.dp),
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
