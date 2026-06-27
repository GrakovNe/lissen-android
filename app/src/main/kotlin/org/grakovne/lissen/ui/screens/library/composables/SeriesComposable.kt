package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.ui.components.AsyncShimmeringImage
import org.grakovne.lissen.ui.navigation.AppNavigationService

private val SERIES_COVER_SIZE = 64.dp
private val SERIES_COVER_STEP = 6.dp

// How long a series row must stay on screen before its books are prefetched.
private const val SERIES_PREFETCH_DWELL_MS = 200L

@Composable
fun SeriesComposable(
  series: LibraryEntry.SeriesEntry,
  expanded: Boolean,
  loading: Boolean,
  books: List<Book>,
  imageLoader: ImageLoader,
  navController: AppNavigationService,
  onToggle: () -> Unit,
  onPrefetch: () -> Unit,
) {
  val context = LocalContext.current

  // The effect lives only while the row is composed, so scrolling past before the dwell cancels it.
  LaunchedEffect(series.id) {
    delay(SERIES_PREFETCH_DWELL_MS)
    onPrefetch()
  }

  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .clickable { onToggle() }
          .testTag("seriesItem_${series.id}")
          .padding(horizontal = 4.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SeriesCoverStack(
        coverItemIds = series.coverItemIds,
        contentDescription = "${series.title} cover",
        imageLoader = imageLoader,
      )

      Spacer(Modifier.width(16.dp))

      Column(Modifier.weight(1f)) {
        Text(
          text = series.title,
          style =
            MaterialTheme.typography.bodyMedium.copy(
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onBackground,
            ),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(2.dp))

        series.author
          ?.takeIf { it.isNotBlank() }
          ?.let {
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

        Text(
          text = context.resources.getQuantityString(R.plurals.series_books_count, series.bookCount, series.bookCount),
          style =
            MaterialTheme.typography.bodyMedium.copy(
              color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            ),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Spacer(Modifier.width(16.dp))

      Icon(
        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
      )
    }

    AnimatedVisibility(visible = expanded) {
      Column(modifier = Modifier.fillMaxWidth()) {
        when {
          loading && books.isEmpty() -> {
            Box(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .padding(vertical = 16.dp),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }

          else -> {
            val sequences = books.mapIndexed { index, book -> book.seriesSequence() ?: "${index + 1}" }
            // Reserve the width of the longest number so every number is right-aligned under the same edge.
            val widthReserve = "0".repeat(sequences.maxOfOrNull { it.length } ?: 1)

            books.forEachIndexed { index, book ->
              BookComposable(
                book = book,
                imageLoader = imageLoader,
                navController = navController,
                showSeries = false,
                leading = { SeriesSequenceLabel(number = sequences[index], widthReserve = widthReserve) },
              )
            }
          }
        }
      }
    }
  }
}

/** The book's position in the series (e.g. "1", "2.5"), parsed from its formatted series string. */
private fun Book.seriesSequence(): String? =
  series
    ?.substringAfterLast('#', "")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

@Composable
private fun SeriesSequenceLabel(
  number: String,
  widthReserve: String,
) {
  val style =
    MaterialTheme.typography.bodyMedium.copy(
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
    )

  Box(
    modifier = Modifier.padding(end = 10.dp),
    contentAlignment = Alignment.CenterEnd,
  ) {
    // Invisible widest number reserves the column width; the visible one is then right-aligned.
    Text(text = widthReserve, style = style, maxLines = 1, modifier = Modifier.alpha(0f))
    Text(text = number, style = style, maxLines = 1)
  }
}

/**
 * Renders up to [LibraryEntry.SeriesEntry.MAX_COVERS] covers as an overlapping stack so the row reads
 * as a series, while keeping the same footprint as a regular book cover.
 */
@Composable
private fun SeriesCoverStack(
  coverItemIds: List<String>,
  contentDescription: String,
  imageLoader: ImageLoader,
) {
  val context = LocalContext.current
  val covers = coverItemIds.take(LibraryEntry.SeriesEntry.MAX_COVERS)

  // The step between cards is constant, while the card size grows as covers get fewer so the stack
  // always fills the whole box (1 cover -> a full-size cover, no padding; 2 -> 58dp; 3 -> 52dp).
  val cardSize = SERIES_COVER_SIZE - SERIES_COVER_STEP * (covers.size - 1).coerceAtLeast(0)

  Box(modifier = Modifier.size(SERIES_COVER_SIZE)) {
    covers
      .asReversed()
      .forEachIndexed { index, coverId ->
        val offset = SERIES_COVER_STEP * index
        val imageRequest =
          remember(coverId) {
            ImageRequest
              .Builder(context)
              .data(coverId)
              .build()
          }

        AsyncShimmeringImage(
          imageRequest = imageRequest,
          imageLoader = imageLoader,
          contentDescription = contentDescription,
          contentScale = ContentScale.FillBounds,
          modifier =
            Modifier
              .offset(x = offset, y = offset)
              .size(cardSize)
              .shadow(2.dp, RoundedCornerShape(4.dp))
              .clip(RoundedCornerShape(4.dp)),
          error = painterResource(R.drawable.cover_fallback),
        )
      }
  }
}
