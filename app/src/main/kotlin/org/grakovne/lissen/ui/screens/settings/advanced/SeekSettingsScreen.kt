package org.grakovne.lissen.ui.screens.settings.advanced

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.grakovne.lissen.R
import org.grakovne.lissen.lib.domain.SeekTime
import org.grakovne.lissen.lib.domain.SeekTimeOption
import org.grakovne.lissen.ui.screens.settings.composable.CommonSettingsItem
import org.grakovne.lissen.ui.screens.settings.composable.CommonSettingsItemComposable
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SeekSettingsScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val viewModel: SettingsViewModel = hiltViewModel()

  val preferredSeekTime by viewModel.seekTime.observeAsState()

  var rewindTimeExpanded by remember { mutableStateOf(false) }
  var forwardTimeExpanded by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.settings_screen_seek_time_title),
            style = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
          )
        },
        navigationIcon = {
          IconButton(onClick = { onBack() }) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = "Back",
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
          SeekTimeOptionComposable(
            title = stringResource(R.string.rewind_interval),
            currentOption = preferredSeekTime?.rewind ?: SeekTime.Default.rewind,
          ) { rewindTimeExpanded = true }

          SeekTimeOptionComposable(
            title = stringResource(R.string.forward_interval),
            currentOption = preferredSeekTime?.forward ?: SeekTime.Default.forward,
          ) { forwardTimeExpanded = true }
        }
      }
    },
  )

  if (rewindTimeExpanded) {
    CommonSettingsItemComposable(
      items = SeekTimeOption.entries.map { it.toSettingsItem(context) },
      selectedItem = preferredSeekTime?.rewind?.toSettingsItem(context),
      onDismissRequest = { rewindTimeExpanded = false },
      onItemSelected = { item ->
        SeekTimeOption
          .entries
          .find { it.name == item.id }
          ?.let { viewModel.preferRewindRewind(it) }
      },
    )
  }

  if (forwardTimeExpanded) {
    CommonSettingsItemComposable(
      items = SeekTimeOption.entries.map { it.toSettingsItem(context) },
      selectedItem = preferredSeekTime?.forward?.toSettingsItem(context),
      onDismissRequest = { forwardTimeExpanded = false },
      onItemSelected = { item ->
        SeekTimeOption
          .entries
          .find { it.name == item.id }
          ?.let { viewModel.preferForwardRewind(it) }
      },
    )
  }
}

@Composable
fun SeekTimeOptionComposable(
  title: String,
  currentOption: SeekTimeOption,
  onClicked: () -> Unit,
) {
  val context = LocalContext.current

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { onClicked() }
        .padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(
      modifier = Modifier.weight(1f),
    ) {
      Text(
        text = title,
        style =
          typography.bodyLarge.copy(
            fontWeight = FontWeight.SemiBold,
          ),
        modifier = Modifier.padding(bottom = 4.dp),
      )
      Text(
        text = currentOption.toItem(context),
        style = typography.bodyMedium,
      )
    }
  }
}

private fun SeekTimeOption.toSettingsItem(context: Context): CommonSettingsItem = CommonSettingsItem(this.name, this.toItem(context), null)

private fun SeekTimeOption.toItem(context: Context): String =
  when (this) {
    SeekTimeOption.SEEK_5 -> context.getString(R.string.seek_interval_5_seconds)
    SeekTimeOption.SEEK_10 -> context.getString(R.string.seek_interval_10_seconds)
    SeekTimeOption.SEEK_15 -> context.getString(R.string.seek_interval_15_seconds)
    SeekTimeOption.SEEK_30 -> context.getString(R.string.seek_interval_30_seconds)
    SeekTimeOption.SEEK_60 -> context.getString(R.string.seek_interval_60_seconds)
  }
