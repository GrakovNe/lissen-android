/**
 * Copyright @alexstyl
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 *
 * File has been copied from https://composeicons.com/icons/lucide/book-headphones under ISC Licence
 */
package org.grakovne.lissen.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

public val BookHeadphones: ImageVector
  get() {
    if (_bookHeadphones != null) {
      return _bookHeadphones!!
    }
    _bookHeadphones =
      ImageVector
        .Builder(
          name = "BookHeadphones",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        ).apply {
          path(
            fill = null,
            fillAlpha = 1.0f,
            stroke = SolidColor(Color(0xFF000000)),
            strokeAlpha = 1.0f,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineMiter = 1.0f,
            pathFillType = PathFillType.NonZero,
          ) {
            moveTo(4f, 19.5f)
            verticalLineToRelative(-15f)
            arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 6.5f, 2f)
            horizontalLineTo(19f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1f, 1f)
            verticalLineToRelative(18f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1f, 1f)
            horizontalLineTo(6.5f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -5f)
            horizontalLineTo(20f)
          }
          path(
            fill = null,
            fillAlpha = 1.0f,
            stroke = SolidColor(Color(0xFF000000)),
            strokeAlpha = 1.0f,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineMiter = 1.0f,
            pathFillType = PathFillType.NonZero,
          ) {
            moveTo(8f, 12f)
            verticalLineToRelative(-2f)
            arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8f, 0f)
            verticalLineToRelative(2f)
          }
          path(
            fill = null,
            fillAlpha = 1.0f,
            stroke = SolidColor(Color(0xFF000000)),
            strokeAlpha = 1.0f,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineMiter = 1.0f,
            pathFillType = PathFillType.NonZero,
          ) {
            moveTo(16f, 12f)
            arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 15f, 13f)
            arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 14f, 12f)
            arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16f, 12f)
            close()
          }
          path(
            fill = null,
            fillAlpha = 1.0f,
            stroke = SolidColor(Color(0xFF000000)),
            strokeAlpha = 1.0f,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineMiter = 1.0f,
            pathFillType = PathFillType.NonZero,
          ) {
            moveTo(10f, 12f)
            arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 9f, 13f)
            arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8f, 12f)
            arcTo(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 10f, 12f)
            close()
          }
        }.build()
    return _bookHeadphones!!
  }

private var _bookHeadphones: ImageVector? = null
