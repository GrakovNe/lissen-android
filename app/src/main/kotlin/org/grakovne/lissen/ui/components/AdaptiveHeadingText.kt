package org.grakovne.lissen.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

@Composable
fun AdaptiveHeadingText(
  text: String,
  modifier: Modifier = Modifier,
  largeStyle: TextStyle = MaterialTheme.typography.titleLarge,
  smallStyle: TextStyle = MaterialTheme.typography.titleMedium,
  color: Color? = null,
) {
  BoxWithConstraints(modifier) {
    val measurer = rememberTextMeasurer()

    val useSmallStyle =
      if (maxWidth == Dp.Infinity) {
        false
      } else {
        with(LocalDensity.current) {
          measurer.measure(text = text, style = largeStyle, maxLines = 1).size.width > maxWidth.toPx()
        }
      }

    Text(
      text = text,
      style = if (useSmallStyle) smallStyle else largeStyle,
      color = color ?: LocalContentColor.current,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.fillMaxWidth(),
    )
  }
}
