package org.grakovne.lissen.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.LruCache
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_MEDIA_NEXT
import android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.future
import org.grakovne.lissen.BuildConfig
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.lib.domain.SeekTimeOption
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.playback.service.PlaybackService
import org.grakovne.lissen.playback.service.PlaybackSynchronizationService
import org.grakovne.lissen.ui.activity.AppActivity
import org.grakovne.lissen.util.asListenableFuture
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaLibrarySessionProvider
  @OptIn(UnstableApi::class)
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
    private val preferences: LissenSharedPreferences,
    private val mediaRepository: MediaRepository,
    private val lissenMediaProvider: LissenMediaProvider,
    private val exoPlayer: ExoPlayer,
    private val libraryTree: MediaLibraryTree,
    private val playbackSynchronizationService: PlaybackSynchronizationService,
  ) {
    @OptIn(UnstableApi::class, DelicateCoroutinesApi::class)
    fun provideMediaLibrarySession(mediaLibraryService: MediaLibraryService): MediaLibraryService.MediaLibrarySession {
      val knownPackages =
        listOf(
          "com.google.android.projection.gearhead", // Android Auto
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
        .Builder(
          mediaLibraryService,
          exoPlayer,
          object : MediaLibraryService.MediaLibrarySession.Callback {
            val futureScope = CoroutineScope(Dispatchers.Default)

            override fun onMediaButtonEvent(
              session: MediaSession,
              controllerInfo: MediaSession.ControllerInfo,
              intent: Intent,
            ): Boolean {
              Timber.d("Executing media button event from: $controllerInfo")

              val keyEvent =
                intent
                  .getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                  ?: return super.onMediaButtonEvent(session, controllerInfo, intent)

              Timber.d("Got media key event: $keyEvent")

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

                else -> {
                  return super.onMediaButtonEvent(session, controllerInfo, intent)
                }
              }
            }

            @OptIn(UnstableApi::class)
            override fun onConnect(
              session: MediaSession,
              controller: MediaSession.ControllerInfo,
            ): MediaSession.ConnectionResult {
              if (
                session.isMediaNotificationController(controller) ||
                session.isAutomotiveController(controller) ||
                session.isAutoCompanionController(controller)
              ) {
                val rewindCommand = SessionCommand(REWIND_COMMAND, Bundle.EMPTY)
                val forwardCommand = SessionCommand(FORWARD_COMMAND, Bundle.EMPTY)
                val seekTime = preferences.getSeekTime()

                val sessionCommands =
                  MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                    .buildUpon()
                    .add(rewindCommand)
                    .add(forwardCommand)
                    .build()

                val rewindButton =
                  CommandButton
                    .Builder(Companion.rewindCommand)
                    .setSessionCommand(rewindCommand)
                    .setDisplayName("Rewind")
                    .setEnabled(true)
                    .build()

                val forwardButton =
                  CommandButton
                    .Builder(Companion.forwardCommand)
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
              // Default commands without media button preferences for common controllers.
              return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
            }

            override fun onCustomCommand(
              session: MediaSession,
              controller: MediaSession.ControllerInfo,
              customCommand: SessionCommand,
              args: Bundle,
            ): ListenableFuture<SessionResult> {
              Timber.d("Executing: ${customCommand.customAction}")

              when (customCommand.customAction) {
                FORWARD_COMMAND -> mediaRepository.forward()
                REWIND_COMMAND -> mediaRepository.rewind()
              }

              return super.onCustomCommand(session, controller, customCommand, args)
            }

            override fun onGetLibraryRoot(
              session: MediaLibraryService.MediaLibrarySession,
              browser: MediaSession.ControllerInfo,
              params: MediaLibraryService.LibraryParams?,
            ): ListenableFuture<LibraryResult<MediaItem>> = libraryTree.getRootItem()

            override fun onGetChildren(
              session: MediaLibraryService.MediaLibrarySession,
              browser: MediaSession.ControllerInfo,
              parentId: String,
              page: Int,
              pageSize: Int,
              params: MediaLibraryService.LibraryParams?,
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = libraryTree.getChildren(parentId, page, pageSize)

            override fun onGetItem(
              session: MediaLibraryService.MediaLibrarySession,
              browser: MediaSession.ControllerInfo,
              mediaId: String,
            ): ListenableFuture<LibraryResult<MediaItem>> = libraryTree.getItem(mediaId)

            override fun onSetMediaItems(
              mediaSession: MediaSession,
              controller: MediaSession.ControllerInfo,
              mediaItems: List<MediaItem>,
              startIndex: Int,
              startPositionMs: Long,
            ): ListenableFuture<MediaItemsWithStartPosition> {
              if (mediaItems.size == 1 && mediaItems[0].mediaId.startsWith("[bookID]")) {
                return futureScope
                  .future {
                    val bookId = mediaItems[0].mediaId.removePrefix("[bookID]")
                    lissenMediaProvider
                      .fetchBook(bookId)
                      .foldAsync(
                        onSuccess = {
                          async {
                            playbackSynchronizationService.startPlaybackSynchronization(it)
                          }
                          PlaybackService.bookToChapterMediaItems(it)
                        },
                        onFailure = { MediaItemsWithStartPosition(emptyList(), 0, 0) },
                      )
                  }.asListenableFuture()
              }
              return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
            }

            var searchCache = LruCache<String, ListenableFuture<List<MediaItem>>>(3)

            override fun onSearch(
              session: MediaLibraryService.MediaLibrarySession,
              browser: MediaSession.ControllerInfo,
              query: String,
              params: MediaLibraryService.LibraryParams?,
            ): ListenableFuture<LibraryResult<Void>> {
              val searchFuture =
                synchronized(searchCache) {
                  searchCache.get(query) ?: libraryTree
                    .searchBooks(query)
                    .also { searchCache.put(query, it) }
                }

              searchFuture.addListener({
                val results = searchFuture.get()
                session.notifySearchResultChanged(browser, query, results.size, params)
              }, context.mainExecutor)

              return Futures.immediateFuture(LibraryResult.ofVoid())
            }

            override fun onGetSearchResult(
              session: MediaLibraryService.MediaLibrarySession,
              browser: MediaSession.ControllerInfo,
              query: String,
              page: Int,
              pageSize: Int,
              params: MediaLibraryService.LibraryParams?,
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
              val searchFuture = searchCache.get(query)
              return Futures.transform(
                searchFuture,
                { items ->
                  val fromIndex = (page * pageSize).coerceAtMost(items.size)
                  val toIndex = (fromIndex + pageSize).coerceAtMost(items.size)
                  LibraryResult.ofItemList(items.subList(fromIndex, toIndex), params)
                },
                context.mainExecutor,
              )
            }
          },
        ).setSessionActivity(
          PendingIntent.getActivity(
            context,
            0,
            Intent(context, AppActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
          ),
        ).build()
    }

    companion object {
      private const val rewindCommand = CommandButton.ICON_SKIP_BACK

      private val forwardCommand = CommandButton.ICON_SKIP_FORWARD
        get() = field

      private const val REWIND_COMMAND = "notification_rewind"
      private const val FORWARD_COMMAND = "notification_forward"
    }
  }
