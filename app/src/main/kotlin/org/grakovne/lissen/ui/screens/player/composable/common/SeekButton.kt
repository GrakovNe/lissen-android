package org.grakovne.lissen.ui.screens.player.composable.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.grakovne.lissen.R

@Composable
fun SeekButton(
  duration: Int,
  isForward: Boolean,
  onClick: () -> Unit,
) {
  IconButton(
    onClick = onClick,
    modifier = Modifier.size(48.dp),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = Icons.Rounded.Replay,
        contentDescription =
          when (isForward) {
            true -> stringResource(R.string.seek_forward_description, duration)
            false -> stringResource(R.string.seek_rewind_description, duration)
          },
        tint = MaterialTheme.colorScheme.onBackground,
        modifier =
          Modifier
            .size(48.dp)
            .graphicsLayer {
              if (isForward) {
                scaleX = -1f
              }
            },
      )

      Text(
        text = duration.toString(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.offset(y = 2.dp),
      )
    }
  }
}
