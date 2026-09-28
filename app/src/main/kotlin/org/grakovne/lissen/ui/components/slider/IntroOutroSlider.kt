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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.ui.extensions.formatTime
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Two thumbs on one ruler: the left one measures seconds from the start of the episode,
 * the right one seconds from its end. Each half spans five minutes from its edge. Tapping
 * grabs the nearer thumb, so the thumbs can be reached wherever they sit.
 */
@Composable
fun IntroOutroSlider(
  introSeconds: Int,
  outroSeconds: Int,
  modifier: Modifier = Modifier,
  stateDescription: String? = null,
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

  // the header of the other rulers, a size down so two of them stay quiet inside a settings sheet
  val valueStyle = MaterialTheme.typography.titleMedium.copy(color = palette.onSurface)
  // numerals sit tighter without the body letter spacing, which keeps five-character labels a minute apart
  val tickStyle = MaterialTheme.typography.bodySmall.copy(color = palette.variant, letterSpacing = 0.sp)

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
            val geometry = Geometry(size.width.toFloat(), INSET.toPx())
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
    val geometry = Geometry(size.width, INSET.toPx())
    val introX = geometry.introX(introSeconds)
    val outroX = geometry.outroX(outroSeconds)

    drawRuler(geometry, introX, outroX, palette, textMeasurer, tickStyle)

    listOf(Thumb.INTRO to introX, Thumb.OUTRO to outroX).forEach { (thumb, x) ->
      drawPillThumb(x, active = dragging == thumb, palette)
    }

    // the value follows its thumb like the header follows the centre of the other rulers,
    // and each stays on its own half so the two never collide
    val markerTop = TRACK_Y.toPx() - PILL_HALF.toPx() - MARKER_GAP.toPx() - MARKER_HEIGHT.toPx()
    val mid = size.width / 2f
    listOf(Thumb.INTRO to introX, Thumb.OUTRO to outroX).forEach { (thumb, x) ->
      val seconds = if (thumb == Thumb.INTRO) introSeconds else outroSeconds
      val value = textMeasurer.measure(seconds.formatTime(), valueStyle)
      val centered = x - value.size.width / 2f
      val left =
        when (thumb) {
          Thumb.INTRO -> centered.coerceIn(0f, mid - value.size.width - MARKER_GAP.toPx())
          Thumb.OUTRO -> centered.coerceIn(mid + MARKER_GAP.toPx(), size.width - value.size.width)
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
        color = palette.onSurface,
      )
    }
  }
}

/** The ruler of the volume slider: cut-off ticks tinted, the middle fading out, where the scale means nothing. */
private fun DrawScope.drawRuler(
  geometry: Geometry,
  introX: Float,
  outroX: Float,
  palette: Palette,
  textMeasurer: TextMeasurer,
  tickStyle: TextStyle,
) {
  val trackY = TRACK_Y.toPx()
  val majorHalf = MAJOR_TICK_HALF.toPx()
  val minorHalf = MINOR_TICK_HALF.toPx()

  for (seconds in 0..MAX_SECONDS step TICK_SECONDS) {
    val major = seconds % LABEL_SECONDS == 0
    val distance = seconds * geometry.pxPerSecond
    val alpha = (1f - distance / geometry.half).coerceIn(MIN_TICK_ALPHA, 1f)
    val length = if (major) majorHalf else minorHalf

    listOf(geometry.start + distance, geometry.end - distance).forEach { x ->
      val cut = x < introX || x > outroX
      drawLine(
        color = if (cut) palette.accent.copy(alpha = alpha * CUT_TICK_ALPHA) else palette.onSurface.copy(alpha = alpha * TICK_ALPHA),
        start = Offset(x, trackY - length),
        end = Offset(x, trackY + length),
        strokeWidth = TICK_WIDTH.toPx(),
        cap = StrokeCap.Round,
      )
    }

    if (major && seconds != 0 && seconds != MAX_SECONDS) {
      val label = textMeasurer.measure(seconds.formatTime(), tickStyle)
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

private fun DrawScope.drawPillThumb(
  x: Float,
  active: Boolean,
  palette: Palette,
) {
  val trackY = TRACK_Y.toPx()
  val pillWidth = (if (active) PILL_WIDTH + 2.dp else PILL_WIDTH).toPx()
  val pillHalf = PILL_HALF.toPx()
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

private class Palette(
  val surface: Color,
  val onSurface: Color,
  val variant: Color,
  val accent: Color,
)

private enum class Thumb { INTRO, OUTRO }

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
private val MARKER_GAP = 8.dp
private val VALUE_GAP = 2.dp
private val MARKER_WIDTH = 8.dp
private val MARKER_HEIGHT = 4.dp
private val PILL_WIDTH = 3.dp
private val PILL_HALF = 14.dp
private val MAJOR_TICK_HALF = 11.dp
private val MINOR_TICK_HALF = 5.5.dp
private val TICK_WIDTH = 1.5.dp
