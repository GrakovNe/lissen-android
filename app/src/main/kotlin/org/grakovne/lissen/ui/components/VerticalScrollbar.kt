/*
 * MIT License
 *
 * Original Copyright (c) 2022 Albert Chang
 * Adapted Copyright (c) 2025 Max Grakov
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * Adapted from:
 * https://gist.github.com/mxalbert1996/33a360fcab2105a31e5355af98216f5a
 */
package org.grakovne.lissen.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect
import org.acra.ACRA
import timber.log.Timber

fun Modifier.withScrollbar(
  state: LazyListState,
  color: Color,
  totalItems: Int?,
  ignoreItems: List<String> = emptyList(),
): Modifier =
  composed {
    val itemHeights = remember { mutableStateMapOf<Int, Int>() }

    LaunchedEffect(totalItems) {
      itemHeights.clear()
    }

    LaunchedEffect(state) {
      snapshotFlow { state.layoutInfo.visibleItemsInfo }
        .collect { visible ->
          visible.forEach { item ->
            if (itemHeights[item.index] != item.size) {
              itemHeights[item.index] = item.size
            }
          }
        }
    }

    val reachedEnd = LocalLayoutDirection.current == LayoutDirection.Ltr

    drawWithContent {
      drawContent()

      try {
        drawScrollbar(
          atEnd = reachedEnd,
          state = state,
          totalItems = totalItems,
          headerCount = ignoreItems.size,
          itemHeights = itemHeights,
          color = color,
        )
      } catch (ex: Exception) {
        Timber.w("Unable to apply scrollbar due to ${ex.message}")
        ACRA.errorReporter.handleSilentException(ex)
      }
    }
  }

private fun DrawScope.drawScrollbar(
  atEnd: Boolean,
  state: LazyListState,
  totalItems: Int?,
  headerCount: Int,
  itemHeights: Map<Int, Int>,
  color: Color,
) {
  val layoutInfo = state.layoutInfo
  val viewportSize = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
  if (viewportSize <= 0) {
    return
  }

  val visible = layoutInfo.visibleItemsInfo
  if (visible.isEmpty()) {
    return
  }

  val measured = itemHeights.takeIf { it.isNotEmpty() } ?: visible.associate { it.index to it.size }

  val sortedHeights = measured.values.sorted()
  val rowHeight = sortedHeights[sortedHeights.size / 2].coerceAtLeast(1).toFloat()

  val itemCount =
    when (totalItems) {
      null -> layoutInfo.totalItemsCount
      else -> totalItems + headerCount
    }.coerceAtLeast(1)

  val unknownCount = (itemCount - measured.size).coerceAtLeast(0)
  val totalSize = measured.values.sum() + unknownCount * rowHeight
  if (totalSize <= 0f) {
    return
  }

  val canvasSize = size.height
  val thumbSize = (viewportSize / totalSize) * canvasSize
  if (thumbSize >= canvasSize * 0.95f) {
    return
  }

  val first = visible.first()
  val pixelsAbove =
    (0 until first.index).sumOf { measured[it]?.toLong() ?: rowHeight.toLong() }.toFloat()
  val scrolledPixels = pixelsAbove - first.offset

  val startOffset = (scrolledPixels / totalSize) * canvasSize

  drawScrollbarThumb(atEnd, thumbSize, startOffset, color)
}

private fun DrawScope.drawScrollbarThumb(
  atEnd: Boolean,
  thumbSize: Float,
  startOffset: Float,
  color: Color,
) {
  val thickness = 3.dp.toPx()
  val radius = 3.dp.toPx()
  val horizontalPadding = 6.dp.toPx()
  val verticalPadding = 4.dp.toPx()

  val availableHeight = (size.height - 2 * verticalPadding).coerceAtLeast(0f)
  val maxY = (availableHeight - thumbSize).coerceAtLeast(0f)

  val topLeft =
    Offset(
      x = if (atEnd) size.width - thickness - horizontalPadding else horizontalPadding,
      y = verticalPadding + startOffset.coerceIn(0f, maxY),
    )

  drawRoundRect(
    color = color,
    topLeft = topLeft,
    size = Size(thickness, thumbSize.coerceAtMost(availableHeight)),
    cornerRadius = CornerRadius(radius),
  )
}
