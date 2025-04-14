package org.grakovne.lissen.ui.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import javax.inject.Inject

@AndroidEntryPoint
class PlayLastShortcutReceiver : BroadcastReceiver() {

    @Inject
    lateinit var playbackLauncher: PlaybackLauncher

    @Inject
    lateinit var preferences: LissenSharedPreferences

    override fun onReceive(context: Context, intent: Intent) {
        val playingBook = preferences.getPlayingBook() ?: return

        CoroutineScope(Dispatchers.Main).launch {
            playbackLauncher.prepareAndPlay(playingBook)
        }
    }
}
