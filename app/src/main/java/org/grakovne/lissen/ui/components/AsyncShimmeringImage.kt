package org.grakovne.lissen.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
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
  val painter =
    rememberAsyncImagePainter(
      model = imageRequest,
      imageLoader = imageLoader,
      error = error,
      contentScale = contentScale,
    )

  val isLoading = painter.state is AsyncImagePainter.State.Loading
  onLoadingStateChanged(isLoading)

  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center,
  ) {
    if (backdropMode == BackdropMode.BLUR) {
      Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
          Modifier
            .fillMaxSize()
            .blur(32.dp),
      )
    }

    if (isLoading) {
      Box(
        Modifier
          .fillMaxSize()
          .background(Color.Gray)
          .shimmer(),
      )
    }

    Image(
      painter = painter,
      contentDescription = contentDescription,
      contentScale = contentScale,
      modifier = Modifier.fillMaxSize(),
    )
  }
}

enum class BackdropMode { PLAIN, BLUR }
