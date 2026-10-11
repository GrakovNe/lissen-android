package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import kotlinx.coroutines.delay
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.common.seriesSequence
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.ui.navigation.AppNavigationService

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
  LaunchedEffect(series.id) {
    delay(SERIES_PREFETCH_DWELL_MS)
    onPrefetch()
  }

  LibraryGroupComposable(
    title = series.title,
    subtitle = series.author?.takeIf { it.isNotBlank() },
    itemCount = series.bookCount,
    testTag = "seriesItem_${series.id}",
    expanded = expanded,
    loading = loading,
    items = books,
    onToggle = onToggle,
    cover = { GroupCoverStack(groupId = series.id, coverItemIds = series.coverItemIds, imageLoader = imageLoader) },
    itemRows = {
      val sequences = it.mapIndexed { index, book -> book.seriesSequence() ?: "${index + 1}" }
      val widthReserve = "0".repeat(sequences.maxOfOrNull { sequence -> sequence.length } ?: 1)

      it.forEachIndexed { index, book ->
        BookComposable(
          book = book,
          imageLoader = imageLoader,
          navController = navController,
          grouping = LibraryGrouping.SERIES,
          leading = { SeriesSequenceLabel(number = sequences[index], widthReserve = widthReserve) },
        )
      }
    },
  )
}

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
    Text(text = widthReserve, style = style, maxLines = 1, modifier = Modifier.alpha(0f))
    Text(text = number, style = style, maxLines = 1)
  }
}
