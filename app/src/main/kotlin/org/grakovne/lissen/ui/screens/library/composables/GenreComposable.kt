package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.ui.navigation.AppNavigationService

@Composable
fun GenreComposable(
  genre: LibraryEntry.GenreEntry,
  expanded: Boolean,
  loading: Boolean,
  books: List<Book>,
  imageLoader: ImageLoader,
  navController: AppNavigationService,
  onToggle: () -> Unit,
) {
  LibraryGroupComposable(
    groupId = genre.id,
    title = genre.name,
    itemCount = genre.bookCount,
    testTag = "genreItem_${genre.id}",
    expanded = expanded,
    loading = loading,
    items = books,
    onToggle = onToggle,
    cover = { GroupDrawableCover(R.drawable.genre_fallback) },
    itemRows = { GroupBooks(it, imageLoader, navController) },
  )
}
