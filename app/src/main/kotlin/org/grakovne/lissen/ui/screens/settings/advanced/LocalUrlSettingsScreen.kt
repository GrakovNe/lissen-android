package org.grakovne.lissen.ui.screens.settings.advanced

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.grakovne.lissen.R
import org.grakovne.lissen.lib.domain.connection.LocalUrl
import org.grakovne.lissen.ui.screens.common.hasLocationPermission
import org.grakovne.lissen.viewmodel.SettingsViewModel
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalUrlSettingsScreen(onBack: () -> Unit) {
  val settingsViewModel: SettingsViewModel = hiltViewModel()
  val localUrls = settingsViewModel.localUrls.observeAsState(emptyList())

  val context = LocalContext.current

  val fabHeight = 56.dp
  val additionalPadding = 16.dp

  val state = rememberLazyListState()
  val coroutineScope = rememberCoroutineScope()

  var hasPermission by remember { mutableStateOf(hasLocationPermission(context)) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.settings_screen_internal_connection_url_title),
            style = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colorScheme.onSurface,
          )
        },
        navigationIcon = {
          IconButton(onClick = {
            onBack()
          }) {
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
      LazyColumn(
        state = state,
        contentPadding =
          PaddingValues(
            top = innerPadding.calculateTopPadding(),
            bottom = innerPadding.calculateBottomPadding() + fabHeight + additionalPadding,
          ),
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        val customHeaders =
          when (localUrls.value.isEmpty()) {
            true -> listOf(LocalUrl.empty())
            false -> localUrls.value
          }

        itemsIndexed(customHeaders) { index, header ->
          LocalUrlComposable(
            enabled = hasPermission,
            url = header,
            onChanged = { newPair ->
              val updatedList = customHeaders.toMutableList()
              updatedList[index] = newPair

              settingsViewModel.updateLocalUrls(updatedList)
            },
            onDelete = { pair ->
              val updatedList = customHeaders.toMutableList()
              updatedList.remove(pair)

              if (updatedList.isEmpty()) {
                updatedList.add(LocalUrl.empty())
              }

              settingsViewModel.updateLocalUrls(updatedList)
            },
          )

          if (index < customHeaders.size - 1) {
            HorizontalDivider(
              modifier =
                Modifier
                  .height(1.dp)
                  .padding(horizontal = 24.dp),
            )
          }
        }
      }
    },
    floatingActionButtonPosition = FabPosition.Center,
    floatingActionButton = {
      when (hasPermission) {
        true -> {
          FloatingActionButton(
            containerColor = colorScheme.primary,
            shape = CircleShape,
            onClick = {
              val updatedList = localUrls.value.toMutableList()
              updatedList.add(LocalUrl.empty())
              settingsViewModel.updateLocalUrls(updatedList)

              coroutineScope.launch {
                state.scrollToItem(max(0, updatedList.size - 1))
              }
            },
          ) {
            Icon(
              imageVector = Icons.Filled.Add,
              contentDescription = "Add",
            )
          }
        }

        false -> LocationPermissionBanner { hasPermission = it }
      }
    },
  )
}

@Composable
fun LocationPermissionBanner(
  modifier: Modifier = Modifier,
  onResult: (Boolean) -> Unit,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = Icons.Default.LocationOn,
      contentDescription = null,
      tint = colorScheme.primary,
      modifier = Modifier.padding(end = 12.dp),
    )

    val permissionRequestLauncher =
      rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = onResult,
      )

    Text(
      text = stringResource(R.string.location_permission_request_hint),
      style =
        typography.bodyMedium.copy(
          color = colorScheme.onSurface,
        ),
      modifier = Modifier.weight(1f),
    )

    TextButton(
      onClick = { permissionRequestLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
    ) {
      Text(
        text = stringResource(R.string.permission_request_grant_button),
        style =
          typography.bodyMedium.copy(
            color = colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
          ),
      )
    }
  }
}
