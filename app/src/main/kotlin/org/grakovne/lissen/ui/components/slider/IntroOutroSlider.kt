package org.grakovne.lissen.ui.components.slider

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.grakovne.lissen.common.withHaptic
import kotlin.math.abs
import kotlin.math.roundToInt

enum class IntroOutroSliderStyle { THIN, RULER }

/**
 * Two thumbs on one track: the left one measures seconds from the start of the episode,
 * the right one seconds from its end. Each half of the track spans [0, maxSeconds] from its edge.
 */
@Composable
fun IntroOutroSlider(
  introSeconds: Int,
  outroSeconds: Int,
  modifier: Modifier = Modifier,
  style: IntroOutroSliderStyle = IntroOutroSliderStyle.RULER,
  maxSeconds: Int = MAX_SECONDS,
  onUpdate: (introSeconds: Int, outroSeconds: Int) -> Unit,
) {
  val view = LocalView.current
  val textMeasurer = rememberTextMeasurer()
  val colorScheme = MaterialTheme.colorScheme

  var dragging by remember { mutableStateOf<Thumb?>(null) }

  // the gesture block outlives recompositions, so it must read the latest values, not the captured ones
  val currentIntro by rememberUpdatedState(introSeconds)
  val currentOutro by rememberUpdatedState(outroSeconds)
  val currentOnUpdate by rememberUpdatedState(onUpdate)

  val palette =
    Palette(
      surface = colorScheme.surface,
      onSurface = colorScheme.onSurface,
      variant = colorScheme.onSurfaceVariant,
      accent = colorScheme.primary,
    )

  val labelStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.onSurface)
  val tickStyle = TextStyle(fontSize = 10.sp, color = palette.variant)

  Canvas(
    modifier =
      modifier
        .fillMaxWidth()
        .height(TOTAL_HEIGHT)
        .systemGestureExclusion()
        .pointerInput(maxSeconds) {
          awaitEachGesture {
            val down = awaitFirstDown()
            val geometry = Geometry(size.width.toFloat(), INSET.toPx(), maxSeconds)
            val introX = geometry.introX(currentIntro)
            val outroX = geometry.outroX(currentOutro)

            val thumb =
              when {
                abs(down.position.x - introX) <= abs(down.position.x - outroX) -> Thumb.INTRO
                else -> Thumb.OUTRO
              }
            dragging = thumb

            var intro = currentIntro
            var outro = currentOutro

            fun apply(x: Float) {
              val (newIntro, newOutro) =
                when (thumb) {
                  Thumb.INTRO -> geometry.introSeconds(x) to outro
                  Thumb.OUTRO -> intro to geometry.outroSeconds(x)
                }
              if (newIntro != intro || newOutro != outro) {
                intro = newIntro
                outro = newOutro
                withHaptic(view) { currentOnUpdate(intro, outro) }
              }
            }

            apply(down.position.x)
            down.consume()

            horizontalDrag(down.id) { change ->
              apply(change.position.x)
              if (change.positionChange() != Offset.Zero) change.consume()
            }

            dragging = null
          }
        },
  ) {
    val geometry = Geometry(size.width, INSET.toPx(), maxSeconds)
    val introX = geometry.introX(introSeconds)
    val outroX = geometry.outroX(outroSeconds)

    when (style) {
      IntroOutroSliderStyle.THIN -> {
        drawThin(geometry, introX, outroX, palette)
        drawScale(geometry, palette, textMeasurer, tickStyle)
      }

      IntroOutroSliderStyle.RULER -> {
        drawRuler(geometry, introX, outroX, palette, textMeasurer, tickStyle)
      }
    }

    listOf(Thumb.INTRO to introX, Thumb.OUTRO to outroX).forEach { (thumb, x) ->
      val active = dragging == thumb
      when (style) {
        IntroOutroSliderStyle.RULER -> drawPillThumb(x, active, palette)
        else -> drawRoundThumb(x, active, palette)
      }

      val seconds = if (thumb == Thumb.INTRO) introSeconds else outroSeconds
      val label = textMeasurer.measure(seconds.toClock(), labelStyle)
      // the labels stay on their own side of the middle, so they never overlap when both thumbs meet
      val centered = x - label.size.width / 2f
      val mid = size.width / 2f
      val labelX =
        when (thumb) {
          Thumb.INTRO -> centered.coerceIn(0f, mid - label.size.width - LABEL_GAP.toPx())
          Thumb.OUTRO -> centered.coerceIn(mid + LABEL_GAP.toPx(), size.width - label.size.width)
        }
      drawText(
        textLayoutResult = label,
        topLeft = Offset(labelX, TRACK_Y.toPx() - THUMB_RADIUS.toPx() - LABEL_GAP.toPx() - label.size.height),
      )
    }
  }
}

