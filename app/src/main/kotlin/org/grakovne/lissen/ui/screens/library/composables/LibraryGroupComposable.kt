package org.grakovne.lissen.ui.screens.library.composables

import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import org.grakovne.lissen.R
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.ui.components.AsyncShimmeringImage
import org.grakovne.lissen.ui.components.GroupCoverKey
import org.grakovne.lissen.ui.navigation.AppNavigationService

private const val MAX_GROUP_COVERS = 3

private const val PREFETCH_DWELL_MS = 200L

@Composable
fun <T> LibraryGroupComposable(
  groupId: String,
  title: String,
  itemCount: Int,
  testTag: String,
  expanded: Boolean,
  loading: Boolean,
  items: List<T>,
  onToggle: () -> Unit,
  subtitle: String? = null,
  @PluralsRes countPlurals: Int = R.plurals.series_books_count,
  onPrefetch: (() -> Unit)? = null,
  cover: @Composable () -> Unit,
  itemRows: @Composable (List<T>) -> Unit,
) {
  val context = LocalContext.current

  // prefetch after a short dwell
  LaunchedEffect(groupId) {
    onPrefetch?.let {
      delay(PREFETCH_DWELL_MS)
      it()
    }
  }

  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .semantics(mergeDescendants = true) {}
          .clickable(role = Role.Button) { onToggle() }
          .testTag(testTag)
          .padding(horizontal = 4.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      cover()

      Spacer(Modifier.width(16.dp))

      Column(Modifier.weight(1f)) {
        Text(
          text = title,
          style =
            MaterialTheme.typography.bodyMedium.copy(
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onBackground,
            ),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(2.dp))

        subtitle?.let {
          Text(
            text = it,
            style =
              MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
              ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Text(
          text = context.resources.getQuantityString(countPlurals, itemCount, itemCount),
          style =
            MaterialTheme.typography.bodyMedium.copy(
              color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            ),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Spacer(Modifier.width(16.dp))

      Icon(
        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
      )
    }

    AnimatedVisibility(visible = expanded) {
      Column(modifier = Modifier.fillMaxWidth()) {
        when {
          loading && items.isEmpty() -> {
            Box(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .padding(vertical = 16.dp),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }

          else -> {
            itemRows(items)
          }
        }
      }
    }
  }
}

@Composable
fun GroupBooks(
  books: List<Book>,
  imageLoader: ImageLoader,
  navController: AppNavigationService,
  grouping: LibraryGrouping = LibraryGrouping.NONE,
) {
  books.forEach { book ->
    BookComposable(
      book = book,
      imageLoader = imageLoader,
      navController = navController,
      grouping = grouping,
      leading = { GroupBookLeading() },
    )
  }
}

// an invisible digit indents the book like a numbered book of a series
@Composable
fun GroupBookLeading() {
  Box(modifier = Modifier.padding(end = 10.dp)) {
    Text(
      text = "0",
      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
      maxLines = 1,
      modifier = Modifier.alpha(0f),
    )
  }
}

@Composable
fun GroupDrawableCover(
  @DrawableRes drawable: Int,
) {
  Image(
    painter = painterResource(drawable),
    contentDescription = null,
    contentScale = ContentScale.FillBounds,
    modifier =
      Modifier
        .size(LibraryItemCoverSize)
        .clip(RoundedCornerShape(4.dp)),
  )
}

@Composable
fun GroupCoverStack(
  groupId: String,
  coverItemIds: List<String>,
  imageLoader: ImageLoader,
) {
  val context = LocalContext.current

  val imageRequest =
    remember(groupId, coverItemIds) {
      ImageRequest
        .Builder(context)
        .data(GroupCoverKey(groupId, coverItemIds.take(MAX_GROUP_COVERS)))
        .build()
    }

  AsyncShimmeringImage(
    imageRequest = imageRequest,
    imageLoader = imageLoader,
    contentDescription = null,
    contentScale = ContentScale.Fit,
    modifier =
      Modifier
        .size(LibraryItemCoverSize)
        .clip(RoundedCornerShape(4.dp)),
    error = painterResource(R.drawable.cover_fallback),
  )
}
