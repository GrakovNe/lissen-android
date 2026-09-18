package org.grakovne.lissen.ui.screens.player.composable.placeholder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.shimmer
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.ui.screens.player.composable.PlayingQueueHeaderComposable
import org.grakovne.lissen.ui.screens.player.composable.common.provideNowPlayingTitle

@Composable
fun PlayingQueuePlaceholderComposable(
  libraryType: LibraryType,
  modifier: Modifier = Modifier,
  expandable: Boolean = true,
) {
  val context = LocalContext.current

  Column(modifier = modifier.padding(horizontal = 16.dp)) {
    PlayingQueueHeaderComposable(
      title = provideNowPlayingTitle(libraryType, context),
      textStyle = typography.titleMedium.copy(fontSize = typography.titleMedium.fontSize * 1.25f, fontWeight = FontWeight.SemiBold),
      expandable = expandable,
      onExpand = {},
    )

    Spacer(modifier = Modifier.height(12.dp))

    LazyColumn(
      modifier =
        Modifier
          .fillMaxWidth(),
    ) {
      items(10) {
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .height(36.dp)
              .clip(RoundedCornerShape(8.dp))
              .shimmer()
              .background(Color.Gray),
        )

        Spacer(Modifier.height(8.dp))

        HorizontalDivider(
          thickness = 1.dp,
          modifier = Modifier.padding(horizontal = 4.dp),
        )

        Spacer(Modifier.height(8.dp))
      }
    }
  }
}
