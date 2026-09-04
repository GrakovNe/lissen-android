package org.grakovne.lissen.ui.screens.settings.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.lib.domain.ChapterSkipConfig
import org.grakovne.lissen.ui.screens.player.composable.ChapterSkipComposable
import org.grakovne.lissen.viewmodel.PlayerViewModel

@Composable
fun ChapterSkipSettingsComposable(playerViewModel: PlayerViewModel) {
  var expanded by remember { mutableStateOf(false) }
  val chapterSkipConfig by playerViewModel.chapterSkipConfig.observeAsState(ChapterSkipConfig())
  val book by playerViewModel.book.observeAsState(null)

  val isBookPlaying = book != null
  val playingBook = book

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .then(
          if (isBookPlaying) {
            Modifier.clickable { expanded = true }
          } else {
            Modifier
          },
        ).padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(
      modifier = Modifier.weight(1f),
    ) {
      Text(
        text = stringResource(R.string.chapter_skip_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
        color = if (isBookPlaying) colorScheme.onSurface else colorScheme.onSurfaceVariant,
      )
      Text(
        text =
          if (isBookPlaying) {
            stringResource(R.string.chapter_skip_settings_description)
          } else {
            stringResource(R.string.chapter_skip_no_book_playing)
          },
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
      )
      if (playingBook != null) {
        Text(
          text = playingBook.title,
          style = typography.bodyMedium,
          color = colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 2.dp),
        )
      }
    }
  }

  if (expanded) {
    ChapterSkipComposable(
      config = chapterSkipConfig,
      onConfigChanged = { playerViewModel.updateChapterSkipConfig(it) },
      onDismissRequest = { expanded = false },
    )
  }
}
