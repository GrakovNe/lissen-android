package org.grakovne.lissen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.grakovne.lissen.R
import org.grakovne.lissen.ui.adaptive.recentBookItemWidth

data class CarouselBook(
  val id: String,
  val coverModel: Any,
  val title: String,
  val subtitle: String? = null,
  val author: String? = null,
  val progress: Float? = null,
)

@Composable
fun BookCoverCarousel(
  books: List<CarouselBook>,
  imageLoader: ImageLoader,
  onClick: (CarouselBook) -> Unit,
  modifier: Modifier = Modifier,
  centered: Boolean = false,
  openLabel: String? = null,
  itemTestTag: String? = null,
) {
  val itemWidth = recentBookItemWidth()

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .then(modifier),
    horizontalArrangement =
      when (centered) {
        true -> Arrangement.spacedBy(CAROUSEL_SPACING, Alignment.CenterHorizontally)
        false -> Arrangement.spacedBy(CAROUSEL_SPACING)
      },
  ) {
    books.forEach { book ->
      BookCoverCarouselItem(
        book = book,
        width = itemWidth,
        imageLoader = imageLoader,
        openLabel = openLabel,
        testTag = itemTestTag,
        onClick = { onClick(book) },
      )
    }
  }
}

@Composable
private fun BookCoverCarouselItem(
  book: CarouselBook,
  width: Dp,
  imageLoader: ImageLoader,
  openLabel: String?,
  testTag: String?,
  onClick: () -> Unit,
) {
  val context = LocalContext.current

  val imageRequest =
    remember(book.id) {
      ImageRequest
        .Builder(context)
        .data(book.coverModel)
        .crossfade(300)
        .build()
    }

  Column(
    modifier =
      Modifier
        .width(width)
        .then(testTag?.let { Modifier.testTag(it) } ?: Modifier)
        .semantics(mergeDescendants = true) {}
        .clickable(
          onClickLabel = openLabel,
          role = Role.Button,
        ) { onClick() },
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
    ) {
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .aspectRatio(1f),
      ) {
        AsyncShimmeringImage(
          imageRequest = imageRequest,
          imageLoader = imageLoader,
          contentDescription = null,
          contentScale = ContentScale.FillBounds,
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp)),
          error = painterResource(R.drawable.cover_fallback),
        )
      }

      when (book.progress) {
        null -> {
          Spacer(modifier = Modifier.height(8.dp))
        }

        else -> {
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier =
                Modifier
                  .weight(1f)
                  .height(2.dp),
            ) {
              Box(
                modifier =
                  Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Gray.copy(alpha = 0.4f)),
              )
              Box(
                modifier =
                  Modifier
                    .fillMaxWidth(book.progress)
                    .clip(RoundedCornerShape(8.dp))
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
              )
            }

            Text(
              text = "${(book.progress * 100).toInt()}%",
              fontSize = typography.bodySmall.fontSize,
              fontWeight = FontWeight.Medium,
              modifier = Modifier.padding(start = 12.dp),
            )
          }
        }
      }
    }

    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
      Text(
        text = book.title,
        style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )

      Spacer(modifier = Modifier.height(2.dp))

      book.subtitle?.let {
        Text(
          text = it,
          style =
            typography.bodySmall.copy(
              color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            ),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      book.author?.let {
        Text(
          text = it,
          style =
            typography.bodySmall.copy(
              color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            ),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

private val CAROUSEL_SPACING = 16.dp
