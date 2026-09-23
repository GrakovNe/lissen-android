package org.grakovne.lissen.playback

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.DetailedItem
import timber.log.Timber

/**
 * The bookmarks of the playing item, shown in the order it is playing in. The list follows
 * [playingBook]: a refresh whose item is no longer the playing one is dropped, since that
 * item's own refresh follows. Storage and the server speak canonical positions; the
 * translations live in [BookmarkCoordinates].
 */
class PlayingBookmarks(
  private val mediaChannel: LissenMediaProvider,
  private val playingBook: StateFlow<DetailedItem?>,
  private val scope: CoroutineScope,
  private val io: CoroutineDispatcher = Dispatchers.IO,
) {
  private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
  val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

  // the item (and order) the displayed positions were translated for
  @Volatile
  private var displayedFor: DetailedItem? = null

  /**
   * Every freshly prepared item pulls its bookmarks from the server, so that ones added from
   * another device show up whichever way the item arrived (the screen, the widget, the media
   * session).
   */
  fun refreshFromServerAsync() {
    scope.launch { refreshFromServer() }
  }

  suspend fun refreshFromServer() {
    val book = playingBook.value ?: return
    val fetched = withContext(io) { mediaChannel.updateAndProvideBookmarks(book.id) }

    show(fetched, itemId = book.id)
  }

  /**
   * Records a bookmark at [totalPosition] of [book]. It is kept locally right away and reaches
   * the server on its own later, so the returned bookmark is the local record. `null` when the
   * position falls outside every chapter.
   */
  suspend fun create(
    book: DetailedItem,
    totalPosition: Double,
    title: String?,
  ): Bookmark? {
    Timber.d("Creating bookmark for ${book.id} at position=${totalPosition.toInt()}s")

    val draft = BookmarkCoordinates.draft(book, totalPosition, title)
    if (draft == null) {
      Timber.w("Unable to create bookmark: no chapter at position=${totalPosition.toInt()}s")
      return null
    }

    val created =
      mediaChannel.createBookmark(
        libraryItemId = book.id,
        totalPosition = draft.storedPosition,
        title = draft.title,
      )

    refreshFromCache(book.id)
    return created
  }

  /** Drops the stored bookmark behind [bookmark], which carries a display position. */
  suspend fun drop(bookmark: Bookmark) {
    Timber.d("Dropping bookmark for ${bookmark.libraryItemId} at position=${bookmark.totalPosition.toInt()}s")

    // the item the displayed list was built for, which is not necessarily the one playing now
    val stored =
      displayedFor
        ?.takeIf { it.id == bookmark.libraryItemId }
        ?.let { book -> BookmarkCoordinates.storedFor(book, bookmark, mediaChannel.provideBookmarks(bookmark.libraryItemId)) }
        ?: bookmark

    mediaChannel.dropBookmark(bookmark = stored)
    refreshFromCache(bookmark.libraryItemId)
  }

  /**
   * The playing item was rebuilt in another order. The list in memory follows at once, so
   * nothing can act on positions of the old order; the exact stored values, translated for
   * the new order, replace it as soon as they are read.
   */
  fun followReorder(
    from: DetailedItem,
    to: DetailedItem,
  ) {
    _bookmarks.value = BookmarkCoordinates.translated(_bookmarks.value, from, to)
    displayedFor = to
    scope.launch { refreshFromCache(from.id) }
  }

  private suspend fun refreshFromCache(itemId: String) {
    val stored = withContext(io) { mediaChannel.provideBookmarks(itemId) }

    show(stored, itemId = itemId)
  }

  /**
   * Shows [stored] translated for whatever order is playing at the moment of the write, so
   * that a reorder landing during the read cannot leave the list in the previous order.
   */
  private fun show(
    stored: List<Bookmark>,
    itemId: String,
  ) {
    val book = playingBook.value

    // another item started playing meanwhile: its own refresh will follow, these rows are not its
    if (book?.id != itemId) return

    displayedFor = book
    _bookmarks.value = BookmarkCoordinates.inPlayingOrder(stored, book)
  }
}
