package org.grakovne.lissen.ui.screens.library.composables

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
import androidx.compose.material.DismissValue.Default
import androidx.compose.material.DismissValue.DismissedToEnd
import androidx.compose.material.DismissValue.DismissedToStart
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.rememberDismissState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
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

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MiniPlayerComposable(
    navController: AppNavigationService,
    modifier: Modifier = Modifier,
    book: DetailedItem,
    imageLoader: ImageLoader,
    playerViewModel: PlayerViewModel,
) {
    val density = LocalDensity.current
    val isPlaying: Boolean by playerViewModel.isPlaying.observeAsState(false)

    val dismissState = rememberDismissState(
        confirmStateChange = { dismissValue ->
            when (dismissValue) {
                DismissedToEnd, DismissedToStart -> {
                    playerViewModel.clearPlayingBook()
                    true
                }

                Default -> false
            }
        },
    )

    SwipeToDismiss(
        state = dismissState,
        dismissThresholds = { FractionalThreshold(0.5f) },
        background = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CloseActionBackground() // left Side
                CloseActionBackground() // Right Side
            }
        },
    ) {
        Surface(
            shadowElevation = 4.dp,
            modifier = modifier.clickable { navController.showPlayer(book.id, book.title) },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    book
                        .author
                        ?.let {
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
            contentDescription = "Close",
            tint = colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Close",
            style = typography.labelSmall,
            color = colorScheme.onSurface,
        )
    }
}
