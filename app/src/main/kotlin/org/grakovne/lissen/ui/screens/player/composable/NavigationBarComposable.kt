package org.grakovne.lissen.ui.screens.player.composable

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.map
import kotlinx.coroutines.launch
import org.grakovne.lissen.R
import org.grakovne.lissen.common.PlaybackVolumeBoost
import org.grakovne.lissen.content.cache.persistent.CacheState
import org.grakovne.lissen.lib.domain.CacheStatus
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.ui.components.DownloadProgressIcon
import org.grakovne.lissen.ui.extensions.formatTime
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.screens.settings.composable.CommonSettingsItem
import org.grakovne.lissen.ui.screens.settings.composable.CommonSettingsItemComposable
import org.grakovne.lissen.viewmodel.CachingModelView
import org.grakovne.lissen.viewmodel.PlayerViewModel
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
fun NavigationBarComposable(
  book: DetailedItem,
  playerViewModel: PlayerViewModel,
  contentCachingModelView: CachingModelView,
  settingsViewModel: SettingsViewModel,
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
  val preferredPlaybackVolumeBoost by settingsViewModel.preferredPlaybackVolumeBoost.observeAsState()
  val isOnline by playerViewModel.isOnline.collectAsState(initial = false)

  val hasDownloadedChapters by contentCachingModelView.hasDownloadedChapters(book.id).observeAsState(false)

  var playbackSpeedExpanded by remember { mutableStateOf(false) }
  var timerExpanded by remember { mutableStateOf(false) }
  var downloadsExpanded by remember { mutableStateOf(false) }
  var volumeBoostExpanded by remember { mutableStateOf(false) }

  val scope = rememberCoroutineScope()
  val context = androidx.compose.ui.platform.LocalContext.current

  Surface(
    shadowElevation = 0.dp,
    color = Color.Transparent,
    modifier = modifier.padding(top = 24.dp, bottom = 12.dp, start = 12.dp, end = 12.dp),
  ) {
    Surface(
      color = colorScheme.surfaceVariant.copy(alpha = 0.4f),
      shape = androidx.compose.foundation.shape.CircleShape,
      modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val iconSize = 24.dp

        PlayerActionItem(
          icon = {
            DownloadProgressIcon(
              cacheState = cacheProgress,
              size = iconSize,
            )
          },
          enabled = hasEpisodes,
          onClick = { downloadsExpanded = true },
          modifier = Modifier.weight(1f),
        )

        PlayerActionItem(
          icon = {
            Icon(
              Icons.Outlined.VolumeUp,
              contentDescription = stringResource(R.string.volume_boost_title),
              modifier = Modifier.size(iconSize),
            )
          },
          enabled = true,
          onClick = { volumeBoostExpanded = true },
          modifier = Modifier.weight(1f),
        )

        PlayerActionItem(
          icon = {
            if (playbackSpeed != 1f) {
              Text(
                text = "${playbackSpeed}x",
                style = typography.bodyMedium,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
              )
            } else {
              Icon(
                Icons.Outlined.SlowMotionVideo,
                contentDescription = stringResource(R.string.player_screen_playback_speed_navigation),
                modifier = Modifier.size(iconSize),
              )
            }
          },
          enabled = hasEpisodes,
          onClick = { playbackSpeedExpanded = true },
          modifier = Modifier.weight(1f),
        )

        PlayerActionItem(
          icon = {
            if (timerOption != null) {
              Text(
                text = (timerRemaining ?: 0).toInt().formatTime(),
                style = typography.bodyMedium,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
              )
            } else {
              Icon(
                Icons.Outlined.Timer,
                contentDescription = stringResource(R.string.player_screen_timer_navigation),
                modifier = Modifier.size(iconSize),
              )
            }
          },
          enabled = hasEpisodes,
          onClick = { timerExpanded = true },
          modifier = Modifier.weight(1f),
        )
      }
    }

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

    if (volumeBoostExpanded) {
      CommonSettingsItemComposable(
        title = stringResource(R.string.volume_boost_title),
        items =
          listOf(
            PlaybackVolumeBoost.DISABLED.toItem(context),
            PlaybackVolumeBoost.LOW.toItem(context),
            PlaybackVolumeBoost.MEDIUM.toItem(context),
            PlaybackVolumeBoost.HIGH.toItem(context),
            PlaybackVolumeBoost.MAX.toItem(context),
          ),
        selectedItem = preferredPlaybackVolumeBoost?.toItem(context),
        onDismissRequest = { volumeBoostExpanded = false },
        onItemSelected = { item ->
          PlaybackVolumeBoost
            .entries
            .find { it.name == item.id }
            ?.let { settingsViewModel.preferPlaybackVolumeBoost(it) }
        },
      )
    }

    if (downloadsExpanded) {
      DownloadsComposable(
        libraryType = libraryType,
        hasCachedEpisodes = hasDownloadedChapters,
        isOnline = isOnline,
        cachingInProgress = cacheProgress.status is CacheStatus.Caching,
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
              }
            }
        },
        onRequestedStop = {
          playerViewModel
            .book
            .value
            ?.let {
              scope.launch {
                contentCachingModelView.stopCaching(it)
              }
            }
        },
        onDismissRequest = { downloadsExpanded = false },
      )
    }
  }
}

@Composable
private fun PlayerActionItem(
  icon: @Composable () -> Unit,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    modifier =
      modifier
        .clickable(
          enabled = enabled,
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = onClick,
        ).padding(horizontal = 8.dp),
  ) {
    icon()
  }
}

private fun PlaybackVolumeBoost.toItem(context: Context): CommonSettingsItem {
  val id = this.name
  val name =
    when (this) {
      PlaybackVolumeBoost.DISABLED -> context.getString(R.string.volume_boost_disabled)
      PlaybackVolumeBoost.LOW -> context.getString(R.string.volume_boost_low)
      PlaybackVolumeBoost.MEDIUM -> context.getString(R.string.volume_boost_medium)
      PlaybackVolumeBoost.HIGH -> context.getString(R.string.volume_boost_high)
      PlaybackVolumeBoost.MAX -> context.getString(R.string.volume_boost_max)
    }

  return CommonSettingsItem(id, name, null)
}
