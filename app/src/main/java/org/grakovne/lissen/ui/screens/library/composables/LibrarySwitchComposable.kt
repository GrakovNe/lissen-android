package org.grakovne.lissen.ui.screens.library.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun LibrarySwitchComposable(
    onclick: () -> Unit,
) {
    Spacer(modifier = Modifier.width(4.dp))

    Icon(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onclick() },
        imageVector = Icons.Outlined.ArrowDropDown,
        contentDescription = null,
    )
}
