package org.grakovne.lissen.ui.screens.settings.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisconnectServerComposable(
  navController: AppNavigationService,
  viewModel: SettingsViewModel,
) {
  LaunchedEffect(Unit) {
    viewModel.refreshConnectionInfo()
  }

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable {
          navController.showLogin()
          viewModel.logout()
        }.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column {
      Text(
        text = stringResource(R.string.disconnect_from_server_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        color = colorScheme.error,
      )
    }
  }
}
