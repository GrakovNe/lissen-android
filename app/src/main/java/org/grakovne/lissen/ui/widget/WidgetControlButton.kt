package org.grakovne.lissen.ui.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.size
import androidx.glance.unit.ColorProvider

@Composable
fun WidgetControlButton(
    icon: ImageProvider,
    contentColor: ColorProvider,
    onClick: Action
) {
    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
        Image(
            provider = icon,
            contentDescription = null,
            colorFilter = ColorFilter.tint(contentColor),
            modifier = GlanceModifier
                .size(42.dp)
                .clickable(onClick)
        )
    }

}