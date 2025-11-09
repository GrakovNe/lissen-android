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

import android.view.ViewConfiguration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.util.fastSumBy
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest

fun Modifier.withScrollbar(
  state: LazyListState,
  color: Color,
  totalItems: Int?,
  offsetItems: Int = 0,
  scrollableElementPrefix: String? = null,
): Modifier =
  baseScrollbar { atEnd ->
    val layoutInfo = state.layoutInfo
    val viewportSize = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset

    val items =
      when (scrollableElementPrefix) {
        null -> layoutInfo.visibleItemsInfo
        else ->
          layoutInfo.visibleItemsInfo.filter {
            val key = it.key
            key is String && key.startsWith(scrollableElementPrefix)
          }
      }

    val itemsSize = items.fastSumBy { it.size }

    val count = totalItems?.let { it + offsetItems } ?: layoutInfo.totalItemsCount

    if (items.size < count || itemsSize > viewportSize) {
      val itemSize = itemsSize.toFloat() / items.size

      val totalSize = itemSize * count
      val canvasSize = size.height
      val thumbSize = (viewportSize / totalSize) * canvasSize
      val startOffset =
        if (items.isEmpty()) {
          0f
        } else {
          val first = items.first()
          (itemSize * first.index - first.offset) / totalSize * canvasSize
        }
      drawScrollbarThumb(atEnd, thumbSize, startOffset, color)
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
  // if (thumbSize > availableHeight) return

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

    val alpha = remember { Animatable(0f) }

    LaunchedEffect(scrolled, alpha) {
      scrolled.collectLatest {
        alpha.snapTo(1f)
        delay(ViewConfiguration.getScrollDefaultDelay().toLong())
        alpha.animateTo(0f, animationSpec = tween(ViewConfiguration.getScrollBarFadeDuration()))
      }
    }

    val reachedEnd = LocalLayoutDirection.current == LayoutDirection.Ltr

    nestedScroll(nestedScrollConnection).drawWithContent {
      drawContent()
      onDraw(reachedEnd)
    }
  }
