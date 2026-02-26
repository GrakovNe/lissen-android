package org.grakovne.lissen.ui.screens.library.composables

import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DoNotDisturbOnTotalSilence
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.request.ImageRequest
import org.grakovne.lissen.R
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.lib.domain.Book
import org.grakovne.lissen.ui.components.AsyncShimmeringImage
import org.grakovne.lissen.ui.navigation.AppNavigationService

@Composable
fun BookComposable(
  book: Book,
  imageLoader: ImageLoader,
  navController: AppNavigationService,
) {
  val context = LocalContext.current

  var isPlayingItemOptionsExpanded by remember { mutableStateOf(false) }

  val imageRequest =
    remember(book.id) {
      ImageRequest
        .Builder(context)
        .data(book.id)
        .build()
    }

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .combinedClickable(
          onClick = { navController.showPlayer(book.id, book.title, book.subtitle) },
          hapticFeedbackEnabled = true,
          onLongClick = { isPlayingItemOptionsExpanded = true },
        ).testTag("bookItem_${book.id}")
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
          .aspectRatio(1f)
          .clip(RoundedCornerShape(4.dp)),
      error = painterResource(R.drawable.cover_fallback),
    )

    Spacer(Modifier.width(16.dp))

    Column(
      Modifier
        .weight(1f),
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

      BookMetadataComposable(book)
    }

    Spacer(Modifier.width(16.dp))
  }

  if (isPlayingItemOptionsExpanded) {
    PlayingItemOptionsComposable(
      item = book,
      onDismissRequest = { isPlayingItemOptionsExpanded = false },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayingItemOptionsComposable(
  item: Book,
  onDismissRequest: () -> Unit,
) {
  ModalBottomSheet(
    containerColor = colorScheme.background,
    onDismissRequest = onDismissRequest,
    content = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = item.title,
          style = typography.bodyLarge,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        ListItem(
          modifier =
            Modifier
              .fillMaxWidth()
              .clickable { },
          headlineContent = {
            Text(
              text = "Отметить прослушанной",
              style = typography.bodyMedium,
            )
          },
          trailingContent = {},
          leadingContent = {
//            Icon(
//              imageVector = Icons.Outlined.DoNotDisturbOnTotalSilence,
//              contentDescription = null,
//            )
          },
        )
      }
    },
  )
}
