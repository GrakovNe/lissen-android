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
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun PlaybackSpeedSlider(
  speed: Float,
  speedRange: ClosedRange<Float>,
  modifier: Modifier = Modifier,
  onSpeedUpdate: (Float) -> Unit,
) {
  val sliderRange = speedRange.start.toSliderValue()..speedRange.endInclusive.toSliderValue()
  val sliderState =
    rememberSaveable(saver = SpeedSliderState.saver(onSpeedUpdate)) {
      SpeedSliderState(
        current = speed.toSliderValue(),
        bounds = sliderRange,
        onUpdate = onSpeedUpdate,
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
      modifier = Modifier.fillMaxWidth().sliderDrag(sliderState, totalSegments),
      contentAlignment = Alignment.TopCenter,
    ) {
      val segmentWidth: Dp = maxWidth / totalSegments
      val segmentPixelWidth: Float = constraints.maxWidth.toFloat() / totalSegments
      val visibleSegmentCount = (totalSegments + 1) / 2
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
        )
      }
    }
  }
}

@Composable
private fun SpeedSliderSegment(
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
    modifier = Modifier.width(segmentWidth).graphicsLayer(alpha = alphaValue, translationX = offset),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier.width(barThickness).height(barLength).background(barColor),
    )
    if (index % 5 == 0) {
      Text(
        text = String.format(Locale.US, "%.2f", index.toSpeed()),
        style = typography.bodyMedium,
      )
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
  state: SpeedSliderState,
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
            val newSliderValue = state.current - deltaX / sliderStep
            launch { state.snapTo(newSliderValue) }
            velocityTracker.addPosition(change.uptimeMillis, change.position)
            change.consume()
          }
        }
        val velocity = velocityTracker.calculateVelocity().x / segments
        val targetValue = decayAnimation.calculateTargetValue(state.current, -velocity)
        launch {
          state.animateDecayTo(targetValue)
          state.snapToNearest()
        }
      }
    }
  }

class SpeedSliderState(
  current: Int,
  val bounds: ClosedRange<Int>,
  private val onUpdate: (Float) -> Unit,
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
    onUpdate(limitedValue.toInt().toSpeed())
  }

  suspend fun snapToNearest() {
    val target =
      animState.value
        .roundToInt()
        .toFloat()
        .coerceIn(floatBounds)
    animState.animateTo(target, animationSpec = springSpec)
    onUpdate(target.toInt().toSpeed())
  }

  suspend fun animateDecayTo(target: Float) {
    val initialVelocity = (target - current).coerceIn(-maxSpeed, maxSpeed)
    animState.animateTo(target.coerceIn(floatBounds), initialVelocity = initialVelocity, animationSpec = springSpec)
  }

  companion object {
    private const val maxSpeed = 10f
    private val springSpec = FloatSpringSpec(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)

    fun saver(onUpdate: (Float) -> Unit) =
      Saver<SpeedSliderState, List<Any>>(
        save = { listOf(it.current.roundToInt(), it.bounds.start, it.bounds.endInclusive) },
        restore = {
          SpeedSliderState(
            current = it[0] as Int,
            bounds = (it[1] as Int)..(it[2] as Int),
            onUpdate = onUpdate,
          )
        },
      )
  }
}

private const val speedStep = 0.05f
private val barThickness = 2.dp
private val barLength = 28.dp
private const val totalSegments = 12
private const val minAlpha = 0.25f

private fun Float.toSliderValue(): Int = (this / speedStep).roundToInt()

private fun Int.toSpeed(): Float = this * speedStep
