package org.grakovne.lissen.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.ImageLoader
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.ui.icons.Search
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.screens.player.composable.NavigationBarComposable
import org.grakovne.lissen.ui.screens.player.composable.PlayingQueueComposable
import org.grakovne.lissen.ui.screens.player.composable.TrackControlComposable
import org.grakovne.lissen.ui.screens.player.composable.TrackDetailsComposable
import org.grakovne.lissen.ui.screens.player.composable.fallback.PlayingQueueFallbackComposable
import org.grakovne.lissen.ui.screens.player.composable.placeholder.PlayingQueuePlaceholderComposable
import org.grakovne.lissen.ui.screens.player.composable.placeholder.TrackControlPlaceholderComposable
import org.grakovne.lissen.ui.screens.player.composable.placeholder.TrackDetailsPlaceholderComposable
import org.grakovne.lissen.ui.screens.player.composable.provideNowPlayingTitle
import org.grakovne.lissen.viewmodel.CachingModelView
import org.grakovne.lissen.viewmodel.LibraryViewModel
import org.grakovne.lissen.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    navController: AppNavigationService,
    imageLoader: ImageLoader,
    bookId: String,
    bookTitle: String,
    bookSubtitle: String?,
) {
    val context = LocalContext.current

    val cachingModelView: CachingModelView = hiltViewModel()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val libraryViewModel: LibraryViewModel = hiltViewModel()

    val titleTextStyle = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)

    val playingBook by playerViewModel.book.observeAsState()
    val isPlaybackReady by playerViewModel.isPlaybackReady.observeAsState(false)
    val playingQueueExpanded by playerViewModel.playingQueueExpanded.observeAsState(false)
    val searchRequested by playerViewModel.searchRequested.observeAsState(false)

    var itemDetailsSelected by remember { mutableStateOf(false) }

    val screenTitle = when (playingQueueExpanded) {
        true -> provideNowPlayingTitle(libraryViewModel.fetchPreferredLibraryType(), context)
        false -> stringResource(R.string.player_screen_title)
    }

    fun stepBack() {
        when {
            searchRequested -> playerViewModel.dismissSearch()
            playingQueueExpanded -> playerViewModel.collapsePlayingQueue()
            else -> navController.showLibrary()
        }
    }

    BackHandler(enabled = searchRequested || playingQueueExpanded) {
        stepBack()
    }

    LaunchedEffect(Unit) {
        bookId
            .takeIf { playingItemChanged(it, playingBook) || cachePolicyChanged(cachingModelView, playingBook) }
            ?.let { playerViewModel.preparePlayback(it) }
    }

    LaunchedEffect(playingQueueExpanded) {
        if (playingQueueExpanded.not()) {
            playerViewModel.dismissSearch()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                actions = {
                    if (playingQueueExpanded) {
                        AnimatedContent(
                            targetState = searchRequested,
                            label = "library_action_animation",
                            transitionSpec = {
                                fadeIn(animationSpec = keyframes { durationMillis = 150 }) togetherWith
                                    fadeOut(animationSpec = keyframes { durationMillis = 150 })
                            },
                        ) { isSearchRequested ->
                            when (isSearchRequested) {
                                true -> ChapterSearchActionComposable(
                                    onSearchRequested = { playerViewModel.updateSearch(it) },
                                )

                                false -> Row {
                                    IconButton(
                                        onClick = { playerViewModel.requestSearch() },
                                        modifier = Modifier.padding(end = 4.dp),
                                    ) {
                                        Icon(
                                            imageVector = Search,
                                            contentDescription = null,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Row {
                            IconButton(
                                onClick = { itemDetailsSelected = true },
                                modifier = Modifier.padding(end = 4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                },
                title = {
                    Text(
                        text = screenTitle,
                        style = titleTextStyle,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { stepBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = colorScheme.onSurface,
                        )
                    }
                },
            )
        },
        bottomBar = {
            playingBook
                ?.let {
                    NavigationBarComposable(
                        book = it,
                        playerViewModel = playerViewModel,
                        contentCachingModelView = cachingModelView,
                        navController = navController,
                        libraryType = libraryViewModel.fetchPreferredLibraryType(),
                    )
                }
        },
        modifier = Modifier.systemBarsPadding(),
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .testTag("playerScreen")
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    visible = playingQueueExpanded.not(),
                    enter = expandVertically(animationSpec = tween(400)),
                    exit = shrinkVertically(animationSpec = tween(400)),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (!isPlaybackReady) {
                            TrackDetailsPlaceholderComposable(bookTitle, bookSubtitle)
                        } else {
                            TrackDetailsComposable(
                                viewModel = playerViewModel,
                                imageLoader = imageLoader,
                                libraryViewModel = libraryViewModel,
                            )
                        }

                        if (!isPlaybackReady) {
                            TrackControlPlaceholderComposable(
                                modifier = Modifier,
                            )
                        } else {
                            TrackControlComposable(
                                viewModel = playerViewModel,
                                modifier = Modifier,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                when {
                    isPlaybackReady.not() -> {
                        PlayingQueuePlaceholderComposable(
                            libraryViewModel = libraryViewModel,
                            modifier = Modifier,
                        )
                    }
                    playingBook?.chapters.isNullOrEmpty() -> {
                        PlayingQueueFallbackComposable(
                            libraryViewModel = libraryViewModel,
                            modifier = Modifier,
                        )
                    }

                    else -> {
                        PlayingQueueComposable(
                            libraryViewModel = libraryViewModel,
                            viewModel = playerViewModel,
                            modifier = Modifier,
                        )
                    }
                }
            }
        },
    )

    if (itemDetailsSelected) {
        ModalBottomSheet(
            onDismissRequest = { itemDetailsSelected = false },
            containerColor = colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp, horizontal = 4.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = bookTitle,
                        style = typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = colorScheme.onSurface,
                    )

                    bookSubtitle?.let {
                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = it,
                            style = typography.titleSmall,
                            color = colorScheme.onBackground.copy(alpha = 0.6f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    playingBook
                        ?.author
                        ?.let {
                            InfoRow(
                                icon = Icons.Default.Person,
                                label = stringResource(R.string.playing_item_details_author),
                                textValue = it,
                            )
                        }

                    playingBook
                        ?.series
                        ?.takeIf { it.isNotEmpty() }
                        ?.let {
                            InfoRow(
                                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                                label = stringResource(R.string.playing_item_details_series),
                                textValue = it.joinToString(", ") { series ->
                                    buildString {
                                        append(series.name)
                                        series.serialNumber
                                            ?.takeIf { it.isNotBlank() }
                                            ?.let { serial -> append(" #$serial") }
                                    }
                                },
                            )
                        }

                    playingBook
                        ?.publisher
                        ?.let {
                            InfoRow(
                                icon = Icons.Default.Business,
                                label = stringResource(R.string.playing_item_details_publisher),
                                textValue = it,
                            )
                        }

                    playingBook
                        ?.year
                        ?.let {
                            InfoRow(
                                icon = Icons.Default.CalendarMonth,
                                label = stringResource(R.string.playing_item_details_year),
                                textValue = it,
                            )
                        }
                }

                playingBook
                    ?.abstract
                    ?.let {
                        HorizontalDivider(
                            modifier = Modifier
                                .padding(vertical = 16.dp, horizontal = 16.dp)
                                .alpha(0.2f),
                        )

                        val html = (playingBook?.abstract ?: "").replace("\n", "<br>")
                        val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)

                        Text(
                            text = spanned.toString(),
                            style = typography.bodyMedium.copy(lineHeight = 22.sp),
                            color = colorScheme.onSurface,
                            textAlign = TextAlign.Justify,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    textValue: String,
) {
    Spacer(modifier = Modifier.height(8.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$label: ",
            style = typography.bodyMedium,
            color = Color.Gray,
        )
        Text(
            text = textValue,
            style = typography.bodyMedium,
        )
    }
}

private fun playingItemChanged(
    item: String,
    playingBook: DetailedItem?,
) = item != playingBook?.id

private fun cachePolicyChanged(
    cachingModelView: CachingModelView,
    playingBook: DetailedItem?,
) = cachingModelView.localCacheUsing() != playingBook?.localProvided
