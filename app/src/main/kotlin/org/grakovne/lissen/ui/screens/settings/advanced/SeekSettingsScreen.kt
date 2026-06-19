package org.grakovne.lissen.ui.screens.settings.advanced

import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.grakovne.lissen.R
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.domain.SeekTime
import org.grakovne.lissen.ui.components.slider.SeekTimeSlider
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SeekSettingsScreen(onBack: () -> Unit) {
  val viewModel: SettingsViewModel = hiltViewModel()
  val preferredSeekTime by viewModel.seekTime.collectAsState()

  var rewindExpanded by remember { mutableStateOf(false) }
  var forwardExpanded by remember { mutableStateOf(false) }

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
            .padding(innerPadding)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        SeekTimeRowComposable(
          title = stringResource(R.string.rewind_interval),
          currentSeconds = preferredSeekTime.rewind,
          onClicked = { rewindExpanded = true },
        )

        SeekTimeRowComposable(
          title = stringResource(R.string.forward_interval),
          currentSeconds = preferredSeekTime.forward,
          onClicked = { forwardExpanded = true },
        )
      }
    },
  )

  if (rewindExpanded) {
    SeekTimeBottomSheet(
      title = stringResource(R.string.rewind_interval),
      currentSeconds = preferredSeekTime.rewind,
      onDismissRequest = { rewindExpanded = false },
      onUpdate = { viewModel.preferRewindRewind(it) },
    )
  }

  if (forwardExpanded) {
    SeekTimeBottomSheet(
      title = stringResource(R.string.forward_interval),
      currentSeconds = preferredSeekTime.forward,
      onDismissRequest = { forwardExpanded = false },
      onUpdate = { viewModel.preferForwardRewind(it) },
    )
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SeekTimeBottomSheet(
  title: String,
  currentSeconds: Int,
  onDismissRequest: () -> Unit,
  onUpdate: (Int) -> Unit,
) {
  val view: View = LocalView.current
  val context = LocalContext.current
  var selectedSeconds by remember { mutableIntStateOf(currentSeconds) }

  ModalBottomSheet(
    containerColor = colorScheme.background,
    onDismissRequest = onDismissRequest,
    content = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = title,
          style = typography.bodyLarge,
        )

        SeekTimeSlider(
          context = context,
          seconds = selectedSeconds,
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(vertical = 16.dp),
          onUpdate = {
            selectedSeconds = it
            onUpdate(it)
          },
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          seekTimePresets.forEach { preset ->
            FilledTonalButton(
              onClick = {
                withHaptic(view) {
                  selectedSeconds = preset
                  onUpdate(preset)
                }
              },
              modifier = Modifier.size(56.dp),
              shape = CircleShape,
              colors =
                ButtonDefaults.filledTonalButtonColors(
                  containerColor =
                    if (selectedSeconds == preset) colorScheme.primary else colorScheme.surfaceContainer,
                  contentColor =
                    if (selectedSeconds == preset) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                ),
              contentPadding = PaddingValues(0.dp),
            ) {
              Text(
                text = "$preset",
                style =
                  if (selectedSeconds == preset) {
                    typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                  } else {
                    typography.labelMedium
                  },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    },
  )
}

@Composable
private fun SeekTimeRowComposable(
  title: String,
  currentSeconds: Int,
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
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
      )
      Text(
        text = context.resources.getQuantityString(R.plurals.seek_interval_seconds, currentSeconds, currentSeconds),
        style = typography.bodyMedium,
      )
    }
  }
}

private val seekTimePresets = listOf(5, 10, 15, 30, 60)
