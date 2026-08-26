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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeoutOrNull
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.service.PlaybackSynchronizationService
import org.grakovne.lissen.playback.service.bookToChapterMediaItems
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

    override fun onConnect(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
      val prevChapterCommand = SessionCommand(PREV_CHAPTER_COMMAND, Bundle.EMPTY)
      val rewindCommand = SessionCommand(REWIND_COMMAND, Bundle.EMPTY)
      val forwardCommand = SessionCommand(FORWARD_COMMAND, Bundle.EMPTY)
      val nextChapterCommand = SessionCommand(NEXT_CHAPTER_COMMAND, Bundle.EMPTY)

      val seekTime = preferences.getSeekTime()

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
          .setSlots(CommandButton.SLOT_OVERFLOW)
          .build()

      val nextChapterButton =
        CommandButton
          .Builder(CommandButton.ICON_NEXT)
          .setSessionCommand(nextChapterCommand)
          .setDisplayName("Next Chapter")
          .setSlots(CommandButton.SLOT_OVERFLOW)
          .setEnabled(true)
          .build()

      val rewindButton =
        CommandButton
          .Builder(
            when (seekTime.rewind) {
              5 -> CommandButton.ICON_SKIP_BACK_5
              10 -> CommandButton.ICON_SKIP_BACK_10
              15 -> CommandButton.ICON_SKIP_BACK_15
              30 -> CommandButton.ICON_SKIP_BACK_30
              else -> CommandButton.ICON_SKIP_BACK
            },
          ).setSessionCommand(rewindCommand)
          .setDisplayName("Rewind")
          .setEnabled(true)
          .setSlots(CommandButton.SLOT_BACK)
          .build()

      val forwardButton =
        CommandButton
          .Builder(
            when (seekTime.forward) {
              5 -> CommandButton.ICON_SKIP_FORWARD_5
              10 -> CommandButton.ICON_SKIP_FORWARD_10
              15 -> CommandButton.ICON_SKIP_FORWARD_15
              30 -> CommandButton.ICON_SKIP_FORWARD_30
              else -> CommandButton.ICON_SKIP_FORWARD
            },
          ).setSessionCommand(forwardCommand)
          .setDisplayName("Forward")
          .setSlots(CommandButton.SLOT_FORWARD)
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
                    bookToChapterMediaItems(it)
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
          val book = refreshedBook ?: storedBook

          if (book.canProducePlaybackQueue().not()) {
            throw IllegalStateException("Book can't produce a playback queue (bookId=${book.id})")
          }

          if (isForPlayback) {
            refreshedBook?.let { preferences.savePlayingItem(it) }
            playbackSynchronizationService.startPlaybackSynchronization(book)
            mediaRepository.registerPlayingBook(book)
          }

          bookToChapterMediaItems(book)
        }

    private suspend fun refreshBookForResumption(storedBook: DetailedItem): DetailedItem? {
      val refreshed =
        try {
          withTimeoutOrNull(REFRESH_TIMEOUT_MS) { lissenMediaProvider.fetchBook(storedBook.id) }
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

      private const val REFRESH_TIMEOUT_MS = 2_000L
    }
  }

@Suppress("DEPRECATION")
private inline fun <reified T : Parcelable> Intent.getParcelable(key: String): T? =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    getParcelableExtra(key, T::class.java)
  } else {
    getParcelableExtra(key)
  }
