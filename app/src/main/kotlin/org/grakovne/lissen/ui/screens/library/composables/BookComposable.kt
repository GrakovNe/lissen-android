package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.request.ImageRequest
import org.grakovne.lissen.R
import org.grakovne.lissen.lib.domain.PlayingItem
import org.grakovne.lissen.ui.components.AsyncShimmeringImage
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.viewmodel.LibraryViewModel

@Composable
fun BookComposable(
  playingItem: PlayingItem,
  imageLoader: ImageLoader,
  navController: AppNavigationService,
  libraryViewModel: LibraryViewModel,
  onContentRefresh: () -> Unit,
) {
  val context = LocalContext.current

  var isPlayingItemOptionsExpanded by remember { mutableStateOf(false) }

  val imageRequest =
    remember(playingItem.id) {
      ImageRequest
        .Builder(context)
        .data(playingItem.id)
        .build()
    }

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .combinedClickable(
          onClick = { navController.showPlayer(playingItem.id, playingItem.title, playingItem.subtitle) },
          hapticFeedbackEnabled = true,
          onLongClick = { isPlayingItemOptionsExpanded = true },
        ).testTag("bookItem_${playingItem.id}")
        .padding(horizontal = 4.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AsyncShimmeringImage(
      imageRequest = imageRequest,
      imageLoader = imageLoader,
      contentDescription = "${playingItem.title} cover",
      contentScale = ContentScale.FillBounds,
      modifier =
        Modifier
          .size(64.dp)
          .aspectRatio(1f)
          .clip(RoundedCornerShape(4.dp)),
      error = painterResource(R.drawable.cover_fallback),
    )

    Spacer(Modifier.width(16.dp))

    Column(
      Modifier
        .weight(1f),
    ) {
      Column {
        Text(
          text = playingItem.title,
          style =
            MaterialTheme.typography.bodyMedium.copy(
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onBackground,
            ),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      BookMetadataComposable(playingItem)
    }

    Spacer(Modifier.width(16.dp))
  }

  if (isPlayingItemOptionsExpanded) {
    PlayingItemOptionsComposable(
      item = playingItem,
      onMarkFinished = {
        libraryViewModel.markPlayingItemsListened(playingItem)
      },
      onResetProgress = {
        libraryViewModel.resetPlayingItemProgress(playingItem)
      },
      onDismissRequest = { isPlayingItemOptionsExpanded = false },
    )
  }
}

@Composable
fun BookMetadataComposable(playingItem: PlayingItem) {
  if ((playingItem.series?.isNotBlank() == true) || (playingItem.author != null)) {
    Spacer(modifier = Modifier.height(2.dp))
  }

  playingItem.author?.takeIf { it.isNotBlank() }?.let {
    Text(
      text = it,
      style =
        MaterialTheme.typography.bodyMedium.copy(
          color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        ),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }

  playingItem.series?.takeIf { it.isNotBlank() }?.let {
    Text(
      text = it,
      style =
        MaterialTheme.typography.bodyMedium.copy(
          color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        ),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
