package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.lib.domain.Book

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayingItemOptionsComposable(
  item: Book,
  onDismissRequest: () -> Unit,
  onMarkFinished: () -> Unit,
  onResetProgress: () -> Unit,
) {
  ModalBottomSheet(
    containerColor = colorScheme.background,
    onDismissRequest = onDismissRequest,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp)
          .padding(horizontal = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = item.title,
        style = typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )

      Spacer(modifier = Modifier.height(16.dp))

      ListItem(
        modifier =
          Modifier
            .fillMaxWidth()
            .clickable { onMarkFinished() },
        leadingContent = {
          Icon(
            imageVector = Icons.Outlined.DoneAll,
            contentDescription = null,
          )
        },
        headlineContent = {
          Text(
            text = stringResource(R.string.playing_item_menu_mark_as_finished),
            style = typography.bodyMedium,
          )
        },
        trailingContent = {},
      )

      HorizontalDivider()

      ListItem(
        modifier =
          Modifier
            .fillMaxWidth()
            .clickable { onResetProgress() },
        leadingContent = {
          Icon(
            imageVector = Icons.Outlined.Restore,
            contentDescription = null,
          )
        },
        headlineContent = {
          Text(
            text = stringResource(R.string.playing_item_menu_reset_progress),
            style = typography.bodyMedium,
          )
        },
        trailingContent = {},
      )
    }
  }
}
