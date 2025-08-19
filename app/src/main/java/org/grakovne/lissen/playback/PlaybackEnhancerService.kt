package org.grakovne.lissen.playback

import android.media.audiofx.LoudnessEnhancer
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import org.grakovne.lissen.common.RunningComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackEnhancerService @OptIn(UnstableApi::class)
@Inject constructor(
  private val player: ExoPlayer
): RunningComponent {
  private var enhancer: LoudnessEnhancer? = null
  
  @OptIn(UnstableApi::class)
  override fun onCreate() {
    player.addListener(
      object : Player.Listener {
        override fun onAudioSessionIdChanged(id: Int) {
          enhancer?.release()
          if (id != C.AUDIO_SESSION_ID_UNSET) {
            enhancer = LoudnessEnhancer(id).apply {
              enabled = true
              setTargetGain((1200))
            }
          }
        }
      }
    )
    
    enableEnhance()
  }
  
  fun disableEnhance() {
    enhancer?.setTargetGain(0)
  }
  
  fun enableEnhance() {
    enhancer?.setTargetGain(1200)
  }
}
