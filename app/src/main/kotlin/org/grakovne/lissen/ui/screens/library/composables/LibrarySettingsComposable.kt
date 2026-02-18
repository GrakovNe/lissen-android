package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.grakovne.lissen.R
import org.grakovne.lissen.viewmodel.CachingModelView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySettingsComposable(
  cachingModelView: CachingModelView = hiltViewModel(),
  onDismissRequest: () -> Unit,
  onForceLocalToggled: () -> Unit,
) {
  val forceCache by cachingModelView.forceCache.collectAsState(false)
  val context = LocalContext.current

  ModalBottomSheet(
    containerColor = colorScheme.background,
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
          item {
            LibrarySettingsComposableItem(
              title = context.getString(R.string.enable_offline),
              icon = ImageVector.vectorResource(id = R.drawable.available_offline_outline),
              state = forceCache,
              onStateChange = { onForceLocalToggled() },
            )
          }
        }
      }
    },
  )
}

@Composable
fun LibrarySettingsComposableItem(
  title: String,
  icon: ImageVector,
  state: Boolean,
  onStateChange: (Boolean) -> Unit,
) {
  ListItem(
    modifier = Modifier,
    headlineContent = { Text(text = title) },
    leadingContent = {
      Icon(
        imageVector = icon,
        contentDescription = null,
      )
    },
    trailingContent = {
      Switch(
        checked = state,
        onCheckedChange = onStateChange,
        enabled = true,
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
}
