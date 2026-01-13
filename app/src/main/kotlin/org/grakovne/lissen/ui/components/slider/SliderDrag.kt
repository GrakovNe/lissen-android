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
import kotlin.math.abs

@SuppressLint("ReturnFromAwaitPointerEventScope", "MultipleAwaitPointerEventScopes")
fun Modifier.sliderDrag(
  state: SliderState,
  segments: Int,
): Modifier =
  pointerInput(state, segments) {
    val decay = splineBasedDecay<Float>(this)

    coroutineScope {
      while (isActive) {
        val pointerId = awaitPointerEventScope { awaitFirstDown().id }
        state.cancelAnimations()

        val velocityTracker = VelocityTracker()
        var lastValue = state.current

        awaitPointerEventScope {
          horizontalDrag(pointerId) { change ->
            val deltaX = change.positionChange().x
            if (deltaX == 0f) return@horizontalDrag

            val pixelsPerStep = size.width / segments.toFloat()
            val deltaValue = -deltaX / pixelsPerStep
            val newValue = lastValue + deltaValue
            lastValue = newValue

            launch {
              state.snapTo(newValue)
            }

            velocityTracker.addPosition(
              change.uptimeMillis,
              change.position,
            )

            change.consume()
          }
        }

        val velocityPx = velocityTracker.calculateVelocity().x
        val pixelsPerStep = size.width / segments.toFloat()

        val velocityValue = (-velocityPx / pixelsPerStep).coerceIn(-50f, 50f)

        if (abs(velocityValue) < 0.2f) {
          launch { state.snapToNearest() }
          continue
        }

        val target =
          decay.calculateTargetValue(
            state.current,
            velocityValue,
          )

        launch {
          state.animateDecayTo(target)
          state.snapToNearest()
        }
      }
    }
  }
