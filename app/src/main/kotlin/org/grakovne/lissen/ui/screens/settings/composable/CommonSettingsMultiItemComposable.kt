package org.grakovne.lissen.ui.screens.settings.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonSettingsMultiItemComposable(
  items: List<Pair<CommonSettingsItem, Boolean>>,
  onDismissRequest: () -> Unit,
  onItemChanged: (String, Boolean) -> Unit,
) {
  ModalBottomSheet(
    containerColor = MaterialTheme.colorScheme.background,
    onDismissRequest = onDismissRequest,
    content = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .padding(horizontal = 16.dp),
      ) {
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
          itemsIndexed(items) { index, item ->
            var isChecked by remember { mutableStateOf(item.second) }

            ListItem(
              headlineContent = {
                Row { Text(item.first.name) }
              },
              trailingContent = {
                Switch(
                  checked = isChecked,
                  onCheckedChange = {
                    isChecked = it
                    onItemChanged(item.first.id, it)
                  },
                  colors =
                    SwitchDefaults.colors(
                      uncheckedTrackColor = colorScheme.background,
                      checkedBorderColor = colorScheme.onSurface,
                      checkedThumbColor = colorScheme.onSurface,
                      checkedTrackColor = colorScheme.background,
                    ),
                )
              },
            )
            if (index < items.lastIndex) {
              HorizontalDivider()
            }
          }
        }
      }
    },
  )
}
