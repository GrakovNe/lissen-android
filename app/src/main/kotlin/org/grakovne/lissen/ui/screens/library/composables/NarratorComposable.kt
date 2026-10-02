package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.ui.navigation.AppNavigationService

// not prefetched like a series or an author: one narrator can hold a large part of the library
@Composable
fun NarratorComposable(
  narrator: LibraryEntry.NarratorEntry,
  expanded: Boolean,
  loading: Boolean,
  books: List<Book>,
  imageLoader: ImageLoader,
  navController: AppNavigationService,
  onToggle: () -> Unit,
) {
  LibraryGroupComposable(
    title = narrator.name,
    bookCount = narrator.bookCount,
    testTag = "narratorItem_${narrator.id}",
    expanded = expanded,
    loading = loading,
    books = books,
    onToggle = onToggle,
    cover = { GroupDrawableCover(R.drawable.narrator_fallback) },
    bookRows = { GroupBooks(it, imageLoader, navController) },
  )
}
