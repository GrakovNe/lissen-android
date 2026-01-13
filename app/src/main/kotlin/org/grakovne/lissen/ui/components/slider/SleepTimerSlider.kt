package org.grakovne.lissen.ui.components.slider

import android.content.Context
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MusicNote
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
import org.grakovne.lissen.R
import org.grakovne.lissen.lib.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.lib.domain.DurationTimerOption
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.lib.domain.LibraryType.LIBRARY
import org.grakovne.lissen.lib.domain.LibraryType.PODCAST
import org.grakovne.lissen.lib.domain.LibraryType.UNKNOWN
import org.grakovne.lissen.lib.domain.TimerOption
import kotlin.math.roundToInt

@Composable
fun SleepTimerSlider(
  context: Context,
  libraryType: LibraryType,
  option: TimerOption?,
  modifier: Modifier = Modifier,
  onUpdate: (TimerOption?) -> Unit,
) {
  val sliderRange = INTERNAL_MIN_VALUE..INTERNAL_MAX_VALUE

  val onValueUpdate: (Float) -> Unit = { value ->
    onUpdate(value.toTimerOption())
  }

  val sliderState =
    rememberSaveable(saver = SliderState.saver(onValueUpdate)) {
      SliderState(
        current = option.toInternalValue(),
        bounds = sliderRange,
        onUpdate = onValueUpdate,
      )
    }

  LaunchedEffect(Unit) {
    sliderState.snapTo(sliderState.current)
  }

  LaunchedEffect(option) {
    sliderState.animateDecayTo(option.toInternalValue().toFloat())
  }

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = sliderState.current.toLabelText(libraryType, context),
      style = typography.headlineSmall,
    )

    Icon(
      imageVector = Icons.Filled.ArrowDropDown,
      contentDescription = null,
    )

    BoxWithConstraints(
      modifier =
        Modifier
          .fillMaxWidth()
          .sliderDrag(sliderState, visibleSegments),
      contentAlignment = Alignment.TopCenter,
    ) {
      val segmentWidth: Dp = maxWidth / visibleSegments
      val segmentPixelWidth = constraints.maxWidth.toFloat() / visibleSegments
      val visibleSegmentCount = (visibleSegments + 1) / 2

      val minIndex =
        (sliderState.current - visibleSegmentCount)
          .roundToInt()
          .coerceAtLeast(sliderRange.first)

      val maxIndex =
        (sliderState.current + visibleSegmentCount)
          .roundToInt()
          .coerceAtMost(sliderRange.last)

      val centerPixel = constraints.maxWidth / 2f

      for (index in minIndex..maxIndex) {
        SpeedSliderSegment(
          index = index,
          currentValue = sliderState.current,
          segmentWidth = segmentWidth,
          segmentPixelWidth = segmentPixelWidth,
          centerPixel = centerPixel,
          barColor = colorScheme.onSurface,
          formatIndex = { index.toLabelIcon() },
          labeledIndexes = labeledIndexes,
        )
      }
    }
  }
}

private fun TimerOption?.toInternalValue(): Int =
  when (this) {
    null -> INTERNAL_DISABLED
    is DurationTimerOption -> duration.coerceIn(1, INTERNAL_MAX_VALUE)
    CurrentEpisodeTimerOption -> INTERNAL_CHAPTER_END
  }

private fun Float.toTimerOption(): TimerOption? {
  val value = roundToInt()
  return when (value) {
    INTERNAL_DISABLED -> null
    INTERNAL_CHAPTER_END -> CurrentEpisodeTimerOption
    else -> DurationTimerOption(value)
  }
}

private fun Float.toLabelText(
  libraryType: LibraryType,
  context: Context,
): String {
  val value = roundToInt()
  return when (value) {
    INTERNAL_DISABLED -> context.getString(R.string.timer_option_disabled)
    INTERNAL_CHAPTER_END ->
      when (libraryType) {
        LIBRARY -> context.getString(R.string.timer_option_after_current_chapter)
        PODCAST, UNKNOWN -> context.getString(R.string.timer_option_after_current_episode)
      }

    else ->
      context.resources.getQuantityString(
        R.plurals.timer_option_after_time,
        value,
        value,
      )
  }
}

private fun Int.toLabelIcon(): Any =
  when (this) {
    INTERNAL_DISABLED -> Icons.Outlined.Close
    INTERNAL_CHAPTER_END -> Icons.Outlined.MusicNote
    else -> this
  }

private const val INTERNAL_MIN_VALUE = -1
private const val INTERNAL_MAX_VALUE = 120

private const val INTERNAL_DISABLED = 0
private const val INTERNAL_CHAPTER_END = -1

private const val visibleSegments = 12

private val labeledIndexes =
  listOf(
    INTERNAL_CHAPTER_END,
    INTERNAL_DISABLED,
  ) + (5..INTERNAL_MAX_VALUE step 5)
