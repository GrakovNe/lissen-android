package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun SearchActionComposable(
    onSearchDismissed: () -> Unit,
    onSearchRequested: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val searchText = remember { mutableStateOf("") }

    fun updateSearchText(text: String) {
        searchText.value = text
        onSearchRequested(searchText.value)
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(40.dp)
    ) {
        IconButton(
            modifier = Modifier.height(36.dp),
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
                .height(36.dp)
                .background(colorScheme.surfaceContainer, RoundedCornerShape(36.dp))
                .padding(start = 16.dp, end = 4.dp)
        ) {
            BasicTextField(
                value = searchText.value,
                onValueChange = { updateSearchText(it) },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                textStyle = typography.bodyLarge,
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Search
                ),
                decorationBox = { innerTextField ->
                    if (searchText.value.isEmpty()) {
                        Text(
                            "Search",
                            color = Color.Gray,
                            style = typography.bodyLarge
                        )
                    }
                    innerTextField()
                }
            )

            if (searchText.value.isNotEmpty()) {
                IconButton(
                    modifier = Modifier.height(36.dp),
                    onClick = { updateSearchText("") }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Clear,
                        contentDescription = "Clear"
                    )
                }
            }
        }
    }
}
