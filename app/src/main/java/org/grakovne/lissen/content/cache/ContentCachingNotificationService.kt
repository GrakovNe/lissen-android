package org.grakovne.lissen.content.cache

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.viewmodel.CacheStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentCachingNotificationService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val service = context.getSystemService(NotificationManager::class.java)

    fun cancel() = context
        .getSystemService(NotificationManager::class.java)
        .cancel(NOTIFICATION_ID)

    fun updateCachingNotification(
        items: List<Pair<DetailedItem, CacheStatus>>,
    ): Notification = Notification
        .Builder(context, createNotificationChannel())
        .setContentText(items.provideCachingTitles())
        .setSubText(context.getString(R.string.notification_content_caching_title))
        .setSmallIcon(R.drawable.ic_downloading)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setProgress(0, 0, true)
        .build()
        .also { service.notify(NOTIFICATION_ID, it) }

    fun updateErrorNotification(): Notification = Notification
        .Builder(context, createNotificationChannel())
        .setContentTitle(context.getString(R.string.notification_content_caching_error_title))
        .setContentText(context.getString(R.string.notification_content_caching_error_description))
        .setSmallIcon(R.drawable.ic_downloading)
        .build()
        .also { service.notify(NOTIFICATION_ID, it) }

    private fun createNotificationChannel(): String {
        val channelId = "caching_channel"

        val channel = NotificationChannel(
            channelId,
            context.getString(R.string.notification_content_caching_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        )

        service.createNotificationChannel(channel)
        return channelId
    }

    companion object {

        private fun List<Pair<DetailedItem, CacheStatus>>.provideCachingTitles() = this
            .filter { (_, status) -> CacheStatus.Caching == status }
            .joinToString(", ") { (key, _) -> key.title }

        const val NOTIFICATION_ID = 2042025
    }
}
