package org.grakovne.lissen.ui.screens.settings.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
fun ServerSettingsComposable(
    navController: AppNavigationService,
    viewModel: SettingsViewModel
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_screen_server_connection),
                style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = "${viewModel.host.value}",
                style = typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 4.dp),
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = {
                navController.showLogin()
                viewModel.logout()
            }
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Logout"
            )
        }
    }
}
