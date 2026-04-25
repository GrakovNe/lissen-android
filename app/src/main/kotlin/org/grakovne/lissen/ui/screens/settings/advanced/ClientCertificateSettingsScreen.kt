package org.grakovne.lissen.ui.screens.settings.advanced

import android.security.KeyChain
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

  val cancelledToast = stringResource(R.string.settings_screen_client_cert_picker_cancelled_toast)

  val onChooseCertificate: () -> Unit = {
    activity?.let { act ->
      KeyChain.choosePrivateKeyAlias(
        act,
        { selectedAlias ->
          if (selectedAlias != null) {
            viewModel.saveClientCertAlias(selectedAlias)
          } else {
            act.runOnUiThread {
              Toast.makeText(act, cancelledToast, Toast.LENGTH_SHORT).show()
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
          IconButton(onClick = onBack) {
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        when (val alias = clientCertAlias) {
          null -> {
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(24.dp),
              color = colorScheme.surfaceContainer,
            ) {
              Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
              ) {
                Row(
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                  verticalAlignment = Alignment.Top,
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                  )

                  Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                  ) {
                    Text(
                      text = stringResource(R.string.settings_screen_client_cert_empty_state_title),
                      style = typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                      text = stringResource(R.string.settings_screen_client_cert_hint),
                      style = typography.bodyMedium,
                      color = colorScheme.onSurfaceVariant,
                    )
                  }
                }

                HorizontalDivider(color = colorScheme.outlineVariant)

                InfoRow(
                  icon = Icons.Outlined.VpnKey,
                  title = stringResource(R.string.settings_screen_client_cert_install_help_title),
                  text = stringResource(R.string.settings_screen_client_cert_empty_state_description),
                )

                HorizontalDivider(color = colorScheme.outlineVariant)

                InfoRow(
                  icon = Icons.Outlined.Info,
                  title = "How to install",
                  text = stringResource(R.string.settings_screen_client_cert_install_help_description),
                )
              }
            }
          }

          else -> {
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(24.dp),
              color = colorScheme.surfaceContainer,
            ) {
              Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
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
          }
        }
      }

      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Button(
          onClick = onChooseCertificate,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
        ) {
          Text(text = stringResource(R.string.settings_screen_client_cert_choose_action))
        }

        if (clientCertAlias != null) {
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .clickable { viewModel.clearClientCertAlias() }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = stringResource(R.string.settings_screen_client_cert_remove_action),
              style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
              color = colorScheme.error,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun InfoRow(
  icon: ImageVector,
  title: String,
  text: String,
) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.Top,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 2.dp),
    )

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = title,
        style = typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
      )
      Text(
        text = text,
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
      )
    }
  }
}
