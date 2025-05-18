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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.valentinilk.shimmer.shimmer

@Composable
fun AsyncShimmeringImage(
  imageRequest: ImageRequest,
  imageLoader: ImageLoader,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  contentScale: ContentScale,
  error: Painter,
  backdropMode: BackdropMode = BackdropMode.PLAIN,
  onLoadingStateChanged: (Boolean) -> Unit = {},
) {
  var isLoading by remember { mutableStateOf(true) }
  onLoadingStateChanged(isLoading)

  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center,
  ) {
    if (backdropMode == BackdropMode.BLUR && isLoading.not()) {
      AsyncImage(
        model = imageRequest,
        imageLoader = imageLoader,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
          Modifier
            .matchParentSize()
            .blur(32.dp),
        error = error,
      )
    }

    if (isLoading) {
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .background(Color.Gray)
            .shimmer(),
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

enum class BackdropMode { PLAIN, BLUR }
