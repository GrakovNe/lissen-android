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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.BookChapterState
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.ui.extensions.formatLeadingMinutes

@Composable
fun PlaylistItemComposable(
    track: PlayingChapter,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(horizontal = 6.dp)
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

            track.podcastEpisodeState == BookChapterState.FINISHED -> Icon(
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
            color = when (track.available) {
                true -> colorScheme.onBackground
                false -> colorScheme.onBackground.copy(alpha = 0.4f)
            },
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = track.duration.toInt().formatLeadingMinutes(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp),
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = when (track.available) {
                true -> colorScheme.onBackground.copy(alpha = 0.6f)
                false -> colorScheme.onBackground.copy(alpha = 0.4f)
            },
        )
    }
}
