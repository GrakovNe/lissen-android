package org.grakovne.lissen.widget.cover

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class PlayerCoverWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = PlayerCoverWidget()
}
