package org.grakovne.lissen.ui.screens.player.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowDropUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R

/**
 * Title of the playing queue with the expand arrow that took over the former "Chapters" tile.
 * The arrow is hidden where the queue is always expanded (two-pane layout).
 */
@Composable
fun PlayingQueueHeaderComposable(
  title: String,
  fontSize: TextUnit,
  expanded: Boolean,
  switchable: Boolean,
  modifier: Modifier = Modifier,
  onToggle: () -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      modifier
        .padding(horizontal = 6.dp)
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
      fontSize = fontSize,
      fontWeight = FontWeight.SemiBold,
      color = colorScheme.primary,
    )

    if (switchable) {
      Spacer(modifier = Modifier.width(4.dp))

      Icon(
        imageVector = if (expanded) Icons.Outlined.ArrowDropUp else Icons.Outlined.ArrowDropDown,
        contentDescription = stringResource(if (expanded) R.string.a11y_collapse_queue else R.string.a11y_expand_queue),
        tint = colorScheme.primary,
        modifier =
          Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            .testTag("playingQueueSwitch"),
      )
    }
  }
}
