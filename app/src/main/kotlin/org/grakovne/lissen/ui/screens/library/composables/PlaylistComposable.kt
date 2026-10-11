package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import org.grakovne.lissen.R
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.PlaylistItem
import org.grakovne.lissen.ui.navigation.AppNavigationService

@Composable
fun PlaylistComposable(
  playlist: LibraryEntry.PlaylistEntry,
  expanded: Boolean,
  imageLoader: ImageLoader,
  navController: AppNavigationService,
  onToggle: () -> Unit,
) {
  LibraryGroupComposable(
    groupId = playlist.id,
    title = playlist.name,
    itemCount = playlist.itemCount,
    testTag = "playlistItem_${playlist.id}",
    expanded = expanded,
    loading = false,
    items = playlist.items,
    onToggle = onToggle,
    countPlurals = R.plurals.playlist_items_count,
    cover = { GroupCoverStack(groupId = playlist.id, coverItemIds = playlist.coverItemIds, imageLoader = imageLoader) },
    itemRows = { rows ->
      rows.forEach { item ->
        when (item.episodeId) {
          null -> {
            BookComposable(
              book = item.book,
              imageLoader = imageLoader,
              navController = navController,
              grouping = LibraryGrouping.PLAYLIST,
              leading = { GroupBookLeading() },
            )
          }

          else -> {
            PlaylistEpisodeComposable(
              item = item,
              episodeId = item.episodeId,
              imageLoader = imageLoader,
              navController = navController,
            )
          }
        }
      }
    },
  )
}

@Composable
private fun PlaylistEpisodeComposable(
  item: PlaylistItem,
  episodeId: String,
  imageLoader: ImageLoader,
  navController: AppNavigationService,
) {
  val podcast = item.book

  LibraryItemRowComposable(
    coverItemId = podcast.id,
    title = item.episodeTitle ?: podcast.title,
    testTag = "playlistEpisodeItem_${podcast.id}_$episodeId",
    imageLoader = imageLoader,
    onClick = { navController.showPlayer(podcast.id, podcast.title, podcast.subtitle, chapterId = episodeId) },
    leading = { GroupBookLeading() },
    metadata = {
      Spacer(modifier = Modifier.height(2.dp))

      Text(
        text = podcast.title,
        style =
          MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
          ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    },
  )
}
