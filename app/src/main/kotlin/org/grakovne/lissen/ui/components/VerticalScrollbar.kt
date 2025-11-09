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

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.acra.ACRA
import timber.log.Timber

fun Modifier.withScrollbar(
  state: LazyListState,
  color: Color,
  totalItems: Int?,
  ignoreItems: List<String> = emptyList(),
): Modifier {
  try {
    return baseScrollbar { atEnd ->
      val layoutInfo = state.layoutInfo
      val viewportSize = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset

      val items =
        layoutInfo.visibleItemsInfo
          .filterNot {
            val key = it.key
            key is String && ignoreItems.contains(key)
          }

      val itemsSize = items.sumOf { it.size }
      val count = totalItems ?: layoutInfo.totalItemsCount

      if (items.size < count || itemsSize > viewportSize) {
        val itemSize = itemsSize.toFloat() / items.size

        val totalSize = itemSize * count
        val canvasSize = size.height
        val thumbSize = (viewportSize / totalSize) * canvasSize

        if (thumbSize > canvasSize * 0.95) {
          return@baseScrollbar
        }

        val startOffset =
          items
            .firstOrNull()
            ?.let { (itemSize * it.index - it.offset) / totalSize * canvasSize }
            ?: 0f

        drawScrollbarThumb(atEnd, thumbSize, startOffset, color)
      }
    }
  } catch (ex: Exception) {
    Timber.w("Unable to apply scrollbar due to ${ex.message}")
    ACRA.errorReporter.handleSilentException(ex)
    return this
  }
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

private fun Modifier.baseScrollbar(onDraw: DrawScope.(atEnd: Boolean) -> Unit): Modifier =
  composed {
    val scrolled =
      remember {
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
      }

    val nestedScrollConnection =
      remember(Orientation.Vertical, scrolled) {
        object : NestedScrollConnection {
          override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
          ) = Offset.Zero.also {
            val delta = consumed.y
            if (delta != 0f) scrolled.tryEmit(Unit)
          }
        }
      }

    val reachedEnd = LocalLayoutDirection.current == LayoutDirection.Ltr

    nestedScroll(nestedScrollConnection).drawWithContent {
      drawContent()
      onDraw(reachedEnd)
    }
  }
