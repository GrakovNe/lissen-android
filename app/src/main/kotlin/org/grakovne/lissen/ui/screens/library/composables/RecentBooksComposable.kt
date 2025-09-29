package org.grakovne.lissen.ui.screens.library.composables

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.grakovne.lissen.R
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.lib.domain.RecentBook
import org.grakovne.lissen.ui.components.AsyncShimmeringImage
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.viewmodel.LibraryViewModel

@Composable
fun RecentBooksComposable(
  navController: AppNavigationService,
  recentBooks: List<RecentBook>,
  imageLoader: ImageLoader,
  modifier: Modifier = Modifier,
  libraryViewModel: LibraryViewModel,
) {
  val configuration = LocalConfiguration.current
  val screenWidth = remember { configuration.screenWidthDp.dp }

  val itemsVisible = 2.3f
  val spacing = 16.dp
  val totalSpacing = spacing * (itemsVisible + 1)
  val itemWidth = (screenWidth - totalSpacing) / itemsVisible

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    recentBooks
      .forEach { book ->
        RecentBookItemComposable(
          book = book,
          width = itemWidth,
          imageLoader = imageLoader,
          navController = navController,
          libraryViewModel = libraryViewModel,
        )
      }
  }
}

@Composable
fun RecentBookItemComposable(
  navController: AppNavigationService,
  book: RecentBook,
  width: Dp,
  imageLoader: ImageLoader,
  libraryViewModel: LibraryViewModel,
) {
  Column(
    modifier =
      Modifier
        .width(width)
        .clickable { navController.showPlayer(book.id, book.title, book.subtitle) },
  ) {
    val context = LocalContext.current
    var coverLoading by remember { mutableStateOf(true) }

    val imageRequest =
      remember(book.id) {
        ImageRequest
          .Builder(context)
          .data(book.id)
          .crossfade(300)
          .build()
      }

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
          contentDescription = "${book.title} cover",
          contentScale = ContentScale.FillBounds,
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp)),
          error = painterResource(R.drawable.cover_fallback),
          onLoadingStateChanged = { coverLoading = it },
        )
      }

      if (libraryViewModel.fetchPreferredLibraryType() == LibraryType.LIBRARY) {
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
                  .fillMaxWidth(calculateProgress(book))
                  .clip(RoundedCornerShape(8.dp))
                  .fillMaxHeight()
                  .background(MaterialTheme.colorScheme.primary),
            )
          }

          Text(
            text = "${(calculateProgress(book) * 100).toInt()}%",
            fontSize = typography.bodySmall.fontSize,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 12.dp),
          )
        }
      } else {
        Spacer(modifier = Modifier.height(8.dp))
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

private fun calculateProgress(book: RecentBook): Float = book.listenedPercentage?.div(100.0f) ?: 0.0f
