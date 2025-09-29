package org.grakovne.lissen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.valentinilk.shimmer.shimmer

@Composable
fun AsyncShimmeringImage(
  imageRequest: ImageRequest,
  imageLoader: ImageLoader,
  contentDescription: String,
  contentScale: ContentScale,
  modifier: Modifier = Modifier,
  error: Painter,
  onLoadingStateChanged: (Boolean) -> Unit = {},
) {
  var isLoading by remember { mutableStateOf(true) }
  onLoadingStateChanged(isLoading)

  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center,
  ) {
    if (isLoading) {
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .shimmer()
            .background(Color.Gray),
      )
    }

    AsyncImage(
      model = imageRequest,
      imageLoader = imageLoader,
      contentDescription = contentDescription,
      contentScale = contentScale,
      modifier = Modifier.fillMaxSize(),
      onSuccess = {
        isLoading = false
        onLoadingStateChanged(false)
      },
      onError = {
        isLoading = false
        onLoadingStateChanged(false)
      },
      error = error,
    )
  }
}
