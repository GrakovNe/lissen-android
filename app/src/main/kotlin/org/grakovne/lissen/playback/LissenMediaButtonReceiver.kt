package org.grakovne.lissen.playback

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver
import dagger.hilt.android.AndroidEntryPoint
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
open class LissenMediaButtonReceiver : MediaButtonReceiver() {
  @Inject
  lateinit var preferences: PlaybackPreferences

  override fun shouldStartForegroundService(
    context: Context,
    intent: Intent,
  ): Boolean = preferences.getPlayingItem()?.canProducePlaybackQueue() == true
}