/** Variant B: a thin quiet track, only the cut-off parts get the accent. */
private fun DrawScope.drawThin(
  geometry: Geometry,
  introX: Float,
  outroX: Float,
  palette: Palette,
) {
  val trackY = TRACK_Y.toPx()
  val trackHeight = 4.dp.toPx()

  drawRoundRect(
    color = palette.onSurface.copy(alpha = 0.16f),
    topLeft = Offset(geometry.start, trackY - trackHeight / 2),
    size = Size(geometry.length, trackHeight),
    cornerRadius = CornerRadius(trackHeight / 2),
  )
  listOf(geometry.start to introX, outroX to geometry.end).forEach { (from, to) ->
    if (to - from <= 0f) return@forEach
    drawRoundRect(
      color = palette.accent,
      topLeft = Offset(from, trackY - trackHeight / 2),
      size = Size(to - from, trackHeight),
      cornerRadius = CornerRadius(trackHeight / 2),
    )
  }
}

/** Variant C: the ruler of the volume slider, cut-off ticks tinted, the middle fading out. */
private fun DrawScope.drawRuler(
  geometry: Geometry,
  introX: Float,
  outroX: Float,
  palette: Palette,
  textMeasurer: TextMeasurer,
  tickStyle: TextStyle,
) {
  val trackY = TRACK_Y.toPx()
  val majorHalf = 14.dp.toPx()
  val minorHalf = 7.dp.toPx()

  for (seconds in 0..geometry.maxSeconds step RULER_TICK_SECONDS) {
    val major = seconds % LABEL_SECONDS == 0
    val distance = seconds * geometry.pxPerSecond
    val alpha = (1f - distance / geometry.half).coerceIn(MIN_TICK_ALPHA, 1f)
    val length = if (major) majorHalf else minorHalf

    listOf(geometry.start + distance, geometry.end - distance).forEach { x ->
      val cut = x < introX || x > outroX
      drawLine(
        color = if (cut) palette.accent.copy(alpha = alpha) else palette.onSurface.copy(alpha = alpha * 0.7f),
        start = Offset(x, trackY - length),
        end = Offset(x, trackY + length),
        strokeWidth = 1.5.dp.toPx(),
        cap = StrokeCap.Round,
      )
    }

    if (major && seconds != 0 && seconds != geometry.maxSeconds) {
      val label = textMeasurer.measure(seconds.toClock(), tickStyle)
      listOf(geometry.start + distance, geometry.end - distance).forEach { x ->
        drawText(
          textLayoutResult = label,
          color = palette.variant.copy(alpha = alpha),
          topLeft = Offset(x - label.size.width / 2f, trackY + majorHalf + 4.dp.toPx()),
        )
      }
    }
  }
}

