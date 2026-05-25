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
import kotlin.math.roundToInt

@Composable
fun CommonSlider(
  internalValue: Int,
  range: IntRange,
  formatHeader: (Float) -> String,
  formatIndex: (Int) -> Any,
  modifier: Modifier = Modifier,
  labeledIndexes: List<Int> = emptyList(),
  onUpdate: (Float) -> Unit,
) {
  val floatRange = range.first.toFloat()..range.last.toFloat()

  val sliderState =
    rememberSaveable(saver = SliderState.saver(onUpdate)) {
      SliderState(
        current = internalValue,
        bounds = range,
        onUpdate = onUpdate,
      )
    }

  LaunchedEffect(Unit) { sliderState.snapTo(sliderState.current) }
  LaunchedEffect(internalValue) { sliderState.animateDecayTo(internalValue.toFloat().coerceIn(floatRange)) }

  val current = sliderState.current.coerceIn(floatRange)

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = formatHeader(current),
      style = typography.headlineSmall,
    )
    Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null)

    BoxWithConstraints(
      modifier =
        Modifier
          .fillMaxWidth()
          .sliderDrag(sliderState, VISIBLE_SEGMENTS),
      contentAlignment = Alignment.TopCenter,
    ) {
      val segmentWidth: Dp = maxWidth / VISIBLE_SEGMENTS
      val segmentPixelWidth = constraints.maxWidth.toFloat() / VISIBLE_SEGMENTS
      val visibleSegmentCount = (VISIBLE_SEGMENTS + 1) / 2
      val minIndex = (current - visibleSegmentCount).roundToInt().coerceAtLeast(range.first)
      val maxIndex = (current + visibleSegmentCount).roundToInt().coerceAtMost(range.last)
      val centerPixel = constraints.maxWidth / 2f

      for (index in minIndex..maxIndex) {
        SpeedSliderSegment(
          index = index,
          currentValue = current,
          segmentWidth = segmentWidth,
          segmentPixelWidth = segmentPixelWidth,
          centerPixel = centerPixel,
          barColor = colorScheme.onSurface,
          formatIndex = { formatIndex(index) },
          labeledIndexes = labeledIndexes,
        )
      }
    }
  }
}

private const val VISIBLE_SEGMENTS = 12
