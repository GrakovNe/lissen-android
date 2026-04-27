package org.grakovne.lissen.ui.screens.player.composable.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.lib.domain.SeekTimeOption
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SeekButtonComposable(
  option: SeekTimeOption,
  direction: SeekButtonDirection,
  contentDescription: String,
  tint: Color = colorScheme.onBackground,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .size(48.dp)
        .semantics {
          this.contentDescription = contentDescription
        },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val strokeWidth = 2.5.dp.toPx()
      val padding = 8.dp.toPx()
      val arcSize = Size(size.width - padding * 2, size.height - padding * 2)
      val startAngle =
        when (direction) {
          SeekButtonDirection.REWIND -> 190f
          SeekButtonDirection.FORWARD -> -10f
        }
      val sweepAngle =
        when (direction) {
          SeekButtonDirection.REWIND -> -280f
          SeekButtonDirection.FORWARD -> 280f
        }

      drawArc(
        color = tint,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(padding, padding),
        size = arcSize,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
      )

      drawArrowHead(
        direction = direction,
        tint = tint,
        padding = padding,
      )
    }

    Text(
      text = option.seconds().toString(),
      style =
        typography.labelMedium.copy(
          fontWeight = FontWeight.SemiBold,
        ),
      color = tint,
    )
  }
}

enum class SeekButtonDirection {
  REWIND,
  FORWARD,
}

fun SeekTimeOption.seconds(): Int =
  when (this) {
    SeekTimeOption.SEEK_5 -> 5
    SeekTimeOption.SEEK_10 -> 10
    SeekTimeOption.SEEK_15 -> 15
    SeekTimeOption.SEEK_30 -> 30
    SeekTimeOption.SEEK_60 -> 60
  }

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrowHead(
  direction: SeekButtonDirection,
  tint: Color,
  padding: Float,
) {
  val radius = (size.minDimension - padding * 2) / 2
  val center = Offset(size.width / 2, size.height / 2)
  val angleDegrees = 270f
  val angle = Math.toRadians(angleDegrees.toDouble())
  val tip =
    Offset(
      x = center.x + radius * cos(angle).toFloat(),
      y = center.y + radius * sin(angle).toFloat(),
    )
  val arrowSize = 6.dp.toPx()
  val path =
    when (direction) {
      SeekButtonDirection.REWIND -> {
        Path().apply {
          moveTo(tip.x - arrowSize, tip.y)
          lineTo(tip.x + arrowSize * 0.45f, tip.y - arrowSize * 0.8f)
          lineTo(tip.x + arrowSize * 0.45f, tip.y + arrowSize * 0.8f)
          close()
        }
      }

      SeekButtonDirection.FORWARD -> {
        Path().apply {
          moveTo(tip.x + arrowSize, tip.y)
          lineTo(tip.x - arrowSize * 0.45f, tip.y - arrowSize * 0.8f)
          lineTo(tip.x - arrowSize * 0.45f, tip.y + arrowSize * 0.8f)
          close()
        }
      }
    }

  drawPath(path = path, color = tint)
}
