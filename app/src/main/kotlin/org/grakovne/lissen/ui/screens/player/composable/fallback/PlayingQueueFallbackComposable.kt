package org.grakovne.lissen.ui.screens.player.composable.fallback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.grakovne.lissen.R
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.viewmodel.LibraryViewModel

@Composable
fun PlayingQueueFallbackComposable(
  modifier: Modifier = Modifier,
  libraryViewModel: LibraryViewModel,
) {
  Column(
    modifier =
      modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      textAlign = TextAlign.Center,
      text =
        when (libraryViewModel.fetchPreferredLibraryType()) {
          LibraryType.LIBRARY -> stringResource(R.string.chapters_list_empty)
          LibraryType.PODCAST -> stringResource(R.string.episodes_list_empty)
          LibraryType.UNKNOWN -> stringResource(R.string.items_list_empty)
        },
      style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp),
    )
  }
}
