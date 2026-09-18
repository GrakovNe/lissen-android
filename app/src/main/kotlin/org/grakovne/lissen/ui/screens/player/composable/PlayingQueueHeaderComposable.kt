package org.grakovne.lissen.ui.screens.player.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowDropUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R

/**
 * Title of the playing queue with the expand arrow that took over the former "Chapters" tile.
 * Sits above the list while collapsed and moves into the top bar while expanded; the arrow is
 * hidden where the queue is always expanded (two-pane layout).
 */
@Composable
fun PlayingQueueHeaderComposable(
  title: String,
  textStyle: TextStyle,
  color: Color,
  expanded: Boolean,
  switchable: Boolean,
  modifier: Modifier = Modifier,
  onToggle: () -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      modifier
        .let {
          when (switchable) {
            true -> {
              it.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
              ) { onToggle() }
            }

            false -> {
              it
            }
          }
        },
  ) {
    Text(
      text = title,
      style = textStyle,
      color = color,
      maxLines = 1,
    )

    if (switchable) {
      Spacer(modifier = Modifier.width(4.dp))

      Icon(
        imageVector = if (expanded) Icons.Outlined.ArrowDropUp else Icons.Outlined.ArrowDropDown,
        contentDescription = stringResource(if (expanded) R.string.a11y_collapse_queue else R.string.a11y_expand_queue),
        tint = color,
        modifier =
          Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            .testTag("playingQueueSwitch"),
      )
    }
  }
}
