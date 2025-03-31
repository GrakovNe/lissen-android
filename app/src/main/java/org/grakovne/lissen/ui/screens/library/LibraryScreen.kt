package org.grakovne.lissen.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil.ImageLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.grakovne.lissen.R
import org.grakovne.lissen.channel.common.LibraryType
import org.grakovne.lissen.common.NetworkQualityService
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.ui.extensions.withMinimumTime
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.screens.common.RequestNotificationPermissions
import org.grakovne.lissen.ui.screens.library.composables.BookComposable
import org.grakovne.lissen.ui.screens.library.composables.DefaultActionComposable
import org.grakovne.lissen.ui.screens.library.composables.LibrarySearchActionComposable
import org.grakovne.lissen.ui.screens.library.composables.MiniPlayerComposable
import org.grakovne.lissen.ui.screens.library.composables.RecentBooksComposable
import org.grakovne.lissen.ui.screens.library.composables.fallback.LibraryFallbackComposable
import org.grakovne.lissen.ui.screens.library.composables.placeholder.LibraryPlaceholderComposable
import org.grakovne.lissen.ui.screens.library.composables.placeholder.RecentBooksPlaceholderComposable
import org.grakovne.lissen.viewmodel.ContentCachingModelView
import org.grakovne.lissen.viewmodel.LibraryViewModel
import org.grakovne.lissen.viewmodel.PlayerViewModel
import org.grakovne.lissen.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun LibraryScreen(
    navController: AppNavigationService,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    contentCachingModelView: ContentCachingModelView = hiltViewModel(),
    imageLoader: ImageLoader,
    networkQualityService: NetworkQualityService,
) {
    RequestNotificationPermissions()

    val coroutineScope = rememberCoroutineScope()

    val recentBooks: List<RecentBook> by libraryViewModel.recentBooks.observeAsState(emptyList())

    var currentLibraryId by rememberSaveable { mutableStateOf("") }
    var pullRefreshing by remember { mutableStateOf(false) }
    val recentBookRefreshing by libraryViewModel.recentBookUpdating.observeAsState(false)
    val searchRequested by libraryViewModel.searchRequested.observeAsState(false)
    val preparingError by playerViewModel.preparingError.observeAsState(false)

    val preferredLibrary by settingsViewModel.preferredLibrary.observeAsState()
    val libraries by settingsViewModel.libraries.observeAsState(emptyList())
    var preferredLibraryExpanded by remember { mutableStateOf(false) }

    val library = when (searchRequested) {
        true -> libraryViewModel.searchPager.collectAsLazyPagingItems()
        false -> libraryViewModel.libraryPager.collectAsLazyPagingItems()
    }

    BackHandler(enabled = searchRequested) {
        libraryViewModel.dismissSearch()
    }

    fun refreshContent(showPullRefreshing: Boolean) {
        coroutineScope.launch {
            if (showPullRefreshing) {
                pullRefreshing = true
            }

            val minimumTime = when (showPullRefreshing) {
                true -> 500L
                false -> 0L
            }

            withMinimumTime(minimumTime) {
                listOf(
                    async { settingsViewModel.fetchLibraries() },
                    async { libraryViewModel.refreshLibrary() },
                    async { libraryViewModel.fetchRecentListening() },
                ).awaitAll()
            }

            pullRefreshing = false
        }
    }

    val isPlaceholderRequired by remember {
        derivedStateOf {
            if (searchRequested) {
                return@derivedStateOf false
            }

            pullRefreshing || recentBookRefreshing || library.loadState.refresh is LoadState.Loading
        }
    }

    LaunchedEffect(preparingError) {
        if (preparingError) {
            playerViewModel.clearPlayingBook()
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = pullRefreshing,
        onRefresh = {
            refreshContent(showPullRefreshing = true)
        },
    )

    val titleTextStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
    val titleHeightDp = with(LocalDensity.current) { titleTextStyle.lineHeight.toPx().toDp() }

    val libraryListState = rememberLazyListState()

    val playingBook by playerViewModel.book.observeAsState()
    val context = LocalContext.current

    fun isRecentVisible(): Boolean {
        val fetchAvailable = networkQualityService.isNetworkAvailable() || contentCachingModelView.localCacheUsing()
        val hasContent = recentBooks.isEmpty().not()
        return !searchRequested && hasContent && fetchAvailable
    }

    LaunchedEffect(Unit) {
        if (library.itemCount == 0 || currentLibraryId != settingsViewModel.fetchPreferredLibraryId()) {
            libraryViewModel.refreshRecentListening()
            libraryViewModel.refreshLibrary()
            currentLibraryId = settingsViewModel.fetchPreferredLibraryId()
            settingsViewModel.fetchLibraries()
        }
    }

    LaunchedEffect(searchRequested) {
        if (!searchRequested) {
            libraryListState.scrollToItem(0)
        }
    }

    fun provideLibraryTitle(): String {
        val type = libraryViewModel.fetchPreferredLibraryType()

        return when (type) {
            LibraryType.LIBRARY ->
                libraryViewModel
                    .fetchPreferredLibraryTitle()
                    ?: context.getString(R.string.library_screen_library_title)

            LibraryType.PODCAST ->
                libraryViewModel
                    .fetchPreferredLibraryTitle()
                    ?: context.getString(R.string.library_screen_podcast_title)

            LibraryType.UNKNOWN -> ""
        }
    }

    val navBarTitle by remember {
        derivedStateOf {
            val showRecent = isRecentVisible()
            val recentBlockVisible = libraryListState.layoutInfo.visibleItemsInfo.firstOrNull()?.key == "recent_books"

            when {
                isPlaceholderRequired -> context.getString(R.string.library_screen_continue_listening_title)
                showRecent && recentBlockVisible -> context.getString(R.string.library_screen_continue_listening_title)
                else -> provideLibraryTitle()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                actions = {
                    AnimatedContent(
                        targetState = searchRequested,
                        label = "library_action_animation",
                        transitionSpec = {
                            fadeIn(animationSpec = keyframes { durationMillis = 150 }) togetherWith
                                fadeOut(animationSpec = keyframes { durationMillis = 150 })
                        },
                    ) { isSearchRequested ->
                        when (isSearchRequested) {
                            true -> LibrarySearchActionComposable(
                                onSearchDismissed = { libraryViewModel.dismissSearch() },
                                onSearchRequested = { libraryViewModel.updateSearch(it) },
                            )

                            false -> DefaultActionComposable(
                                navController = navController,
                                contentCachingModelView = contentCachingModelView,
                                playerViewModel = playerViewModel,
                                onContentRefreshing = { refreshContent(showPullRefreshing = false) },
                                onSearchRequested = { libraryViewModel.requestSearch() },
                            )
                        }
                    }
                },
                title = {
                    if (!searchRequested) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = navBarTitle,
                                style = titleTextStyle,
                                maxLines = 1,
                            )

                            if (navBarTitle == provideLibraryTitle()) {
                                Spacer(modifier = Modifier.width(4.dp))

                                Icon(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { preferredLibraryExpanded = true },
                                    imageVector = Icons.Outlined.ArrowDropDown,
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.systemBarsPadding(),
            )
        },
        bottomBar = {
            playingBook?.let {
                Surface(shadowElevation = 4.dp) {
                    MiniPlayerComposable(
                        navController = navController,
                        book = it,
                        imageLoader = imageLoader,
                        playerViewModel = playerViewModel,
                    )
                }
            }
        },
        modifier = Modifier
            .systemBarsPadding()
            .fillMaxSize(),
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .testTag("libraryScreen")
                    .pullRefresh(pullRefreshState)
                    .fillMaxSize(),
            ) {
                LazyColumn(
                    state = libraryListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    item(key = "recent_books") {
                        val showRecent = isRecentVisible()

                        when {
                            isPlaceholderRequired -> {
                                RecentBooksPlaceholderComposable(
                                    libraryViewModel = libraryViewModel,
                                )
                            }

                            showRecent -> {
                                RecentBooksComposable(
                                    navController = navController,
                                    recentBooks = recentBooks,
                                    imageLoader = imageLoader,
                                    libraryViewModel = libraryViewModel,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    item(key = "library_title") {
                        if (!searchRequested && isRecentVisible()) {
                            AnimatedContent(
                                targetState = navBarTitle,
                                transitionSpec = {
                                    fadeIn(
                                        animationSpec =
                                        tween(300),
                                    ) togetherWith fadeOut(
                                        animationSpec = tween(
                                            300,
                                        ),
                                    )
                                },
                                label = "library_header_fade",
                            ) {
                                when {
                                    it == provideLibraryTitle() ->
                                        Spacer(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(titleHeightDp),
                                        )

                                    else -> {
                                        if (isPlaceholderRequired.not()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    style = titleTextStyle,
                                                    text = provideLibraryTitle(),
                                                    modifier = Modifier,
                                                )

                                                Spacer(modifier = Modifier.width(4.dp))

                                                Icon(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .clickable { preferredLibraryExpanded = true },
                                                    imageVector = Icons.Outlined.ArrowDropDown,
                                                    contentDescription = null,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "library_spacer") { Spacer(modifier = Modifier.height(8.dp)) }

                    when {
                        isPlaceholderRequired -> item { LibraryPlaceholderComposable() }
                        library.itemCount == 0 -> {
                            item {
                                LibraryFallbackComposable(
                                    searchRequested = searchRequested,
                                    contentCachingModelView = contentCachingModelView,
                                    networkQualityService = networkQualityService,
                                    libraryViewModel = libraryViewModel,
                                )
                            }
                        }

                        else -> items(count = library.itemCount, key = { "library_item_$it" }) {
                            val book = library[it] ?: return@items

                            BookComposable(
                                book = book,
                                imageLoader = imageLoader,
                                navController = navController,
                            )
                        }
                    }
                }

                if (!searchRequested) {
                    PullRefreshIndicator(
                        refreshing = pullRefreshing,
                        state = pullRefreshState,
                        contentColor = colorScheme.primary,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        },
    )

    if (preferredLibraryExpanded) {
        PreferredLibrarySettingComposable(
            libraries = libraries,
            preferredLibrary = preferredLibrary,
            onDismissRequest = { preferredLibraryExpanded = false },
            onItemSelected = {
                settingsViewModel.preferLibrary(it)
                currentLibraryId = settingsViewModel.fetchPreferredLibraryId()
                refreshContent(false)
                playerViewModel.clearPlayingBook()
            },
        )
    }
}
