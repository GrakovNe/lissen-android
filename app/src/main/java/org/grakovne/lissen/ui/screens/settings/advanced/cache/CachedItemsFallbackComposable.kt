package org.grakovne.lissen.ui.screens.settings.advanced.cache

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

@Composable
fun CachedItemsFallbackComposable() {
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
      Box(
        modifier =
          Modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
          contentDescription = "Library placeholder",
          tint = Color.White,
          modifier = Modifier.size(64.dp),
        )
      }

      Text(
        textAlign = TextAlign.Center,
        text = stringResource(R.string.offline_cache_is_empty),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(top = 36.dp),
      )
    }
  }
}
