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
    groupId = narrator.id,
    title = narrator.name,
    itemCount = narrator.bookCount,
    testTag = "narratorItem_${narrator.id}",
    expanded = expanded,
    loading = loading,
    items = books,
    onToggle = onToggle,
    cover = { GroupDrawableCover(R.drawable.narrator_fallback) },
    itemRows = { GroupBooks(it, imageLoader, navController) },
  )
}
