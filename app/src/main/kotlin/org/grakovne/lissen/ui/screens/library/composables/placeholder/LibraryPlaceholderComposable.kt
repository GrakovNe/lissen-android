package org.grakovne.lissen.ui.screens.library.composables.placeholder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.shimmer

@Composable
fun LibraryPlaceholderComposable(itemCount: Int = 15) {
  Column(
    modifier = Modifier.fillMaxWidth(),
  ) {
    repeat(itemCount) { LibraryItemPlaceholderComposable() }
  }
}

@Composable
fun LibraryItemPlaceholderComposable() {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier =
        Modifier
          .size(64.dp)
          .aspectRatio(1f)
          .clip(RoundedCornerShape(4.dp))
          .shimmer()
          .background(Color.Gray),
    )

    Spacer(modifier = Modifier.width(16.dp))

    Column(modifier = Modifier.weight(1f)) {
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .shimmer()
            .background(Color.Gray),
      )

      Spacer(modifier = Modifier.height(8.dp))

      Box(
        modifier =
          Modifier
            .fillMaxWidth(0.6f)
            .height(12.dp)
            .clip(RoundedCornerShape(4.dp))
            .shimmer()
            .background(Color.Gray),
      )
    }

    Spacer(modifier = Modifier.width(16.dp))
  }
}
