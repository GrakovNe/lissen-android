package org.grakovne.lissen.ui.screens.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.common.withHaptic

@Composable
fun ChaptersCountStepper(
  count: Int,
  onCountChanged: (Int) -> Unit,
  modifier: Modifier = Modifier,
  minCount: Int = 1,
  maxCount: Int = Int.MAX_VALUE,
  enabled: Boolean = true,
  numberColor: Color = Color.Unspecified,
) {
  val view = LocalView.current
  val stepperColors = IconButtonDefaults.iconButtonColors(contentColor = colorScheme.primary)

  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.End,
  ) {
    IconButton(
      modifier = Modifier.size(32.dp),
      colors = stepperColors,
      enabled = enabled && count > minCount,
      onClick = {
        withHaptic(view) {
          onCountChanged((count - 1).coerceIn(minCount, maxCount))
        }
      },
    ) {
      Icon(
        imageVector = Icons.Rounded.Remove,
        contentDescription = stringResource(R.string.downloads_menu_download_option_decrease),
        modifier = Modifier.size(18.dp),
      )
    }

    Text(
      text = count.toString(),
      style = typography.titleMedium,
      color = numberColor,
      textAlign = TextAlign.Center,
      modifier = Modifier.widthIn(min = 40.dp),
    )

    IconButton(
      modifier = Modifier.size(32.dp),
      colors = stepperColors,
      enabled = enabled && count < maxCount,
      onClick = {
        withHaptic(view) {
          onCountChanged((count + 1).coerceIn(minCount, maxCount))
        }
      },
    ) {
      Icon(
        imageVector = Icons.Rounded.Add,
        contentDescription = stringResource(R.string.downloads_menu_download_option_increase),
        modifier = Modifier.size(18.dp),
      )
    }
  }
}
