package org.grakovne.lissen.ui.screens.settings.composable

import androidx.annotation.Keep
import androidx.compose.ui.graphics.vector.ImageVector

@Keep
data class CommonSettingsItem(
    val id: String,
    val name: String,
    val icon: ImageVector?,
)
