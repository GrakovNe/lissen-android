package org.grakovne.lissen.ui.screens.settings.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.grakovne.lissen.BuildConfig
import org.grakovne.lissen.R

@Composable
fun LicenseFooterComposable() {
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(16.dp),
  ) {
    HorizontalDivider(
      modifier = Modifier.padding(horizontal = 12.dp),
      color = colorScheme.onSurface.copy(alpha = 0.2f),
    )

    Text(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(top = 16.dp),
      text =
        stringResource(
          R.string.settings_screen_footer_app_name_pattern,
          stringResource(R.string.branding_name),
          BuildConfig.VERSION_NAME,
        ),
      style =
        TextStyle(
          fontFamily = FontFamily.Monospace,
          textAlign = TextAlign.Center,
          fontSize = 12.sp,
          color = colorScheme.onSurface.copy(alpha = 0.6f),
        ),
    )

    Text(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(top = 4.dp),
      text =
        "${stringResource(R.string.settings_screen_footer_copyright_original)} • ${
          stringResource(
            R.string.settings_screen_footer_copyright_fork,
          )
        } • ${stringResource(R.string.settings_screen_footer_license)}",
      style =
        TextStyle(
          fontFamily = FontFamily.Monospace,
          textAlign = TextAlign.Center,
          fontSize = 10.sp,
          color = colorScheme.onSurface.copy(alpha = 0.4f),
        ),
    )
  }
}
