package org.grakovne.lissen.wear.ui.screens.chapters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.tooling.preview.devices.WearDevices
import com.google.android.horologist.compose.layout.AppScaffold
import com.google.android.horologist.compose.layout.ColumnItemType
import com.google.android.horologist.compose.layout.rememberResponsiveColumnPadding
import org.grakovne.lissen.lib.domain.PlayingChapter
import org.grakovne.lissen.lib.ui.extensions.formatDuration
import org.grakovne.lissen.wear.presentation.theme.LissenTheme
import org.grakovne.lissen.wear.ui.screens.player.PlayerViewModel

@Composable
fun ChaptersScreen(
  onChapterSelect: () -> Unit = {}
) {
  val playerViewModel: PlayerViewModel = hiltViewModel()
  val playingBook by playerViewModel.playingBook.observeAsState()

  ChaptersScreen(
    chapters = playingBook?.chapters ?: emptyList(),
    onChapterSelect = onChapterSelect
  )
}

@Composable
fun ChaptersScreen(
  chapters: List<PlayingChapter>,
  onChapterSelect: () -> Unit = {}
) {
  ScreenScaffold {
    val columnState = rememberTransformingLazyColumnState()
    val contentPadding = rememberResponsiveColumnPadding(
      first = ColumnItemType.ListHeader,
      last = ColumnItemType.Button
    )
    val transformationSpec = rememberTransformationSpec()

    if (chapters.isNotEmpty()) {
      TransformingLazyColumn(
        state = columnState,
        contentPadding = contentPadding
      ) {
        item {
          ListHeader(
            modifier = Modifier
              .fillMaxWidth()
              .transformedHeight(this, transformationSpec),
            transformation = SurfaceTransformation(transformationSpec)
          ) {
            Text("Chapters", maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
        items(count = chapters.size, key = { "chapter_$it" }) {
          val chapter = chapters[it]
          ChapterListItem(
            modifier = Modifier
              .fillMaxWidth()
              .transformedHeight(this, transformationSpec),
            transformation = SurfaceTransformation(transformationSpec),
            chapter = chapter,
            onClick = {
              onChapterSelect()
            }
          )
        }
      }
    } else {
      Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Text("no book selected")
      }
    }
  }
}

@Composable
fun ChapterListItem(
  chapter: PlayingChapter,
  modifier: Modifier = Modifier,
  transformation: SurfaceTransformation? = null,
  onClick: () -> Unit
) {
  Button(
    modifier = modifier,
    transformation = transformation,
    colors = ButtonDefaults.filledTonalButtonColors(),
    onClick = onClick
  ) {
    Column {
      Text(chapter.title, color = MaterialTheme.colorScheme.onSurface)
      Text(
        chapter.duration.toInt().formatDuration(),
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
  LissenTheme {
    AppScaffold {
      ChaptersScreen()
    }
  }
}
