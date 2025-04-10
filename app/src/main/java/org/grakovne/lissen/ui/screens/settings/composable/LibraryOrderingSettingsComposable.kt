package org.grakovne.lissen.ui.screens.settings.composable

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.common.ColorScheme
import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.common.LibraryOrderingDirection
import org.grakovne.lissen.common.LibraryOrderingOption
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
fun LibraryOrderingSettingsComposable(
    viewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    var libraryOrderingExpanded by remember { mutableStateOf(false) }

    val preferredLibraryOrderingOption by viewModel
        .preferredLibraryOrderingOption
        .observeAsState(LibraryOrderingConfiguration.default)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { libraryOrderingExpanded = true }
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "Library ordering",
                style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = preferredLibraryOrderingOption.option.toItem(context).name ?: "",
                style = typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )
        }
    }

    if (libraryOrderingExpanded) {
        CommonSettingsItemComposable(
            items = listOf(
                LibraryOrderingOption.TITLE.toItem(context),
                LibraryOrderingOption.AUTHOR.toItem(context),
                LibraryOrderingOption.DURATION.toItem(context),
                LibraryOrderingOption.PUBLISHED_YEAR.toItem(context),
                LibraryOrderingOption.CREATED_AT.toItem(context),
                LibraryOrderingOption.MODIFIED_AT.toItem(context),
            ),
            selectedItem = null,
            onDismissRequest = { libraryOrderingExpanded = false },
            onItemSelected = { item ->
                LibraryOrderingOption
                    .entries
                    .find { it.name == item.id }
                    ?.let {
                        viewModel
                            .preferLibraryOrdering(LibraryOrderingConfiguration(
                                option = it,
                                direction = LibraryOrderingDirection.ASCENDING
                            )
                            )
                    }
            },
        )
    }
}

private fun LibraryOrderingOption.toItem(context: Context): CommonSettingsItem {
    val id = this.name

    val name = when (this) {
        LibraryOrderingOption.TITLE -> "Title"
        LibraryOrderingOption.AUTHOR -> "Author"
        LibraryOrderingOption.DURATION -> "Duration"
        LibraryOrderingOption.PUBLISHED_YEAR -> "Published year"
        LibraryOrderingOption.CREATED_AT -> "Creation date"
        LibraryOrderingOption.MODIFIED_AT -> "Latest modification"
    }

    return CommonSettingsItem(id, name, null)
}
