package org.grakovne.lissen.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.with
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import org.grakovne.lissen.ui.screens.player.composable.PlayerNavBarComposable
import org.grakovne.lissen.ui.screens.player.composable.PlayingQueueComposable
import org.grakovne.lissen.ui.screens.player.composable.TrackControlComposable
import org.grakovne.lissen.ui.screens.player.composable.TrackDetailsComposable
import org.grakovne.lissen.ui.screens.player.composable.placeholder.PlayingQueuePlaceholderComposable
import org.grakovne.lissen.ui.screens.player.composable.placeholder.TrackDetailsPlaceholderComposable
import org.grakovne.lissen.viewmodel.PlayerViewModel


@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun PlayerScreen(
    navController: NavController,
    onBack: () -> Unit,
    bookId: String?
) {

    val viewModel: PlayerViewModel = hiltViewModel()
    val titleTextStyle = typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    val isPlaybackReady by viewModel.isPlaybackReady.observeAsState(false)
    val playingQueueExpanded by viewModel.playingQueueExpanded.observeAsState(false)

    LaunchedEffect(Unit) {
        bookId?.let { viewModel.fetchBookDetails(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Library",
                        style = titleTextStyle,
                        color = colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = colorScheme.onSurface
                        )
                    }
                }
            )
        },
        bottomBar = {
            PlayerNavBarComposable(
                viewModel,
                navController = navController,
                onChaptersClick = { viewModel.togglePlayingQueue() }
            )
        },
        modifier = Modifier
            .systemBarsPadding()
            .fillMaxHeight(),
        content = { innerPadding ->

            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    visible = !playingQueueExpanded,
                    enter = expandVertically(animationSpec = tween(500)),
                    exit = shrinkVertically(animationSpec = tween(500))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        if (!isPlaybackReady) {
                            TrackDetailsPlaceholderComposable()
                        } else {
                            TrackDetailsComposable(
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TrackControlComposable(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Crossfade(targetState = isPlaybackReady) { playbackReady ->
                    if (playbackReady) {
                        PlayingQueueComposable(
                            viewModel = viewModel,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        PlayingQueuePlaceholderComposable(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    )
}

