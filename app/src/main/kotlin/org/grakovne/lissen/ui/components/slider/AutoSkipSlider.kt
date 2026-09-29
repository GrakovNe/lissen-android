package org.grakovne.lissen.ui.components.slider

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.ui.extensions.formatTime
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Two thumbs on one ruler: the left one measures seconds from the start of the chapter,
 * the right one seconds from its end. Each half spans five minutes from its edge. Tapping
 * grabs the nearer thumb, so the thumbs can be reached wherever they sit.
 */
@Composable
fun AutoSkipSlider(
  introSeconds: Int,
  outroSeconds: Int,
  modifier: Modifier = Modifier,
  stateDescription: String? = null,
  onUpdateFinished: () -> Unit = {},
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
  val currentOnUpdateFinished by rememberUpdatedState(onUpdateFinished)

  val colors =
    RulerColors(
      surface = colorScheme.surface,
      onSurface = colorScheme.onSurface,
      variant = colorScheme.onSurfaceVariant,
      accent = colorScheme.primary,
    )

  // the header of the other rulers, a size down so two of them stay quiet inside a settings sheet
  val valueStyle = MaterialTheme.typography.titleMedium.copy(color = colors.onSurface)
  // numerals sit tighter without the body letter spacing, which keeps five-character labels a minute apart
  val tickStyle = MaterialTheme.typography.bodySmall.copy(color = colors.variant, letterSpacing = 0.sp)
  // the labels never change: laid out once, not on every frame of a drag
  val tickLabels =
    remember(textMeasurer, tickStyle) {
      (LABEL_SECONDS until MAX_SECONDS step LABEL_SECONDS).associateWith { textMeasurer.measure(it.formatTime(), tickStyle) }
    }

  Canvas(
    modifier =
      modifier
        .fillMaxWidth()
        .height(TOTAL_HEIGHT)
        .systemGestureExclusion()
        .semantics { stateDescription?.let { this.stateDescription = it } }
        .pointerInput(Unit) {
          awaitEachGesture {
            val down = awaitFirstDown()
            val geometry = RulerScale(size.width.toFloat(), INSET.toPx())
            val introX = geometry.introX(currentIntro)
            val outroX = geometry.outroX(currentOutro)
            val x = down.position.x

            val thumb = pickThumb(x, introX, outroX, mid = geometry.start + geometry.half, reach = THUMB_REACH.toPx())

            var intro = currentIntro
            var outro = currentOutro

            fun moveTo(x: Float) {
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

            // nothing moves before the touch slop is passed sideways: a vertical pull belongs to the
            // sheet, a touch that ends where it began is a tap that jumps the thumb there
            val drag =
              awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                change.consume()
                moveTo(change.position.x)
              }

            when {
              drag != null -> {
                dragging = thumb
                try {
                  horizontalDrag(drag.id) { change ->
                    change.consume()
                    moveTo(change.position.x)
                  }
                } finally {
                  // a sheet that closes mid-drag cancels the gesture; what was dragged so far still counts
                  dragging = null
                  currentOnUpdateFinished()
                }
              }

              currentEvent.changes.any { it.id == down.id && it.changedToUpIgnoreConsumed() } -> {
                moveTo(x)
                currentOnUpdateFinished()
              }

              else -> {}
            }
          }
        },
  ) {
    val geometry = RulerScale(size.width, INSET.toPx())
    val introX = geometry.introX(introSeconds)
    val outroX = geometry.outroX(outroSeconds)

    drawRuler(geometry, introX, outroX, colors, tickLabels)

    val thumbs = listOf(Thumb.INTRO to introX, Thumb.OUTRO to outroX)
    thumbs.forEach { (thumb, x) ->
      drawPillThumb(x, active = dragging == thumb, colors)
    }

    // the value follows its thumb like the header follows the centre of the other rulers,
    // and each stays on its own half so the two never collide
    val markerTop = TRACK_Y.toPx() - PILL_HALF.toPx() - MARKER_GAP.toPx() - MARKER_HEIGHT.toPx()
    val mid = size.width / 2f
    thumbs.forEach { (thumb, x) ->
      val seconds = if (thumb == Thumb.INTRO) introSeconds else outroSeconds
      val value = textMeasurer.measure(seconds.formatTime(), valueStyle)
      val centered = x - value.size.width / 2f
      val left =
        when (thumb) {
          // a canvas too narrow for both values leaves an empty range, which coerceIn rejects
          Thumb.INTRO -> centered.coerceIn(0f, maxOf(0f, mid - value.size.width - MARKER_GAP.toPx()))

          Thumb.OUTRO -> centered.coerceIn(mid + MARKER_GAP.toPx(), maxOf(mid + MARKER_GAP.toPx(), size.width - value.size.width))
        }
      drawText(textLayoutResult = value, topLeft = Offset(left, markerTop - VALUE_GAP.toPx() - value.size.height))

      val markerHalf = MARKER_WIDTH.toPx() / 2f
      drawPath(
        path =
          Path().apply {
            moveTo(x - markerHalf, markerTop)
            lineTo(x + markerHalf, markerTop)
            lineTo(x, markerTop + MARKER_HEIGHT.toPx())
            close()
          },
        color = colors.onSurface,
      )
    }
  }
}

