package org.grakovne.lissen.ui.screens.player.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import org.grakovne.lissen.ui.components.BookCoverKey
import org.grakovne.lissen.ui.components.LissenModalBottomSheet

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
      Column(Modifier.padding(horizontal = 24.dp)) {
        FinishedBookRow(
          book = book,
          coverModel = coverModel,
          imageLoader = imageLoader,
        )

        Spacer(Modifier.height(24.dp))

        Text(
          text = finishedMoreLabel(book, suggestion),
          style = typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Spacer(Modifier.height(12.dp))

      FinishedCandidateCarousel(
        suggestion = suggestion,
        imageLoader = imageLoader,
        candidateCoverModel = candidateCoverModel,
        onCandidateClick = onCandidateClick,
      )

      Spacer(Modifier.height(16.dp))

      Text(
        text = finishedSeeAllLabel(book, suggestion),
        style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
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
private fun FinishedCandidateCarousel(
  suggestion: FinishedSuggestion,
  imageLoader: ImageLoader,
  candidateCoverModel: (Book) -> Any,
  onCandidateClick: (Book) -> Unit,
) {
  val itemWidth = recentBookItemWidth()
  val screenWidth = LocalConfiguration.current.screenWidthDp.dp
  val carouselWidth = itemWidth * suggestion.candidates.size + 16.dp * (suggestion.candidates.size - 1) + 8.dp
  val centered = suggestion.candidates.size < MAX_FINISHED_CANDIDATES || carouselWidth <= screenWidth

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 4.dp),
    horizontalArrangement =
      when (centered) {
        true -> Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        false -> Arrangement.spacedBy(16.dp)
      },
  ) {
    suggestion.candidates.forEach { candidate ->
      FinishedCandidateItem(
        candidate = candidate,
        width = itemWidth,
        coverModel = candidateCoverModel(candidate),
        imageLoader = imageLoader,
        onClick = { onCandidateClick(candidate) },
      )
    }
  }
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
        style = typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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
private fun FinishedCandidateItem(
  candidate: Book,
  width: Dp,
  coverModel: Any,
  imageLoader: ImageLoader,
  onClick: () -> Unit,
) {
  val context = LocalContext.current

  val imageRequest =
    remember(coverModel) {
      ImageRequest
        .Builder(context)
        .data(coverModel)
        .build()
    }

  Column(
    modifier =
      Modifier
        .width(width)
        .testTag("finishedCandidate")
        .clickable(onClick = onClick),
  ) {
    AsyncShimmeringImage(
      imageRequest = imageRequest,
      imageLoader = imageLoader,
      contentDescription = null,
      contentScale = ContentScale.FillBounds,
      modifier =
        Modifier
          .fillMaxWidth()
          .aspectRatio(1f)
          .clip(RoundedCornerShape(8.dp)),
      error = painterResource(R.drawable.cover_fallback),
    )

    Spacer(Modifier.height(6.dp))

    Text(
      text = candidate.title,
      style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
      color = colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )

    candidate
      .author
      ?.takeIf { it.isNotBlank() }
      ?.let {
        Spacer(Modifier.height(2.dp))

        Text(
          text = it,
          style = typography.bodySmall.copy(color = colorScheme.onSurface.copy(alpha = 0.6f)),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
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
    FinishedSuggestionSource.RANDOM -> stringResource(R.string.playing_finished_more_random)
  }

@Composable
private fun finishedSeeAllLabel(
  book: DetailedItem,
  suggestion: FinishedSuggestion,
): String =
  when (suggestion.source) {
    FinishedSuggestionSource.SERIES -> stringResource(R.string.playing_finished_see_all_series)
    FinishedSuggestionSource.AUTHOR -> stringResource(R.string.playing_finished_see_all_author, book.author.orEmpty())
    FinishedSuggestionSource.RANDOM -> stringResource(R.string.playing_finished_see_all_random)
  }

fun DetailedItem.seriesName(): String = series.firstOrNull { it.name.isNotBlank() }?.name.orEmpty()
