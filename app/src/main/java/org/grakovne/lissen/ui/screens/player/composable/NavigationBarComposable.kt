package org.grakovne.lissen.ui.screens.player.composable

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.grakovne.lissen.R
import org.grakovne.lissen.channel.common.LibraryType
import org.grakovne.lissen.content.cache.CacheState
import org.grakovne.lissen.domain.CacheStatus
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.ui.icons.TimerPlay
import org.grakovne.lissen.ui.icons.loader.Loader10
import org.grakovne.lissen.ui.icons.loader.Loader20
import org.grakovne.lissen.ui.icons.loader.Loader40
import org.grakovne.lissen.ui.icons.loader.Loader60
import org.grakovne.lissen.ui.icons.loader.Loader80
import org.grakovne.lissen.ui.icons.loader.Loader90
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
    val playbackSpeed by playerViewModel.playbackSpeed.observeAsState(1f)
    val playingQueueExpanded by playerViewModel.playingQueueExpanded.observeAsState(false)

    val isMetadataCached by contentCachingModelView.provideCacheState(book.id).observeAsState(false)

    var playbackSpeedExpanded by remember { mutableStateOf(false) }
    var timerExpanded by remember { mutableStateOf(false) }
    var downloadsExpanded by remember { mutableStateOf(false) }

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
                icon = {
                    Icon(
                        Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = stringResource(R.string.player_screen_chapter_list_navigation),
                        modifier = Modifier.size(iconSize),
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.player_screen_chapter_list_navigation),
                        style = labelStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                selected = playingQueueExpanded,
                onClick = { playerViewModel.togglePlayingQueue() },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colorScheme.primary,
                    indicatorColor = colorScheme.surfaceContainer,
                ),
            )

            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = provideCachingStateIcon(
                            cacheState = cacheProgress,
                            hasCached = isMetadataCached,
                        ),
                        contentDescription = stringResource(R.string.player_screen_downloads_navigation),
                        modifier = Modifier.size(iconSize),
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
                selected = false,
                onClick = { downloadsExpanded = true },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colorScheme.primary,
                    indicatorColor = colorScheme.surfaceContainer,
                ),
            )

            NavigationBarItem(
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
                enabled = true,
                colors = NavigationBarItemDefaults.colors(
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
                    Text(
                        text = stringResource(R.string.player_screen_timer_navigation),
                        style = labelStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                selected = false,
                onClick = { timerExpanded = true },
                colors = NavigationBarItemDefaults.colors(
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
                                    mediaItemId = it.id,
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
                                contentCachingModelView.dropCache(it.id)

                                playerViewModel.clearPlayingBook()
                                navController.showLibrary(true)
                            }
                    },
                    onDismissRequest = { downloadsExpanded = false },
                )
            }
        }
    }
}

private fun provideCachingStateIcon(
    hasCached: Boolean,
    cacheState: CacheState,
): ImageVector {
    if (cacheState.status is CacheStatus.Caching) {
        return when {
            cacheState.progress < 1.0 / 6 -> Loader10
            cacheState.progress < 2.0 / 6 -> Loader20
            cacheState.progress < 3.0 / 6 -> Loader40
            cacheState.progress < 4.0 / 6 -> Loader60
            cacheState.progress < 5.0 / 6 -> Loader80
            else -> Loader90
        }
    }

    return when (hasCached) {
        true -> cachedIcon
        else -> defaultIcon
    }
}

private val cachedIcon = Icons.Outlined.CloudDone
private val defaultIcon = Icons.Outlined.CloudDownload
