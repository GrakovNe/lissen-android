/**
 * Copyright @alexstyl
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 * File has been copied from https://composeicons.com/icons/material-symbols/outlined/upload_file
 * under Apache License and modified according the requirements
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

val availableOffline: ImageVector
  get() {
    if (_undefined != null) {
      return _undefined!!
    }
    _undefined =
      ImageVector
        .Builder(
          name = "Download_file",
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
            moveTo(440f, 440f)
            horizontalLineToRelative(80f)
            verticalLineToRelative(167f)
            lineToRelative(64f, -64f)
            lineToRelative(56f, 57f)
            lineToRelative(-160f, 160f)
            lineToRelative(-160f, -160f)
            lineToRelative(57f, -56f)
            lineToRelative(63f, 63f)
            close()

            moveTo(240f, 880f)
            quadToRelative(-33f, 0f, -56.5f, -23.5f)
            reflectiveQuadTo(160f, 800f)
            verticalLineToRelative(-640f)
            quadToRelative(0f, -33f, 23.5f, -56.5f)
            reflectiveQuadTo(240f, 80f)
            horizontalLineToRelative(320f)
            lineToRelative(240f, 240f)
            verticalLineToRelative(480f)
            quadToRelative(0f, 33f, -23.5f, 56.5f)
            reflectiveQuadTo(720f, 880f)
            close()

            moveToRelative(280f, -520f)
            verticalLineToRelative(-200f)
            horizontalLineTo(240f)
            verticalLineToRelative(640f)
            horizontalLineToRelative(480f)
            verticalLineToRelative(-440f)
            close()

            moveTo(240f, 160f)
            verticalLineToRelative(200f)
            close()
            verticalLineToRelative(640f)
            close()
          }
        }.build()
    return _undefined!!
  }

private var _undefined: ImageVector? = null
