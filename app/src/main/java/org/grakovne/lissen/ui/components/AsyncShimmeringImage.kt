package org.grakovne.lissen.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.valentinilk.shimmer.shimmer
import org.grakovne.lissen.common.HokoBlurTransformation

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
  var isLoadingBlur by remember { mutableStateOf(true) }
  var isLoadingOriginal by remember { mutableStateOf(true) }
  
  val isLoading = isLoadingBlur || isLoadingOriginal
  onLoadingStateChanged(isLoading)
  
  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center,
  ) {
    if (isLoading) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .shimmer()
          .background(Color.Gray),
      )
    } else {
      // Отрисовка блюреного фона
      AsyncImage(
        model = imageRequest.newBuilder()
          .transformations(HokoBlurTransformation(LocalContext.current))
          .build(),
        imageLoader = imageLoader,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
        error = error,
      )
      
      // Отрисовка оригинала поверх
      AsyncImage(
        model = imageRequest,
        imageLoader = imageLoader,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = Modifier.fillMaxSize(),
        error = error,
      )
    }
  }
}
