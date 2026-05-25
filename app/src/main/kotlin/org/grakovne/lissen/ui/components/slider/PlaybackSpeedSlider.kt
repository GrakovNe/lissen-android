package org.grakovne.lissen.ui.components.slider

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PlaybackSpeedSlider(
  speed: Float,
  speedRange: ClosedRange<Float>,
  modifier: Modifier = Modifier,
  onSpeedUpdate: (Float) -> Unit,
) {
  val sliderRange = speedRange.start.toSliderValue()..speedRange.endInclusive.toSliderValue()

  CommonSlider(
    internalValue = speed.toSliderValue(),
    range = sliderRange,
    formatHeader = { String.format(Locale.US, "%.2fx", it.roundToInt().toSpeed()) },
    formatIndex = { String.format(Locale.US, "%.2f", it.toSpeed()) },
    modifier = modifier,
    onUpdate = { onSpeedUpdate(it.toInt().toSpeed()) },
  )
}

private const val SPEED_STEP = 0.05f

private fun Float.toSliderValue(): Int = (this / SPEED_STEP).roundToInt()

private fun Int.toSpeed(): Float = this * SPEED_STEP
