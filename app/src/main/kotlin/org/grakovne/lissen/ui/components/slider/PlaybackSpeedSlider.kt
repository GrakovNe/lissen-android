package org.grakovne.lissen.ui.components.slider

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
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
  val valueModifier: (Float) -> Unit = { onSpeedUpdate(it.toInt().toSpeed()) }

  val sliderState =
    rememberSaveable(saver = SliderState.saver(valueModifier)) {
      SliderState(
        current = speed.toSliderValue(),
        bounds = sliderRange,
        onUpdate = valueModifier,
      )
    }

  LaunchedEffect(Unit) { sliderState.snapTo(sliderState.current) }
  LaunchedEffect(speed) { sliderState.animateDecayTo(speed.toSliderValue().toFloat()) }

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = String.format(Locale.US, "%.2fx", sliderState.current.roundToInt().toSpeed()),
      style = typography.headlineSmall,
    )
    Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null)

    BoxWithConstraints(
      modifier =
        Modifier
          .fillMaxWidth()
          .sliderDrag(sliderState, visibleSegments),
      contentAlignment = Alignment.TopCenter,
    ) {
      val segmentWidth: Dp = maxWidth / visibleSegments
      val segmentPixelWidth: Float = constraints.maxWidth.toFloat() / visibleSegments
      val visibleSegmentCount = (visibleSegments + 1) / 2
      val minIndex = (sliderState.current - visibleSegmentCount).toInt().coerceAtLeast(sliderRange.first)
      val maxIndex = (sliderState.current + visibleSegmentCount).toInt().coerceAtMost(sliderRange.last)
      val centerPixel = constraints.maxWidth / 2f

      for (index in minIndex..maxIndex) {
        SpeedSliderSegment(
          index = index,
          currentValue = sliderState.current,
          segmentWidth = segmentWidth,
          segmentPixelWidth = segmentPixelWidth,
          centerPixel = centerPixel,
          barColor = colorScheme.onSurface,
          formatIndex = { String.format(Locale.US, "%.2f", index.toSpeed()) },
        )
      }
    }
  }
}

private const val speedStep = 0.05f
private const val visibleSegments = 12

private fun Float.toSliderValue(): Int = (this / speedStep).roundToInt()

private fun Int.toSpeed(): Float = this * speedStep
