package org.grakovne.lissen.ui.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.playback.service.PlaybackService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun prepareAndPlay(itemId: DetailedItem) = withContext(Dispatchers.Main) {
        val ready = CompletableDeferred<Unit>()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == PlaybackService.PLAYBACK_READY) {
                    LocalBroadcastManager.getInstance(context).unregisterReceiver(this)
                    ready.complete(Unit)
                }
            }
        }

        LocalBroadcastManager.getInstance(context).registerReceiver(
            receiver,
            IntentFilter(PlaybackService.PLAYBACK_READY)
        )

        val setIntent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_SET_PLAYBACK
            putExtra(PlaybackService.BOOK_EXTRA, itemId)
        }
        context.startForegroundService(setIntent)

        ready.await()

        val playIntent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_PLAY
        }
        context.startForegroundService(playIntent)
    }
}