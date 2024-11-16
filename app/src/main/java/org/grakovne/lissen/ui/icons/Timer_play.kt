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
 * File has been copied from https://composeicons.com/icons/material-symbols/outlined/timer_play
 * under Apache License
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

val Timer_play: ImageVector
    get() {
        if (_Timer_play != null) {
            return _Timer_play!!
        }
        _Timer_play = ImageVector.Builder(
            name = "Timer_play",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
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
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(360f, 120f)
                verticalLineToRelative(-80f)
                horizontalLineToRelative(240f)
                verticalLineToRelative(80f)
                close()
                moveTo(480f, 880f)
                quadToRelative(-74f, 0f, -139.5f, -28.5f)
                reflectiveQuadTo(226f, 774f)
                reflectiveQuadToRelative(-77.5f, -114.5f)
                reflectiveQuadTo(120f, 520f)
                reflectiveQuadToRelative(28.5f, -139.5f)
                reflectiveQuadTo(226f, 266f)
                reflectiveQuadToRelative(114.5f, -77.5f)
                reflectiveQuadTo(480f, 160f)
                quadToRelative(62f, 0f, 119f, 20f)
                reflectiveQuadToRelative(107f, 58f)
                lineToRelative(56f, -56f)
                lineToRelative(56f, 56f)
                lineToRelative(-56f, 56f)
                quadToRelative(38f, 50f, 58f, 107f)
                reflectiveQuadToRelative(20f, 119f)
                quadToRelative(0f, 74f, -28.5f, 139.5f)
                reflectiveQuadTo(734f, 774f)
                reflectiveQuadToRelative(-114.5f, 77.5f)
                reflectiveQuadTo(480f, 880f)
                moveToRelative(0f, -80f)
                quadToRelative(116f, 0f, 198f, -82f)
                reflectiveQuadToRelative(82f, -198f)
                reflectiveQuadToRelative(-82f, -198f)
                reflectiveQuadToRelative(-198f, -82f)
                reflectiveQuadToRelative(-198f, 82f)
                reflectiveQuadToRelative(-82f, 198f)
                reflectiveQuadToRelative(82f, 198f)
                reflectiveQuadToRelative(198f, 82f)
                moveToRelative(-80f, -120f)
                lineToRelative(240f, -160f)
                lineToRelative(-240f, -160f)
                close()
            }
        }.build()
        return _Timer_play!!
    }

private var _Timer_play: ImageVector? = null
