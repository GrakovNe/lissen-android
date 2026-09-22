package org.grakovne.lissen.playback

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.playback.BookmarkCoordinates.inOrderOf
import org.grakovne.lissen.playback.BookmarkCoordinates.movedWith
import timber.log.Timber

/**
 * The bookmarks of the item that is playing, kept in the coordinates of the order it plays
 * in. Every read goes through the channel, is translated for the item playing at the moment
 * of the write and is dropped if another item took over meanwhile: its own refresh follows.
 */
class PlaybackBookmarks(
  private val mediaChannel: LissenMediaProvider,
  private val playingBook: StateFlow<DetailedItem?>,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
  val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

  // the item (and order) the displayed positions were translated for
  @Volatile
  private var displayedFor: DetailedItem? = null

  suspend fun create(
    totalPosition: Double,
    title: String? = null,
  ) {
    val book = playingBook.value ?: return
    Timber.d("Creating bookmark for ${book.id} at position=${totalPosition.toInt()}s")

    val draft =
      BookmarkCoordinates.draft(book, totalPosition, title)
        ?: return Timber.w("Unable to create bookmark: no chapter at position=${totalPosition.toInt()}s")

    mediaChannel.createBookmark(
      title = draft.title,
      libraryItemId = book.id,
      totalPosition = draft.storedPosition,
    )

    refreshFromCache(book.id)
  }

  /**
   * [bookmark] carries a display position of the list last shown, which was built for
   * [displayedFor]: not necessarily the item playing now.
   */
  suspend fun drop(bookmark: Bookmark) {
    Timber.d("Dropping bookmark for ${bookmark.libraryItemId} at position=${bookmark.totalPosition.toInt()}s")

    val stored =
      displayedFor
        ?.takeIf { it.id == bookmark.libraryItemId }
        ?.let { BookmarkCoordinates.stored(bookmark, mediaChannel.provideBookmarks(it.id), it) }
        ?: bookmark

    mediaChannel.dropBookmark(bookmark = stored)

    refreshFromCache(bookmark.libraryItemId)
  }

  /** The stored bookmarks of [itemId] as the cache has them. */
  suspend fun refreshFromCache(itemId: String) = show(itemId) { mediaChannel.provideBookmarks(itemId) }

  /**
   * The stored bookmarks of the playing item, pulled from the server first so that ones
   * added from another device show up.
   */
  suspend fun refreshFromServer() {
    val book = playingBook.value ?: return
    show(book.id) { mediaChannel.updateAndProvideBookmarks(book.id) }
  }

  /**
   * The list in memory follows a reorder at once, so nothing can act on positions of the old
   * order; the exact stored values, translated for the new order, replace it on the next read.
   */
  fun followReorder(
    from: DetailedItem,
    to: DetailedItem,
  ) {
    _bookmarks.value = _bookmarks.value.map { it.movedWith(from, to) }
    displayedFor = to
  }

  private suspend fun show(
    itemId: String,
    fetch: suspend () -> List<Bookmark>,
  ) {
    val stored = withContext(ioDispatcher) { fetch() }

    // another item started playing meanwhile: its own refresh will follow, these rows are not its
    val current = playingBook.value?.takeIf { it.id == itemId } ?: return

    displayedFor = current
    _bookmarks.value = stored.inOrderOf(current)
  }
}
