package org.grakovne.lissen.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.ImageLoader
import org.grakovne.lissen.R
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.screens.player.composable.NavigationBarComposable
import org.grakovne.lissen.ui.screens.player.composable.PlayingQueueComposable
import org.grakovne.lissen.ui.screens.player.composable.TrackControlComposable
import org.grakovne.lissen.ui.screens.player.composable.TrackDetailsComposable
import org.grakovne.lissen.ui.screens.player.composable.placeholder.PlayingQueuePlaceholderComposable
import org.grakovne.lissen.ui.screens.player.composable.placeholder.TrackDetailsPlaceholderComposable
import org.grakovne.lissen.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    navController: AppNavigationService,
    imageLoader: ImageLoader,
    bookId: String,
    bookTitle: String
) {
    val viewModel: PlayerViewModel = hiltViewModel()
    val titleTextStyle = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)

    val playingBook by viewModel.book.observeAsState()
    val isPlaybackReady by viewModel.isPlaybackReady.observeAsState(false)
    val playingQueueExpanded by viewModel.playingQueueExpanded.observeAsState(false)

    LaunchedEffect(bookId) {
        bookId
            .takeIf { it != playingBook?.id }
            ?.let { viewModel.preparePlayback(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.player_screen_title),
                        style = titleTextStyle,
                        color = colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.showLibrary() }) {
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
            NavigationBarComposable(
                viewModel = viewModel,
                navController = navController
            )
        },
        modifier = Modifier.systemBarsPadding(),
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .testTag("playerScreen")
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
                    visible = playingQueueExpanded.not(),
                    enter = expandVertically(animationSpec = tween(400)),
                    exit = shrinkVertically(animationSpec = tween(400))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!isPlaybackReady) {
                            TrackDetailsPlaceholderComposable(bookTitle)
                        } else {
                            TrackDetailsComposable(
                                viewModel = viewModel,
                                imageLoader = imageLoader
                            )
                        }

                        TrackControlComposable(
                            viewModel = viewModel,
                            modifier = Modifier
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isPlaybackReady) {
                    PlayingQueueComposable(
                        viewModel = viewModel,
                        modifier = Modifier
                    )
                } else {
                    PlayingQueuePlaceholderComposable(
                        modifier = Modifier
                    )
                }
            }
        }
    )
}
