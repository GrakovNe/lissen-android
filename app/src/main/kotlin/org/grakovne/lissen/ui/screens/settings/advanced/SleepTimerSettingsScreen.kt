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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.ui.components.LissenModalBottomSheet
import org.grakovne.lissen.ui.components.slider.CommonSlider
import org.grakovne.lissen.ui.screens.settings.composable.SettingsToggleItem
import org.grakovne.lissen.ui.screens.settings.composable.SettingsTopAppBar
import org.grakovne.lissen.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@Composable
fun SleepTimerSettingsScreen(onBack: () -> Unit) {
  val viewModel: SettingsViewModel = hiltViewModel()
  val fadeEnabled by viewModel.sleepTimerFadeEnabled.collectAsState()
  val fadeSeconds by viewModel.sleepTimerFadeSeconds.collectAsState()

  var durationExpanded by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      SettingsTopAppBar(
        title = stringResource(R.string.sleep_timer_settings_title),
        onBack = onBack,
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
        SettingsToggleItem(
          title = stringResource(R.string.sleep_timer_fade_title),
          description = stringResource(R.string.sleep_timer_fade_description),
          initialState = fadeEnabled,
        ) { viewModel.preferSleepTimerFadeEnabled(it) }

        FadeDurationRowComposable(
          seconds = fadeSeconds,
          enabled = fadeEnabled,
          onClicked = { durationExpanded = true },
        )
      }
    },
  )

  if (durationExpanded) {
    FadeDurationBottomSheet(
      currentSeconds = fadeSeconds,
      onDismissRequest = { durationExpanded = false },
      onUpdate = { viewModel.preferSleepTimerFadeSeconds(it) },
    )
  }
}

@Composable
private fun FadeDurationRowComposable(
  seconds: Int,
  enabled: Boolean,
  onClicked: () -> Unit,
) {
  val context = LocalContext.current

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .let {
          when (enabled) {
            true -> it.clickable { onClicked() }
            false -> it
          }
        }.padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.sleep_timer_fade_duration_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
        color =
          when (enabled) {
            true -> colorScheme.onBackground
            false -> colorScheme.onBackground.copy(alpha = 0.4f)
          },
      )
      Text(
        text = context.resources.getQuantityString(R.plurals.fade_duration_seconds, seconds, seconds),
        style = typography.bodyMedium,
        color =
          when (enabled) {
            true -> colorScheme.onSurfaceVariant
            false -> colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
          },
      )
    }
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FadeDurationBottomSheet(
  currentSeconds: Int,
  onDismissRequest: () -> Unit,
  onUpdate: (Int) -> Unit,
) {
  val view: View = LocalView.current
  val context = LocalContext.current
  var selectedSeconds by remember { mutableIntStateOf(currentSeconds) }

  LissenModalBottomSheet(
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
          text = stringResource(R.string.sleep_timer_fade_duration_title),
          style = typography.bodyLarge,
        )

        CommonSlider(
          internalValue = selectedSeconds.coerceIn(FADE_MIN_SECONDS, FADE_MAX_SECONDS),
          range = FADE_MIN_SECONDS..FADE_MAX_SECONDS,
          formatHeader = { value ->
            val seconds = value.roundToInt().coerceIn(FADE_MIN_SECONDS, FADE_MAX_SECONDS)
            context.resources.getQuantityString(R.plurals.fade_duration_seconds, seconds, seconds)
          },
          formatIndex = { "$it" },
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(vertical = 16.dp),
          labeledIndexes = (FADE_MIN_SECONDS..FADE_MAX_SECONDS step 5).toList(),
          onUpdate = {
            val seconds = it.roundToInt().coerceIn(FADE_MIN_SECONDS, FADE_MAX_SECONDS)
            selectedSeconds = seconds
            onUpdate(seconds)
          },
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          fadeTimePresets.forEach { preset ->
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

private const val FADE_MIN_SECONDS = PlaybackPreferences.MIN_SLEEP_TIMER_FADE_SECONDS
private const val FADE_MAX_SECONDS = PlaybackPreferences.MAX_SLEEP_TIMER_FADE_SECONDS

private val fadeTimePresets = listOf(5, 10, 15, 30, 60)
