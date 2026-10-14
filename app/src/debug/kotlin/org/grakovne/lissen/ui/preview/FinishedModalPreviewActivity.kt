package org.grakovne.lissen.ui.preview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint
import org.grakovne.lissen.common.ColorScheme
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.BookSeries
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.FinishedSuggestion
import org.grakovne.lissen.domain.FinishedSuggestionSource
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.ui.components.BookCoverKey
import org.grakovne.lissen.ui.screens.player.composable.FinishedBookSuggestion
import org.grakovne.lissen.ui.theme.LissenTheme
import javax.inject.Inject

/**
 * Debug-only harness: renders the finished-book suggestion over a mock player backdrop
 * with real books fetched from a server, so every suggestion case can be screenshotted.
 *
 * adb shell am start -n org.grakovne.lissen.debug/org.grakovne.lissen.ui.preview.FinishedModalPreviewActivity \
 *   --ei case 1 --es host https://example.org --es username user --es password secret
 */
@AndroidEntryPoint
class FinishedModalPreviewActivity : ComponentActivity() {
  @Inject
  lateinit var imageLoader: ImageLoader

  @Inject
  lateinit var mediaProvider: LissenMediaProvider

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val case = intent.getIntExtra(EXTRA_CASE, 1)
    val host = intent.getStringExtra(EXTRA_HOST).orEmpty()
    val username = intent.getStringExtra(EXTRA_USERNAME).orEmpty()
    val password = intent.getStringExtra(EXTRA_PASSWORD).orEmpty()

    setContent {
      LissenTheme(colorSchemePreference = ColorScheme.LIGHT, materialYouEnabled = false) {
        var state by remember { mutableStateOf<PreviewState?>(null) }

        LaunchedEffect(Unit) { state = loadPreview(mediaProvider, host, username, password, case) }

        Box(
          modifier =
            Modifier
              .fillMaxSize()
              .background(colorScheme.background),
        ) {
          when (val current = state) {
            null -> {
              CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            else -> {
              MockPlayerBackdrop(imageLoader = imageLoader, book = current.book)

              current.suggestion?.let { suggestion ->
                FinishedBookSuggestion(
                  book = current.book,
                  suggestion = suggestion,
                  imageLoader = imageLoader,
                  coverModel = BookCoverKey(current.book.id),
                  onDismiss = {},
                  onCandidateClick = {},
                  onSeeAll = {},
                )
              }
            }
          }
        }
      }
    }
  }

  private companion object {
    const val EXTRA_CASE = "case"
    const val EXTRA_HOST = "host"
    const val EXTRA_USERNAME = "username"
    const val EXTRA_PASSWORD = "password"
  }
}

private class PreviewState(
  val book: DetailedItem,
  val suggestion: FinishedSuggestion?,
)

private suspend fun loadPreview(
  media: LissenMediaProvider,
  host: String,
  username: String,
  password: String,
  case: Int,
): PreviewState {
  media.authorize(host, username, password)

  val libraries = media.fetchLibraries().fold(onSuccess = { it }, onFailure = { emptyList() })
  val libraryId = libraries.first().id
  val books = fetchAllBooks(media, libraryId).sortedBy { it.title }

  val frai = books.filter { it.author == "Макс Фрай" }

  return when (case) {
    1 -> seriesPreview(books, "Князь света", "Хроники Амбера", frai.take(3))
    2 -> seriesPreview(books, "Князь света", "Хроники Амбера", frai.take(2))
    3 -> seriesPreview(books, "Князь света", "Хроники Амбера", frai.take(1))
    4 -> authorPreview(frai.first(), frai.drop(1).take(3))
    5 -> authorPreview(frai.first(), frai.drop(1).take(2))
    6 -> authorPreview(frai.first(), frai.drop(1).take(1))
    7 -> randomPreview(books.first { it.author == "Фёдор Достоевский" }, books.shuffled().take(3))
    else -> PreviewState(book = toDetailedItem(books.first()), suggestion = null)
  }
}

