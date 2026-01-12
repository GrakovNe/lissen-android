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
import org.grakovne.lissen.lib.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.lib.domain.DurationTimerOption
import org.grakovne.lissen.lib.domain.TimerOption

@Composable
fun SleepTimerSlider(
  option: TimerOption?,
  modifier: Modifier = Modifier,
  onUpdate: (TimerOption?) -> Unit,
) {
  val sliderRange = INTERNAL_DISABLED..INTERNAL_CHAPTER_END

  val valueModifier: (Float) -> Unit = { onUpdate(it.toInt().toOption()) }

  val sliderState =
    rememberSaveable(saver = SliderState.saver(valueModifier)) {
      SliderState(
        current = option.toValue(),
        bounds = sliderRange,
        onUpdate = valueModifier,
      )
    }

  LaunchedEffect(Unit) { sliderState.snapTo(sliderState.current) }
  LaunchedEffect(option) { sliderState.animateDecayTo(option.toValue().toFloat()) }

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = sliderState.current.toInt().toLabel(),
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
          formatIndex = { it.toLabel() },
          maxIndex = INTERNAL_CHAPTER_END,
        )
      }
    }
  }
}

private const val INTERNAL_DISABLED = 0
private const val INTERNAL_CHAPTER_END = 61

private fun TimerOption?.toValue(): Int =
  when (this) {
    null -> INTERNAL_DISABLED
    is DurationTimerOption -> duration.coerceIn(1, 60)
    CurrentEpisodeTimerOption -> INTERNAL_CHAPTER_END
  }

private fun Int.toOption(): TimerOption? =
  when (this) {
    INTERNAL_DISABLED -> null
    INTERNAL_CHAPTER_END -> CurrentEpisodeTimerOption
    else -> DurationTimerOption(this)
  }

private fun Int.toLabel(): String =
  when (this) {
    INTERNAL_DISABLED -> "Disabled"
    INTERNAL_CHAPTER_END -> "When the chapter ends"
    else -> "$this min"
  }

private const val visibleSegments = 12
