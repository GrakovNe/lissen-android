package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.request.ImageRequest
import org.grakovne.lissen.R
import org.grakovne.lissen.content.cache.CacheProgress
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.BookCachedState
import org.grakovne.lissen.ui.components.AsyncShimmeringImage
import org.grakovne.lissen.ui.extensions.formatShortly
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.viewmodel.BookCacheAction
import org.grakovne.lissen.viewmodel.CachingModelView

@Composable
fun BookComposable(
    book: Book,
    imageLoader: ImageLoader,
    navController: AppNavigationService,
    cachingModelView: CachingModelView,
    onRemoveBook: () -> Unit,
) {
    val cacheProgress by cachingModelView.getCacheProgress(book.id).collectAsState()
    val context = LocalContext.current
    var showDeleteFromCacheDialog by remember { mutableStateOf(false) }

    val imageRequest = remember(book.id) {
        ImageRequest.Builder(context)
            .data(book.id)
            .size(coil.size.Size.ORIGINAL)
            .build()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { navController.showPlayer(book.id, book.title) }
            .testTag("bookItem_${book.id}")
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncShimmeringImage(
            imageRequest = imageRequest,
            imageLoader = imageLoader,
            contentDescription = "${book.title} cover",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .size(64.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(4.dp)),
            error = painterResource(R.drawable.cover_fallback),
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            book
                .author
                ?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            provideCachingStateIcon(book, cacheProgress)
                ?.let { icon ->
                    IconButton(
                        onClick = {
                            cachingModelView
                                .provideCacheAction(book)
                                ?.let {
                                    when (it) {
                                        BookCacheAction.CACHE -> cachingModelView.cacheBook(book)
                                        BookCacheAction.DROP -> {
                                            showDeleteFromCacheDialog = true
                                        }
                                    }
                                }
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = "Caching Book State",
                        )
                    }
                }

            if (book.duration > 0) {
                Text(
                    text = book.duration.formatShortly(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(6.dp))
        }
    }

    if (showDeleteFromCacheDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteFromCacheDialog = false },
            title = { Text(text = stringResource(R.string.dialog_remove_from_cache_title)) },
            text = { Text(stringResource(R.string.dialog_remove_from_cache_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        cachingModelView.dropCache(book)
                        onRemoveBook.invoke()
                        showDeleteFromCacheDialog = false
                    },
                ) {
                    Text(
                        text = stringResource(R.string.dialog_confirm_remove),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteFromCacheDialog = false }) {
                    Text(
                        text = stringResource(R.string.dialog_dismiss_cancel),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
        )
    }
}

private fun provideCachingStateIcon(
    book: Book,
    cacheProgress: CacheProgress,
): ImageVector? = when (cacheProgress) {
    CacheProgress.Completed -> cachedIcon
    CacheProgress.Removed -> ableToCacheIcon
    CacheProgress.Error -> ableToCacheIcon
    CacheProgress.Idle -> provideIdleStateIcon(book)
    is CacheProgress.Caching -> cachingIcon
}

private fun provideIdleStateIcon(book: Book): ImageVector? = when (book.cachedState) {
    BookCachedState.ABLE_TO_CACHE -> ableToCacheIcon
    BookCachedState.CACHED -> cachedIcon
    BookCachedState.UNABLE_TO_CACHE -> null
}

private val ableToCacheIcon = Icons.Outlined.Cloud
private val cachedIcon = Icons.Outlined.CloudDownload
private val cachingIcon = Icons.Outlined.Downloading
