package org.grakovne.lissen.ui.screens.player.composable

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Headset
import androidx.compose.material.icons.outlined.Speed
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.grakovne.lissen.R
import org.grakovne.lissen.ui.icons.Timer_play
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.viewmodel.PlayerViewModel

@Composable
fun NavigationBarComposable(
    viewModel: PlayerViewModel,
    navController: AppNavigationService,
    modifier: Modifier = Modifier
) {
    val timerOption by viewModel.timerOption.observeAsState(null)
    val playbackSpeed by viewModel.playbackSpeed.observeAsState(1f)
    val playingQueueExpanded by viewModel.playingQueueExpanded.observeAsState(false)

    var playbackSpeedExpanded by remember { mutableStateOf(false) }
    var timerExpanded by remember { mutableStateOf(false) }

    Surface(
        shadowElevation = 4.dp,
        modifier = modifier.height(64.dp)
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            contentColor = colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth()
        ) {
            val iconSize = 24.dp
            val labelStyle = typography.labelSmall.copy(fontSize = 10.sp)

            NavigationBarItem(
                icon = {
                    Icon(
                        Icons.Outlined.Headset,
                        contentDescription = stringResource(R.string.player_screen_library_navigation),
                        modifier = Modifier.size(iconSize)
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.player_screen_library_navigation),
                        style = labelStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                selected = false,
                onClick = { navController.showLibrary() },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colorScheme.primary,
                    indicatorColor = colorScheme.surfaceContainer
                )
            )

            NavigationBarItem(
                icon = {
                    Icon(
                        Icons.Outlined.Book,
                        contentDescription = stringResource(R.string.player_screen_chapter_list_navigation),
                        modifier = Modifier.size(iconSize)
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.player_screen_chapter_list_navigation),
                        style = labelStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                selected = playingQueueExpanded,
                onClick = { viewModel.togglePlayingQueue() },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colorScheme.primary,
                    indicatorColor = colorScheme.surfaceContainer
                )
            )

            NavigationBarItem(
                icon = {
                    Icon(
                        Icons.Outlined.Speed,
                        contentDescription = stringResource(R.string.player_screen_playback_speed_navigation),
                        modifier = Modifier.size(iconSize)
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.player_screen_playback_speed_navigation),
                        style = labelStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                selected = false,
                onClick = { playbackSpeedExpanded = true },
                enabled = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colorScheme.primary,
                    indicatorColor = colorScheme.surfaceContainer
                )
            )

            NavigationBarItem(
                icon = {
                    Icon(
                        when (timerOption) {
                            null -> Icons.Outlined.Timer
                            else -> Timer_play
                        },
                        contentDescription = "Timer",
                        modifier = Modifier.size(iconSize)
                    )
                },
                label = {
                    Text(
                        text = "Timer",
                        style = labelStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                selected = false,
                onClick = { timerExpanded = true },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colorScheme.primary,
                    indicatorColor = colorScheme.surfaceContainer
                )
            )

            if (playbackSpeedExpanded) {
                PlaybackSpeedComposable(
                    currentSpeed = playbackSpeed,
                    onSpeedChange = { viewModel.setPlaybackSpeed(it) },
                    onDismissRequest = { playbackSpeedExpanded = false }
                )
            }

            if (timerExpanded) {
                TimerComposable(
                    currentOption = timerOption,
                    onOptionSelected = { viewModel.setTimer(it) },
                    onDismissRequest = { timerExpanded = false }
                )
            }
        }
    }
}
