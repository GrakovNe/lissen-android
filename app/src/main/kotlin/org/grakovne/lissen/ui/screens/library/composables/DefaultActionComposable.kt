package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.ui.icons.Search

@Composable
fun DefaultActionComposable(
  onSearchRequested: () -> Unit,
  onPreferencesRequested: () -> Unit,
) {
  Row {
    IconButton(
      modifier = Modifier.offset(x = 4.dp),
      onClick = { onSearchRequested() },
    ) {
      Icon(
        imageVector = Search,
        contentDescription = null,
      )
    }
    IconButton(onClick = { onPreferencesRequested() }) {
      Icon(
        imageVector = Icons.Outlined.MoreVert,
        contentDescription = "Menu",
      )
    }
  }
}
