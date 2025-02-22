package org.grakovne.lissen.ui.screens.settings.composable

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.NotInterested
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.channel.common.LibraryType
import org.grakovne.lissen.common.ColorScheme
import org.grakovne.lissen.viewmodel.PlayerViewModel
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
fun CommonSettingsComposable(
    viewModel: SettingsViewModel,
    playerViewModel: PlayerViewModel,
) {
    val libraries by viewModel.libraries.observeAsState(emptyList())
    val preferredLibrary by viewModel.preferredLibrary.observeAsState()
    val preferredColorScheme by viewModel.preferredColorScheme.observeAsState()

    val host by viewModel.host.observeAsState("")
    var preferredLibraryExpanded by remember { mutableStateOf(false) }
    var colorSchemeExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current

    SideEffect {
        viewModel.fetchLibraries()
    }

    if (host?.isNotEmpty() == true) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { preferredLibraryExpanded = true }
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_screen_preferred_library_title),
                    style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Text(
                    text = preferredLibrary?.title
                        ?: stringResource(R.string.library_is_not_available),
                    style = typography.bodyMedium,
                    color = when (preferredLibrary?.title) {
                        null -> colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else -> colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { colorSchemeExpanded = true }
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = stringResource(R.string.settings_screen_color_scheme_title),
                style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = preferredColorScheme?.toItem(context)?.name ?: "",
                style = typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )
        }
    }

    if (preferredLibraryExpanded && libraries != null && libraries.isNotEmpty()) {
        CommonSettingsItemComposable(
            items = libraries.map { CommonSettingsItem(it.id, it.title, it.type.provideIcon()) },
            selectedItem = preferredLibrary?.let { CommonSettingsItem(it.id, it.title, it.type.provideIcon()) },
            onDismissRequest = { preferredLibraryExpanded = false },
            onItemSelected = { item ->
                libraries
                    .find { it.id == item.id }
                    ?.let { viewModel.preferLibrary(it) }
                    ?.also { playerViewModel.clearPlayingBook() }
            },
        )
    }

    if (colorSchemeExpanded) {
        CommonSettingsItemComposable(
            items = listOf(
                ColorScheme.LIGHT.toItem(context),
                ColorScheme.FOLLOW_SYSTEM.toItem(context),
                ColorScheme.DARK.toItem(context),
            ),
            selectedItem = preferredColorScheme?.toItem(context),
            onDismissRequest = { colorSchemeExpanded = false },
            onItemSelected = { item ->
                ColorScheme
                    .entries
                    .find { it.name == item.id }
                    ?.let { viewModel.preferColorScheme(it) }
            },
        )
    }
}

private fun ColorScheme.toItem(context: Context): CommonSettingsItem {
    val id = this.name
    val name = when (this) {
        ColorScheme.FOLLOW_SYSTEM -> context.getString(R.string.color_scheme_follow_system)
        ColorScheme.LIGHT -> context.getString(R.string.color_scheme_light)
        ColorScheme.DARK -> context.getString(R.string.color_scheme_dark)
    }

    return CommonSettingsItem(id, name, null)
}

private fun LibraryType.provideIcon() = when (this) {
    LibraryType.LIBRARY -> Icons.Outlined.Book
    LibraryType.PODCAST -> Icons.Outlined.Podcasts
    LibraryType.UNKNOWN -> Icons.Outlined.NotInterested
}
