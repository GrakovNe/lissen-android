package org.grakovne.lissen.ui.screens.settings.composable

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import org.grakovne.lissen.R

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsTopAppBar(
  title: String,
  onBack: () -> Unit,
) {
  TopAppBar(
    title = {
      Text(
        text = title,
        style = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.semantics { heading() },
        color = colorScheme.onSurface,
      )
    },
    navigationIcon = {
      IconButton(onClick = onBack) {
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
          contentDescription = stringResource(R.string.a11y_back),
          tint = colorScheme.onSurface,
        )
      }
    },
  )
}
