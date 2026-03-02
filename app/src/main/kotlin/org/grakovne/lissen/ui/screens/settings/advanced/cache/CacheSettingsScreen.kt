package org.grakovne.lissen.ui.screens.settings.advanced.cache

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.grakovne.lissen.R
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.screens.settings.advanced.AdvancedSettingsNavigationItemComposable
import org.grakovne.lissen.ui.screens.settings.composable.SettingsToggleItem
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CacheSettingsScreen(
  onBack: () -> Unit,
  navController: AppNavigationService,
  viewModel: SettingsViewModel = hiltViewModel(),
) {
  val preferredDownloadOption by viewModel.preferredAutoDownloadOption.observeAsState()
  val autoDownloadDelayed by viewModel.autoDownloadDelayed.observeAsState(true)

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.download_settings_title),
            style = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colorScheme.onSurface,
          )
        },
        navigationIcon = {
          IconButton(onClick = { onBack() }) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = "Back",
              tint = colorScheme.onSurface,
            )
          }
        },
      )
    },
    modifier =
      Modifier
        .systemBarsPadding()
        .fillMaxHeight(),
    content = { innerPadding ->
      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(innerPadding),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Column(
          modifier =
            Modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState()),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          AutoCacheSettingsComposable(viewModel)

          NetworkTypeAutoCacheSettingsComposable(viewModel, preferredDownloadOption != null)

          LibraryTypeAutoCacheSettingsComposable(viewModel, preferredDownloadOption != null)

          SettingsToggleItem(
            enabled = preferredDownloadOption != null,
            title = stringResource(R.string.settings_screen_delay_autodownload_title),
            description = stringResource(R.string.settings_screen_delay_autodownload_description),
            initialState = autoDownloadDelayed,
          ) { viewModel.preferAutoDownloadDelayed(it) }

          AdvancedSettingsNavigationItemComposable(
            title = stringResource(R.string.settings_screen_cached_items_title),
            description = stringResource(R.string.settings_screen_cached_items_hint),
            onclick = { navController.showCachedItemsSettings() },
          )
        }
      }
    },
  )
}
