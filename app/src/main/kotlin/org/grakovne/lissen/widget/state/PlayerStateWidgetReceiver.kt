package org.grakovne.lissen.widget.state

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class PlayerStateWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = PlayerStateWidget()
}
