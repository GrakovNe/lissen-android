package org.grakovne.lissen.ui.screens.settings.advanced.cache

import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.ImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.grakovne.lissen.R
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.PlayingChapter
import org.grakovne.lissen.ui.components.AsyncShimmeringImage
import org.grakovne.lissen.ui.components.BookCoverFetcher.Companion.LocalOnlyKey
import org.grakovne.lissen.ui.extensions.withMinimumTime
import org.grakovne.lissen.viewmodel.CachingModelView
import org.grakovne.lissen.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun CachedItemsSettingsScreen(
  onBack: () -> Unit,
  imageLoader: ImageLoader,
  viewModel: CachingModelView = hiltViewModel(),
  playerViewModel: PlayerViewModel = hiltViewModel(),
) {
  val view: View = LocalView.current
  val scope = rememberCoroutineScope()

  var pullRefreshing by remember { mutableStateOf(false) }
  val cachedItems = viewModel.libraryPager.collectAsLazyPagingItems()

  fun refreshContent(showPullRefreshing: Boolean) {
    scope.launch {
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
        withHaptic(view) { refreshContent(showPullRefreshing = true) }
      },
    )

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.settings_screen_cached_items_title),
            style = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colorScheme.onSurface,
          )
        },
        navigationIcon = {
          IconButton(onClick = { onBack() }) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = "Back",
              tint = colorScheme.onSurface,
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
      when (cachedItems.itemCount == 0) {
        true -> CachedItemsFallbackComposable()
        false ->
          CachedItemsComposable(
            cachedItems = cachedItems,
            imageLoader = imageLoader,
            viewModel = viewModel,
            playerViewModel = playerViewModel,
            onItemRemoved = { refreshContent(showPullRefreshing = false) },
          )
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
private fun CachedItemsComposable(
  cachedItems: LazyPagingItems<DetailedItem>,
  imageLoader: ImageLoader,
  viewModel: CachingModelView,
  playerViewModel: PlayerViewModel,
  onItemRemoved: () -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
  ) {
    items(count = cachedItems.itemCount, key = { index -> cachedItems[index]?.id ?: "cached_library_item_$index" }) {
      val item = cachedItems[it] ?: return@items
      CachedItemComposable(
        book = item,
        imageLoader = imageLoader,
        viewModel = viewModel,
        playerViewModel = playerViewModel,
        onItemRemoved = onItemRemoved,
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
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var expanded by remember { mutableStateOf(false) }

  val imageRequest =
    remember(book.id) {
      ImageRequest
        .Builder(context)
        .data(book.id)
        .apply { extras[LocalOnlyKey] = true }
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
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = book.title,
              style =
                typography.bodyMedium.copy(
                  fontWeight = FontWeight.SemiBold,
                  color = colorScheme.onBackground,
                ),
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false),
            )

            Spacer(Modifier.width(4.dp))

            Icon(
              imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
              tint = colorScheme.onBackground,
            )
          }

          book
            .author
            ?.takeIf { it.isNotBlank() }
            ?.let {
              Text(
                modifier = Modifier.padding(vertical = 2.dp),
                text = it,
                style =
                  typography.bodyMedium.copy(
                    color = colorScheme.onBackground.copy(alpha = 0.6f),
                  ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
        }

        Spacer(Modifier.width(spacing))

        IconButton(onClick = {
          scope
            .launch {
              dropCache(
                item = book,
                cachingModelView = viewModel,
                playerViewModel = playerViewModel,
              )

              onItemRemoved()
            }
        }) {
          Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = null,
            tint = colorScheme.onSurface,
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
  val scope = rememberCoroutineScope()

  Spacer(modifier = Modifier.height(spacing))

  val availableChapters =
    item
      .chapters
      .filter { it.available }

  availableChapters.forEachIndexed { index, chapter ->
    key(chapter.id) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(vertical = spacing / 2)
            .padding(start = chapterIndent),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(text = chapter.title, style = typography.bodyMedium)
        }

        Box(
          modifier =
            Modifier
              .size(48.dp)
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                  scope.launch {
                    dropCache(
                      item = item,
                      chapter = chapter,
                      cachingModelView = viewModel,
                      playerViewModel = playerViewModel,
                    )
                    onItemRemoved()
                  }
                },
              ),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = null,
            tint = colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
          )
        }
      }

      if (index < availableChapters.lastIndex) {
        HorizontalDivider(
          thickness = 1.dp,
          modifier =
            Modifier.padding(
              start = chapterIndent,
              end = spacing,
            ),
        )
      }
    }
  }
}

private suspend fun dropCache(
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

  val isLatestChapter =
    item
      .chapters
      .filter { it.available }
      .let { it - chapter }
      .isEmpty()

  when (isLatestChapter) {
    true -> dropCache(item, cachingModelView, playerViewModel)
    false -> cachingModelView.dropCache(item, chapter)
  }
}

private suspend fun dropCache(
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
