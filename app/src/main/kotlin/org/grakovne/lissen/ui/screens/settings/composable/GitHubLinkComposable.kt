package org.grakovne.lissen.ui.screens.settings.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import timber.log.Timber

@Composable
fun GitHubLinkComposable() {
  val uriHandler = LocalUriHandler.current

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable {
          try {
            uriHandler.openUri("https://github.com/GrakovNe/lissen-android")
          } catch (ex: Exception) {
            Timber.d("Unable to open Github Link due to ${ex.message}")
          }
        }.padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(
      modifier = Modifier.weight(1f),
    ) {
      Text(
        text = stringResource(R.string.source_code_on_github_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
      )
      Text(
        text = stringResource(R.string.source_code_on_github_subtitle),
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.padding(bottom = 4.dp),
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
