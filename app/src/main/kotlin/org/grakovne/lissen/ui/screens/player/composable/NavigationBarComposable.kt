package org.grakovne.lissen.ui.screens.player.composable

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.map
import kotlinx.coroutines.launch
import org.grakovne.lissen.R
import org.grakovne.lissen.content.cache.persistent.CacheState
import org.grakovne.lissen.lib.domain.CacheStatus
import org.grakovne.lissen.lib.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.DurationTimerOption
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.ui.extensions.formatTime
import org.grakovne.lissen.ui.icons.TimerPlay
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.viewmodel.CachingModelView
import org.grakovne.lissen.viewmodel.PlayerViewModel

@Composable
fun NavigationBarComposable(
  book: DetailedItem,
  playerViewModel: PlayerViewModel,
  contentCachingModelView: CachingModelView,
  navController: AppNavigationService,
  modifier: Modifier = Modifier,
  libraryType: LibraryType,
) {
  val cacheProgress: CacheState by contentCachingModelView.getProgress(book.id).collectAsState()
  val timerOption by playerViewModel.timerOption.observeAsState(null)
  val timerRemaining by playerViewModel.timerRemaining.observeAsState(0)
  val playbackSpeed by playerViewModel.playbackSpeed.observeAsState(1f)
  val playingQueueExpanded by playerViewModel.playingQueueExpanded.observeAsState(false)
  val hasEpisodes by playerViewModel.book.map { book.chapters.isNotEmpty() }.observeAsState(true)

  val isMetadataCached by contentCachingModelView.provideCacheState(book.id).observeAsState(false)

  var playbackSpeedExpanded by remember { mutableStateOf(false) }
  var timerExpanded by remember { mutableStateOf(false) }
  var downloadsExpanded by remember { mutableStateOf(false) }

  val scope = rememberCoroutineScope()

  Surface(
    shadowElevation = 4.dp,
    modifier = modifier.height(64.dp),
  ) {
    NavigationBar(
      containerColor = Color.Transparent,
      contentColor = colorScheme.onBackground,
      modifier = Modifier.fillMaxWidth(),
    ) {
      val iconSize = 24.dp
      val labelStyle = typography.labelSmall.copy(fontSize = 10.sp)

      NavigationBarItem(
        enabled = hasEpisodes,
        icon = {
          Icon(
            Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription =
              when (libraryType) {
                LibraryType.LIBRARY -> stringResource(R.string.player_screen_chapter_list_navigation_library)
                LibraryType.PODCAST -> stringResource(R.string.player_screen_chapter_list_navigation_podcast)
                LibraryType.UNKNOWN -> stringResource(R.string.player_screen_chapter_list_navigation_items)
              },
            modifier = Modifier.size(iconSize),
          )
        },
        label = {
          Text(
            text =
              when (libraryType) {
                LibraryType.LIBRARY -> stringResource(R.string.player_screen_chapter_list_navigation_library)
                LibraryType.PODCAST -> stringResource(R.string.player_screen_chapter_list_navigation_podcast)
                LibraryType.UNKNOWN -> stringResource(R.string.player_screen_chapter_list_navigation_items)
              },
            style = labelStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        selected = playingQueueExpanded,
        onClick = { playerViewModel.togglePlayingQueue() },
        colors =
          NavigationBarItemDefaults.colors(
            selectedIconColor = colorScheme.primary,
            indicatorColor = colorScheme.surfaceContainer,
          ),
      )

      NavigationBarItem(
        icon = {
          DownloadProgressIcon(
            cacheState = cacheProgress,
            size = iconSize,
          )
        },
        label = {
          Text(
            text = stringResource(R.string.player_screen_downloads_navigation),
            style = labelStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        enabled = hasEpisodes,
        selected = false,
        onClick = { downloadsExpanded = true },
        colors =
          NavigationBarItemDefaults.colors(
            selectedIconColor = colorScheme.primary,
            indicatorColor = colorScheme.surfaceContainer,
          ),
      )

      NavigationBarItem(
        enabled = hasEpisodes,
        icon = {
          Icon(
            Icons.Outlined.SlowMotionVideo,
            contentDescription = stringResource(R.string.player_screen_playback_speed_navigation),
            modifier = Modifier.size(iconSize),
          )
        },
        label = {
          Text(
            text = stringResource(R.string.player_screen_playback_speed_navigation),
            style = labelStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        selected = false,
        onClick = { playbackSpeedExpanded = true },
        colors =
          NavigationBarItemDefaults.colors(
            selectedIconColor = colorScheme.primary,
            indicatorColor = colorScheme.surfaceContainer,
          ),
      )

      NavigationBarItem(
        icon = {
          Icon(
            when (timerOption) {
              null -> Icons.Outlined.Timer
              else -> TimerPlay
            },
            contentDescription = stringResource(R.string.player_screen_timer_navigation),
            modifier = Modifier.size(iconSize),
          )
        },
        label = {
          when (timerOption) {
            is DurationTimerOption, CurrentEpisodeTimerOption -> {
              Text(
                text =
                  timerRemaining
                    ?.toInt()
                    ?.formatTime(false)
                    ?: stringResource(R.string.player_screen_timer_navigation),
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }

            null ->
              Text(
                text = stringResource(R.string.player_screen_timer_navigation),
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
          }
        },
        enabled = hasEpisodes,
        selected = false,
        onClick = { timerExpanded = true },
        colors =
          NavigationBarItemDefaults.colors(
            selectedIconColor = colorScheme.primary,
            indicatorColor = colorScheme.surfaceContainer,
          ),
      )

      if (playbackSpeedExpanded) {
        PlaybackSpeedComposable(
          currentSpeed = playbackSpeed,
          onSpeedChange = { playerViewModel.setPlaybackSpeed(it) },
          onDismissRequest = { playbackSpeedExpanded = false },
        )
      }

      if (timerExpanded) {
        TimerComposable(
          libraryType = libraryType,
          currentOption = timerOption,
          onOptionSelected = { playerViewModel.setTimer(it) },
          onDismissRequest = { timerExpanded = false },
        )
      }

      if (downloadsExpanded) {
        DownloadsComposable(
          libraryType = libraryType,
          hasCachedEpisodes = isMetadataCached,
          isForceCache = contentCachingModelView.localCacheUsing(),
          onRequestedDownload = { option ->
            playerViewModel.book.value?.let {
              contentCachingModelView
                .cache(
                  mediaItem = it,
                  currentPosition = playerViewModel.totalPosition.value ?: 0.0,
                  option = option,
                )
            }
          },
          onRequestedDrop = {
            playerViewModel
              .book
              .value
              ?.let {
                scope.launch {
                  contentCachingModelView.dropCache(it.id)

                  playerViewModel.clearPlayingBook()
                  navController.showLibrary(true)
                }
              }
          },
          onDismissRequest = { downloadsExpanded = false },
        )
      }
    }
  }
}

@Composable
private fun DownloadProgressIcon(
  cacheState: CacheState,
  size: Dp,
) {
  if (cacheState.status is CacheStatus.Caching) {
    val iconSize = size - 2.dp
    CircularProgressIndicator(
      progress = { cacheState.progress.coerceIn(0.0, 1.0).toFloat() },
      modifier = Modifier.size(iconSize),
      strokeWidth = iconSize * 0.1f,
      color = colorScheme.primary,
      trackColor = LocalContentColor.current,
      strokeCap = StrokeCap.Butt,
      gapSize = 2.dp,
    )
  } else {
    Icon(
      imageVector = Icons.Outlined.CloudDownload,
      contentDescription = stringResource(R.string.player_screen_downloads_navigation),
      modifier = Modifier.size(size),
    )
  }
}
