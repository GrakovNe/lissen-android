package org.grakovne.lissen.ui.screens.settings.advanced.cache

import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.grakovne.lissen.R
import org.grakovne.lissen.common.hapticAction
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.ui.components.AsyncShimmeringImage
import org.grakovne.lissen.ui.extensions.formatLeadingMinutes
import org.grakovne.lissen.ui.extensions.withMinimumTime
import org.grakovne.lissen.viewmodel.CachingModelView
import org.grakovne.lissen.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun CachedItemsSettingsScreen(
    imageLoader: ImageLoader,
    viewModel: CachingModelView = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val view: View = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    var pullRefreshing by remember { mutableStateOf(false) }
    val cachedItems = viewModel.libraryPager.collectAsLazyPagingItems()

    fun refreshContent(showPullRefreshing: Boolean) {
        coroutineScope.launch {
            if (showPullRefreshing) {
                pullRefreshing = true
            }

            val minimumTime =
                when (showPullRefreshing) {
                    true -> 500L
                    false -> 0L
                }

            withMinimumTime(minimumTime) {
                listOf(
                    async { viewModel.fetchCachedItems() },
                ).awaitAll()
            }

            pullRefreshing = false
        }
    }

    val pullRefreshState =
        rememberPullRefreshState(
            refreshing = pullRefreshing,
            onRefresh = {
                hapticAction(view) { refreshContent(showPullRefreshing = true) }
            },
        )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.content_downloads_list_settings_screen_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { /* back */ }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .testTag("libraryScreen")
                    .pullRefresh(pullRefreshState)
                    .fillMaxSize(),
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize(),
            ) {
                items(count = cachedItems.itemCount, key = { "cached_library_item_$it" }) {
                    val item = cachedItems[it] ?: return@items
                    CachedItemComposable(
                        book = item,
                        imageLoader = imageLoader,
                        viewModel = viewModel,
                        playerViewModel = playerViewModel,
                        onItemRemoved = {
                            refreshContent(showPullRefreshing = false)
                        },
                    )
                }
            }

            PullRefreshIndicator(
                refreshing = pullRefreshing,
                state = pullRefreshState,
                contentColor = colorScheme.primary,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun CachedItemComposable(
    book: DetailedItem,
    imageLoader: ImageLoader,
    viewModel: CachingModelView,
    playerViewModel: PlayerViewModel,
    onItemRemoved: () -> Unit,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val imageRequest =
        remember(book.id) {
            ImageRequest
                .Builder(context)
                .data(book.id)
                .size(coil.size.Size.ORIGINAL)
                .build()
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { expanded = expanded.not() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
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

                Spacer(Modifier.width(spacing))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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

                        Spacer(Modifier.width(4.dp))

                        Icon(
                            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    book.author?.takeIf { it.isNotBlank() }?.let {
                        Spacer(modifier = Modifier.height(2.dp))
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

                Spacer(Modifier.width(spacing))

                IconButton(onClick = {
                    dropCache(
                        item = book,
                        cachingModelView = viewModel,
                        playerViewModel = playerViewModel,
                    )
                    onItemRemoved()
                }) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (expanded) {
                CachedItemChapterComposable(book, onItemRemoved, viewModel, playerViewModel)
            }
        }
    }
}

@Composable
private fun CachedItemChapterComposable(
    item: DetailedItem,
    onItemRemoved: () -> Unit,
    viewModel: CachingModelView,
    playerViewModel: PlayerViewModel,
) {
    Spacer(modifier = Modifier.height(spacing))
    item
        .chapters
        .filter { it.available }
        .map { chapter ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = chapterIndent, end = spacing, top = spacing, bottom = spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = chapter.title, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = chapter.duration.toInt().formatLeadingMinutes(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                IconButton(onClick = {
                    dropCache(
                        item = item,
                        chapter = chapter,
                        cachingModelView = viewModel,
                        playerViewModel = playerViewModel,
                    )
                    onItemRemoved()
                }) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
}

private fun dropCache(
    item: DetailedItem,
    chapter: PlayingChapter,
    cachingModelView: CachingModelView,
    playerViewModel: PlayerViewModel,
) {
    playerViewModel.book.value?.let { playingBook ->
        if (playingBook.id == item.id) {
            playerViewModel.clearPlayingBook()
        }
    }

    when (item.chapters.all { it.id == chapter.id }) {
        true -> dropCache(item, cachingModelView, playerViewModel)
        false -> cachingModelView.dropCache(item, chapter)
    }
}

private fun dropCache(
    item: DetailedItem,
    cachingModelView: CachingModelView,
    playerViewModel: PlayerViewModel,
) {
    playerViewModel.book.value?.let { playingBook ->
        if (playingBook.id == item.id) {
            playerViewModel.clearPlayingBook()
        }
    }

    cachingModelView.dropCache(item.id)
}

private val thumbnailSize = 64.dp
private val spacing = 16.dp
private val chapterIndent = thumbnailSize + spacing
