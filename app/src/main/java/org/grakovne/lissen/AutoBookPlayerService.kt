
package org.grakovne.lissen

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import dagger.hilt.android.AndroidEntryPoint

import org.grakovne.lissen.content.LissenMediaProvider

import org.grakovne.lissen.widget.MediaRepository

import javax.inject.Inject

import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AutoBookPlayerService : MediaLibraryService() {

    private lateinit var mediaSession: MediaLibrarySession
    private lateinit var books: MutableList<MediaItem>

    @Inject
    lateinit var exoPlayer: ExoPlayer

    @Inject
    lateinit var mediaChannel: LissenMediaProvider

    @Inject
    lateinit var mediaRepository: MediaRepository

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CoroutineScope(Dispatchers.IO).launch{
            books = getBooksMediaItems()
        }
        return super.onStartCommand(intent, flags, startId)

    }

    override fun onCreate() {
        super.onCreate()

        mediaSession = MediaLibrarySession.Builder(
                this,
                exoPlayer,
                MediaLibrarySessionCallback()
        ).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    suspend fun getBooksMediaItems(): MutableList<MediaItem>{
        mediaChannel.fetchLibraries().fold( onSuccess = {it.first()}, onFailure = {null})?.let { library ->
            val books = mediaChannel.fetchBooks(library.id,4,1)
            return books
                    .fold(onSuccess = { pagedItems -> pagedItems.items.map {
                        return@map MediaItem.Builder()
                                .setMediaId(it.id)
                                .setMediaMetadata(MediaMetadata.Builder()
                                        .setTitle(it.title)
                                        .setSubtitle(it.author)
                                        .build()
                                )
                                .build()
                    } }, onFailure = { listOf() })
                    .toMutableList()
        }
        return mutableListOf()
    }


    private inner class MediaLibrarySessionCallback: MediaLibrarySession.Callback {
        //All clients can connect
        @OptIn(UnstableApi::class)
        override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
                    .build()
        }

        override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(
                    LibraryResult.ofItem(
                           books[0],
                            params
                    )
            )
        }

        override fun onGetItem(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(
                    LibraryResult.ofItem(
                            books[0],
                            null
                    )
            )
        }

        override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return Futures.immediateFuture(
                    LibraryResult.ofItemList(
                            books,
                            null
                    )
            )
        }
    }


    companion object{
        private const val TAG: String = "AutoBookPlayerService"
    }
}