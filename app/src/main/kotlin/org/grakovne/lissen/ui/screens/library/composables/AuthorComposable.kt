package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import org.grakovne.lissen.R
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.ui.components.AsyncShimmeringImage
import org.grakovne.lissen.ui.components.AuthorCoverKey
import org.grakovne.lissen.ui.navigation.AppNavigationService

private const val AUTHOR_PREFETCH_DWELL_MS = 200L

@Composable
fun AuthorComposable(
  author: LibraryEntry.AuthorEntry,
  expanded: Boolean,
  loading: Boolean,
  books: List<Book>,
  imageLoader: ImageLoader,
  navController: AppNavigationService,
  onToggle: () -> Unit,
  onPrefetch: () -> Unit,
) {
  val context = LocalContext.current

  LaunchedEffect(author.id) {
    delay(AUTHOR_PREFETCH_DWELL_MS)
    onPrefetch()
  }

  val imageRequest =
    remember(author.id) {
      ImageRequest
        .Builder(context)
        .data(AuthorCoverKey(author.id))
        .build()
    }

  LibraryGroupComposable(
    title = author.name,
    bookCount = author.bookCount,
    testTag = "authorItem_${author.id}",
    expanded = expanded,
    loading = loading,
    books = books,
    onToggle = onToggle,
    cover = {
      AsyncShimmeringImage(
        imageRequest = imageRequest,
        imageLoader = imageLoader,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier =
          Modifier
            .size(LibraryItemCoverSize)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp)),
        error = painterResource(R.drawable.author_fallback),
      )
    },
    bookRows = { GroupBooks(it, imageLoader, navController, LibraryGrouping.AUTHOR) },
  )
}
