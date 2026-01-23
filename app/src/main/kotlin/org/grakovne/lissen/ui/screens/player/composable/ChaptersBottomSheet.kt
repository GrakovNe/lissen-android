package org.grakovne.lissen.ui.screens.player.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.lib.domain.BookChapterState
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.PlayingChapter
import org.grakovne.lissen.ui.effects.WindowBlurEffect
import org.grakovne.lissen.ui.extensions.formatTime
import org.grakovne.lissen.ui.theme.Spacing
import org.grakovne.lissen.viewmodel.CachingModelView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaptersBottomSheet(
  book: DetailedItem,
  currentPosition: Double,
  currentChapterIndex: Int,
  isOnline: Boolean,
  cachingModelView: CachingModelView,
  onChapterSelected: (PlayingChapter) -> Unit,
  onDismissRequest: () -> Unit,
) {
  WindowBlurEffect()

  ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    containerColor = colorScheme.background,
    scrimColor = colorScheme.scrim.copy(alpha = 0.65f),
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(bottom = Spacing.md)
          .padding(horizontal = Spacing.md),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = stringResource(R.string.player_screen_chapter_list_title),
        style = typography.titleLarge.copy(fontWeight = FontWeight.Bold),
      )

      Spacer(modifier = Modifier.height(Spacing.sm))

      val maxDuration = book.chapters.maxOfOrNull { it.duration } ?: 0.0

      LazyColumn(modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(book.chapters) { index, chapter ->
          val isCached by cachingModelView.provideCacheState(book.id, chapter.id).observeAsState(false)
          val isPlayingChapter = index == currentChapterIndex

          val chapterStart = chapter.start
          val chapterEnd = chapterStart + chapter.duration

          val progressRaw = (currentPosition - chapterStart) / (chapterEnd - chapterStart)
          val progress =
            when {
              currentPosition >= chapterEnd -> 1f
              currentPosition <= chapterStart -> 0f
              else -> progressRaw.toFloat()
            }

          val canPlay = isCached || isOnline

          ChapterListItem(
            chapter = chapter,
            isPlaying = isPlayingChapter,
            isCached = isCached,
            canPlay = canPlay,
            progress = progress,
            maxDuration = maxDuration,
            onClick = {
              onChapterSelected(chapter)
            },
          )

          if (index < book.chapters.size - 1) {
            HorizontalDivider(
              modifier = Modifier,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ChapterListItem(
  chapter: PlayingChapter,
  isPlaying: Boolean,
  isCached: Boolean,
  canPlay: Boolean,
  progress: Float,
  maxDuration: Double,
  onClick: () -> Unit,
) {
  val fontScale = LocalDensity.current.fontScale
  val textMeasurer = rememberTextMeasurer()
  val density = LocalDensity.current

  val forceLeadingHours = maxDuration >= 60 * 60
  val maxDurationText = remember(maxDuration) { maxDuration.toInt().formatTime(forceLeadingHours) }
  val bodySmallStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)

  val durationColumnWidth =
    remember(maxDurationText, density, bodySmallStyle) {
      with(density) {
        textMeasurer
          .measure(
            text = AnnotatedString(maxDurationText),
            style = bodySmallStyle,
          ).size
          .width
          .toDp()
      }
    }

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(enabled = canPlay, onClick = onClick)
        .padding(vertical = 12.dp, horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Icon Section
    if (isPlaying) {
      Icon(
        imageVector = Icons.Outlined.Audiotrack,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = if (canPlay) colorScheme.primary else colorScheme.onBackground.copy(alpha = 0.4f),
      )
    } else if (chapter.podcastEpisodeState == BookChapterState.FINISHED || progress >= 1f) {
      Icon(
        imageVector = Icons.Outlined.CheckCircle,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = if (canPlay) colorScheme.onSurface.copy(alpha = 0.4f) else colorScheme.onSurface.copy(alpha = 0.2f),
      )
    } else if (progress > 0f) {
      CircularProgressIndicator(
        progress = { progress },
        modifier = Modifier.size(20.dp),
        color = if (canPlay) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.4f),
        strokeWidth = 2.dp,
        trackColor = colorScheme.onSurface.copy(alpha = 0.2f),
      )
    } else {
      Spacer(modifier = Modifier.size(20.dp))
    }

    Spacer(modifier = Modifier.width(16.dp))

    // Title
    Text(
      text = chapter.title,
      style = typography.titleSmall,
      color = if (canPlay) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.4f),
      fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal,
      modifier = Modifier.weight(1f),
    )

    Spacer(modifier = Modifier.width(Spacing.sm))

    // Offline Icon
    if (isCached) {
      Icon(
        imageVector = ImageVector.vectorResource(id = R.drawable.available_offline_filled),
        contentDescription = "Available offline",
        modifier = Modifier.size(Spacing.md),
        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
      )
      Spacer(modifier = Modifier.width(Spacing.sm))
    } else {
      // maintain alignment
      Spacer(modifier = Modifier.width(Spacing.md))
      Spacer(modifier = Modifier.width(Spacing.sm))
    }

    // Duration
    Text(
      text = chapter.duration.toInt().formatTime(forceLeadingHours),
      style = typography.bodySmall,
      modifier = Modifier.width(durationColumnWidth),
      textAlign = TextAlign.End,
      fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal,
      maxLines = 1,
      color = if (canPlay) colorScheme.onSurfaceVariant else colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
    )
  }
}
