package org.grakovne.lissen.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import org.grakovne.lissen.R
import org.grakovne.lissen.content.ExternalCoverProvider
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import org.grakovne.lissen.lib.domain.Book
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.Library
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.lib.domain.RecentBook
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.util.asListenableFuture
import org.grakovne.lissen.util.listenableFuture
import javax.inject.Inject
import javax.inject.Singleton

// --- Media Tree DSL ---

@DslMarker
annotation class MediaTreeDsl

class MediaTreeNode(
  val item: MediaItem,
  private val staticChildren: List<MediaTreeNode>,
  private val dynamicResolver: (suspend (String) -> MediaTreeNode?)?,
  private val childrenProvider: (suspend (Int, Int) -> List<MediaItem>)?,
) {
  suspend fun child(segment: String): MediaTreeNode? =
    staticChildren.find { it.item.mediaId.substringAfterLast('/') == segment }
      ?: dynamicResolver?.invoke(segment)

  suspend fun listChildren(
    page: Int,
    pageSize: Int,
  ): List<MediaItem> =
    childrenProvider?.invoke(page, pageSize)
      ?: staticChildren.map { it.item }
}

@MediaTreeDsl
class MediaTreeBuilder {
  private val children = mutableListOf<MediaTreeNode>()
  private var dynamicResolver: (suspend (String) -> MediaTreeNode?)? = null
  private var childrenProvider: (suspend (Int, Int) -> List<MediaItem>)? = null

  operator fun MediaTreeNode.unaryPlus() {
    children += this
  }

  fun resolveChild(resolver: suspend (String) -> MediaTreeNode?) {
    dynamicResolver = resolver
  }

  fun pagedChildren(provider: suspend (Int, Int) -> List<MediaItem>) {
    childrenProvider = provider
  }

  fun build(item: MediaItem): MediaTreeNode = MediaTreeNode(item, children.toList(), dynamicResolver, childrenProvider)
}

fun mediaTreeNode(
  item: MediaItem,
  block: MediaTreeBuilder.() -> Unit = {},
): MediaTreeNode = MediaTreeBuilder().apply(block).build(item)

// --- MediaLibraryTree ---

