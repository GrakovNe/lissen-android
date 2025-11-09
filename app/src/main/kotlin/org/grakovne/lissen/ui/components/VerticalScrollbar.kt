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
): Modifier =
  baseScrollbar(Orientation.Vertical) { atEnd ->
    val layoutInfo = state.layoutInfo
    val viewportSize = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    val items = layoutInfo.visibleItemsInfo
    val itemsSize = items.fastSumBy { it.size }

    val networkItems = totalItems ?: 0
    val extraItems = layoutInfo.totalItemsCount - networkItems
    val count = networkItems + extraItems

    if (items.size < count || itemsSize > viewportSize) {
      val itemSize = itemsSize.toFloat() / items.size
      val totalSize = itemSize * count
      val canvasSize = size.height
      val thumbSize = (viewportSize / totalSize) * canvasSize

      val firstVisible = items.firstOrNull()
      val startOffset =
        if (firstVisible != null) {
          val scrolled = firstVisible.index * itemSize - firstVisible.offset
          (scrolled / totalSize * canvasSize).coerceIn(0f, canvasSize - thumbSize)
        } else {
          0f
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
  val padding = 6.dp.toPx()

  val topLeft = Offset(if (atEnd) size.width - thickness - padding else padding, startOffset)
  val rectSize = Size(thickness, thumbSize)

  drawRoundRect(
    color = color,
    topLeft = topLeft,
    size = rectSize,
    cornerRadius = CornerRadius(radius),
  )
}

private fun Modifier.baseScrollbar(
  orientation: Orientation,
  onDraw: DrawScope.(atEnd: Boolean) -> Unit,
): Modifier =
  composed {
    val scrolled =
      remember {
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
      }

    val nestedScrollConnection =
      remember(orientation, scrolled) {
        object : NestedScrollConnection {
          override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
          ) = Offset.Zero.also {
            val delta = if (orientation == Orientation.Horizontal) consumed.x else consumed.y
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

    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val atEnd = orientation == Orientation.Vertical && isLtr || orientation == Orientation.Horizontal

    nestedScroll(nestedScrollConnection).drawWithContent {
      drawContent()
      onDraw(atEnd)
    }
  }
