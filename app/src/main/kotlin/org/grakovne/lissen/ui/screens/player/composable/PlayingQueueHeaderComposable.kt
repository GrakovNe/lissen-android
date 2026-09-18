package org.grakovne.lissen.ui.screens.player.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R

/**
 * Title of the playing queue. On a phone the queue behaves like a sheet, so a drag handle sits
 * above the title: it tells that the list can be pulled up and down, and a tap toggles it.
 * Where the queue is always expanded (two-pane layout) there is no handle.
 */
@Composable
fun PlayingQueueHeaderComposable(
  title: String,
  textStyle: TextStyle,
  expanded: Boolean,
  draggable: Boolean,
  modifier: Modifier = Modifier,
  onToggle: () -> Unit,
) {
  val handleDescription = stringResource(if (expanded) R.string.a11y_collapse_queue else R.string.a11y_expand_queue)

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .let {
          when (draggable) {
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
    if (draggable) {
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .semantics { contentDescription = handleDescription }
            .testTag("playingQueueHandle"),
      ) {
        Box(
          modifier =
            Modifier
              .width(32.dp)
              .height(4.dp)
              .clip(RoundedCornerShape(2.dp))
              .background(colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
      }
    }

    Text(
      text = title,
      style = textStyle,
      color = colorScheme.primary,
      maxLines = 1,
      modifier = Modifier.padding(horizontal = 6.dp),
    )
  }
}
