package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.domain.Book

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookActionsComposable(
  book: Book,
  isFinished: Boolean?,
  onDismissRequest: () -> Unit,
  onMarkAsCompleted: () -> Unit,
  onMarkAsNotCompleted: () -> Unit,
) {
  ModalBottomSheet(
    containerColor = colorScheme.surface,
    onDismissRequest = onDismissRequest,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    Column(
      modifier =
        Modifier
          .testTag("bookActionsSheet")
          .fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = book.title,
        style = typography.bodyLarge,
        color = colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
      )

      Spacer(modifier = Modifier.height(4.dp))

      when (isFinished) {
        false -> {
          BookActionRow(
            title = stringResource(R.string.library_book_action_mark_as_completed),
            icon = Icons.Outlined.Check,
            onClick = onMarkAsCompleted,
          )
        }

        true -> {
          BookActionRow(
            title = stringResource(R.string.library_book_action_mark_as_not_completed),
            icon = Icons.Outlined.RemoveDone,
            onClick = onMarkAsNotCompleted,
          )
        }

        null -> {
          Unit
        }
      }
    }
  }
}

@Composable
private fun BookActionRow(
  title: String,
  icon: ImageVector,
  onClick: () -> Unit,
) {
  val view = LocalView.current
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { withHaptic(view) { onClick() } }
        .padding(horizontal = 16.dp, vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title,
      style = typography.bodyLarge,
      color = colorScheme.onSurface,
      modifier = Modifier.weight(1f),
    )
    Spacer(modifier = Modifier.width(12.dp))
    Icon(
      imageVector = icon,
      contentDescription = null,
      modifier = Modifier.size(20.dp),
      tint = colorScheme.onSurface,
    )
  }
}
