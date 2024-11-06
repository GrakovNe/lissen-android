package org.grakovne.lissen.ui.screens.settings.advanced

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.connection.ServerCustomHeader
import org.grakovne.lissen.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomHeadersSettingsScreen(
    onBack: () -> Unit
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val headers = settingsViewModel.customHeaders.observeAsState(emptyList())

    val fabHeight = 56.dp
    val additionalPadding = 16.dp

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.custom_headers_title),
                        style = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = colorScheme.onSurface
                        )
                    }
                }
            )
        },
        modifier = Modifier
            .systemBarsPadding()
            .fillMaxHeight(),
        content = { innerPadding ->
            LazyColumn(
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + fabHeight + additionalPadding
                ),
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                itemsIndexed(headers.value) { index, header ->
                    CustomHeaderComposable(
                        header = header,
                        onChanged = { newPair ->
                            val updatedList = headers.value.toMutableList()
                            updatedList[index] = newPair

                            settingsViewModel.updateCustomHeaders(updatedList)
                        },
                        onDelete = { pair ->
                            val updatedList = headers.value.toMutableList()
                            updatedList.remove(pair)

                            if (updatedList.isEmpty()) {
                                updatedList.add(ServerCustomHeader.empty())
                            }

                            settingsViewModel.updateCustomHeaders(updatedList)
                        }
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            FloatingActionButton(
                containerColor = colorScheme.primary,
                shape = CircleShape,
                onClick = {
                    val updatedList = headers.value.toMutableList()
                    updatedList.add(ServerCustomHeader.empty())
                    settingsViewModel.updateCustomHeaders(updatedList)
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add"
                )
            }
        }
    )
}
