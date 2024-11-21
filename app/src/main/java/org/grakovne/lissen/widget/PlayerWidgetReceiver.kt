package org.grakovne.lissen.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PlayerWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = PlayerWidget()

    override fun onEnabled(context: Context?) {
        if (context == null) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(PlayerWidget::class.java)

            glanceIds
                .forEach { glanceId ->
                    updateAppWidgetState(context, glanceId) { prefs ->
                        prefs[PlayerWidget.bookId] = ""
                        prefs[PlayerWidget.encodedCover] = ""
                        prefs[PlayerWidget.title] = "Lissen"
                        prefs[PlayerWidget.chapterTitle] = "Click to open App"
                        prefs[PlayerWidget.isPlaying] = false
                    }
                    PlayerWidget().update(context, glanceId)
                }
        }

        super.onEnabled(context)
    }
}