private fun DrawScope.drawScale(
  geometry: Geometry,
  palette: Palette,
  textMeasurer: TextMeasurer,
  tickStyle: TextStyle,
) {
  val top = TRACK_Y.toPx() + 10.dp.toPx()
  for (seconds in 0..geometry.maxSeconds step TICK_SECONDS) {
    val major = seconds % LABEL_SECONDS == 0
    val distance = seconds * geometry.pxPerSecond
    val alpha = (1f - distance / geometry.half).coerceIn(MIN_TICK_ALPHA, 1f)
    val tickHeight = (if (major) MAJOR_TICK else MINOR_TICK).toPx()

    listOf(geometry.start + distance, geometry.end - distance).forEach { x ->
      drawLine(
        color = palette.variant.copy(alpha = alpha * 0.6f),
        start = Offset(x, top),
        end = Offset(x, top + tickHeight),
        strokeWidth = 1.5.dp.toPx(),
      )
    }

    if (major && seconds != 0 && seconds != geometry.maxSeconds) {
      val label = textMeasurer.measure(seconds.toClock(), tickStyle)
      listOf(geometry.start + distance, geometry.end - distance).forEach { x ->
        drawText(
          textLayoutResult = label,
          color = palette.variant.copy(alpha = alpha),
          topLeft = Offset(x - label.size.width / 2f, top + MAJOR_TICK.toPx() + 2.dp.toPx()),
        )
      }
    }
  }
}

private fun DrawScope.drawRoundThumb(
  x: Float,
  active: Boolean,
  palette: Palette,
) {
  val trackY = TRACK_Y.toPx()
  val radius = THUMB_RADIUS.toPx() * if (active) 1.15f else 1f
  drawCircle(color = palette.surface, radius = radius + 2.dp.toPx(), center = Offset(x, trackY))
  drawCircle(color = palette.onSurface, radius = radius, center = Offset(x, trackY))
}

private fun DrawScope.drawPillThumb(
  x: Float,
  active: Boolean,
  palette: Palette,
) {
  val trackY = TRACK_Y.toPx()
  val pillWidth = (if (active) 6.dp else 4.dp).toPx()
  val pillHalf = 18.dp.toPx()
  drawRoundRect(
    color = palette.surface,
    topLeft = Offset(x - pillWidth / 2 - 2.dp.toPx(), trackY - pillHalf - 2.dp.toPx()),
    size = Size(pillWidth + 4.dp.toPx(), pillHalf * 2 + 4.dp.toPx()),
    cornerRadius = CornerRadius(pillWidth),
  )
  drawRoundRect(
    color = palette.onSurface,
    topLeft = Offset(x - pillWidth / 2, trackY - pillHalf),
    size = Size(pillWidth, pillHalf * 2),
    cornerRadius = CornerRadius(pillWidth / 2),
  )
}

private class Geometry(
  width: Float,
  inset: Float,
  val maxSeconds: Int,
) {
  val start = inset
  val end = width - inset
  val length = end - start
  val half = length / 2f
  val pxPerSecond = half / maxSeconds

  fun introX(seconds: Int) = start + seconds * pxPerSecond

  fun outroX(seconds: Int) = end - seconds * pxPerSecond

  fun introSeconds(x: Float) = ((x - start) / pxPerSecond).toSteppedSeconds(maxSeconds)

  fun outroSeconds(x: Float) = ((end - x) / pxPerSecond).toSteppedSeconds(maxSeconds)
}

private class Palette(
  val surface: Color,
  val onSurface: Color,
  val variant: Color,
  val accent: Color,
)

private enum class Thumb { INTRO, OUTRO }

private fun Float.toSteppedSeconds(maxSeconds: Int): Int = ((this / STEP_SECONDS).roundToInt() * STEP_SECONDS).coerceIn(0, maxSeconds)

internal fun Int.toClock(): String = "%d:%02d".format(this / 60, this % 60)

private const val MAX_SECONDS = 300
private const val STEP_SECONDS = 5
private const val TICK_SECONDS = 15
private const val RULER_TICK_SECONDS = 10
private const val LABEL_SECONDS = 60
private const val MIN_TICK_ALPHA = 0.15f

private val TOTAL_HEIGHT = 88.dp
private val TRACK_Y = 42.dp
private val THUMB_RADIUS = 10.dp
private val LABEL_GAP = 6.dp
private val MAJOR_TICK = 10.dp
private val MINOR_TICK = 5.dp
private val INSET = 12.dp
