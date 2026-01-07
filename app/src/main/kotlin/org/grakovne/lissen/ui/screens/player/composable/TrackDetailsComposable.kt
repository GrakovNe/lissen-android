package org.grakovne.lissen.ui.screens.player.composable

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.request.ImageRequest
import org.grakovne.lissen.R
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.ui.components.AsyncShimmeringImage
import org.grakovne.lissen.viewmodel.LibraryViewModel
import org.grakovne.lissen.viewmodel.PlayerViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackDetailsComposable(
  libraryViewModel: LibraryViewModel,
  viewModel: PlayerViewModel,
  modifier: Modifier = Modifier,
  imageLoader: ImageLoader,
  onTitleClick: () -> Unit = {},
  onChapterClick: () -> Unit = {},
) {
  val currentTrackIndex by viewModel.currentChapterIndex.observeAsState(0)
  val book by viewModel.book.observeAsState()

  val context = LocalContext.current

  val imageRequest =
    remember(book?.id) {
      ImageRequest
        .Builder(context)
        .data(book?.id)
        .size(coil3.size.Size.ORIGINAL)
        .build()
    }

  val configuration = LocalConfiguration.current
  val screenHeight = configuration.screenHeightDp.dp
  val maxImageHeight = screenHeight * 0.40f

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier,
  ) {
    AsyncShimmeringImage(
      imageRequest = imageRequest,
      imageLoader = imageLoader,
      contentDescription = "${book?.title} cover",
      contentScale = ContentScale.FillBounds,
      modifier =
        Modifier
          .heightIn(max = maxImageHeight)
          .aspectRatio(1f)
          .clip(RoundedCornerShape(8.dp))
          .shadow(12.dp, RoundedCornerShape(8.dp)),
      error = painterResource(R.drawable.cover_fallback),
    )

    Spacer(modifier = Modifier.height(24.dp))

    // Book Title
    Text(
      text = book?.title.orEmpty(),
      style = typography.headlineMedium,
      fontWeight = FontWeight.Bold,
      color = colorScheme.onBackground,
      textAlign = TextAlign.Center,
      overflow = TextOverflow.Ellipsis,
      maxLines = 2,
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp)
          .clickable(onClick = onTitleClick),
    )

    Spacer(modifier = Modifier.height(4.dp))

    // Author
    book?.author?.takeIf { it.isNotBlank() }?.let { author ->
      Text(
        text = stringResource(R.string.book_detail_author_pattern, author),
        style = typography.titleSmall,
        color = colorScheme.onBackground.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
      )
      Spacer(modifier = Modifier.height(24.dp))
    }

    // Chapter Title & Index
    val chapterTitle = book?.chapters?.getOrNull(currentTrackIndex)?.title
    val chapterIndexString =
      provideChapterIndexTitle(
        currentTrackIndex = currentTrackIndex,
        book = book,
        libraryType = libraryViewModel.fetchPreferredLibraryType(),
        context = context,
      )

    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.fillMaxWidth().clickable(onClick = onChapterClick),
    ) {
      if (!chapterTitle.isNullOrBlank()) {
        Text(
          text = chapterTitle,
          style = typography.bodyMedium,
          color = colorScheme.onBackground.copy(alpha = 0.9f),
          textAlign = TextAlign.Center,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier =
            Modifier
              .fillMaxWidth(),
        )

        Text(
          text = chapterIndexString,
          style = typography.bodySmall,
          textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
          color = colorScheme.onBackground.copy(alpha = 0.5f),
          textAlign = TextAlign.Center,
          maxLines = 1,
          modifier = Modifier.fillMaxWidth(),
        )
      } else {
        Text(
          text = chapterIndexString,
          style = typography.bodyMedium,
          textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
          color = colorScheme.onBackground.copy(alpha = 0.7f),
          textAlign = TextAlign.Center,
          maxLines = 1,
          modifier =
            Modifier
              .fillMaxWidth(),
        )
      }
    }
  }
}

private fun provideChapterIndexTitle(
  currentTrackIndex: Int,
  book: DetailedItem?,
  libraryType: LibraryType,
  context: Context,
): String =
  when (libraryType) {
    LibraryType.LIBRARY ->
      context.getString(
        R.string.player_screen_now_playing_title_chapter_of,
        currentTrackIndex + 1,
        book?.chapters?.size ?: "?",
      )

    LibraryType.PODCAST ->
      context.getString(
        R.string.player_screen_now_playing_title_podcast_of,
        currentTrackIndex + 1,
        book?.chapters?.size ?: "?",
      )

    LibraryType.UNKNOWN ->
      context.getString(
        R.string.player_screen_now_playing_title_item_of,
        currentTrackIndex + 1,
        book?.chapters?.size ?: "?",
      )
  }

private fun provideChapterNumberTitle(
  currentTrackIndex: Int,
  book: DetailedItem?,
  libraryType: LibraryType,
  context: Context,
): String =
  when (libraryType) {
    LibraryType.LIBRARY -> {
      val part =
        context.getString(
          R.string.player_screen_now_playing_title_chapter_of,
          currentTrackIndex + 1,
          book?.chapters?.size ?: "?",
        )
      val chapterTitle = book?.chapters?.getOrNull(currentTrackIndex)?.title
      if (chapterTitle.isNullOrBlank()) part else "$chapterTitle ($part)"
    }

    LibraryType.PODCAST ->
      context.getString(
        R.string.player_screen_now_playing_title_podcast_of,
        currentTrackIndex + 1,
        book?.chapters?.size ?: "?",
      )

    LibraryType.UNKNOWN ->
      context.getString(
        R.string.player_screen_now_playing_title_item_of,
        currentTrackIndex + 1,
        book?.chapters?.size ?: "?",
      )
  }
