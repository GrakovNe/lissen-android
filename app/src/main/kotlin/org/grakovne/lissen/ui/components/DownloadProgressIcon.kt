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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
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
    val progress = cacheState.progress.coerceIn(0.0, 1.0).toFloat()
    val progressPercent = (progress * 100).toInt()
    val progressDescription = stringResource(R.string.download_progress_description, progressPercent)

    val iconSize = size - 2.dp
    CircularProgressIndicator(
      progress = { progress },
      modifier =
        Modifier
          .semantics(mergeDescendants = true) {
            progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
            contentDescription = progressDescription
          }.size(iconSize),
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
