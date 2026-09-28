package org.grakovne.lissen.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/** Material "compress" turned on its side: two arrows pushing in from the ends of a timeline. */
val SkipEdges: ImageVector by lazy {
  val source = Icons.Outlined.Compress
  ImageVector
    .Builder(
      name = "SkipEdges",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = source.viewportWidth,
      viewportHeight = source.viewportHeight,
    ).apply {
      group(rotate = 90f, pivotX = source.viewportWidth / 2, pivotY = source.viewportHeight / 2) {
        source.root.forEach { node ->
          if (node is VectorPath) {
            addPath(
              pathData = node.pathData,
              pathFillType = node.pathFillType,
              fill = SolidColor(Color.Black),
            )
          }
        }
      }
    }.build()
}
