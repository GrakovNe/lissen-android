package org.grakovne.lissen.ui.screens.settings.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
fun GeneralSettingsComposable(viewModel: SettingsViewModel) {
    val isServerConnected by viewModel.isConnected.observeAsState(false)
    val libraries by viewModel.libraries.observeAsState(emptyList())
    val preferredLibrary by viewModel.preferredLibrary.observeAsState()

    var preferredLibraryExpanded by remember { mutableStateOf(false) }
    var colorSchemeExpanded by remember { mutableStateOf(false) }

    Row(modifier = Modifier
            .fillMaxWidth()
            .clickable { preferredLibraryExpanded = true }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                    text = stringResource(R.string.settings_screen_preferred_library_title),
                    style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                    text = preferredLibrary?.title ?: "",
                    style = typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
            )
        }
    }

    Row(modifier = Modifier
            .fillMaxWidth()
            .clickable { colorSchemeExpanded = true }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(
                modifier = Modifier.weight(1f)
        ) {
            Text(
                    text = "Color Scheme",
                    style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                    text = "System",
                    style = typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
            )
        }
    }

    if (preferredLibraryExpanded) {
        GeneralSettingsItemComposable(
                items = libraries.map { GeneralSettingsItem(it.id, it.title) },
                selectedItem = preferredLibrary?.let { GeneralSettingsItem(it.id, it.title) },
                onDismissRequest = { preferredLibraryExpanded = false },
                onItemSelected = { item ->
                    libraries
                            .find { it.id == item.id }
                            ?.let { viewModel.preferLibrary(it) }
                }
        )
    }

    if (colorSchemeExpanded) {
        GeneralSettingsItemComposable(
                items = listOf(
                        GeneralSettingsItem("1", "Light"),
                        GeneralSettingsItem("2", "System"),
                        GeneralSettingsItem("3", "Dark")
                ),
                selectedItem = GeneralSettingsItem("1", "Light"),
                onDismissRequest = { colorSchemeExpanded = false },
                onItemSelected = { item ->
                }
        )
    }
}
