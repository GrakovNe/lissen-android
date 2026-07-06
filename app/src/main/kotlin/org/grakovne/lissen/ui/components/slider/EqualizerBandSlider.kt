package org.grakovne.lissen.ui.components.slider

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.common.withHaptic
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun EqualizerBandSlider(
  value: Int,
  minDb: Int,
  maxDb: Int,
  onValueChange: (Int) -> Unit,
  modifier: Modifier = Modifier,
  trackHeight: Dp = EQUALIZER_SLIDER_HEIGHT,
) {
  val view = LocalView.current
  val density = LocalDensity.current
  val currentValue by rememberUpdatedState(value)
  val currentOnValueChange by rememberUpdatedState(onValueChange)

  val trackColor = colorScheme.surfaceContainer
  val activeColor = colorScheme.onSurface.copy(alpha = 0.35f)
  val thumbColor = colorScheme.onSurface
  val zeroThumbColor = colorScheme.surface
  val zeroThumbBorderColor = colorScheme.outlineVariant

  val span = (maxDb - minDb).toFloat()

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(trackHeight)
        .pointerInput(minDb, maxDb) {
          val thumbRadius = with(density) { EQUALIZER_THUMB_RADIUS.toPx() }
          val usable = size.height - thumbRadius * 2

          fun dbAt(y: Float): Int =
            (maxDb - (y - thumbRadius) / usable * span)
              .roundToInt()
              .coerceIn(minDb, maxDb)

          var lastDb = 0

          fun update(y: Float) {
            val db = dbAt(y)
            if (db != lastDb) {
              lastDb = db
              withHaptic(view) { currentOnValueChange(db) }
            }
          }

          detectVerticalDragGestures(
            onDragStart = { offset ->
              lastDb = currentValue
              update(offset.y)
            },
            onVerticalDrag = { change, _ ->
              change.consume()
              update(change.position.y)
            },
          )
        },
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val thumbRadius = EQUALIZER_THUMB_RADIUS.toPx()
      val trackWidth = EQUALIZER_TRACK_WIDTH.toPx()
      val trackLeft = (size.width - trackWidth) / 2f
      val usable = size.height - thumbRadius * 2

      fun yOf(db: Float): Float = thumbRadius + (maxDb - db) / span * usable

      val yZero = yOf(0f)
      val yThumb = yOf(value.toFloat())

      drawRoundRect(
        color = trackColor,
        topLeft = Offset(trackLeft, 0f),
        size = Size(trackWidth, size.height),
        cornerRadius = CornerRadius(trackWidth / 2f),
      )

      if (value != 0) {
        drawRoundRect(
          color = activeColor,
          topLeft = Offset(trackLeft, minOf(yZero, yThumb)),
          size = Size(trackWidth, abs(yThumb - yZero)),
          cornerRadius = CornerRadius(trackWidth / 2f),
        )

        drawCircle(
          color = thumbColor,
          radius = thumbRadius,
          center = Offset(size.width / 2f, yThumb),
        )
      } else {
        drawCircle(
          color = zeroThumbColor,
          radius = thumbRadius,
          center = Offset(size.width / 2f, yThumb),
        )

        drawCircle(
          color = zeroThumbBorderColor,
          radius = thumbRadius - 1.dp.toPx(),
          center = Offset(size.width / 2f, yThumb),
          style = Stroke(width = 2.dp.toPx()),
        )
      }
    }
  }
}

val EQUALIZER_SLIDER_HEIGHT = 144.dp
val EQUALIZER_THUMB_RADIUS = 10.dp
private val EQUALIZER_TRACK_WIDTH = 4.dp
