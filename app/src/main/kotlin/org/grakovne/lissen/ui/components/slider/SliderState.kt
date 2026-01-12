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
  val current: Float get() = animState.value

  suspend fun cancelAnimations() {
    animState.stop()
  }

  suspend fun snapTo(value: Float) {
    val limitedValue = value.coerceIn(floatBounds)
    animState.snapTo(limitedValue)
    onUpdate(limitedValue)
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
    val initialVelocity = (target - current).coerceIn(-maxSpeed, maxSpeed)
    animState.animateTo(target.coerceIn(floatBounds), initialVelocity = initialVelocity, animationSpec = springSpec)
  }

  companion object {
    private const val maxSpeed = 10f
    private val springSpec = FloatSpringSpec(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)

    fun saver(onUpdate: (Float) -> Unit) =
      Saver<SliderState, List<Any>>(
        save = { listOf(it.current.roundToInt(), it.bounds.start, it.bounds.endInclusive) },
        restore = {
          SliderState(
            current = it[0] as Int,
            bounds = (it[1] as Int)..(it[2] as Int),
            onUpdate = onUpdate,
          )
        },
      )
  }
}
