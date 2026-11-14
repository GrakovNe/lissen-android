package org.grakovne.lissen.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.widget.Toast
import dagger.hilt.android.qualifiers.ApplicationContext
import org.grakovne.lissen.R
import javax.inject.Inject
import javax.inject.Singleton

/** Tells the listener that playback stopped because the server could not be reached. */
@Singleton
class PlaybackInterruptionNotifier
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
  ) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    /** [autoResume] says whether playback will continue on its own once the server is back. */
    fun showServerUnreachable(autoResume: Boolean) {
      val description =
        when (autoResume) {
          true -> R.string.notification_playback_interrupted_auto_description
          false -> R.string.notification_playback_interrupted_manual_description
        }.let(context::getString)

      // without the notification permission the warning still has to reach the listener
      if (manager.areNotificationsEnabled().not()) {
        Toast.makeText(context, description, Toast.LENGTH_LONG).show()
        return
      }

      val notification =
        Notification
          .Builder(context, createChannel())
          .setSmallIcon(R.drawable.ic_cloud_off)
          .setContentTitle(context.getString(R.string.notification_playback_interrupted_title))
          .setContentText(description)
          .setStyle(Notification.BigTextStyle().bigText(description))
          .setCategory(Notification.CATEGORY_ERROR)
          .setOnlyAlertOnce(true)
          .setAutoCancel(true)
          .apply { openAppIntent()?.let(::setContentIntent) }
          .build()

      manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel() = manager.cancel(NOTIFICATION_ID)

    private fun openAppIntent(): PendingIntent? =
      context.packageManager
        .getLaunchIntentForPackage(context.packageName)
        ?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }

    private fun createChannel(): String {
      val channel =
        NotificationChannel(
          CHANNEL_ID,
          context.getString(R.string.notification_playback_interrupted_channel),
          NotificationManager.IMPORTANCE_HIGH,
        )

      manager.createNotificationChannel(channel)
      return CHANNEL_ID
    }

    companion object {
      private const val CHANNEL_ID = "playback_interruption_channel"
      const val NOTIFICATION_ID = 3042025
    }
  }
