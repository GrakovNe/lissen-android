package org.grakovne.lissen.ui.screens.settings.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.EqualizerSettings
import org.grakovne.lissen.playback.EqualizerCapabilities
import org.grakovne.lissen.ui.components.LissenModalBottomSheet
import org.grakovne.lissen.ui.components.slider.EQUALIZER_SLIDER_HEIGHT
import org.grakovne.lissen.ui.components.slider.EQUALIZER_THUMB_RADIUS
import org.grakovne.lissen.ui.components.slider.EqualizerBandSlider
import org.grakovne.lissen.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@Composable
fun EqualizerSettingsComposable(viewModel: SettingsViewModel) {
  var equalizerExpanded by remember { mutableStateOf(false) }
  val settings by viewModel.equalizer.collectAsState()
  val capabilities by viewModel.equalizerCapabilities.collectAsState()

  EqualizerSettingsRow(
    capabilities = capabilities,
    active = settings.isActive,
    onClick = { equalizerExpanded = true },
  )

  if (equalizerExpanded) {
    capabilities
      ?.takeIf { it.available }
      ?.let { deviceCapabilities ->
        LissenModalBottomSheet(
          containerColor = colorScheme.background,
          scrollable = false,
          onDismissRequest = { equalizerExpanded = false },
          content = {
            EqualizerSettingsContent(
              settings = settings,
              capabilities = deviceCapabilities,
              onGainChange = viewModel::preferEqualizerGain,
              onReset = viewModel::resetEqualizer,
              modifier = Modifier.fillMaxWidth(),
            )
          },
        )
      }
  }
}

@Composable
internal fun EqualizerSettingsRow(
  capabilities: EqualizerCapabilities?,
  active: Boolean,
  onClick: () -> Unit,
) {
  if (capabilities?.available != true) return

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { onClick() }
        .padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.settings_screen_equalizer_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
      )
      Text(
        text =
          when (active) {
            true -> stringResource(R.string.settings_screen_equalizer_enabled)
            false -> stringResource(R.string.settings_screen_equalizer_disabled)
          },
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
internal fun EqualizerSettingsContent(
  settings: EqualizerSettings,
  capabilities: EqualizerCapabilities,
  onGainChange: (Int, Int) -> Unit,
  onReset: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val gainLabelHeight =
    with(LocalDensity.current) { typography.bodySmall.lineHeight.toDp() }
      .coerceAtLeast(GAIN_LABEL_MIN_HEIGHT)

  val span = (capabilities.maxDb - capabilities.minDb).toFloat()
  val zeroLineOffset =
    EQUALIZER_THUMB_RADIUS + (EQUALIZER_SLIDER_HEIGHT - EQUALIZER_THUMB_RADIUS * 2) * (capabilities.maxDb / span)

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = stringResource(R.string.settings_screen_equalizer_title),
      style = typography.bodyLarge,
    )

    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 28.dp)
          .padding(top = 16.dp),
    ) {
      HorizontalDivider(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(top = gainLabelHeight + BAND_LABEL_GAP + zeroLineOffset),
        color = colorScheme.outlineVariant,
      )

      Row(modifier = Modifier.fillMaxWidth()) {
        capabilities.bands.forEachIndexed { index, band ->
          EqualizerBandColumn(
            db = settings.gains.getOrElse(index) { 0 },
            centerFreqHz = band.centerFreqHz,
            minDb = capabilities.minDb,
            maxDb = capabilities.maxDb,
            labelHeight = gainLabelHeight,
            onGainChange = { onGainChange(index, it) },
            modifier = Modifier.weight(1f),
          )
        }
      }
    }

    val restoreDescription = stringResource(R.string.a11y_equalizer_restore)

    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .clickable(role = Role.Button) { onReset() }
          .semantics { contentDescription = restoreDescription }
          .padding(horizontal = 24.dp, vertical = 16.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.equalizer_restore_default),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        color = colorScheme.error,
      )
    }
  }
}

@Composable
private fun EqualizerBandColumn(
  db: Int,
  centerFreqHz: Int,
  minDb: Int,
  maxDb: Int,
  labelHeight: Dp,
  onGainChange: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val bandDescription = stringResource(R.string.a11y_equalizer_band, freqLabel(centerFreqHz))
  val bandState = "${gainLabel(db)} dB"

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier =
      modifier
        .clearAndSetSemantics {
          contentDescription = bandDescription
          stateDescription = bandState
          progressBarRangeInfo =
            ProgressBarRangeInfo(
              current = db.toFloat(),
              range = minDb.toFloat()..maxDb.toFloat(),
              steps = maxDb - minDb - 1,
            )

          setProgress { target ->
            onGainChange(target.roundToInt().coerceIn(minDb, maxDb))
            true
          }
        },
  ) {
    Box(
      modifier = Modifier.height(labelHeight),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = gainLabel(db),
        style =
          when (db) {
            0 -> typography.bodySmall
            else -> typography.bodySmall.copy(fontWeight = FontWeight.Medium)
          },
        color =
          when (db) {
            0 -> colorScheme.onSurfaceVariant
            else -> colorScheme.onSurface
          },
      )
    }

    EqualizerBandSlider(
      value = db,
      minDb = minDb,
      maxDb = maxDb,
      onValueChange = onGainChange,
      modifier = Modifier.padding(top = BAND_LABEL_GAP),
    )

    Text(
      text = freqLabel(centerFreqHz),
      style = typography.bodySmall,
      color = colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = BAND_LABEL_GAP),
    )
  }
}

internal fun gainLabel(db: Int): String =
  when {
    db == 0 -> "0"
    db > 0 -> "+$db"
    else -> "−${-db}"
  }

internal fun freqLabel(hz: Int): String =
  when {
    hz < 1000 -> {
      "$hz"
    }

    else -> {
      val kilo = (hz / 100f).roundToInt() / 10f
      when (kilo % 1f == 0f) {
        true -> "${kilo.toInt()}k"
        false -> "${kilo}k"
      }
    }
  }

private val GAIN_LABEL_MIN_HEIGHT = 20.dp
private val BAND_LABEL_GAP = 10.dp
