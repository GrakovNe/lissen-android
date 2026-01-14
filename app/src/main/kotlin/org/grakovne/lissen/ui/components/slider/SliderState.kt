package org.grakovne.lissen.ui.components.slider

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.Spring
import androidx.compose.runtime.saveable.Saver
import kotlin.math.roundToInt

class SliderState(
  current: Int,
  val bounds: ClosedRange<Int>,
  private val onUpdate: (Float) -> Unit,
) {
  private val floatBounds = bounds.start.toFloat()..bounds.endInclusive.toFloat()
  private val animState = Animatable(current.toFloat())

  val current: Float
    get() = animState.value

  suspend fun cancelAnimations() {
    animState.stop()
  }

  suspend fun snapTo(value: Float) {
    val limited = value.coerceIn(floatBounds)
    animState.snapTo(limited)
    onUpdate(limited)
  }

  suspend fun snapToNearest() {
    val target =
      animState.value
        .roundToInt()
        .toFloat()
        .coerceIn(floatBounds)
    animState.animateTo(target, animationSpec = springSpec)
    onUpdate(target)
  }

  suspend fun animateDecayTo(target: Float) {
    val limitedTarget = target.coerceIn(floatBounds)
    val velocity = (limitedTarget - current).coerceIn(-maxSpeed, maxSpeed)
    animState.animateTo(
      targetValue = limitedTarget,
      initialVelocity = velocity,
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

    fun saver(onUpdate: (Float) -> Unit) =
      Saver<SliderState, List<Int>>(
        save = {
          listOf(
            it.current.roundToInt(),
            it.bounds.start,
            it.bounds.endInclusive,
          )
        },
        restore = {
          SliderState(
            current = it[0],
            bounds = it[1]..it[2],
            onUpdate = onUpdate,
          )
        },
      )
  }
}
