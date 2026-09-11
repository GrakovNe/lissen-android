package org.grakovne.lissen.ui.components.slider

import android.content.Context
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import org.grakovne.lissen.R
import kotlin.math.roundToInt

@Composable
fun ChapterSkipSlider(
  seconds: Int,
  modifier: Modifier = Modifier,
  onUpdate: (Int) -> Unit,
) {
  val sliderRange = SKIP_MIN_VALUE..SKIP_MAX_VALUE

  val onValueUpdate: (Float) -> Unit = { value ->
    onUpdate(value.roundToInt().coerceIn(sliderRange))
  }

  val sliderState =
    rememberSaveable(saver = SliderState.saver(onValueUpdate)) {
      SliderState(
        current = seconds.coerceIn(sliderRange),
        bounds = sliderRange,
        onUpdate = onValueUpdate,
      )
    }

  LaunchedEffect(Unit) {
    sliderState.snapTo(sliderState.current)
  }

  LaunchedEffect(seconds) {
    sliderState.animateDecayTo(seconds.coerceIn(sliderRange).toFloat())
  }

  val clampedCurrent = sliderState.current.coerceIn(sliderRange.first.toFloat(), sliderRange.last.toFloat())

  val context = LocalContext.current

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = clampedCurrent.toLabelText(context),
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
          .sliderDrag(sliderState, SKIP_VISIBLE_SEGMENTS),
      contentAlignment = Alignment.TopCenter,
    ) {
      val segmentWidth: Dp = maxWidth / SKIP_VISIBLE_SEGMENTS
      val segmentPixelWidth = constraints.maxWidth.toFloat() / SKIP_VISIBLE_SEGMENTS
      val visibleSegmentCount = (SKIP_VISIBLE_SEGMENTS + 1) / 2

      val minIndex =
        (clampedCurrent - visibleSegmentCount)
          .roundToInt()
          .coerceAtLeast(sliderRange.first)

      val maxIndex =
        (clampedCurrent + visibleSegmentCount)
          .roundToInt()
          .coerceAtMost(sliderRange.last)

      val centerPixel = constraints.maxWidth / 2f

      for (index in minIndex..maxIndex) {
        SpeedSliderSegment(
          index = index,
          currentValue = clampedCurrent,
          segmentWidth = segmentWidth,
          segmentPixelWidth = segmentPixelWidth,
          centerPixel = centerPixel,
          barColor = colorScheme.onSurface,
          formatIndex = { index.toSliderLabel() },
          labeledIndexes = SKIP_LABELED_INDEXES,
        )
      }
    }
  }
}

private fun Float.toLabelText(context: Context): String {
  val value = roundToInt().coerceIn(SKIP_MIN_VALUE, SKIP_MAX_VALUE)
  return when (value) {
    0 -> context.getString(R.string.chapter_skip_disabled)
    else -> "${value}s"
  }
}

private fun Int.toSliderLabel(): Any =
  when (this) {
    0 -> Icons.Outlined.Close
    else -> this
  }

private const val SKIP_MIN_VALUE = 0
private const val SKIP_MAX_VALUE = 120
private const val SKIP_VISIBLE_SEGMENTS = 12

private val SKIP_LABELED_INDEXES =
  listOf(0) + (5..SKIP_MAX_VALUE step 5)
