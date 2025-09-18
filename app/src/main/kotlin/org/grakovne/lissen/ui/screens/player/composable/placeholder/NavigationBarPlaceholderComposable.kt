package org.grakovne.lissen.ui.screens.player.composable.placeholder

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.grakovne.lissen.R
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.ui.icons.TimerPlay

@Composable
fun NavigationBarPlaceholderComposable(
  modifier: Modifier = Modifier,
  libraryType: LibraryType,
) {
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
        selected = false,
        onClick = { },
        colors =
          NavigationBarItemDefaults.colors(
            selectedIconColor = colorScheme.primary,
            indicatorColor = colorScheme.surfaceContainer,
          ),
      )

      NavigationBarItem(
        icon = {
          Icon(
            imageVector = Icons.Outlined.CloudDownload,
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
        onClick = {},
        colors =
          NavigationBarItemDefaults.colors(
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
        onClick = { },
        enabled = true,
        colors =
          NavigationBarItemDefaults.colors(
            selectedIconColor = colorScheme.primary,
            indicatorColor = colorScheme.surfaceContainer,
          ),
      )

      NavigationBarItem(
        icon = {
          Icon(
            TimerPlay,
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
        onClick = { },
        colors =
          NavigationBarItemDefaults.colors(
            selectedIconColor = colorScheme.primary,
            indicatorColor = colorScheme.surfaceContainer,
          ),
      )
    }
  }
}
