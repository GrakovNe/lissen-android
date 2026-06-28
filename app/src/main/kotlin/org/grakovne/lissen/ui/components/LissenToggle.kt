package org.grakovne.lissen.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.common.withHaptic

@Composable
fun LissenToggle(
  checked: Boolean,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  onCheckedChange: ((Boolean) -> Unit)? = null,
) {
  val view = LocalView.current

  val trackWidth = 44.dp
  val trackHeight = 26.dp
  val thumbSize = 20.dp
  val gap = 3.dp

  val thumbOffset by animateDpAsState(
    targetValue = if (checked) trackWidth - thumbSize - gap else gap,
    label = "toggleThumbOffset",
  )
  val trackFill by animateColorAsState(
    targetValue = if (checked) colorScheme.onSurface else Color.Transparent,
    label = "toggleTrackFill",
  )
  val borderColor by animateColorAsState(
    targetValue = if (checked) Color.Transparent else colorScheme.onSurface.copy(alpha = 0.30f),
    label = "toggleBorderColor",
  )
  val thumbColor by animateColorAsState(
    targetValue = if (checked) colorScheme.surface else colorScheme.onSurface.copy(alpha = 0.45f),
    label = "toggleThumbColor",
  )

  val interaction =
    when {
      onCheckedChange != null && enabled -> {
        Modifier
          .minimumInteractiveComponentSize()
          .clickable { withHaptic(view) { onCheckedChange(!checked) } }
      }

      else -> {
        Modifier
      }
    }

  Box(
    modifier = modifier.alpha(if (enabled) 1f else 0.4f).then(interaction),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier
          .size(width = trackWidth, height = trackHeight)
          .clip(CircleShape)
          .background(trackFill)
          .border(width = 1.5.dp, color = borderColor, shape = CircleShape),
    ) {
      Box(
        modifier =
          Modifier
            .align(Alignment.CenterStart)
            .offset { IntOffset(x = thumbOffset.roundToPx(), y = 0) }
            .size(thumbSize)
            .clip(CircleShape)
            .background(thumbColor),
      )
    }
  }
}
