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
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
  val view = LocalView.current

  ModalBottomSheet(
    containerColor = colorScheme.background,
    onDismissRequest = onDismissRequest,
    content = {
      Column(
        modifier =
          Modifier
            .testTag("bookActionsSheet")
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = book.title,
          style = typography.bodyLarge,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(8.dp))

        when (isFinished) {
          false -> {
            BookActionRow(
              title = stringResource(R.string.library_book_action_mark_as_completed),
              icon = Icons.Outlined.Archive,
              onClick = { withHaptic(view) { onMarkAsCompleted() } },
            )
          }

          true -> {
            BookActionRow(
              title = stringResource(R.string.library_book_action_mark_as_not_completed),
              icon = Icons.Outlined.Unarchive,
              onClick = { withHaptic(view) { onMarkAsNotCompleted() } },
            )
          }

          null -> {
            Unit
          }
        }
      }
    },
  )
}

@Composable
private fun BookActionRow(
  title: String,
  icon: ImageVector,
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { onClick() }
        .padding(vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title,
      style = typography.bodyMedium,
      color = colorScheme.onBackground,
      modifier = Modifier.weight(1f),
    )
    Spacer(modifier = Modifier.width(12.dp))
    Icon(
      imageVector = icon,
      contentDescription = null,
      modifier = Modifier.size(20.dp),
      tint = colorScheme.onBackground,
    )
  }
}
