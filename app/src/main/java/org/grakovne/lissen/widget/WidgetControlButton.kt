package org.grakovne.lissen.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
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
    onClick: Action,
    modifier: GlanceModifier,
    size: Dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Image(
            provider = icon,
            contentDescription = null,
            colorFilter = ColorFilter.tint(contentColor),
            modifier = GlanceModifier
                .size(size)
                .clickable(onClick)
        )
    }
}
