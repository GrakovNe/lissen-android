package org.grakovne.lissen.ui.components.slider

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.grakovne.lissen.lib.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.lib.domain.DurationTimerOption
import org.grakovne.lissen.lib.domain.TimerOption
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun SleepTimerSlider(
  option: TimerOption?,
  modifier: Modifier = Modifier,
  onUpdate: (TimerOption?) -> Unit,
) {
  val sliderRange = INTERNAL_DISABLED..INTERNAL_CHAPTER_END

  val sliderState =
    rememberSaveable(saver = TimerSliderState.saver(onUpdate)) {
      TimerSliderState(
        current = option.toInternal(),
        bounds = sliderRange,
        onUpdate = onUpdate,
      )
    }

  LaunchedEffect(Unit) { sliderState.snapTo(sliderState.current) }
  LaunchedEffect(option) { sliderState.animateDecayTo(option.toInternal().toFloat()) }

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = sliderState.current.roundToInt().toLabel(),
      style = typography.headlineSmall,
      textAlign = TextAlign.Center,
    )
    Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null)

    BoxWithConstraints(
      modifier = Modifier.fillMaxWidth().sliderDrag(sliderState, totalSegments),
      contentAlignment = Alignment.TopCenter,
    ) {
      val segmentWidth: Dp = maxWidth / totalSegments
      val segmentPixelWidth: Float = constraints.maxWidth.toFloat() / totalSegments
      val visibleSegmentCount = (totalSegments + 1) / 2
      val minIndex =
        (sliderState.current - visibleSegmentCount).toInt().coerceAtLeast(sliderRange.first)
      val maxIndex =
        (sliderState.current + visibleSegmentCount).toInt().coerceAtMost(sliderRange.last)
      val centerPixel = constraints.maxWidth / 2f

      for (index in minIndex..maxIndex) {
        TimerSliderSegment(
          index = index,
          currentValue = sliderState.current,
          segmentWidth = segmentWidth,
          segmentPixelWidth = segmentPixelWidth,
          centerPixel = centerPixel,
          barColor = colorScheme.onSurface,
        )
      }
    }
  }
}

@Composable
private fun TimerSliderSegment(
  index: Int,
  currentValue: Float,
  segmentWidth: Dp,
  segmentPixelWidth: Float,
  centerPixel: Float,
  barColor: Color,
) {
  val offset = (index - currentValue) * segmentPixelWidth
  val alphaValue = calculateAlpha(offset, centerPixel)

  Column(
    modifier =
      Modifier
        .width(segmentWidth)
        .graphicsLayer(
          alpha = alphaValue,
          translationX = offset,
        ),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier =
        Modifier
          .width(barThickness)
          .height(barLength)
          .background(barColor),
    )

    when {
      index == INTERNAL_DISABLED -> {
        Text(
          text = "Disabled",
          style = typography.bodySmall,
          textAlign = TextAlign.Center,
          modifier = Modifier.width(segmentWidth * 2),
        )
      }

      index == INTERNAL_CHAPTER_END -> {
        Text(
          text = "When the chapter ends",
          style = typography.bodySmall,
          textAlign = TextAlign.Center,
          modifier = Modifier.width(segmentWidth * 3),
        )
      }

      index % 5 == 0 -> {
        Text(
          text = "$index min",
          style = typography.bodyMedium,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

private fun calculateAlpha(
  offset: Float,
  centerPixel: Float,
): Float {
  val factor = (offset / centerPixel).absoluteValue
  return 1f - (1f - minAlpha) * factor
}

@SuppressLint("ReturnFromAwaitPointerEventScope", "MultipleAwaitPointerEventScopes")
private fun Modifier.sliderDrag(
  state: TimerSliderState,
  segments: Int,
): Modifier =
  pointerInput(state) {
    val decayAnimation = splineBasedDecay<Float>(this)
    coroutineScope {
      while (isActive) {
        val pointerId = awaitPointerEventScope { awaitFirstDown().id }
        state.cancelAnimations()
        val velocityTracker = VelocityTracker()

        awaitPointerEventScope {
          horizontalDrag(pointerId) { change ->
            val deltaX = change.positionChange().x
            val sliderStep = size.width / segments
            val newValue = state.current - deltaX / sliderStep
            launch { state.snapTo(newValue) }
            velocityTracker.addPosition(change.uptimeMillis, change.position)
            change.consume()
          }
        }

        val velocity = velocityTracker.calculateVelocity().x / segments
        val targetValue =
          decayAnimation.calculateTargetValue(state.current, -velocity)

        launch {
          state.animateDecayTo(targetValue)
          state.snapToNearest()
        }
      }
    }
  }

class TimerSliderState(
  current: Int,
  val bounds: ClosedRange<Int>,
  private val onUpdate: (TimerOption?) -> Unit,
) {
  private val floatBounds = bounds.start.toFloat()..bounds.endInclusive.toFloat()
  private val animState = Animatable(current.toFloat())
  val current: Float get() = animState.value

  suspend fun cancelAnimations() {
    animState.stop()
  }

  suspend fun snapTo(value: Float) {
    val limitedValue = value.coerceIn(floatBounds)
    animState.snapTo(limitedValue)
    onUpdate(limitedValue.roundToInt().toOption())
  }

  suspend fun snapToNearest() {
    val target =
      animState.value
        .roundToInt()
        .toFloat()
        .coerceIn(floatBounds)
    animState.animateTo(target, animationSpec = springSpec)
    onUpdate(target.roundToInt().toOption())
  }

  suspend fun animateDecayTo(target: Float) {
    val initialVelocity = (target - current).coerceIn(-maxSpeed, maxSpeed)
    animState.animateTo(
      target.coerceIn(floatBounds),
      initialVelocity = initialVelocity,
      animationSpec = springSpec,
    )
  }

  companion object {
    private const val maxSpeed = 10f
    private val springSpec =
      FloatSpringSpec(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow,
      )

    fun saver(onUpdate: (TimerOption?) -> Unit) =
      Saver<TimerSliderState, List<Any>>(
        save = { listOf(it.current.roundToInt(), it.bounds.start, it.bounds.endInclusive) },
        restore = {
          TimerSliderState(
            current = it[0] as Int,
            bounds = (it[1] as Int)..(it[2] as Int),
            onUpdate = onUpdate,
          )
        },
      )
  }
}

private const val INTERNAL_DISABLED = 0
private const val INTERNAL_CHAPTER_END = 61

private fun TimerOption?.toInternal(): Int =
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

private const val speedStep = 1f
private val barThickness = 2.dp
private val barLength = 28.dp
private const val totalSegments = 12
private const val minAlpha = 0.25f
