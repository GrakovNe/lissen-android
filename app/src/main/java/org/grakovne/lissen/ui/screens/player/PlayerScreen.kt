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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import org.grakovne.lissen.ui.screens.player.composable.placeholder.PlayingQueuePlaceholderComposable
import org.grakovne.lissen.ui.screens.player.composable.placeholder.TrackControlPlaceholderComposable
import org.grakovne.lissen.ui.screens.player.composable.placeholder.TrackDetailsPlaceholderComposable
import org.grakovne.lissen.ui.screens.player.composable.provideNowPlayingTitle
import org.grakovne.lissen.viewmodel.ContentCachingModelView
import org.grakovne.lissen.viewmodel.LibraryViewModel
import org.grakovne.lissen.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    navController: AppNavigationService,
    imageLoader: ImageLoader,
    bookId: String,
    bookTitle: String,
) {
    val context = LocalContext.current

    val cachingModelView: ContentCachingModelView = hiltViewModel()
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
                            TrackDetailsPlaceholderComposable(bookTitle)
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

                if (isPlaybackReady) {
                    PlayingQueueComposable(
                        libraryViewModel = libraryViewModel,
                        viewModel = playerViewModel,
                        modifier = Modifier,
                    )
                } else {
                    PlayingQueuePlaceholderComposable(
                        libraryViewModel = libraryViewModel,
                        modifier = Modifier,
                    )
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
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "Hyperion",
                        style = typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = colorScheme.onSurface
                    )

                    Spacer(Modifier.height(8.dp))

                    InfoRow(
                        icon = Icons.Default.Person,
                        label = "Author",
                        textValue = "Dan Simmons"
                    )
                    InfoRow(
                        icon = Icons.Default.Business,
                        label = "Publisher",
                        textValue = "Litres"
                    )
                    InfoRow(
                        icon = Icons.Default.CalendarMonth,
                        label = "Year",
                        textValue = "1990"
                    )
                }

                HorizontalDivider(
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .alpha(0.2f)
                )

                Text(
                    text = "The world of the great river Tethys – and the interstellar Hegemony, connecting hundreds of planets with null-portals. A world of space nomads and all-powerful AIs, mysterious Time Tombs and the ruthless \"Angel of Death\" Shrike. A world where the fates of the Soldier and the Priest, the Scholar and the Poet, the Detective and the Consul intertwine in intricate ways.",
                    style = typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = colorScheme.onSurface,
                    textAlign = TextAlign.Justify,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    textValue: String
) {
    Spacer(modifier = Modifier.height(8.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(20.dp)
                .alpha(0.9f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        Text(
            text = textValue,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun playingItemChanged(
    item: String,
    playingBook: DetailedItem?,
) = item != playingBook?.id

private fun cachePolicyChanged(
    contentCachingModelView: ContentCachingModelView,
    playingBook: DetailedItem?,
) = contentCachingModelView.localCacheUsing() != playingBook?.localProvided