@Singleton
class MediaLibraryTree
  @Inject
  @OptIn(UnstableApi::class)
  constructor(
    @ApplicationContext private val context: Context,
    private val preferences: LissenSharedPreferences,
    private val localCacheRepository: LocalCacheRepository,
    private val lissenMediaProvider: LissenMediaProvider,
  ) {
    companion object {
      const val ROOT = "root"
      const val BOOK = "book"

      private const val CONTINUE = "continue"
      private const val RECENT = "recent"
      private const val LIBRARY = "library"
      private const val DOWNLOADS = "downloads"

      fun bookPath(bookId: String) = "$BOOK/$bookId"

      fun parseBookId(mediaId: String) = mediaId.removePrefix("$BOOK/")

      fun isBookPath(mediaId: String) = mediaId.startsWith("$BOOK/")
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    private val root: MediaTreeNode by lazy { buildTree() }

    @OptIn(UnstableApi::class)
    private fun buildTree(): MediaTreeNode =
      mediaTreeNode(folderItem(ROOT, context.getString(R.string.tree_node_root))) {
        +mediaTreeNode(folderItem("$ROOT/$CONTINUE", context.getString(R.string.tree_node_continue))) {
          pagedChildren { _, _ -> continueListeningItems() }
        }

        +mediaTreeNode(folderItem("$ROOT/$RECENT", context.getString(R.string.tree_node_recent))) {
          pagedChildren { _, _ -> recentBooksItems() }
        }

        +mediaTreeNode(folderItem("$ROOT/$LIBRARY", context.getString(R.string.tree_node_library))) {
          pagedChildren { _, _ -> libraryItems() }
          resolveChild { libId ->
            resolveLibrary(libId)?.let { lib ->
              mediaTreeNode(libraryFolderItem("$ROOT/$LIBRARY/$libId", lib)) {
                pagedChildren { page, pageSize -> booksFromLibraryItems(libId, page, pageSize) }
              }
            }
          }
        }

        +mediaTreeNode(folderItem("$ROOT/$DOWNLOADS", context.getString(R.string.tree_node_downloads))) {
          pagedChildren { _, _ -> downloadedBooksItems() }
        }
      }

    fun getRootItem(): ListenableFuture<LibraryResult<MediaItem>> =
      root.item
        .let { LibraryResult.ofItem(it, null) }
        .let { Futures.immediateFuture(it) }

    @OptIn(UnstableApi::class)
    fun getChildren(
      path: String,
      page: Int,
      pageSize: Int,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
      scope
        .listenableFuture {
          navigateTo(path)
            ?.listChildren(page, pageSize)
            ?.let { LibraryResult.ofItemList(it, null) }
            ?: LibraryResult.ofError(SessionError.INFO_CANCELLED)
        }

    @OptIn(UnstableApi::class)
    fun getItem(path: String): ListenableFuture<LibraryResult<MediaItem>> =
      scope
        .listenableFuture {
          when {
            path == ROOT || path.startsWith("$ROOT/") -> {
              navigateTo(path)?.item?.let { LibraryResult.ofItem(it, null) }
                ?: LibraryResult.ofError(SessionError.INFO_CANCELLED)
            }

            isBookPath(path) -> {
              fetchBookItem(parseBookId(path))?.let { LibraryResult.ofItem(it, null) }
                ?: LibraryResult.ofError(SessionError.INFO_CANCELLED)
            }

            else -> {
              LibraryResult.ofError(SessionError.INFO_CANCELLED)
            }
          }
        }

    fun searchBooks(query: String): ListenableFuture<List<MediaItem>> =
      scope
        .listenableFuture {
          preferences.getPreferredLibrary()?.id?.let { libraryId ->
            lissenMediaProvider
              .searchBooks(libraryId, query, limit = 20)
              .fold(
                onSuccess = { books -> books.map { bookItem(it) } },
                onFailure = { emptyList() },
              )
          } ?: emptyList()
        }

    // --- Navigation ---

    private suspend fun navigateTo(path: String): MediaTreeNode? {
      val segments = path.split("/")
      if (segments.isEmpty() || segments[0] != ROOT) return null
      return segments.drop(1).fold(root as MediaTreeNode?) { node, segment ->
        node?.child(segment)
      }
    }

    // --- Item builders ---

    private fun folderItem(
      id: String,
      title: String,
      mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
    ): MediaItem = buildMediaItem(id, title, mediaType, isPlayable = false, isBrowsable = true)

    private fun libraryFolderItem(
      id: String,
      library: Library,
    ): MediaItem =
      buildMediaItem(
        id = id,
        title = library.title,
        mediaType =
          when (library.type) {
            LibraryType.LIBRARY -> MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS
            LibraryType.PODCAST -> MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS
            LibraryType.UNKNOWN -> MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
          },
        isPlayable = false,
        isBrowsable = true,
      )

    private fun bookItem(
      id: String,
      title: String,
      author: String?,
    ): MediaItem =
      buildMediaItem(
        id = bookPath(id),
        title = title,
        artist = author,
        mediaType = MediaMetadata.MEDIA_TYPE_AUDIO_BOOK,
        isPlayable = true,
        isBrowsable = false,
        imageUri = ExternalCoverProvider.coverUri(id),
      )

    private fun bookItem(book: Book) = bookItem(book.id, book.title, book.author)

    private fun bookItem(book: DetailedItem) = bookItem(book.id, book.title, book.author)

    private fun bookItem(book: RecentBook) = bookItem(book.id, book.title, book.author)

    // --- Data fetchers ---

    private suspend fun continueListeningItems(): List<MediaItem> =
      preferences.getPlayingBook()?.let { listOf(bookItem(it)) } ?: emptyList()

    private suspend fun recentBooksItems(): List<MediaItem> =
      preferences.getPreferredLibrary()?.id?.let { libraryId ->
        lissenMediaProvider
          .fetchRecentListenedBooks(libraryId)
          .fold(
            onSuccess = { books -> books.map { bookItem(it) } },
            onFailure = { emptyList() },
          )
      } ?: emptyList()

    private suspend fun libraryItems(): List<MediaItem> =
      lissenMediaProvider
        .fetchLibraries()
        .fold(
          onSuccess = { libs -> libs.map { libraryFolderItem("$ROOT/$LIBRARY/${it.id}", it) } },
          onFailure = { emptyList() },
        )

    private suspend fun resolveLibrary(libId: String): Library? =
      lissenMediaProvider
        .fetchLibraries()
        .fold(
          onSuccess = { libs -> libs.find { it.id == libId } },
          onFailure = { null },
        )

    private suspend fun booksFromLibraryItems(
      libraryId: String,
      page: Int,
      pageSize: Int,
    ): List<MediaItem> =
      lissenMediaProvider
        .fetchBooks(libraryId = libraryId, pageSize = pageSize, pageNumber = page)
        .fold(
          onSuccess = { paged -> paged.items.map { bookItem(it) } },
          onFailure = { emptyList() },
        )

    private suspend fun downloadedBooksItems(): List<MediaItem> =
      localCacheRepository
        .fetchDetailedItems()
        .fold(
          onSuccess = { paged -> paged.items.map { bookItem(it) } },
          onFailure = { emptyList() },
        )

    private suspend fun fetchBookItem(bookId: String): MediaItem? =
      lissenMediaProvider
        .fetchBook(bookId)
        .fold(
          onSuccess = { bookItem(it) },
          onFailure = { null },
        )

    private fun buildMediaItem(
      id: String,
      title: String,
      mediaType: Int,
      isPlayable: Boolean,
      isBrowsable: Boolean,
      artist: String? = null,
      imageUri: Uri? = null,
    ): MediaItem =
      MediaItem
        .Builder()
        .setMediaId(id)
        .setMediaMetadata(
          MediaMetadata
            .Builder()
            .setTitle(title)
            .setArtist(artist)
            .setIsBrowsable(isBrowsable)
            .setIsPlayable(isPlayable)
            .setArtworkUri(imageUri)
            .setMediaType(mediaType)
            .build(),
        ).build()
  }
