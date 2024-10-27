package org.grakovne.lissen.ui.screens.player

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.ImageLoader
import org.grakovne.lissen.R
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.screens.player.composable.PlayerNavBarComposable
import org.grakovne.lissen.ui.screens.player.composable.PlayingQueueComposable
import org.grakovne.lissen.ui.screens.player.composable.TrackControlComposable
import org.grakovne.lissen.ui.screens.player.composable.TrackDetailsComposable
import org.grakovne.lissen.ui.screens.player.composable.placeholder.PlayingQueuePlaceholderComposable
import org.grakovne.lissen.ui.screens.player.composable.placeholder.TrackDetailsPlaceholderComposable
import org.grakovne.lissen.ui.theme.ItemAccented
import org.grakovne.lissen.viewmodel.PlayerViewModel
import kotlin.io.path.Path

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
        navController: AppNavigationService,
        imageLoader: ImageLoader,
        bookId: String?,
        bookTitle: String?
) {
    val viewModel: PlayerViewModel = hiltViewModel()
    val titleTextStyle = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)

    val playingBook by viewModel.book.observeAsState()
    val isPlaybackReady by viewModel.isPlaybackReady.observeAsState(false)
    val playingQueueExpanded by viewModel.playingQueueExpanded.observeAsState(false)

    LaunchedEffect(bookId) {
        bookId
                ?.takeIf { it != playingBook?.id }
                ?.let { viewModel.preparePlayback(it) }
    }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = {
                            Text(
                                    text = stringResource(R.string.player_screen_library_navigation),
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
                PlayerNavBarComposable(
                        viewModel,
                        navController = navController,
                        onChaptersClick = { viewModel.togglePlayingQueue() }
                )
            },
            modifier = Modifier.systemBarsPadding(),
            content = { innerPadding ->
                Column(
                        modifier = Modifier.padding(innerPadding),
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

                            Spacer(modifier = Modifier.height(8.dp))
                            PlaybackSpeedBottomSheet()
                        }
                    }

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

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSpeedBottomSheet(
        currentSpeed: Float = 1.0f,
        onSpeedChange: (Float) -> Unit = {},
        onDismissRequest: () -> Unit = {}
) {
    var selectedValue by remember { mutableFloatStateOf(currentSpeed) }
    val values = listOf(1f, 1.25f, 1.5f, 2f, 3f)

    ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            content = {
                Column(
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                            text = "Playback speed",
                            style = typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                            text = "${String.format("%.2f", selectedValue)}x",
                            style = typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                            value = selectedValue,
                            onValueChange = { value ->
                                val snapThreshold = 0.01f
                                val snappedValue = values.find { kotlin.math.abs(it - value) < snapThreshold } ?: value
                                selectedValue = snappedValue
                                onSpeedChange(snappedValue)
                            },
                            valueRange = 0.5f..3f,
                            modifier = Modifier
                                    .fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        values.forEach { value ->
                            Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(48.dp)
                            ) {
                                Button(
                                        onClick = {
                                            selectedValue = value
                                            onSpeedChange(value)
                                        },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                                containerColor = when (selectedValue == value) {
                                                    true -> colorScheme.primary
                                                    else -> ItemAccented
                                                }
                                        ),
                                        modifier = Modifier.fillMaxSize()
                                ) {
                                }
                                Text(
                                        text = String.format("%.2f", value),
                                        color = Color.Black,
                                        style = typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
    )
}