package org.grakovne.lissen.ui.screens.settings.advanced

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
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.grakovne.lissen.R
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.screens.settings.composable.DisconnectServerComposable
import org.grakovne.lissen.ui.screens.settings.composable.ServerInfoComposable
import org.grakovne.lissen.ui.screens.settings.composable.SettingsToggleItem
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ConnectionSettingsScreen(
  onBack: () -> Unit,
  navController: AppNavigationService,
) {
  val viewModel: SettingsViewModel = hiltViewModel()
  val host by viewModel.host.observeAsState()
  val bypassSsl by viewModel.bypassSsl.observeAsState(false)

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.connection_settings_title),
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
      }

      DisconnectServerComposable(navController, viewModel)
    }
  }
}
