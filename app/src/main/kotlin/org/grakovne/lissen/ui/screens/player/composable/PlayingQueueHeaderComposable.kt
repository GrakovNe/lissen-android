package org.grakovne.lissen.ui.screens.player.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
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
 * Title row of the playing queue. On a phone it is a collapsible section header: the expand
 * chevron sits right after the title, a tap toggles the section and the row itself stays in
 * place. Where the queue is always expanded (two-pane layout) it is a plain title.
 */
@Composable
fun PlayingQueueHeaderComposable(
  title: String,
  textStyle: TextStyle,
  expanded: Boolean,
  expandable: Boolean,
  modifier: Modifier = Modifier,
  onToggle: () -> Unit,
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
                ) { onToggle() }
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
    )

    if (expandable) {
      Spacer(modifier = Modifier.width(4.dp))

      Icon(
        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
        contentDescription = stringResource(if (expanded) R.string.a11y_collapse_queue else R.string.a11y_expand_queue),
        tint = colorScheme.primary,
        modifier = Modifier.size(24.dp),
      )
    }
  }
}
