package org.grakovne.lissen.ui.screens.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil.ImageLoader
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.grakovne.lissen.R
import org.grakovne.lissen.common.NetworkQualityService
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.ui.extensions.withMinimumTime
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.screens.common.RequestNotificationPermissions
import org.grakovne.lissen.ui.screens.library.composables.BookComposable
import org.grakovne.lissen.ui.screens.library.composables.MiniPlayerComposable
import org.grakovne.lissen.ui.screens.library.composables.RecentBooksComposable
import org.grakovne.lissen.ui.screens.library.composables.fallback.LibraryFallbackComposable
import org.grakovne.lissen.ui.screens.library.composables.placeholder.LibraryPlaceholderComposable
import org.grakovne.lissen.ui.screens.library.composables.placeholder.RecentBooksPlaceholderComposable
import org.grakovne.lissen.viewmodel.CachingModelView
import org.grakovne.lissen.viewmodel.LibraryViewModel
import org.grakovne.lissen.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun LibraryScreen(
    navController: AppNavigationService,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    cachingModelView: CachingModelView = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    imageLoader: ImageLoader,
    networkQualityService: NetworkQualityService
) {
    val coroutineScope = rememberCoroutineScope()

    val recentBooks: List<RecentBook> by libraryViewModel.recentBooks.observeAsState(emptyList())
    val library: LazyPagingItems<Book> = libraryViewModel.libraryPager.collectAsLazyPagingItems()

    val hiddenBooks by libraryViewModel.hiddenBooks.collectAsState()

    val recentBookRefreshing by libraryViewModel.recentBookUpdating.observeAsState(false)
    var pullRefreshing by remember { mutableStateOf(false) }

    val showingRecentBooks by remember(recentBooks, hiddenBooks) {
        derivedStateOf { filterRecentBooks(recentBooks, libraryViewModel) }
    }

    fun refreshContent(showRefreshing: Boolean) {
        coroutineScope.launch {
            if (showRefreshing) {
                pullRefreshing = true
            }

            withMinimumTime(500) {
                listOf(
                    async { libraryViewModel.dropHiddenBooks() },
                    async { libraryViewModel.refreshLibrary() },
                    async { libraryViewModel.fetchRecentListening() }
                ).awaitAll()
            }

            pullRefreshing = false
        }
    }

    val isContentLoading by remember {
        derivedStateOf {
            pullRefreshing ||
                recentBookRefreshing ||
                library.loadState.refresh is LoadState.Loading
        }
    }

    var navigationItemSelected by remember { mutableStateOf(false) }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = pullRefreshing,
        onRefresh = {
            refreshContent(showRefreshing = true)
        }
    )

    val titleTextStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
    val titleHeightDp = with(LocalDensity.current) { titleTextStyle.lineHeight.toPx().toDp() }

    val libraryListState = rememberLazyListState()

    val playingBook by playerViewModel.book.observeAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        libraryViewModel.refreshRecentListening()
        libraryViewModel.refreshLibrary()
    }

    RequestNotificationPermissions()

    val navBarTitle by remember {
        derivedStateOf {
            val firstVisibleItemIndex = libraryListState.firstVisibleItemIndex
            when {
                firstVisibleItemIndex >= 1 ||
                    filterRecentBooks(
                        recentBooks,
                        libraryViewModel
                    ).isEmpty() -> context.getString(R.string.library_screen_library_title)

                else -> context.getString(R.string.library_screen_continue_listening_title)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                actions = {
                    IconButton(
                        onClick = {},
                        modifier = Modifier.offset(x = 4.dp)
                    ) {
                        Icon(
                            imageVector = Lucide.Search,
                            contentDescription = null
                        )
                    }
                    IconButton(onClick = {
                        navigationItemSelected = true
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "Menu"
                        )
                    }

                    DropdownMenu(
                        expanded = navigationItemSelected,
                        onDismissRequest = { navigationItemSelected = false },
                        modifier = Modifier
                            .background(colorScheme.background)
                            .padding(4.dp)
                    ) {
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = when (cachingModelView.localCacheUsing()) {
                                        true -> Icons.Outlined.Cloud
                                        else -> Icons.Outlined.CloudOff
                                    },
                                    contentDescription = null
                                )
                            },
                            text = {
                                Text(
                                    text = when (cachingModelView.localCacheUsing()) {
                                        true -> stringResource(R.string.disable_offline)
                                        else -> stringResource(R.string.enable_offline)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            },
                            onClick = {
                                navigationItemSelected = false

                                coroutineScope.launch {
                                    withFrameNanos { }

                                    CoroutineScope(Dispatchers.IO).launch {
                                        cachingModelView.toggleCacheForce()
                                        libraryViewModel.dropHiddenBooks()

                                        refreshContent(showRefreshing = false)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )

                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = null
                                )
                            },
                            text = {
                                Text(
                                    stringResource(R.string.library_screen_preferences_menu_item),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            },
                            onClick = {
                                navigationItemSelected = false
                                navController.showSettings()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                },
                title = {
                    Crossfade(targetState = navBarTitle, label = "navbar_title_fade") { title ->
                        Text(
                            text = title,
                            style = titleTextStyle
                        )
                    }
                },
                modifier = Modifier.systemBarsPadding()
            )
        },
        bottomBar = {
            playingBook?.let {
                MiniPlayerComposable(
                    navController = navController,
                    book = it,
                    imageLoader = imageLoader,
                    playerViewModel = playerViewModel
                )
            }
        },
        modifier = Modifier
            .systemBarsPadding()
            .fillMaxSize(),
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .pullRefresh(pullRefreshState)
                    .fillMaxSize()
            ) {
                LazyColumn(
                    state = libraryListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    item(key = "recent_books") {
                        if (isContentLoading) {
                            RecentBooksPlaceholderComposable()

                            Spacer(modifier = Modifier.height(20.dp))
                        } else {
                            if (showingRecentBooks.isEmpty().not()) {
                                RecentBooksComposable(
                                    navController = navController,
                                    recentBooks = showingRecentBooks,
                                    imageLoader = imageLoader
                                )

                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }
                    }

                    item(key = "library_title") {
                        if (filterRecentBooks(recentBooks, libraryViewModel).isNotEmpty()) {
                            AnimatedContent(
                                targetState = navBarTitle,
                                transitionSpec = {
                                    fadeIn(
                                        animationSpec =
                                        tween(300)
                                    ) togetherWith fadeOut(
                                        animationSpec = tween(
                                            300
                                        )
                                    )
                                },
                                label = "library_header_fade"
                            ) {
                                if (it != stringResource(R.string.library_screen_library_title)) {
                                    Text(
                                        style = titleTextStyle,
                                        text = stringResource(R.string.library_screen_library_title),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Spacer(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(titleHeightDp)
                                    )
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    if (isContentLoading) {
                        item {
                            LibraryPlaceholderComposable()
                        }
                    } else {
                        when (library.itemCount == 0) {
                            true -> {
                                item {
                                    LibraryFallbackComposable(
                                        cachingModelView,
                                        networkQualityService
                                    )
                                }
                            }

                            false -> items(count = library.itemCount) {
                                val book = library[it] ?: return@items
                                val isVisible = remember(hiddenBooks, book.id) {
                                    derivedStateOf { libraryViewModel.isVisible(book.id) }
                                }

                                if (isVisible.value) {
                                    BookComposable(
                                        book = book,
                                        imageLoader = imageLoader,
                                        navController = navController,
                                        cachingModelView = cachingModelView,
                                        onRemoveBook = {
                                            if (cachingModelView.localCacheUsing()) {
                                                libraryViewModel.hideBook(book.id)

                                                val showingBooks = (0..<library.itemCount)
                                                    .mapNotNull { index -> library[index] }
                                                    .count { book -> libraryViewModel.isVisible(book.id) }

                                                if (showingBooks == 0) {
                                                    refreshContent(false)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                PullRefreshIndicator(
                    refreshing = pullRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    )
}

private fun filterRecentBooks(
    books: List<RecentBook>,
    libraryViewModel: LibraryViewModel
) = books.filter { libraryViewModel.isVisible(it.id) }
