package org.grakovne.lissen.ui.screens.player.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R

/**
 * Title of the collapsed playing queue. On a phone the row is an entry point: the trailing
 * chevron is the same "drill in" affordance the settings items use, a tap expands the queue
 * and the screen title takes the queue title over while back collapses it. Where the queue
 * is always expanded (two-pane layout) the row is a plain title.
 */
@Composable
fun PlayingQueueHeaderComposable(
  title: String,
  textStyle: TextStyle,
  expandable: Boolean,
  modifier: Modifier = Modifier,
  onExpand: () -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      modifier
        .fillMaxWidth()
        .let {
          when (expandable) {
            true -> {
              it
                .clickable(
                  interactionSource = remember { MutableInteractionSource() },
                  indication = null,
                ) { onExpand() }
                .testTag("playingQueueHeader")
            }

            false -> {
              it
            }
          }
        }.padding(horizontal = 6.dp),
  ) {
    Text(
      text = title,
      style = textStyle,
      color = colorScheme.primary,
      maxLines = 1,
      modifier = Modifier.weight(1f),
    )

    if (expandable) {
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
        contentDescription = stringResource(R.string.a11y_expand_queue),
        tint = colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
      )
    }
  }
}
