package org.grakovne.lissen.ui.screens.settings.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
    val host by viewModel.host.observeAsState("")
    val username by viewModel.username.observeAsState("")

    Column {
        Text(
            text = stringResource(R.string.settings_screen_server_connection),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
        )

        ListItem(
            headlineContent = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Podcasts,
                        contentDescription = "Preferences",
                        modifier = Modifier
                            .padding(start = 10.dp, end = 3.dp)
                            .size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                modifier = Modifier.padding(start = 10.dp),
                                text = host ?: "",
                                style = TextStyle(fontFamily = FontFamily.Monospace),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        username?.let { }
                        Text(
                            modifier = Modifier.padding(start = 10.dp, top = 4.dp),
                            text = stringResource(
                                R.string.settings_screen_connected_as_title,
                                username ?: ""
                            ),
                            style = TextStyle(fontFamily = FontFamily.Monospace),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            trailingContent = {
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
        )
    }
}
