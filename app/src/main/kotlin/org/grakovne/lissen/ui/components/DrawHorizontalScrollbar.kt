package org.grakovne.lissen.ui.components

/*
 * MIT License
 *
 * Copyright (c) 2022 Albert Chang
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
 */

import android.view.ViewConfiguration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

fun Modifier.drawVerticalScrollbar(
  state: LazyListState,
  reverseScrolling: Boolean = false,
  colorScheme: ColorScheme,
): Modifier = drawScrollbar(state, Orientation.Vertical, reverseScrolling, colorScheme)

private fun Modifier.drawScrollbar(
  state: LazyListState,
  orientation: Orientation,
  reverseScrolling: Boolean,
  colorScheme: ColorScheme,
): Modifier =
  baseScrollbar(
    orientation,
    reverseScrolling,
  ) { reverseDirection, atEnd, alpha ->
    val layoutInfo = state.layoutInfo
    val viewportSize = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    val items = layoutInfo.visibleItemsInfo
    val itemsSize = items.fastSumBy { it.size }

    if (items.size < layoutInfo.totalItemsCount || itemsSize > viewportSize) {
      val estimatedItemSize = if (items.isEmpty()) 0f else itemsSize.toFloat() / items.size
      val totalSize = estimatedItemSize * layoutInfo.totalItemsCount
      val canvasSize = if (orientation == Orientation.Horizontal) size.width else size.height
      val thumbSize = (viewportSize / totalSize) * canvasSize
      val startOffset =
        if (items.isEmpty()) {
          0f
        } else {
          items.first().run { (estimatedItemSize * index - offset) / totalSize * canvasSize }
        }
      drawScrollbarThumb(orientation, reverseDirection, atEnd, alpha, thumbSize, startOffset, colorScheme)
    }
  }

private fun DrawScope.drawScrollbarThumb(
  orientation: Orientation,
  reverseDirection: Boolean,
  atEnd: Boolean,
  alpha: () -> Float,
  thumbSize: Float,
  startOffset: Float,
  colorScheme: ColorScheme,
) {
  val color = colorScheme.primary
  val thicknessPx = 3.dp.toPx()
  val radiusPx = 3.dp.toPx()
  val paddingPx = 6.dp.toPx()

  val topLeft =
    if (orientation == Orientation.Horizontal) {
      Offset(
        if (reverseDirection) size.width - startOffset - thumbSize else startOffset,
        if (atEnd) size.height - thicknessPx - paddingPx else paddingPx,
      )
    } else {
      Offset(
        if (atEnd) size.width - thicknessPx - paddingPx else paddingPx,
        if (reverseDirection) size.height - startOffset - thumbSize else startOffset,
      )
    }

  val rectSize =
    if (orientation == Orientation.Horizontal) {
      Size(thumbSize, thicknessPx)
    } else {
      Size(thicknessPx, thumbSize)
    }

  drawRoundRect(
    color = color.copy(alpha = alpha()),
    topLeft = topLeft,
    size = rectSize,
    cornerRadius = CornerRadius(radiusPx),
  )
}

private fun Modifier.baseScrollbar(
  orientation: Orientation,
  reverseScrolling: Boolean,
  onDraw: DrawScope.(reverseDirection: Boolean, atEnd: Boolean, alpha: () -> Float) -> Unit,
): Modifier =
  composed {
    val scrolled =
      remember {
        MutableSharedFlow<Unit>(
          extraBufferCapacity = 1,
          onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
      }

    val nestedScrollConnection =
      remember(orientation, scrolled) {
        object : NestedScrollConnection {
          override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
          ): Offset {
            val delta = if (orientation == Orientation.Horizontal) consumed.x else consumed.y
            if (delta != 0f) scrolled.tryEmit(Unit)
            return Offset.Zero
          }
        }
      }

    val alpha = remember { Animatable(0f) }

    LaunchedEffect(scrolled, alpha) {
      scrolled.collectLatest {
        alpha.snapTo(1f)
        delay(ViewConfiguration.getScrollDefaultDelay().toLong())
        alpha.animateTo(0f, animationSpec = FadeOutAnimationSpec)
      }
    }

    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val reverseDirection =
      if (orientation == Orientation.Horizontal) {
        if (isLtr) reverseScrolling else !reverseScrolling
      } else {
        reverseScrolling
      }
    val atEnd = if (orientation == Orientation.Vertical) isLtr else true

    Modifier
      .nestedScroll(nestedScrollConnection)
      .drawWithContent {
        drawContent()
        onDraw(reverseDirection, atEnd, alpha::value)
      }
  }

private val FadeOutAnimationSpec =
  tween<Float>(durationMillis = ViewConfiguration.getScrollBarFadeDuration())
