package org.grakovne.lissen.ui.screens.settings.advanced

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import org.grakovne.lissen.domain.connection.ServerCustomHeader
import org.grakovne.lissen.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomHeadersSettingsScreen(
    onBack: () -> Unit
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val headers = settingsViewModel.customHeaders.observeAsState(emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Custom Headers",
                        style = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                headers
                    .value
                    .forEachIndexed { index, header ->
                        CustomHeaderItemComposable(
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
