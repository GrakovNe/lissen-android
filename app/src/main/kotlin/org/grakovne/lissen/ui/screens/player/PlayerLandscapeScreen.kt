package org.grakovne.lissen.ui.screens.player

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.request.ImageRequest
import com.valentinilk.shimmer.shimmer
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.ui.components.AsyncShimmeringImage
import org.grakovne.lissen.ui.screens.player.composable.TrackControlComposable
import org.grakovne.lissen.ui.screens.player.composable.placeholder.TrackControlPlaceholderComposable
import org.grakovne.lissen.viewmodel.LibraryViewModel
import org.grakovne.lissen.viewmodel.PlayerViewModel
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
fun PlayerLandscapeContent(
  innerPadding: PaddingValues,
  book: DetailedItem?,
  isPlaybackReady: Boolean,
  bookTitle: String,
  bookSubtitle: String?,
  playerViewModel: PlayerViewModel,
  libraryViewModel: LibraryViewModel,
  settingsViewModel: SettingsViewModel,
  imageLoader: ImageLoader,
) {
  val currentTrackIndex by playerViewModel.currentChapterIndex.observeAsState(0)
  val context = LocalContext.current
  val libraryType = libraryViewModel.fetchPreferredLibraryType()

  val imageRequest =
    remember(book?.id) {
      ImageRequest
        .Builder(context)
        .data(book?.id)
        .size(coil3.size.Size.ORIGINAL)
        .build()
    }

  Row(
    modifier =
      Modifier
        .testTag("playerScreen")
        .padding(innerPadding)
        .fillMaxSize(),
  ) {
    Box(
      modifier =
        Modifier
          .fillMaxHeight()
          .aspectRatio(1f, matchHeightConstraintsFirst = true)
          .padding(16.dp),
      contentAlignment = Alignment.Center,
    ) {
      if (isPlaybackReady) {
        AsyncShimmeringImage(
          imageRequest = imageRequest,
          imageLoader = imageLoader,
          contentDescription = "${book?.title} cover",
          contentScale = ContentScale.FillBounds,
          modifier =
            Modifier
              .fillMaxSize()
              .clip(RoundedCornerShape(8.dp)),
          error = painterResource(R.drawable.cover_fallback),
        )
      } else {
        Box(
          modifier =
            Modifier
              .fillMaxSize()
              .clip(RoundedCornerShape(8.dp))
              .shimmer()
              .background(Color.Gray),
        )
      }
    }

    Column(
      modifier =
        Modifier
          .weight(1f)
          .fillMaxHeight()
          .padding(end = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      val displayTitle = if (isPlaybackReady) book?.title.orEmpty() else bookTitle
      val displaySubtitle = if (isPlaybackReady) book?.subtitle else bookSubtitle

      Text(
        text = displayTitle,
        style = typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = colorScheme.onBackground,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
      )

      displaySubtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = subtitle,
          style = typography.bodySmall,
          color = colorScheme.onBackground.copy(alpha = 0.6f),
          textAlign = TextAlign.Center,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
        )
      }

      if (isPlaybackReady) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text =
            provideLandscapeChapterTitle(
              currentTrackIndex = currentTrackIndex,
              book = book,
              libraryType = libraryType,
              context = context,
            ),
          style = typography.bodySmall,
          color = colorScheme.onBackground.copy(alpha = 0.6f),
          textAlign = TextAlign.Center,
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      if (isPlaybackReady) {
        TrackControlComposable(
          viewModel = playerViewModel,
          settingsViewModel = settingsViewModel,
        )
      } else {
        TrackControlPlaceholderComposable(
          settingsViewModel = settingsViewModel,
        )
      }
    }
  }
}

private fun provideLandscapeChapterTitle(
  currentTrackIndex: Int,
  book: DetailedItem?,
  libraryType: LibraryType,
  context: Context,
): String =
  when (libraryType) {
    LibraryType.LIBRARY -> {
      context.getString(
        R.string.player_screen_now_playing_title_chapter_of,
        currentTrackIndex + 1,
        book?.chapters?.size ?: "?",
      )
    }

    LibraryType.PODCAST -> {
      context.getString(
        R.string.player_screen_now_playing_title_podcast_of,
        currentTrackIndex + 1,
        book?.chapters?.size ?: "?",
      )
    }

    LibraryType.UNKNOWN -> {
      context.getString(
        R.string.player_screen_now_playing_title_item_of,
        currentTrackIndex + 1,
        book?.chapters?.size ?: "?",
      )
    }
  }
