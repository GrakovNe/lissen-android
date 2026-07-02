package org.grakovne.lissen.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val WIDE_LAYOUT_WIDTH_DP = 600

const val READABLE_CONTENT_MAX_WIDTH_DP = 720

const val RECENT_ITEM_MAX_WIDTH_DP = 150

@Composable
@ReadOnlyComposable
fun isWideLayout(): Boolean = LocalConfiguration.current.screenWidthDp >= WIDE_LAYOUT_WIDTH_DP

@Composable
@ReadOnlyComposable
fun readableContentMaxWidth(): Dp =
  when (isWideLayout()) {
    true -> READABLE_CONTENT_MAX_WIDTH_DP.dp
    false -> Dp.Unspecified
  }

@Composable
@ReadOnlyComposable
fun recentBookItemWidth(): Dp {
  val screenWidth = LocalConfiguration.current.screenWidthDp.dp
  val itemsVisible = 2.3f
  val spacing = 16.dp
  val totalSpacing = spacing * (itemsVisible + 1)
  val raw = (screenWidth - totalSpacing) / itemsVisible

  return when (isWideLayout()) {
    true -> raw.coerceAtMost(RECENT_ITEM_MAX_WIDTH_DP.dp)
    false -> raw
  }
}
