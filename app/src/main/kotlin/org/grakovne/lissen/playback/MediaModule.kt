@file:Suppress("DEPRECATION")

package org.grakovne.lissen.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_MEDIA_NEXT
import android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.grakovne.lissen.lib.domain.SeekTimeOption
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.ui.activity.AppActivity
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {
  @OptIn(UnstableApi::class)
  @Provides
  @Singleton
  fun provideMediaCache(
    @ApplicationContext context: Context,
  ): Cache =
    SimpleCache(
      File(context.cacheDir, "playback_cache"),
      LeastRecentlyUsedCacheEvictor(buildPlaybackCacheLimit(context)),
      StandaloneDatabaseProvider(context),
    )

  @OptIn(UnstableApi::class)
  @Provides
  @Singleton
  fun provideExoPlayer(
    @ApplicationContext context: Context,
  ): ExoPlayer {
    val player =
      ExoPlayer
        .Builder(context)
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(30_000)
        .setHandleAudioBecomingNoisy(true)
        .setAudioAttributes(
          AudioAttributes
            .Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build(),
          true,
        ).build()

    return player
  }

  @OptIn(UnstableApi::class)
  @Provides
  @Singleton
  fun provideMediaSession(
    @ApplicationContext context: Context,
    preferences: LissenSharedPreferences,
    mediaRepository: MediaRepository,
    exoPlayer: ExoPlayer,
  ): MediaSession {
    val sessionActivityPendingIntent =
      PendingIntent.getActivity(
        context,
        0,
        Intent(context, AppActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )

    return MediaSession
      .Builder(context, exoPlayer)
      .setCallback(
        object : MediaSession.Callback {
          override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent,
          ): Boolean {
            Log.d(TAG, "Executing media button event from: $controllerInfo")

            val keyEvent =
              intent
                .getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                ?: return super.onMediaButtonEvent(session, controllerInfo, intent)

            Log.d(TAG, "Got media key event: $keyEvent")

            if (keyEvent.action != KeyEvent.ACTION_DOWN) {
              return super.onMediaButtonEvent(session, controllerInfo, intent)
            }

            when (keyEvent.keyCode) {
              KEYCODE_MEDIA_NEXT -> {
                mediaRepository.forward()
                return true
              }

              KEYCODE_MEDIA_PREVIOUS -> {
                mediaRepository.rewind()
                return true
              }

              else -> return super.onMediaButtonEvent(session, controllerInfo, intent)
            }
          }

          @OptIn(UnstableApi::class)
          override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
          ): MediaSession.ConnectionResult {
            val rewindCommand = SessionCommand(REWIND_COMMAND, Bundle.EMPTY)
            val forwardCommand = SessionCommand(FORWARD_COMMAND, Bundle.EMPTY)
            val seekTime = preferences.getSeekTime()

            val sessionCommands =
              MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(rewindCommand)
                .add(forwardCommand)
                .build()

            val rewindButton =
              CommandButton
                .Builder(provideRewindCommand(seekTime.rewind))
                .setSessionCommand(rewindCommand)
                .setDisplayName("Rewind")
                .setEnabled(true)
                .build()

            val forwardButton =
              CommandButton
                .Builder(provideForwardCommand(seekTime.forward))
                .setSessionCommand(forwardCommand)
                .setDisplayName("Forward")
                .setEnabled(true)
                .build()

            return MediaSession
              .ConnectionResult
              .AcceptedResultBuilder(session)
              .setAvailableSessionCommands(sessionCommands)
              .setCustomLayout(listOf(rewindButton, forwardButton))
              .build()
          }

          override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
          ): ListenableFuture<SessionResult> {
            Log.d(TAG, "Executing: ${customCommand.customAction}")

            when (customCommand.customAction) {
              FORWARD_COMMAND -> mediaRepository.forward()
              REWIND_COMMAND -> mediaRepository.rewind()
            }

            return super.onCustomCommand(session, controller, customCommand, args)
          }
        },
      ).setSessionActivity(sessionActivityPendingIntent)
      .build()
  }

  private fun buildPlaybackCacheLimit(ctx: Context): Long {
    val stat = android.os.StatFs(ctx.cacheDir.path)
    val available = stat.availableBytes
    val dynamicCap = (available - KEEP_FREE_BYTES).coerceAtLeast(MIN_CACHE_BYTES)

    return minOf(MAX_CACHE_BYTES, dynamicCap)
  }

  private fun provideRewindCommand(seekTime: SeekTimeOption) = CommandButton.ICON_SKIP_BACK

  private fun provideForwardCommand(seekTime: SeekTimeOption) = CommandButton.ICON_SKIP_FORWARD

  private const val REWIND_COMMAND = "notification_rewind"
  private const val FORWARD_COMMAND = "notification_forward"

  private const val TAG = "MediaModule"

  private const val MAX_CACHE_BYTES = 512L * 1024 * 1024
  private const val KEEP_FREE_BYTES = 20L * 1024 * 1024
  private const val MIN_CACHE_BYTES = 10L * 1024 * 1024
}
