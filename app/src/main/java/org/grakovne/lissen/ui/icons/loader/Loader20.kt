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
 * File has been copied from https://composeicons.com/icons/material-symbols/outlined/clock_loader_20 under Apache License
 */
package org.grakovne.lissen.ui.icons.loader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Loader20: ImageVector
  get() {
    if (_undefined != null) {
      return _undefined!!
    }
    _undefined =
      ImageVector
        .Builder(
          name = "Clock_loader_20",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 960f,
          viewportHeight = 960f,
        ).apply {
          path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1.0f,
            stroke = null,
            strokeAlpha = 1.0f,
            strokeLineWidth = 1.0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 1.0f,
            pathFillType = PathFillType.NonZero,
          ) {
            moveTo(480f, 880f)
            quadToRelative(-83f, 0f, -156f, -31.5f)
            reflectiveQuadTo(197f, 763f)
            reflectiveQuadToRelative(-85.5f, -127f)
            reflectiveQuadTo(80f, 480f)
            reflectiveQuadToRelative(31.5f, -156f)
            reflectiveQuadTo(197f, 197f)
            reflectiveQuadToRelative(127f, -85.5f)
            reflectiveQuadTo(480f, 80f)
            reflectiveQuadToRelative(156f, 31.5f)
            reflectiveQuadTo(763f, 197f)
            reflectiveQuadToRelative(85.5f, 127f)
            reflectiveQuadTo(880f, 480f)
            reflectiveQuadToRelative(-31.5f, 156f)
            reflectiveQuadTo(763f, 763f)
            reflectiveQuadToRelative(-127f, 85.5f)
            reflectiveQuadTo(480f, 880f)
            moveToRelative(0f, -80f)
            quadToRelative(134f, 0f, 227f, -93f)
            reflectiveQuadToRelative(93f, -227f)
            horizontalLineTo(480f)
            verticalLineToRelative(-320f)
            quadToRelative(-134f, 0f, -227f, 93f)
            reflectiveQuadToRelative(-93f, 227f)
            reflectiveQuadToRelative(93f, 227f)
            reflectiveQuadToRelative(227f, 93f)
          }
        }.build()
    return _undefined!!
  }

private var _undefined: ImageVector? = null
