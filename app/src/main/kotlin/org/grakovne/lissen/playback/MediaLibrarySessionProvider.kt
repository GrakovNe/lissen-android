package org.grakovne.lissen.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import dagger.hilt.android.qualifiers.ApplicationContext
import org.grakovne.lissen.BuildConfig
import org.grakovne.lissen.ui.activity.AppActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaLibrarySessionProvider
  @OptIn(UnstableApi::class)
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
    private val exoPlayer: ExoPlayer,
    private val callback: MediaLibrarySessionCallback,
  ) {
    @OptIn(UnstableApi::class)
    fun provideMediaLibrarySession(mediaLibraryService: MediaLibraryService): MediaLibraryService.MediaLibrarySession {
      val knownPackages =
        listOf(
          // by https://github.com/PaulWoitaschek/Voice/blob/main/core/playback/src/main/kotlin/voice/core/playback/session/ImageFileProvider.kt
          "com.android.systemui",
          "com.google.android.autosimulator",
          "com.google.android.carassistant",
          "com.google.android.googlequicksearchbox",
          "com.google.android.projection.gearhead",
          "com.google.android.wearable.app",
          "androidx.media3.testapp.controller", // Media3 controller test app
        )
      for (pkg in knownPackages) {
        context.grantUriPermission(
          pkg,
          "content://${BuildConfig.APPLICATION_ID}.cover/".toUri(),
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
        )
      }
      return MediaLibraryService.MediaLibrarySession
        .Builder(mediaLibraryService, exoPlayer, callback)
        .setSessionActivity(
          PendingIntent.getActivity(
            context,
            0,
            Intent(context, AppActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
          ),
        ).build()
    }
  }