private suspend fun fetchAllBooks(
  media: LissenMediaProvider,
  libraryId: String,
): List<Book> {
  val collected = mutableListOf<Book>()
  var done = false

  while (!done) {
    done =
      media
        .fetchBooks(libraryId = libraryId, pageSize = 100, pageNumber = collected.size / 100)
        .fold(
          onSuccess = { page ->
            collected += page.items
            page.items.isEmpty() || collected.size >= page.totalItems
          },
          onFailure = { true },
        )
  }

  return collected
}

private fun seriesPreview(
  books: List<Book>,
  finishedTitle: String,
  seriesName: String,
  candidates: List<Book>,
): PreviewState =
  PreviewState(
    book =
      toDetailedItem(
        books.first { it.title == finishedTitle },
      ).copy(series = listOf(BookSeries(serialNumber = "1", name = seriesName))),
    suggestion = FinishedSuggestion(FinishedSuggestionSource.SERIES, candidates),
  )

private fun authorPreview(
  finished: Book,
  candidates: List<Book>,
): PreviewState =
  PreviewState(book = toDetailedItem(finished), suggestion = FinishedSuggestion(FinishedSuggestionSource.AUTHOR, candidates))

private fun randomPreview(
  finished: Book,
  candidates: List<Book>,
): PreviewState =
  PreviewState(book = toDetailedItem(finished), suggestion = FinishedSuggestion(FinishedSuggestionSource.RANDOM, candidates))

private fun toDetailedItem(book: Book): DetailedItem =
  DetailedItem(
    id = book.id,
    title = book.title,
    subtitle = book.subtitle,
    author = book.author,
    narrator = null,
    publisher = null,
    series = book.series?.let { listOf(BookSeries(serialNumber = null, name = it)) }.orEmpty(),
    year = null,
    abstract = null,
    files = emptyList(),
    chapters =
      listOf(
        PlayingChapter(
          available = true,
          podcastEpisodeState = null,
          duration = 61_200.0,
          start = 0.0,
          end = 61_200.0,
          title = "The Whole Book",
          id = "${book.id}-chapter",
        ),
      ),
    progress = MediaProgress(currentTime = 61_200.0, isFinished = true, lastUpdate = 0L),
    libraryId = null,
    libraryType = LibraryType.LIBRARY,
    localProvided = false,
    createdAt = 0L,
    updatedAt = 0L,
  )

@Composable
private fun MockPlayerBackdrop(
  imageLoader: ImageLoader,
  book: DetailedItem,
) {
  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .systemBarsPadding()
        .padding(horizontal = 24.dp, vertical = 48.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    AsyncImage(
      model = BookCoverKey(book.id),
      imageLoader = imageLoader,
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier =
        Modifier
          .size(240.dp)
          .clip(RoundedCornerShape(12.dp)),
    )

    Spacer(Modifier.height(36.dp))

    Text(
      text = book.title,
      style = typography.titleLarge.copy(fontWeight = FontWeight.Bold),
      color = colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(6.dp))

    Text(
      text = book.author.orEmpty(),
      style = typography.bodyMedium,
      color = colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )

    Spacer(Modifier.height(36.dp))

    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(4.dp)
          .clip(RoundedCornerShape(2.dp))
          .background(colorScheme.onSurface.copy(alpha = 0.15f)),
    ) {
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(colorScheme.primary),
      )
    }

    Spacer(Modifier.weight(1f))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Rounded.SkipPrevious,
        contentDescription = null,
        tint = colorScheme.onSurface,
        modifier = Modifier.size(40.dp),
      )

      Box(
        modifier =
          Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(colorScheme.primary),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.PlayArrow,
          contentDescription = null,
          tint = colorScheme.onPrimary,
          modifier = Modifier.size(40.dp),
        )
      }

      Icon(
        imageVector = Icons.Rounded.SkipNext,
        contentDescription = null,
        tint = colorScheme.onSurface,
        modifier = Modifier.size(40.dp),
      )
    }

    Spacer(Modifier.weight(1f))
  }
}
