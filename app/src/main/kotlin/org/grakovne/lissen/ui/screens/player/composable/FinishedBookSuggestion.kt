package org.grakovne.lissen.ui.screens.player.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.request.ImageRequest
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.FinishedSuggestion
import org.grakovne.lissen.domain.FinishedSuggestionSource
import org.grakovne.lissen.domain.MAX_FINISHED_CANDIDATES
import org.grakovne.lissen.ui.adaptive.recentBookItemWidth
import org.grakovne.lissen.ui.components.AsyncShimmeringImage
import org.grakovne.lissen.ui.components.BookCoverCarousel
import org.grakovne.lissen.ui.components.BookCoverKey
import org.grakovne.lissen.ui.components.CarouselBook
import org.grakovne.lissen.ui.components.LissenModalBottomSheet

private val SHEET_HORIZONTAL_PADDING = 24.dp

@Composable
fun FinishedBookSuggestion(
  book: DetailedItem,
  suggestion: FinishedSuggestion,
  imageLoader: ImageLoader,
  coverModel: Any,
  candidateCoverModel: (Book) -> Any = { BookCoverKey(it.id) },
  onDismiss: () -> Unit,
  onCandidateClick: (Book) -> Unit,
  onSeeAll: () -> Unit,
) {
  LissenModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = colorScheme.surface,
    scrollable = false,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(bottom = 32.dp),
    ) {
      Column(Modifier.padding(horizontal = SHEET_HORIZONTAL_PADDING)) {
        FinishedBookRow(
          book = book,
          coverModel = coverModel,
          imageLoader = imageLoader,
        )

        Spacer(Modifier.height(24.dp))

        Text(
          text = finishedMoreLabel(book, suggestion),
          style = typography.bodyLarge,
          color = colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Spacer(Modifier.height(12.dp))

      BookCoverCarousel(
        books =
          suggestion.candidates.map { candidate ->
            CarouselBook(
              id = candidate.id,
              coverModel = candidateCoverModel(candidate),
              title = candidate.title,
              subtitle = candidate.subtitle,
              author = candidate.author,
            )
          },
        imageLoader = imageLoader,
        onClick = { carouselBook ->
          suggestion.candidates.first { it.id == carouselBook.id }.let(onCandidateClick)
        },
        modifier =
          Modifier
            .padding(horizontal = SHEET_HORIZONTAL_PADDING),
        centered = isCarouselCentered(suggestion),
        itemTestTag = "finishedCandidate",
      )

      Spacer(Modifier.height(16.dp))

      Text(
        text = finishedSeeAllLabel(book, suggestion),
        style = typography.bodyMedium,
        color = colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier =
          Modifier
            .fillMaxWidth()
            .testTag("finishedSeeAll")
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
            ) { onSeeAll() }
            .padding(vertical = 8.dp),
      )
    }
  }
}

@Composable
private fun isCarouselCentered(suggestion: FinishedSuggestion): Boolean {
  if (suggestion.candidates.size < MAX_FINISHED_CANDIDATES) {
    return true
  }

  val itemWidth = recentBookItemWidth()
  val availableWidth = LocalConfiguration.current.screenWidthDp.dp - SHEET_HORIZONTAL_PADDING * 2

  return itemWidth * suggestion.candidates.size + 16.dp * (suggestion.candidates.size - 1) <= availableWidth
}

@Composable
private fun FinishedBookRow(
  book: DetailedItem,
  coverModel: Any,
  imageLoader: ImageLoader,
) {
  val context = LocalContext.current

  val imageRequest =
    remember(coverModel) {
      ImageRequest
        .Builder(context)
        .data(coverModel)
        .build()
    }

  Row(verticalAlignment = Alignment.CenterVertically) {
    AsyncShimmeringImage(
      imageRequest = imageRequest,
      imageLoader = imageLoader,
      contentDescription = null,
      contentScale = ContentScale.FillBounds,
      modifier =
        Modifier
          .size(64.dp)
          .clip(RoundedCornerShape(6.dp)),
      error = painterResource(R.drawable.cover_fallback),
    )

    Spacer(Modifier.width(16.dp))

    Column(Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.playing_finished_title).uppercase(),
        style = typography.labelMedium,
        color = colorScheme.primary,
      )

      Spacer(Modifier.height(2.dp))

      Text(
        text = book.title,
        style = typography.bodyMedium,
        color = colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )

      book
        .author
        ?.takeIf { it.isNotBlank() }
        ?.let {
          Text(
            text = it,
            style = typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
    }
  }
}

@Composable
private fun finishedMoreLabel(
  book: DetailedItem,
  suggestion: FinishedSuggestion,
): String =
  when (suggestion.source) {
    FinishedSuggestionSource.SERIES -> stringResource(R.string.playing_finished_more_series, book.seriesName())
    FinishedSuggestionSource.AUTHOR -> stringResource(R.string.playing_finished_more_author, book.author.orEmpty())
    FinishedSuggestionSource.LIBRARY -> stringResource(R.string.playing_finished_more_library)
  }

@Composable
private fun finishedSeeAllLabel(
  book: DetailedItem,
  suggestion: FinishedSuggestion,
): String =
  when (suggestion.source) {
    FinishedSuggestionSource.SERIES -> stringResource(R.string.playing_finished_see_all_series)
    FinishedSuggestionSource.AUTHOR -> stringResource(R.string.playing_finished_see_all_author, book.author.orEmpty())
    FinishedSuggestionSource.LIBRARY -> stringResource(R.string.playing_finished_see_all_library)
  }

fun DetailedItem.seriesName(): String = series.firstOrNull { it.name.isNotBlank() }?.name.orEmpty()
