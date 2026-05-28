package org.grakovne.lissen.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun ArcProgressIndicator(
  progress: Float,
  trackColor: Color,
  progressColor: Color,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier) {
    val strokePx = 2.8.dp.toPx()
    val topLeft = Offset(strokePx / 2, strokePx / 2)
    val arcSize = Size(size.width - strokePx, size.height - strokePx)
    val stroke = Stroke(width = strokePx, cap = StrokeCap.Butt)
    drawArc(
      color = trackColor,
      startAngle = -90f,
      sweepAngle = 360f,
      useCenter = false,
      topLeft = topLeft,
      size = arcSize,
      style = stroke,
    )
    if (progress > 0f) {
      drawArc(
        color = progressColor,
        startAngle = -90f,
        sweepAngle = progress * 360f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = stroke,
      )
    }
  }
}