/** The ruler itself: cut-off ticks tinted, the middle fading out, where the scale means nothing. */
private fun DrawScope.drawRuler(
  geometry: RulerScale,
  introX: Float,
  outroX: Float,
  colors: RulerColors,
  tickLabels: Map<Int, TextLayoutResult>,
) {
  val trackY = TRACK_Y.toPx()
  val majorHalf = MAJOR_TICK_HALF.toPx()
  val minorHalf = MINOR_TICK_HALF.toPx()

  fun drawTick(
    x: Float,
    length: Float,
    alpha: Float,
  ) {
    val cut = x < introX || x > outroX
    drawLine(
      color = if (cut) colors.accent.copy(alpha = alpha * CUT_TICK_ALPHA) else colors.onSurface.copy(alpha = alpha * TICK_ALPHA),
      start = Offset(x, trackY - length),
      end = Offset(x, trackY + length),
      strokeWidth = TICK_WIDTH.toPx(),
      cap = StrokeCap.Round,
    )
  }

  fun drawLabel(
    x: Float,
    label: TextLayoutResult,
    alpha: Float,
  ) = drawText(
    textLayoutResult = label,
    color = colors.variant.copy(alpha = alpha),
    topLeft = Offset(x - label.size.width / 2f, trackY + majorHalf + TICK_LABEL_GAP.toPx()),
  )

  // the same tick on both halves: so far from the start, and so far from the end
  for (seconds in 0..MAX_SECONDS step TICK_SECONDS) {
    val distance = seconds * geometry.pxPerSecond
    val alpha = (1f - distance / geometry.half).coerceIn(MIN_TICK_ALPHA, 1f)
    val length = if (seconds % LABEL_SECONDS == 0) majorHalf else minorHalf

    drawTick(geometry.start + distance, length, alpha)
    drawTick(geometry.end - distance, length, alpha)

    tickLabels[seconds]?.let { label ->
      drawLabel(geometry.start + distance, label, alpha)
      drawLabel(geometry.end - distance, label, alpha)
    }
  }
}

private fun DrawScope.drawPillThumb(
  x: Float,
  active: Boolean,
  colors: RulerColors,
) {
  val trackY = TRACK_Y.toPx()
  val pillWidth = (if (active) PILL_WIDTH + PILL_GRIP else PILL_WIDTH).toPx()
  val pillHalf = PILL_HALF.toPx()
  drawRoundRect(
    color = colors.surface,
    topLeft = Offset(x - pillWidth / 2 - PILL_HALO.toPx(), trackY - pillHalf - PILL_HALO.toPx()),
    size = Size(pillWidth + PILL_HALO.toPx() * 2, pillHalf * 2 + PILL_HALO.toPx() * 2),
    cornerRadius = CornerRadius(pillWidth),
  )
  drawRoundRect(
    color = colors.onSurface,
    topLeft = Offset(x - pillWidth / 2, trackY - pillHalf),
    size = Size(pillWidth, pillHalf * 2),
    cornerRadius = CornerRadius(pillWidth / 2),
  )
}

private class RulerScale(
  width: Float,
  inset: Float,
) {
  val start = inset
  val end = width - inset
  val half = (end - start) / 2f
  val pxPerSecond = half / MAX_SECONDS

  fun introX(seconds: Int) = start + seconds * pxPerSecond

  fun outroX(seconds: Int) = end - seconds * pxPerSecond

  fun introSeconds(x: Float) = ((x - start) / pxPerSecond).toSteppedSeconds()

  fun outroSeconds(x: Float) = ((end - x) / pxPerSecond).toSteppedSeconds()
}

private class RulerColors(
  val surface: Color,
  val onSurface: Color,
  val variant: Color,
  val accent: Color,
)

internal enum class Thumb { INTRO, OUTRO }

/**
 * The thumb a touch at [x] takes: the one whose half of the ruler it is, since the other could
 * not follow it there. The other one only when the touch is within [reach] of it and nearer to
 * it, so a thumb parked at the middle can still be picked up from the far side.
 */
internal fun pickThumb(
  x: Float,
  introX: Float,
  outroX: Float,
  mid: Float,
  reach: Float,
): Thumb {
  val own = if (x <= mid) Thumb.INTRO else Thumb.OUTRO
  val (ownX, otherX) = if (own == Thumb.INTRO) introX to outroX else outroX to introX
  val other = if (own == Thumb.INTRO) Thumb.OUTRO else Thumb.INTRO

  return if (abs(x - otherX) <= reach && abs(x - otherX) < abs(x - ownX)) other else own
}

private fun Float.toSteppedSeconds(): Int = ((this / STEP_SECONDS).roundToInt() * STEP_SECONDS).coerceIn(0, MAX_SECONDS)

private const val MAX_SECONDS = 300
private const val STEP_SECONDS = 5
private const val TICK_SECONDS = 10
private const val LABEL_SECONDS = 60
private const val MIN_TICK_ALPHA = 0.15f
private const val TICK_ALPHA = 0.55f
private const val CUT_TICK_ALPHA = 0.85f

private val TOTAL_HEIGHT = 90.dp
private val TRACK_Y = 52.dp
private val INSET = 12.dp
private val THUMB_REACH = 24.dp
private val MARKER_GAP = 8.dp
private val VALUE_GAP = 2.dp
private val MARKER_WIDTH = 8.dp
private val MARKER_HEIGHT = 4.dp
private val PILL_WIDTH = 3.dp
private val PILL_HALF = 14.dp
private val PILL_GRIP = 2.dp
private val PILL_HALO = 2.dp
private val TICK_LABEL_GAP = 4.dp
private val MAJOR_TICK_HALF = 11.dp
private val MINOR_TICK_HALF = 5.5.dp
private val TICK_WIDTH = 1.5.dp
