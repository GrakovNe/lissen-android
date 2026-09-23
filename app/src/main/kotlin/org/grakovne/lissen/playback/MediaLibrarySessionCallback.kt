package org.grakovne.lissen.playback

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.service.PlaybackService
import org.grakovne.lissen.playback.service.PlaybackSynchronizationService
import org.grakovne.lissen.util.listenableFuture
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@OptIn(UnstableApi::class)
@Singleton
class MediaLibrarySessionCallback
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: PlaybackPreferences,
    private val mediaRepository: MediaRepository,
    private val lissenMediaProvider: LissenMediaProvider,
    private val libraryTree: MediaLibraryTree,
    private val playbackSynchronizationService: PlaybackSynchronizationService,
  ) : MediaLibraryService.MediaLibrarySession.Callback {
    @OptIn(DelicateCoroutinesApi::class)
    private val futureScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    internal var searchCache = LruCache<String, ListenableFuture<List<MediaItem>>>(5)

    /** The bookmark press being handled, from creating the bookmark to the end of its confirmation. */
    private var bookmarkFeedback: Job? = null

    private fun searchFutureFor(query: String): ListenableFuture<List<MediaItem>> {
      val key = query.trim().lowercase()
      return synchronized(searchCache) {
        searchCache.get(key) ?: libraryTree
          .searchBooks(query)
          .also { searchCache.put(key, it) }
      }
    }

    override fun onMediaButtonEvent(
      session: MediaSession,
      controllerInfo: MediaSession.ControllerInfo,
      intent: Intent,
    ): Boolean {
      Timber.d("Executing media button event from: $controllerInfo")

      val keyEvent =
        intent
          .getParcelable<KeyEvent>(Intent.EXTRA_KEY_EVENT)
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

    private val prevChapterCommand = SessionCommand(PREV_CHAPTER_COMMAND, Bundle.EMPTY)
    private val rewindCommand = SessionCommand(REWIND_COMMAND, Bundle.EMPTY)
    private val forwardCommand = SessionCommand(FORWARD_COMMAND, Bundle.EMPTY)
    private val nextChapterCommand = SessionCommand(NEXT_CHAPTER_COMMAND, Bundle.EMPTY)
    private val bookmarkCommand = SessionCommand(BOOKMARK_COMMAND, Bundle.EMPTY)

    private data class MediaButtonSpec(
      val icon: Int,
      val command: SessionCommand,
      val displayName: String,
      val slot: Int,
    )

    private fun mediaButtons(bookmarkConfirmed: Boolean = false): List<CommandButton> {
      val seekTime = preferences.getSeekTime()

      return listOf(
        MediaButtonSpec(
          icon = SKIP_BACK_ICONS[seekTime.rewind] ?: CommandButton.ICON_SKIP_BACK,
          command = rewindCommand,
          displayName = "Rewind",
          slot = CommandButton.SLOT_BACK,
        ),
        MediaButtonSpec(
          icon = SKIP_FORWARD_ICONS[seekTime.forward] ?: CommandButton.ICON_SKIP_FORWARD,
          command = forwardCommand,
          displayName = "Forward",
          slot = CommandButton.SLOT_FORWARD,
        ),
        MediaButtonSpec(
          icon = CommandButton.ICON_PREVIOUS,
          command = prevChapterCommand,
          displayName = "Previous Chapter",
          slot = CommandButton.SLOT_OVERFLOW,
        ),
        MediaButtonSpec(
          icon = CommandButton.ICON_NEXT,
          command = nextChapterCommand,
          displayName = "Next Chapter",
          slot = CommandButton.SLOT_OVERFLOW,
        ),
        MediaButtonSpec(
          icon = if (bookmarkConfirmed) CommandButton.ICON_CHECK_CIRCLE_UNFILLED else CommandButton.ICON_BOOKMARK_UNFILLED,
          command = bookmarkCommand,
          displayName = "Create bookmark",
          slot = CommandButton.SLOT_OVERFLOW,
        ),
      ).map { it.toCommandButton() }
    }

    private fun MediaButtonSpec.toCommandButton(): CommandButton =
      CommandButton
        .Builder(icon)
        .setSessionCommand(command)
        .setDisplayName(displayName)
        .setEnabled(true)
        .setSlots(slot)
        .build()

    override fun onConnect(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
      val sessionCommands =
        MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
          .buildUpon()
          .add(prevChapterCommand)
          .add(rewindCommand)
          .add(forwardCommand)
          .add(nextChapterCommand)
          .add(bookmarkCommand)
          .build()

      // every controller gets the full player command set, trusted or not, as before media3
      // started restricting untrusted controllers by default
      return MediaSession
        .ConnectionResult
        .AcceptedResultBuilder()
        .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
        .setAvailableSessionCommands(sessionCommands)
        .setMediaButtonPreferences(mediaButtons())
        .build()
    }

    override fun onCustomCommand(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
      customCommand: SessionCommand,
      args: Bundle,
    ): ListenableFuture<SessionResult> {
      Timber.d("Executing: ${customCommand.customAction}")

      return when (customCommand.customAction) {
        PREV_CHAPTER_COMMAND -> accepted { mediaRepository.previousTrack(rewindRequired = true) }
        REWIND_COMMAND -> accepted { mediaRepository.rewind() }
        FORWARD_COMMAND -> accepted { mediaRepository.forward() }
        NEXT_CHAPTER_COMMAND -> accepted { mediaRepository.nextTrack() }
        BOOKMARK_COMMAND -> accepted { createBookmark(session) }
        else -> super.onCustomCommand(session, controller, customCommand, args)
      }
    }

    private inline fun accepted(action: () -> Unit): ListenableFuture<SessionResult> {
      action()
      return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    /**
     * Creates a bookmark at the current position and, once it is recorded, shows a check mark in
     * place of the bookmark icon for a moment on every controller of the session. Presses arriving
     * while an earlier one is still being handled, check mark included, are ignored: the button is
     * a single tap, not a way to stack near-identical bookmarks.
     *
     * The session calls back on its application thread, and the handling coroutine stays on the
     * main dispatcher, so [bookmarkFeedback] is only ever touched from one thread.
     */
    private fun createBookmark(session: MediaSession) {
      if (bookmarkFeedback?.isActive == true) {
        return
      }

      bookmarkFeedback =
        futureScope.launch(Dispatchers.Main) {
          createBookmarkOrNull()?.let { showBookmarkConfirmation(session) }
        }
    }

    private suspend fun createBookmarkOrNull(): Bookmark? =
      try {
        mediaRepository.createBookmark()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Timber.w(e, "Unable to create bookmark from the media session")
        null
      }

    private suspend fun showBookmarkConfirmation(session: MediaSession) {
      session.setMediaButtonPreferences(mediaButtons(bookmarkConfirmed = true))
      delay(BOOKMARK_BUTTON_FEEDBACK_DURATION_MS)
      session.setMediaButtonPreferences(mediaButtons(bookmarkConfirmed = false))
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
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = libraryTree.getChildren(parentId, page, pageSize, session)

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
                    preferences.savePlayingItem(it)
                    playbackSynchronizationService.startPlaybackSynchronization(it)
                    mediaRepository.registerPlayingBook(it)
                    PlaybackService.bookToChapterMediaItems(it)
                  },
                  onFailure = { MediaItemsWithStartPosition(emptyList(), 0, 0) },
                )
            }
        } else {
          null
        }
      } ?: super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)

    override fun onPlaybackResumption(
      mediaSession: MediaSession,
      controller: MediaSession.ControllerInfo,
      isForPlayback: Boolean,
    ): ListenableFuture<MediaItemsWithStartPosition> =
      futureScope
        .listenableFuture {
          Timber.d("Resuming playback for: $controller (isForPlayback=$isForPlayback)")

          val storedBook =
            preferences.getPlayingItem()
              ?: throw IllegalStateException("No last played book stored")

          val refreshedBook = refreshBookForResumption(storedBook)
          val book = refreshedBook ?: storedBookWithLatestProgress(storedBook)

          if (book.canProducePlaybackQueue().not()) {
            throw IllegalStateException("Book can't produce a playback queue (bookId=${book.id})")
          }

          if (isForPlayback) {
            refreshedBook?.let { preferences.savePlayingItem(it) }
            playbackSynchronizationService.startPlaybackSynchronization(book)
            mediaRepository.registerPlayingBook(book)
          }

          PlaybackService.bookToChapterMediaItems(book)
        }

    /** The stored item is always playable as it was; a cache failure must not take that away. */
    private suspend fun storedBookWithLatestProgress(storedBook: DetailedItem): DetailedItem =
      try {
        lissenMediaProvider.withLatestProgress(storedBook)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Timber.w("Unable to read the latest local progress for resumption (bookId=${storedBook.id}) due to: ${e.message}")
        storedBook
      }

    private suspend fun refreshBookForResumption(storedBook: DetailedItem): DetailedItem? {
      val refreshed =
        try {
          withTimeoutOrNull(REFRESH_TIMEOUT_MS) { lissenMediaProvider.fetchBook(storedBook.id, storedBook.libraryType) }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Timber.w("Unable to refresh last played book (bookId=${storedBook.id}) for resumption due to: ${e.message}")
          return null
        }

      return when (refreshed) {
        null -> {
          Timber.w("Timed out refreshing last played book (bookId=${storedBook.id}) for resumption")
          null
        }

        is OperationResult.Error -> {
          Timber.w(
            "Unable to refresh last played book (bookId=${storedBook.id}) for resumption due to: ${refreshed.message}",
          )
          null
        }

        is OperationResult.Success -> {
          refreshed
            .data
            .takeIf { it.canProducePlaybackQueue() }
            ?: run {
              Timber.w("Refreshed last played book (bookId=${storedBook.id}) can't produce a playback queue")
              null
            }
        }
      }
    }

    override fun onSearch(
      session: MediaLibraryService.MediaLibrarySession,
      browser: MediaSession.ControllerInfo,
      query: String,
      params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> {
      val searchFuture = searchFutureFor(query)

      searchFuture.addListener({
        val resultSetSize =
          try {
            searchFuture.get().size
          } catch (ex: Exception) {
            Timber.w("Unable to obtain search results for query '$query' due to: ${ex.message}")
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
      val searchFuture = searchFutureFor(query)
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
      internal const val BOOKMARK_COMMAND = "notification_bookmark"

      private const val REFRESH_TIMEOUT_MS = 2_000L
      private const val BOOKMARK_BUTTON_FEEDBACK_DURATION_MS = 3_000L

      private val SKIP_BACK_ICONS =
        mapOf(
          5 to CommandButton.ICON_SKIP_BACK_5,
          10 to CommandButton.ICON_SKIP_BACK_10,
          15 to CommandButton.ICON_SKIP_BACK_15,
          30 to CommandButton.ICON_SKIP_BACK_30,
        )

      private val SKIP_FORWARD_ICONS =
        mapOf(
          5 to CommandButton.ICON_SKIP_FORWARD_5,
          10 to CommandButton.ICON_SKIP_FORWARD_10,
          15 to CommandButton.ICON_SKIP_FORWARD_15,
          30 to CommandButton.ICON_SKIP_FORWARD_30,
        )
    }
  }

@Suppress("DEPRECATION")
private inline fun <reified T : Parcelable> Intent.getParcelable(key: String): T? =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    getParcelableExtra(key, T::class.java)
  } else {
    getParcelableExtra(key)
  }
