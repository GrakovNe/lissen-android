package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.ui.components.BookCoverCarousel
import org.grakovne.lissen.ui.components.BookCoverKey
import org.grakovne.lissen.ui.components.CarouselBook
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
  val showProgress = libraryViewModel.fetchPreferredLibraryType() == LibraryType.LIBRARY
  val openLabel = stringResource(R.string.a11y_open)

  BookCoverCarousel(
    books =
      recentBooks.map { book ->
        CarouselBook(
          id = book.id,
          coverModel = BookCoverKey(book.id),
          title = book.title,
          subtitle = book.subtitle,
          author = book.author,
          progress = calculateProgress(book).takeIf { showProgress },
        )
      },
    imageLoader = imageLoader,
    onClick = { book -> navController.showPlayer(book.id, book.title, book.subtitle) },
    openLabel = openLabel,
    modifier = modifier.padding(horizontal = 4.dp),
  )
}

private fun calculateProgress(book: RecentBook) = (book.listenedPercentage?.div(100.0f) ?: 0.0f).coerceIn(0.0f, 1.0f)
