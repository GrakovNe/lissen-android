package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import org.grakovne.lissen.R
import org.grakovne.lissen.lib.domain.RecentBook
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.viewmodel.LibraryViewModel

@Composable
fun ContinueSeriesComposable(
  navController: AppNavigationService,
  continueSeriesBooks: List<RecentBook>,
  imageLoader: ImageLoader,
  modifier: Modifier = Modifier,
  libraryViewModel: LibraryViewModel,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
  ) {
    Text(
      text = stringResource(R.string.library_screen_continue_series_title),
      style = typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
      modifier = Modifier.padding(horizontal = 20.dp),
    )

    Spacer(modifier = Modifier.height(12.dp))

    val configuration = LocalConfiguration.current
    val screenWidth = remember { configuration.screenWidthDp.dp }

    val itemsVisible = 2.3f
    val spacing = 16.dp
    val totalSpacing = spacing * (itemsVisible + 1)
    val itemWidth = (screenWidth - totalSpacing) / itemsVisible

    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      continueSeriesBooks
        .forEach { book ->
          RecentBookItemComposable(
            book = book,
            width = itemWidth,
            imageLoader = imageLoader,
            navController = navController,
            libraryViewModel = libraryViewModel,
          )
        }
    }
  }
}
