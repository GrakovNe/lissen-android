package org.grakovne.lissen.ui.screens.library.composables

import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.request.ImageRequest
import org.grakovne.lissen.R
import org.grakovne.lissen.common.snapProgress
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.ui.components.AsyncShimmeringImage
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.viewmodel.PlayerViewModel

@Composable
fun MiniPlayerComposable(
  navController: AppNavigationService,
  book: DetailedItem,
  imageLoader: ImageLoader,
  playerViewModel: PlayerViewModel,
  libraryType: LibraryType?,
) {
  val view: View = LocalView.current

  var backgroundVisible by remember { mutableStateOf(true) }

  val dismissState =
    rememberSwipeToDismissBoxState(
      initialValue = SwipeToDismissBoxValue.Settled,
      positionalThreshold = { it * 0.2f },
    )

  LaunchedEffect(dismissState.currentValue) {
    val dismissed =
      when (dismissState.currentValue) {
        SwipeToDismissBoxValue.EndToStart,
        SwipeToDismissBoxValue.StartToEnd,
        -> true

        else -> false
      }

    if (dismissed) {
      withHaptic(view) {
        backgroundVisible = false
        playerViewModel.clearPlayingBook()
      }
    }
  }

  SwipeToDismissBox(
    state = dismissState,
    backgroundContent = {
      Row(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        AnimatedVisibility(
          visible = backgroundVisible,
          exit = fadeOut(animationSpec = tween(300)),
        ) {
          CloseActionBackground()
        }

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
        modifier =
          Modifier
            .testTag("miniPlayer")
            .fillMaxWidth()
            .background(colorScheme.background)
            .clickable { navController.showPlayer(book.id, book.title, book.subtitle) }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val context = LocalContext.current
        val imageRequest =
          remember(book.id) {
            ImageRequest
              .Builder(context)
              .data(book.id)
              .build()
          }

        AsyncShimmeringImage(
          imageRequest = imageRequest,
          imageLoader = imageLoader,
          contentDescription = "${book.title} cover",
          contentScale = ContentScale.FillBounds,
          modifier =
            Modifier
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
            style =
              typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onBackground,
              ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )

          book.author?.let {
            Text(
              text = it,
              style =
                typography.bodyMedium.copy(
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
          PlaybackProgressButton(
            playerViewModel = playerViewModel,
            book = book,
            libraryType = libraryType,
            onClick = { withHaptic(view) { playerViewModel.togglePlayPause() } },
          )
        }
      }
    }
  }
}

@Composable
private fun PlaybackProgressButton(
  playerViewModel: PlayerViewModel,
  book: DetailedItem,
  libraryType: LibraryType?,
  onClick: () -> Unit,
) {
  // Read the per-tick playback position in this leaf only, so a position tick recomposes
  // just the progress button — not the whole mini player (cover, title, author). Keeps the
  // linked-search push (which mounts a fresh library on top of a playing book) smooth.
  val isPlaying by playerViewModel.isPlaying.collectAsState()
  val totalPosition by playerViewModel.totalPosition.collectAsState()

  val progress =
    calculateProgress(
      item = book,
      libraryType = libraryType,
      totalPosition = totalPosition,
    )

  PlaybackButton(
    isPlaying = isPlaying,
    progress = progress,
    onClick = onClick,
  )
}

private fun calculateProgress(
  item: DetailedItem,
  libraryType: LibraryType?,
  totalPosition: Double,
): Float {
  val totalDuration = item.chapters.sumOf { it.duration }

  return when (totalDuration > 0 && libraryType == LibraryType.LIBRARY) {
    true -> (totalPosition / totalDuration).toFloat().coerceIn(0f, 1f).snapProgress()
    false -> 0f
  }
}

@Composable
private fun PlaybackButton(
  isPlaying: Boolean,
  progress: Float,
  onClick: () -> Unit,
) {
  IconButton(onClick = onClick) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.size(34.dp),
    ) {
      CircularProgressIndicator(
        progress = { progress },
        modifier = Modifier.size(28.dp),
        strokeWidth = 28.dp * 0.1f,
        color = colorScheme.primary,
        trackColor = colorScheme.onBackground,
        strokeCap = StrokeCap.Butt,
        gapSize = 2.dp,
      )
      Icon(
        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
        contentDescription = if (isPlaying) "Pause" else "Play",
        tint = colorScheme.onBackground,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

@Composable
fun CloseActionBackground() {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier =
      Modifier
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
