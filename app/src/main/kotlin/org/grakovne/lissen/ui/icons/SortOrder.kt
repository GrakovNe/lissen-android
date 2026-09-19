package org.grakovne.lissen.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Three bars growing towards the arrow, arrow on the right: the ascending glyph points up,
 * the descending one points down. Drawn in the same stroked style as the other custom icons.
 */
val SortAscending: ImageVector
  get() {
    if (_SortAscending != null) {
      return _SortAscending!!
    }
    _SortAscending = sortOrder(name = "SortAscending", ascending = true)
    return _SortAscending!!
  }

val SortDescending: ImageVector
  get() {
    if (_SortDescending != null) {
      return _SortDescending!!
    }
    _SortDescending = sortOrder(name = "SortDescending", ascending = false)
    return _SortDescending!!
  }

private fun sortOrder(
  name: String,
  ascending: Boolean,
): ImageVector =
  ImageVector
    .Builder(
      name = name,
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    ).apply {
      // bars: short at the arrow's tail, long at its head
      val widths = if (ascending) listOf(5f, 9f, 13f) else listOf(13f, 9f, 5f)
      widths.forEachIndexed { index, width ->
        val y = 6f + index * 6f
        stroke {
          moveTo(3f, y)
          lineTo(3f + width, y)
        }
      }

      // arrow shaft
      stroke {
        moveTo(20f, 5f)
        lineTo(20f, 19f)
      }

      // arrow head
      stroke {
        if (ascending) {
          moveTo(16f, 9f)
          lineTo(20f, 5f)
          lineTo(24f, 9f)
        } else {
          moveTo(16f, 15f)
          lineTo(20f, 19f)
          lineTo(24f, 15f)
        }
      }
    }.build()

private fun ImageVector.Builder.stroke(block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) {
  path(
    fill = null,
    stroke = SolidColor(Color(0xFF000000)),
    strokeLineWidth = 2f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
    pathBuilder = block,
  )
}

@Suppress("ObjectPropertyName")
private var _SortAscending: ImageVector? = null

@Suppress("ObjectPropertyName")
private var _SortDescending: ImageVector? = null
