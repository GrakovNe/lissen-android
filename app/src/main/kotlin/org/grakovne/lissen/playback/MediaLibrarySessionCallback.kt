package org.grakovne.lissen.playback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.LruCache
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_MEDIA_NEXT
import android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
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
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.playback.service.PlaybackService
import org.grakovne.lissen.playback.service.PlaybackSynchronizationService
import org.grakovne.lissen.util.listenableFuture
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class MediaLibrarySessionCallback
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
    private val preferences: LissenSharedPreferences,
    private val mediaRepository: MediaRepository,
    private val lissenMediaProvider: LissenMediaProvider,
    private val libraryTree: MediaLibraryTree,
    private val playbackSynchronizationService: PlaybackSynchronizationService,
  ) : MediaLibraryService.MediaLibrarySession.Callback {
    @OptIn(DelicateCoroutinesApi::class)
    private val futureScope = CoroutineScope(Dispatchers.Default)

    internal var searchCache = LruCache<String, ListenableFuture<List<MediaItem>>>(3)

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

      return when (keyEvent.keyCode) {
        KEYCODE_MEDIA_NEXT -> {
          mediaRepository.forward()
          true
        }

        KEYCODE_MEDIA_PREVIOUS -> {
          mediaRepository.rewind()
          true
        }

        else -> {
          super.onMediaButtonEvent(session, controllerInfo, intent)
        }
      }
    }

    override fun onConnect(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
      val prevChapterCommand = SessionCommand(PREV_CHAPTER_COMMAND, Bundle.EMPTY)
      val rewindCommand = SessionCommand(REWIND_COMMAND, Bundle.EMPTY)
      val forwardCommand = SessionCommand(FORWARD_COMMAND, Bundle.EMPTY)
      val nextChapterCommand = SessionCommand(NEXT_CHAPTER_COMMAND, Bundle.EMPTY)

      val sessionCommands =
        MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
          .buildUpon()
          .add(prevChapterCommand)
          .add(rewindCommand)
          .add(forwardCommand)
          .add(nextChapterCommand)
          .build()

      val previousChapterButton =
        CommandButton
          .Builder(CommandButton.ICON_PREVIOUS)
          .setSessionCommand(prevChapterCommand)
          .setDisplayName("Previous Chapter")
          .setEnabled(true)
          .setSlots(CommandButton.SLOT_BACK)
          .build()

      val nextChapterButton =
        CommandButton
          .Builder(CommandButton.ICON_NEXT)
          .setSessionCommand(nextChapterCommand)
          .setDisplayName("Next Chapter")
          .setSlots(CommandButton.SLOT_FORWARD)
          .setEnabled(true)
          .build()

      val rewindButton =
        CommandButton
          .Builder(CommandButton.ICON_SKIP_BACK)
          .setSessionCommand(rewindCommand)
          .setDisplayName("Rewind")
          .setEnabled(true)
          .setSlots(CommandButton.SLOT_OVERFLOW)
          .build()

      val forwardButton =
        CommandButton
          .Builder(CommandButton.ICON_SKIP_FORWARD)
          .setSessionCommand(forwardCommand)
          .setDisplayName("Forward")
          .setSlots(CommandButton.SLOT_OVERFLOW)
          .setEnabled(true)
          .build()

      return MediaSession
        .ConnectionResult
        .AcceptedResultBuilder(session)
        .setAvailableSessionCommands(sessionCommands)
        .setMediaButtonPreferences(listOf(previousChapterButton, rewindButton, forwardButton, nextChapterButton))
        .build()
    }

    override fun onCustomCommand(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
      customCommand: SessionCommand,
      args: Bundle,
    ): ListenableFuture<SessionResult> {
      Timber.d("Executing: ${customCommand.customAction}")

      when (customCommand.customAction) {
        PREV_CHAPTER_COMMAND -> mediaRepository.previousTrack(rewindRequired = true)
        REWIND_COMMAND -> mediaRepository.rewind()
        FORWARD_COMMAND -> mediaRepository.forward()
        NEXT_CHAPTER_COMMAND -> mediaRepository.nextTrack()
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
    ): ListenableFuture<MediaItemsWithStartPosition> =
      mediaItems.singleOrNull()?.let { mediaItem ->
        if (MediaLibraryTree.isBookPath(mediaItem.mediaId) && startIndex == C.INDEX_UNSET && startPositionMs == C.TIME_UNSET) {
          futureScope
            .listenableFuture {
              val bookId = MediaLibraryTree.parseBookId(mediaItem.mediaId)
              lissenMediaProvider
                .fetchBook(bookId)
                .foldAsync(
                  onSuccess = {
                    async {
                      preferences.savePlayingBook(it)
                      playbackSynchronizationService.startPlaybackSynchronization(it)
                    }
                    PlaybackService.bookToChapterMediaItems(it)
                  },
                  onFailure = { MediaItemsWithStartPosition(emptyList(), 0, 0) },
                )
            }
        } else {
          null
        }
      } ?: super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)

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
        val resultSetSize =
          try {
            searchFuture.get().size
          } catch (_: Exception) {
            0
          }
        session.notifySearchResultChanged(browser, query, resultSetSize, params)
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

    companion object {
      internal const val PREV_CHAPTER_COMMAND = "notification_prev_chapter"
      internal const val REWIND_COMMAND = "notification_rewind"
      internal const val FORWARD_COMMAND = "notification_forward"
      internal const val NEXT_CHAPTER_COMMAND = "notification_next_chapter"
    }
  }
