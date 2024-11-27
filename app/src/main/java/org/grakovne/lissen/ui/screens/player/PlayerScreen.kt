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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.ImageLoader
import org.grakovne.lissen.R
import org.grakovne.lissen.ui.icons.Search
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.screens.player.composable.NavigationBarComposable
import org.grakovne.lissen.ui.screens.player.composable.PlayingQueueComposable
import org.grakovne.lissen.ui.screens.player.composable.TrackControlComposable
import org.grakovne.lissen.ui.screens.player.composable.TrackDetailsComposable
import org.grakovne.lissen.ui.screens.player.composable.placeholder.PlayingQueuePlaceholderComposable
import org.grakovne.lissen.ui.screens.player.composable.placeholder.TrackDetailsPlaceholderComposable
import org.grakovne.lissen.viewmodel.NewCachingModelView
import org.grakovne.lissen.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    navController: AppNavigationService,
    imageLoader: ImageLoader,
    bookId: String,
    bookTitle: String,
) {
    val cachingModelView: NewCachingModelView = hiltViewModel()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val titleTextStyle = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)

    val playingBook by playerViewModel.book.observeAsState()
    val isPlaybackReady by playerViewModel.isPlaybackReady.observeAsState(false)
    val playingQueueExpanded by playerViewModel.playingQueueExpanded.observeAsState(false)
    val searchRequested by playerViewModel.searchRequested.observeAsState(false)

    val screenTitle = when (playingQueueExpanded) {
        true -> stringResource(R.string.player_screen_now_playing_title)
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

    LaunchedEffect(bookId) {
        bookId
            .takeIf { it != playingBook?.id }
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
            NavigationBarComposable(
                playerViewModel = playerViewModel,
                cachingModelView = cachingModelView,
                navController = navController,
            )
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
                            )
                        }

                        TrackControlComposable(
                            viewModel = playerViewModel,
                            modifier = Modifier,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isPlaybackReady) {
                    PlayingQueueComposable(
                        viewModel = playerViewModel,
                        modifier = Modifier,
                    )
                } else {
                    PlayingQueuePlaceholderComposable(
                        modifier = Modifier,
                    )
                }
            }
        },
    )
}
