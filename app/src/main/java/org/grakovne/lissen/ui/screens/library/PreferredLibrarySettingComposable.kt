package org.grakovne.lissen.ui.screens.library

import androidx.compose.runtime.Composable
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.ui.screens.settings.composable.CommonSettingsItem
import org.grakovne.lissen.ui.screens.settings.composable.CommonSettingsItemComposable
import org.grakovne.lissen.ui.screens.settings.composable.provideIcon

@Composable
fun PreferredLibrarySettingComposable(
    libraries: List<Library>,
    preferredLibrary: Library?,
    onDismissRequest: () -> Unit,
    onItemSelected: (Library) -> Unit,
) {
    CommonSettingsItemComposable(
        items = libraries.map { CommonSettingsItem(it.id, it.title, it.type.provideIcon()) },
        selectedItem = preferredLibrary?.let { CommonSettingsItem(it.id, it.title, it.type.provideIcon()) },
        onDismissRequest = { onDismissRequest() },
        onItemSelected = { item ->
            val selectedItem = libraries.find { it.id == item.id }
                ?: return@CommonSettingsItemComposable

            if (selectedItem != preferredLibrary) {
                onItemSelected(selectedItem)
            }
        },
    )
}
