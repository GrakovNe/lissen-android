package org.grakovne.lissen.ui.screens.library.composables.fallback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.common.NetworkService
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.viewmodel.CachingModelView
import org.grakovne.lissen.viewmodel.LibraryViewModel

@Composable
fun LibraryFallbackComposable(
  searchRequested: Boolean,
  contentCachingModelView: CachingModelView,
  libraryViewModel: LibraryViewModel,
  networkService: NetworkService,
) {
  val configuration = LocalConfiguration.current
  val screenHeight = configuration.screenHeightDp.dp

  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        .height(screenHeight / 2),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      val hasNetwork = networkService.isNetworkAvailable()
      val isLocalCache = contentCachingModelView.localCacheUsing()

      val text =
        when {
          searchRequested -> null
          isLocalCache ->
            when (libraryViewModel.fetchPreferredLibraryType()) {
              LibraryType.PODCAST -> stringResource(R.string.the_offline_podcasts_is_empty)
              LibraryType.LIBRARY -> stringResource(R.string.the_offline_library_is_empty)
              else -> null
            }
          hasNetwork.not() -> stringResource(R.string.no_internet_connection)
          else -> stringResource(R.string.the_library_is_empty)
        }

      val icon =
        when {
          searchRequested -> null
          isLocalCache -> Icons.AutoMirrored.Filled.LibraryBooks
          hasNetwork.not() -> Icons.Filled.WifiOff
          else -> Icons.AutoMirrored.Filled.LibraryBooks
        }

      icon?.let {
        Box(
          modifier =
            Modifier
              .size(120.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surfaceContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = it,
            contentDescription = "Library placeholder",
            tint = Color.White,
            modifier = Modifier.size(64.dp),
          )
        }
      }

      text?.let {
        Text(
          textAlign = TextAlign.Center,
          text = it,
          style = MaterialTheme.typography.headlineSmall,
          modifier = Modifier.padding(top = 36.dp),
        )
      }
    }
  }
}
