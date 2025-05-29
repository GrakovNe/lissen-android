package org.grakovne.lissen.ui.screens.library.composables.placeholder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.shimmer
import org.grakovne.lissen.viewmodel.LibraryViewModel

@Composable
fun RecentBooksPlaceholderComposable(
  itemCount: Int = 5,
  libraryViewModel: LibraryViewModel,
) {
  val configuration = LocalConfiguration.current
  val screenWidth = remember { configuration.screenWidthDp.dp }

  val itemsVisible = 2.3f
  val spacing = 16.dp
  val totalSpacing = spacing * (itemsVisible + 1)
  val itemWidth = (screenWidth - totalSpacing) / itemsVisible

  LazyRow(
    contentPadding = PaddingValues(horizontal = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    items(itemCount) {
      RecentBookItemComposable(
        width = itemWidth,
        libraryViewModel = libraryViewModel,
      )
    }
  }
}

@Composable
fun RecentBookItemComposable(
  width: Dp,
  libraryViewModel: LibraryViewModel,
) {
  Column(
    modifier =
      Modifier
        .width(width),
  ) {
    Spacer(
      modifier =
        Modifier
          .fillMaxWidth()
          .aspectRatio(1f)
          .clip(RoundedCornerShape(8.dp))
          .shimmer()
          .background(Color.Gray),
    )

    Spacer(modifier = Modifier.height(14.dp))

    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
      Text(
        color = Color.Transparent,
        text = "Crime and Punishment. Novel",
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
        maxLines = 1,
        modifier =
          Modifier
            .clip(RoundedCornerShape(4.dp))
            .shimmer()
            .background(Color.Gray),
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        color = Color.Transparent,
        text = "Fyodor Dostoevsky",
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        modifier =
          Modifier
            .clip(RoundedCornerShape(4.dp))
            .shimmer()
            .background(Color.Gray),
      )
    }
  }
}
