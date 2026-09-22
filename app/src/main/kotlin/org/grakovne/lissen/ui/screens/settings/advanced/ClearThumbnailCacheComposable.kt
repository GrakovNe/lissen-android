package org.grakovne.lissen.ui.screens.settings.advanced

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.grakovne.lissen.R
import org.grakovne.lissen.ui.screens.settings.composable.ConfirmationBottomSheetComposable
import org.grakovne.lissen.viewmodel.CachingModelView

@Composable
fun ClearThumbnailCacheComposable(cachingModelView: CachingModelView) {
  var showConfirmation by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { showConfirmation = true }
        .padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.settings_screen_clear_thumbnail_cache_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        color = colorScheme.onBackground,
      )
      Text(
        text = stringResource(R.string.settings_screen_clear_thumbnail_cache_hint),
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
      )
    }
  }

  if (showConfirmation) {
    ConfirmationBottomSheetComposable(
      message = stringResource(R.string.clear_thumbnail_cache_confirmation_message),
      confirmLabel = stringResource(R.string.clear_thumbnail_cache_confirm),
      onConfirm = {
        showConfirmation = false
        scope.launch {
          cachingModelView.clearShortTermCache()
          showClearedToast(context)
        }
      },
      onDismissRequest = { showConfirmation = false },
    )
  }
}

private fun showClearedToast(context: Context) {
  Toast
    .makeText(
      context,
      context.getString(R.string.settings_screen_clear_thumbnail_cache_success_toast),
      Toast.LENGTH_SHORT,
    ).show()
}
