package org.grakovne.lissen.ui.screens.player.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.lib.domain.BookChapterState
import org.grakovne.lissen.lib.domain.PlayingChapter
import org.grakovne.lissen.ui.extensions.formatTime

@Composable
fun PlaylistItemComposable(
  track: PlayingChapter,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier,
  maxDuration: Double,
  isCached: Boolean,
) {
  val fontScale = LocalDensity.current.fontScale
  val textMeasurer = rememberTextMeasurer()
  val density = LocalDensity.current

  val forceLeadingHours = maxDuration >= 60 * 60
  val maxDurationText = remember(maxDuration) { maxDuration.toInt().formatTime(forceLeadingHours) }
  val bodySmallStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)

  val durationColumnWidth =
    remember(maxDurationText, density, bodySmallStyle) {
      with(density) {
        textMeasurer
          .measure(
            text = AnnotatedString(maxDurationText),
            style = bodySmallStyle,
          ).size
          .width
          .toDp()
      }
    }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      modifier
        .padding(start = 6.dp)
        .padding(end = 4.dp)
        .padding(vertical = 2.dp)
        .clickable(
          onClick = onClick,
          indication = null,
          interactionSource = remember { MutableInteractionSource() },
        ),
  ) {
    when {
      isSelected ->
        Icon(
          imageVector = Icons.Outlined.Audiotrack,
          contentDescription = stringResource(R.string.player_screen_library_playing_title),
          modifier = Modifier.size(16.dp),
        )

      track.podcastEpisodeState == BookChapterState.FINISHED ->
        Icon(
          imageVector = Icons.Outlined.Check,
          contentDescription = stringResource(R.string.player_screen_library_playing_title),
          modifier = Modifier.size(16.dp),
        )

      else -> Spacer(modifier = Modifier.size(16.dp))
    }

    Spacer(modifier = Modifier.width(8.dp))

    Text(
      text = track.title,
      style = MaterialTheme.typography.titleSmall,
      color =
        when (track.available) {
          true -> colorScheme.onBackground
          false -> colorScheme.onBackground.copy(alpha = 0.4f)
        },
      overflow = TextOverflow.Ellipsis,
      fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
      modifier =
        Modifier
          .weight(1f)
          .padding(end = 12.dp),
    )

    if (isCached) {
      Icon(
        imageVector = ImageVector.vectorResource(id = R.drawable.available_offline_filled),
        contentDescription = "Available offline",
        modifier =
          Modifier
            .padding(horizontal = 6.dp * fontScale)
            .size(12.dp),
        tint =
          colorScheme.onBackground.copy(
            alpha = if (isSelected) 0.6f else 0.4f,
          ),
      )
    }

    Text(
      text = track.duration.toInt().formatTime(forceLeadingHours),
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier.width(durationColumnWidth),
      textAlign = TextAlign.End,
      fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
      color =
        when (track.available) {
          true -> colorScheme.onBackground.copy(alpha = 0.6f)
          false -> colorScheme.onBackground.copy(alpha = 0.4f)
        },
    )
  }
}
