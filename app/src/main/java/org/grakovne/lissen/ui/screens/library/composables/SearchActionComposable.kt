package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SearchActionComposable(
    onSearchDismissed: () -> Unit,
) {

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(40.dp)
    ) {
        IconButton(
            onClick = { onSearchDismissed() }
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowBack,
                contentDescription = "Back"
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .background(colorScheme.surfaceContainer, RoundedCornerShape(36.dp))
                .padding(start = 16.dp, end = 4.dp)
        ) {
            val title = remember { mutableStateOf("") }

            BasicTextField(
                value = title.value,
                onValueChange = { title.value = it },
                modifier = Modifier.weight(1f),
                textStyle = typography.bodyMedium,
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (title.value.isEmpty()) {
                        Text(
                            "Search",
                            color = Color.Gray,
                            style = typography.bodyMedium
                        )
                    }
                    innerTextField()
                }
            )
            IconButton(
                onClick = { /* Clear search action */ }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Clear,
                    contentDescription = "Clear"
                )
            }
        }
    }
}
