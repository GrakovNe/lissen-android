package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DoNotDisturbAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.request.ImageRequest
import org.grakovne.lissen.R
import org.grakovne.lissen.channel.common.LibraryType
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.ui.components.AsyncShimmeringImage
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.viewmodel.LibraryViewModel

@Composable
fun BookComposable(
  book: Book,
  imageLoader: ImageLoader,
  navController: AppNavigationService,
  viewModel: LibraryViewModel = viewModel(),
) {
  val context = LocalContext.current
  val isDisabled = book.hasContent.not()
  val disabledAlpha = 0.38f

  val imageRequest =
    remember(book.id) {
      ImageRequest
        .Builder(context)
        .data(book.id)
        .size(coil.size.Size.ORIGINAL)
        .build()
    }

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .semantics { if (isDisabled) disabled() }
        .clickable(enabled = isDisabled.not()) { navController.showPlayer(book.id, book.title, book.subtitle) }
        .testTag("bookItem_${book.id}")
        .padding(horizontal = 4.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AsyncShimmeringImage(
      imageRequest = imageRequest,
      imageLoader = imageLoader,
      contentDescription = "${book.title} cover",
      contentScale = ContentScale.FillBounds,
      modifier =
        Modifier
          .size(64.dp)
          .alpha(if (isDisabled) disabledAlpha else 1f)
          .aspectRatio(1f)
          .clip(RoundedCornerShape(4.dp)),
      error = painterResource(R.drawable.cover_fallback),
    )

    Spacer(Modifier.width(16.dp))

    Column(
      Modifier
        .weight(1f)
        .alpha(if (isDisabled) disabledAlpha else 1f),
    ) {
      Column {
        Text(
          text = book.title,
          style =
            MaterialTheme.typography.bodyMedium.copy(
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onBackground,
            ),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      when (isDisabled) {
        true -> BookUnavailableComposable(viewModel)
        false -> BookMetadataComposable(book)
      }
    }

    Spacer(Modifier.width(16.dp))
  }
}

@Composable
fun BookUnavailableComposable(viewModel: LibraryViewModel) {
  Row(
    modifier = Modifier.padding(top = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = Icons.Outlined.DoNotDisturbAlt,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.error,
      modifier = Modifier.size(16.dp),
    )

    Text(
      text =
        when (viewModel.fetchPreferredLibraryType()) {
          LibraryType.LIBRARY -> stringResource(R.string.chapters_list_empty)
          LibraryType.PODCAST -> stringResource(R.string.episodes_list_empty)
          LibraryType.UNKNOWN -> stringResource(R.string.items_list_empty)
        },
      modifier = Modifier.padding(start = 4.dp),
      style =
        MaterialTheme.typography.bodyMedium.copy(
          color = MaterialTheme.colorScheme.error,
        ),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
fun BookMetadataComposable(book: Book) {
  if ((book.series?.isNotBlank() == true) || (book.author != null)) {
    Spacer(modifier = Modifier.height(2.dp))
  }

  book.author?.takeIf { it.isNotBlank() }?.let {
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

  book.series?.takeIf { it.isNotBlank() }?.let {
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
}
