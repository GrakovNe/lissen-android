package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.grakovne.lissen.R
import org.grakovne.lissen.ui.icons.Search
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.viewmodel.CachingModelView
import org.grakovne.lissen.viewmodel.LibraryViewModel

@Composable
fun DefaultActionComposable(
    navController: AppNavigationService,
    cachingModelView: CachingModelView,
    libraryViewModel: LibraryViewModel,
    onContentRefreshing: (Boolean) -> Unit,
    onSearchRequested: () -> Unit,
) {
    var navigationItemSelected by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Row {
        IconButton(
            onClick = { onSearchRequested() },
            modifier = Modifier.offset(x = 4.dp),
        ) {
            Icon(
                imageVector = Search,
                contentDescription = null,
            )
        }
        IconButton(onClick = {
            navigationItemSelected = true
        }) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "Menu",
            )
        }
    }

    DropdownMenu(
        expanded = navigationItemSelected,
        onDismissRequest = { navigationItemSelected = false },
        modifier = Modifier
            .background(colorScheme.background)
            .padding(4.dp),
    ) {
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = when (cachingModelView.localCacheUsing()) {
                        true -> Icons.Outlined.Cloud
                        else -> Icons.Outlined.CloudOff
                    },
                    contentDescription = null,
                )
            },
            text = {
                Text(
                    text = when (cachingModelView.localCacheUsing()) {
                        true -> stringResource(R.string.disable_offline)
                        else -> stringResource(R.string.enable_offline)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
            onClick = {
                navigationItemSelected = false

                coroutineScope.launch {
                    withFrameNanos { }

                    CoroutineScope(Dispatchers.IO).launch {
                        cachingModelView.toggleCacheForce()
                        libraryViewModel.dropHiddenBooks()

                        onContentRefreshing(false)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        )

        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                )
            },
            text = {
                Text(
                    stringResource(R.string.library_screen_preferences_menu_item),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            },
            onClick = {
                navigationItemSelected = false
                navController.showSettings()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        )
    }
}
