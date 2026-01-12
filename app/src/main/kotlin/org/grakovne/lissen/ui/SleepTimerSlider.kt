package org.grakovne.lissen.ui

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
  option: TimerOption?, // null = disabled
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

  LaunchedEffect(Unit) {
    sliderState.snapTo(sliderState.current)
  }

  LaunchedEffect(option) {
    sliderState.animateDecayTo(option.toInternal().toFloat())
  }

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = sliderState.current.roundToInt().toLabel(),
      style = typography.headlineSmall,
    )
    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)

    BoxWithConstraints(
      modifier = Modifier.fillMaxWidth().sliderDrag(sliderState, totalSegments),
      contentAlignment = Alignment.TopCenter,
    ) {
      val segmentWidth: Dp = maxWidth / totalSegments
      val segmentPixelWidth = constraints.maxWidth.toFloat() / totalSegments
      val visibleCount = (totalSegments + 1) / 2
      val minIndex =
        (sliderState.current - visibleCount).toInt().coerceAtLeast(sliderRange.first)
      val maxIndex =
        (sliderState.current + visibleCount).toInt().coerceAtMost(sliderRange.last)
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

// ===== Segments =====

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
  val alpha = calculateAlpha(offset, centerPixel)

  Column(
    modifier =
      Modifier
        .width(segmentWidth)
        .graphicsLayer(
          translationX = offset,
          alpha = alpha,
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
      index == INTERNAL_DISABLED ->
        Text("Off", style = typography.bodyMedium)

      index == INTERNAL_CHAPTER_END ->
        Text("End", style = typography.bodyMedium)

      index % 5 == 0 ->
        Text("${index}m", style = typography.bodyMedium)
    }
  }
}

// ===== Gesture / inertia =====

@SuppressLint("ReturnFromAwaitPointerEventScope", "MultipleAwaitPointerEventScopes")
private fun Modifier.sliderDrag(
  state: TimerSliderState,
  segments: Int,
): Modifier =
  pointerInput(state) {
    val decay = splineBasedDecay<Float>(this)
    coroutineScope {
      while (isActive) {
        val pointerId = awaitPointerEventScope { awaitFirstDown().id }
        state.cancelAnimations()
        val velocityTracker = VelocityTracker()

        awaitPointerEventScope {
          horizontalDrag(pointerId) { change ->
            val deltaX = change.positionChange().x
            val step = size.width / segments
            val newValue = state.current - deltaX / step
            launch { state.snapTo(newValue) }
            velocityTracker.addPosition(change.uptimeMillis, change.position)
            change.consume()
          }
        }

        val velocity = velocityTracker.calculateVelocity().x / segments
        val target =
          decay.calculateTargetValue(state.current, -velocity)

        launch {
          state.animateDecayTo(target)
          state.snapToNearest()
        }
      }
    }
  }

// ===== State =====

class TimerSliderState(
  current: Int,
  val bounds: ClosedRange<Int>,
  private val onUpdate: (TimerOption?) -> Unit,
) {
  private val floatBounds =
    bounds.start.toFloat()..bounds.endInclusive.toFloat()

  private val anim = Animatable(current.toFloat())

  val current: Float get() = anim.value

  suspend fun cancelAnimations() {
    anim.stop()
  }

  suspend fun snapTo(value: Float) {
    val v = value.coerceIn(floatBounds)
    anim.snapTo(v)
    onUpdate(v.roundToInt().toOption())
  }

  suspend fun snapToNearest() {
    val target =
      anim.value
        .roundToInt()
        .toFloat()
        .coerceIn(floatBounds)
    anim.animateTo(target, animationSpec = springSpec)
    onUpdate(target.roundToInt().toOption())
  }

  suspend fun animateDecayTo(target: Float) {
    val initialVelocity =
      (target - current).coerceIn(-maxSpeed, maxSpeed)
    anim.animateTo(
      target.coerceIn(floatBounds),
      initialVelocity = initialVelocity,
      animationSpec = springSpec,
    )
  }

  companion object {
    private const val maxSpeed = 15f

    private val springSpec =
      FloatSpringSpec(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow,
      )

    fun saver(onUpdate: (TimerOption?) -> Unit) =
      Saver<TimerSliderState, List<Int>>(
        save = {
          listOf(
            it.current.roundToInt(),
            it.bounds.start,
            it.bounds.endInclusive,
          )
        },
        restore = {
          TimerSliderState(
            current = it[0],
            bounds = it[1]..it[2],
            onUpdate = onUpdate,
          )
        },
      )
  }
}

// ===== Internal mapping =====

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
    INTERNAL_DISABLED -> "Off"
    INTERNAL_CHAPTER_END -> "Chapter end"
    else -> "${this}m"
  }

// ===== Visual constants =====

private const val totalSegments = 12
private const val minAlpha = 0.25f
private val barThickness = 2.dp
private val barLength = 28.dp

private fun calculateAlpha(
  offset: Float,
  centerPixel: Float,
): Float {
  val factor = (offset / centerPixel).absoluteValue
  return 1f - (1f - minAlpha) * factor
}
