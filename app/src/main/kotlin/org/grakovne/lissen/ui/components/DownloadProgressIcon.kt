package org.grakovne.lissen.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.content.cache.persistent.CacheState
import org.grakovne.lissen.lib.domain.CacheStatus

@Composable
fun DownloadProgressIcon(
  cacheState: CacheState,
  size: Dp = 24.dp,
  color: Color = LocalContentColor.current,
) {
  if (cacheState.status is CacheStatus.Caching) {
    val iconSize = size - 2.dp
    CircularProgressIndicator(
      progress = { cacheState.progress.coerceIn(0.0, 1.0).toFloat() },
      modifier = Modifier.size(iconSize),
      strokeWidth = iconSize * 0.1f,
      color = colorScheme.primary,
      trackColor = color,
      strokeCap = StrokeCap.Butt,
      gapSize = 2.dp,
    )
  } else {
    Icon(
      imageVector = Icons.Outlined.CloudDownload,
      contentDescription = stringResource(R.string.player_screen_downloads_navigation),
      modifier = Modifier.size(size),
      tint = color,
    )
  }
}
