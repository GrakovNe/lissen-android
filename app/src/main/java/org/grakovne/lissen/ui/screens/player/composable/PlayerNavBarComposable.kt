package org.grakovne.lissen.ui.screens.player.composable

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Headset
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.grakovne.lissen.R
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.theme.ItemAccented
import org.grakovne.lissen.viewmodel.PlayerViewModel

@Composable
fun PlayerNavBarComposable(
    viewModel: PlayerViewModel,
    navController: AppNavigationService,
    modifier: Modifier = Modifier,
    onChaptersClick: () -> Unit
) {
    val context = LocalContext.current
    val playbackSpeed by viewModel.playbackSpeed.observeAsState(1f)
    val playingQueueExpanded by viewModel.playingQueueExpanded.observeAsState(false)

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
                    indicatorColor = ItemAccented
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
                onClick = { onChaptersClick() },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colorScheme.primary,
                    indicatorColor = ItemAccented
                )
            )

            NavigationBarItem(
                icon = {
                    Icon(
                        Icons.Outlined.Speed,
                        contentDescription = stringResource(R.string.player_screen_timer_navigation),
                        modifier = Modifier.size(iconSize)
                    )
                },
                label = {
                    Text(
                        text = playbackSpeed.format(context),
                        style = labelStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                selected = false,
                onClick = { viewModel.togglePlaybackSpeed() },
                enabled = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colorScheme.primary,
                    indicatorColor = ItemAccented
                )
            )

            NavigationBarItem(
                icon = {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.player_screen_preferences_navigation),
                        modifier = Modifier.size(iconSize)
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.player_screen_preferences_navigation),
                        style = labelStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                selected = false,
                onClick = { navController.showSettings() },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colorScheme.primary,
                    indicatorColor = ItemAccented
                )
            )
        }
    }
}

private fun Float.format(context: Context) = when (this) {
    1f -> context.getString(R.string.playback_speed_normal)
    1.5f -> context.getString(R.string.playback_speed_faster)
    2f -> context.getString(R.string.playback_speed_fast)
    else -> context.getString(R.string.playback_speed_custom)
}
