package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.request.ImageRequest
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.ui.components.AsyncShimmeringImage
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.viewmodel.PlayerViewModel

@Composable
fun MiniPlayerComposable(
    navController: AppNavigationService,
    book: DetailedItem,
    imageLoader: ImageLoader,
    playerViewModel: PlayerViewModel,
) {
    val isPlaying: Boolean by playerViewModel.isPlaying.observeAsState(false)
    var backgroundVisible by remember { mutableStateOf(true) }

    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { it * 0.2f },
        confirmValueChange = { newValue: SwipeToDismissBoxValue ->
            when (newValue) {
                SwipeToDismissBoxValue.EndToStart,
                SwipeToDismissBoxValue.StartToEnd,
                -> {
                    backgroundVisible = false
                    true
                }

                else -> false
            }
        },
    )

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            playerViewModel.clearPlayingBook()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd -> Arrangement.Start
                    SwipeToDismissBoxValue.EndToStart -> Arrangement.End
                    SwipeToDismissBoxValue.Settled -> Arrangement.Center
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(
                    visible = backgroundVisible,
                    exit = fadeOut(animationSpec = tween(300)),
                ) {
                    CloseActionBackground()
                }
            }
        },
    ) {
        AnimatedVisibility(
            visible = backgroundVisible,
            exit = fadeOut(animationSpec = tween(300)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorScheme.background)
                    .clickable { navController.showPlayer(book.id, book.title) }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val context = LocalContext.current
                val imageRequest = remember(book.id) {
                    ImageRequest.Builder(context)
                        .data(book.id)
                        .size(coil.size.Size.ORIGINAL)
                        .build()
                }

                AsyncShimmeringImage(
                    imageRequest = imageRequest,
                    imageLoader = imageLoader,
                    contentDescription = "${book.title} cover",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .size(48.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(4.dp)),
                    error = painterResource(R.drawable.cover_fallback),
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = book.title,
                        style = typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onBackground,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    arrayOf(book.subtitle, book.author).forEach {
                        it?.let {
                            Text(
                                text = it,
                                style = typography.bodyMedium.copy(
                                    color = colorScheme.onBackground.copy(alpha = 0.6f),
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row {
                        IconButton(
                            onClick = { playerViewModel.togglePlayPause() },
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CloseActionBackground() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .padding(vertical = 8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = stringResource(R.string.mini_player_action_close),
            tint = colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.mini_player_action_close),
            style = typography.labelSmall,
            color = colorScheme.onSurface,
        )
    }
}
