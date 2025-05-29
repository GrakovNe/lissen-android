package org.grakovne.lissen.ui.screens.settings.composable

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.common.LibraryOrderingDirection
import org.grakovne.lissen.common.LibraryOrderingDirection.ASCENDING
import org.grakovne.lissen.common.LibraryOrderingDirection.DESCENDING
import org.grakovne.lissen.common.LibraryOrderingOption
import org.grakovne.lissen.viewmodel.SettingsViewModel

@Composable
fun LibraryOrderingSettingsComposable(viewModel: SettingsViewModel) {
  val context = LocalContext.current
  var libraryOrderingExpanded by remember { mutableStateOf(false) }

  val configuration by viewModel
    .preferredLibraryOrdering
    .observeAsState(LibraryOrderingConfiguration.default)

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { libraryOrderingExpanded = true }
        .padding(horizontal = 24.dp, vertical = 12.dp),
  ) {
    Column(
      modifier = Modifier.weight(1f),
    ) {
      Text(
        text = stringResource(R.string.settings_screen_library_ordering_title),
        style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(bottom = 4.dp),
      )
      Text(
        text = configuration.option.toItem(context).name,
        style = typography.bodyMedium,
        color = colorScheme.onSurfaceVariant,
      )
    }
  }

  if (libraryOrderingExpanded) {
    CommonSettingsItemComposable(
      items =
        listOf(
          LibraryOrderingOption.TITLE.toItem(context),
          LibraryOrderingOption.AUTHOR.toItem(context),
          LibraryOrderingOption.CREATED_AT.toItem(context),
          LibraryOrderingOption.UPDATED_AT.toItem(context),
        ),
      selectedItem = configuration.option.toItem(context),
      onDismissRequest = { libraryOrderingExpanded = false },
      onItemSelected = { item ->
        LibraryOrderingOption
          .entries
          .find { it.name == item.id }
          ?.let {
            viewModel
              .preferLibraryOrdering(
                LibraryOrderingConfiguration(
                  option = it,
                  direction =
                    provideOrderingDirection(
                      currentConfiguration = configuration,
                      selectedOption = it,
                    ),
                ),
              )
          }
      },
      selectedImage = provideSelectedImage(configuration),
    )
  }
}

private fun provideOrderingDirection(
  currentConfiguration: LibraryOrderingConfiguration,
  selectedOption: LibraryOrderingOption,
): LibraryOrderingDirection {
  if (currentConfiguration.option != selectedOption) {
    return ASCENDING
  }

  return when (currentConfiguration.direction) {
    ASCENDING -> DESCENDING
    DESCENDING -> ASCENDING
  }
}

private fun provideSelectedImage(configuration: LibraryOrderingConfiguration) =
  when (configuration.direction) {
    ASCENDING -> Icons.Outlined.ArrowUpward
    DESCENDING -> Icons.Outlined.ArrowDownward
  }

private fun LibraryOrderingOption.toItem(context: Context): CommonSettingsItem {
  val id = this.name

  val name =
    when (this) {
      LibraryOrderingOption.TITLE -> context.getString(R.string.settings_screen_library_ordering_title_option)
      LibraryOrderingOption.AUTHOR -> context.getString(R.string.settings_screen_library_ordering_author_option)
      LibraryOrderingOption.CREATED_AT ->
        context.getString(
          R.string.settings_screen_library_ordering_creation_date_option,
        )
      LibraryOrderingOption.UPDATED_AT ->
        context.getString(
          R.string.settings_screen_library_ordering_modification_date_option,
        )
    }

  return CommonSettingsItem(id, name, null)
}
