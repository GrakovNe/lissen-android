package org.grakovne.lissen

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import androidx.media.MediaBrowserServiceCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.widget.MediaRepository

import java.util.ArrayList
import javax.inject.Inject

/**
 * This class provides a MediaBrowser through a service. It exposes the media library to a browsing
 * client, through the onGetRoot and onLoadChildren methods. It also creates a MediaSession and
 * exposes it through its MediaSession.Token, which allows the client to create a MediaController
 * that connects to and send control commands to the MediaSession remotely. This is useful for
 * user interfaces that need to interact with your media session, like Android Auto. You can
 * (should) also use the same service from your app's UI, which gives a seamless playback
 * experience to the user.
 *
 *
 * To implement a MediaBrowserService, you need to:
 *
 *  *  Extend [MediaBrowserServiceCompat], implementing the media browsing
 * related methods [MediaBrowserServiceCompat.onGetRoot] and
 * [MediaBrowserServiceCompat.onLoadChildren];
 *
 *  *  In onCreate, start a new [MediaSessionCompat] and notify its parent
 * with the session"s token [MediaBrowserServiceCompat.setSessionToken];
 *
 *  *  Set a callback on the [MediaSessionCompat.setCallback].
 * The callback will receive all the user"s actions, like play, pause, etc;
 *
 *  *  Handle all the actual music playing using any method your app prefers (for example,
 * [android.media.MediaPlayer])
 *
 *  *  Update playbackState, "now playing" metadata and queue, using MediaSession proper methods
 * [MediaSessionCompat.setPlaybackState]
 * [MediaSessionCompat.setMetadata] and
 * [MediaSessionCompat.setQueue])
 *
 *  *  Declare and export the service in AndroidManifest with an intent receiver for the action
 * android.media.browse.MediaBrowserService
 *
 * To make your app compatible with Android Auto, you also need to:
 *
 *  *  Declare a meta-data tag in AndroidManifest.xml linking to a xml resource
 * with a &lt;automotiveApp&gt; root element. For a media app, this must include
 * an &lt;uses name="media"/&gt; element as a child.
 * For example, in AndroidManifest.xml:
 * &lt;meta-data android:name="com.google.android.gms.car.application"
 * android:resource="@xml/automotive_app_desc"/&gt;
 * And in res/values/automotive_app_desc.xml:
 * &lt;automotiveApp&gt;
 * &lt;uses name="media"/&gt;
 * &lt;/automotiveApp&gt;
 *
 */
@AndroidEntryPoint
class AutoBookPlayerService : MediaBrowserServiceCompat() {

    private lateinit var session: MediaSessionCompat
    @Inject
    lateinit var mediaChannel: LissenMediaProvider

    @Inject
    lateinit var mediaRepository: MediaRepository

    private val callback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            Log.d(TAG, "onPlay?")
        }

        override fun onSkipToQueueItem(queueId: Long) {}

        override fun onSeekTo(position: Long) {}

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            Log.d(TAG, "onPlayFromMediaId?: $mediaId")
            if(mediaId == null) return
            CoroutineScope(Dispatchers.Main).launch {
                mediaRepository.preparePlayback(mediaId, true)
                mediaRepository.togglePlayPause()
            }
        }

        override fun onPause() {}

        override fun onStop() {}

        override fun onSkipToNext() {}

        override fun onSkipToPrevious() {}

        override fun onCustomAction(action: String?, extras: Bundle?) {}

        override fun onPlayFromSearch(query: String?, extras: Bundle?) {}
    }

    override fun onCreate() {
        super.onCreate()

        session = MediaSessionCompat(this, "AutoBookPlayerService")
        sessionToken = session.sessionToken
        session.setCallback(callback)
        session.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
    }

    override fun onDestroy() {
        session.release()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        return BrowserRoot("root", null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
        CoroutineScope(Dispatchers.Main).launch {
            result.sendResult(getBooksMediaItems())
        }
        result.detach()
    }

    suspend fun getDetailedBook(id: String): DetailedItem? {
        return mediaChannel.fetchBook(id).fold(onFailure = { null }, onSuccess = { it })
    }
    suspend fun getBooksMediaItems(): MutableList<MediaItem>{
        mediaChannel.fetchLibraries().fold( onSuccess = {it.first()}, onFailure = {null})?.let { library ->
            val books = mediaChannel.fetchBooks(library.id,4,1)
            return books
                    .fold(onSuccess = { pagedItems -> pagedItems.items.map {
                        val desc = MediaDescriptionCompat.Builder()
                                .setMediaId(it.id)
                                .setTitle(it.title)
                                .setSubtitle(it.author)
                                .build()
                        MediaItem(desc, MediaItem.FLAG_PLAYABLE)
                    } }, onFailure = { listOf() })
                    .toMutableList()
        }
        return mutableListOf()
    }


    companion object{
        private const val TAG: String = "AutoBookPlayerService"
    }
}