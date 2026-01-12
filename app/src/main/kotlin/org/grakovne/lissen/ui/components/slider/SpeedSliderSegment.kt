package org.grakovne.lissen.ui.components.slider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

@Composable
fun SpeedSliderSegment(
  index: Int,
  currentValue: Float,
  segmentWidth: Dp,
  segmentPixelWidth: Float,
  centerPixel: Float,
  barColor: Color,
  formatIndex: (Int) -> Any,
  maxIndex: Int? = null,
) {
  val offset = (index - currentValue) * segmentPixelWidth
  val alphaValue = calculateAlpha(offset, centerPixel)

  Column(
    modifier =
      Modifier
        .width(segmentWidth)
        .graphicsLayer(alpha = alphaValue, translationX = offset),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier =
        Modifier
          .width(barThickness)
          .height(barLength)
          .background(barColor),
    )
    if (index == 0 || index % 5 == 0 || index == maxIndex) {
      val label = formatIndex(index)
      when (label) {
        is String -> Text(text = label, style = typography.bodyMedium)
        is Int -> Text(text = label.toString(), style = typography.bodyMedium)
        is androidx.compose.ui.graphics.vector.ImageVector ->
          Icon(imageVector = label, contentDescription = null)
      }
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

private val barThickness = 2.dp
private val barLength = 28.dp
private const val minAlpha = 0.25f
