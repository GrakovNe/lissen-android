package org.grakovne.lissen.ui.components.slider

import android.annotation.SuppressLint
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@SuppressLint("ReturnFromAwaitPointerEventScope", "MultipleAwaitPointerEventScopes")
fun Modifier.sliderDrag(
  state: SliderState,
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
