package org.grakovne.lissen.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.grakovne.lissen.R
import org.grakovne.lissen.content.ExternalCoverProvider
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.RecentBook
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.util.listenableFuture
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

// --- Media Tree DSL ---

@DslMarker
annotation class MediaTreeDsl

class MediaTreeNode(
  val item: MediaItem,
  private val staticChildren: List<MediaTreeNode>,
  private val dynamicResolver: (suspend (String) -> MediaTreeNode?)?,
  private val childrenProvider: (suspend (Int, Int, MediaLibrarySession) -> List<MediaItem>)?,
) {
  suspend fun child(segment: String): MediaTreeNode? =
    staticChildren.find { it.item.mediaId.substringAfterLast('/') == segment }
      ?: dynamicResolver?.invoke(segment)

  suspend fun listChildren(
    page: Int,
    pageSize: Int,
    session: MediaLibraryService.MediaLibrarySession,
  ): List<MediaItem> =
    childrenProvider?.invoke(page, pageSize, session)
      ?: staticChildren.map { it.item }
}

@MediaTreeDsl
class MediaTreeBuilder {
  private val children = mutableListOf<MediaTreeNode>()
  private var resolveChild: (suspend (String) -> MediaTreeNode?)? = null
  private var pagedChildren: (suspend (Int, Int, MediaLibrarySession) -> List<MediaItem>)? = null

  operator fun MediaTreeNode.unaryPlus() {
    children += this
  }

  fun resolveChild(resolver: suspend (String) -> MediaTreeNode?) {
    resolveChild = resolver
  }

  fun pagedChildren(provider: suspend (Int, Int, MediaLibrarySession) -> List<MediaItem>) {
    pagedChildren = provider
  }

  fun build(item: MediaItem): MediaTreeNode = MediaTreeNode(item, children.toList(), resolveChild, pagedChildren)
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
    @param:ApplicationContext private val context: Context,
    private val playbackPreferences: PlaybackPreferences,
    private val libraryPreferences: LibraryPreferences,
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

    private val recentItemsCache = AtomicReference<List<MediaItem>>(emptyList())

    private suspend fun libraries(): List<Library> =
      lissenMediaProvider
        .fetchLibraries()
        .fold(
          onSuccess = { it },
          onFailure = { emptyList() },
        )

    private val root: MediaTreeNode by lazy { buildTree() }

    @OptIn(UnstableApi::class)
    private fun buildTree(): MediaTreeNode =
      mediaTreeNode(folderItem(ROOT, context.getString(R.string.tree_node_root))) {
        +mediaTreeNode(folderItem("$ROOT/$RECENT", context.getString(R.string.tree_node_recent))) {
          pagedChildren { _, _, session -> recentBooksItems(session) }
        }

        +mediaTreeNode(folderItem("$ROOT/$LIBRARY", context.getString(R.string.tree_node_library))) {
          pagedChildren { _, _ -> libraryItems() }
          resolveChild { libraryId ->
            resolveLibrary(libraryId)?.let { library ->
              mediaTreeNode(libraryFolderItem("$ROOT/$LIBRARY/$libraryId", library)) {
                +mediaTreeNode(folderItem("$ROOT/$LIBRARY/$libraryId/all", "By title")) {
                  pagedChildren { page, pageSize -> booksFromLibrary(libraryId = libraryId, page = page, pageSize = pageSize) }
                }
                library.filters?.genres?.let { genres ->
                  +mediaTreeNode(folderItem("$ROOT/$LIBRARY/$libraryId/genre", "By genre")) {
                    for (genre in genres) {
                      +mediaTreeNode(folderItem("$ROOT/$LIBRARY/$libraryId/genre/$genre", genre)) {
                        pagedChildren { page, pageSize ->
                          booksFromLibrary(
                            libraryId = libraryId,
                            page = page,
                            pageSize = pageSize,
                            extraFilter =
                              "genres" to genre,
                          )
                        }
                      }
                    }
                  }
                }
                library.filters?.tags?.let { tags ->
                  +mediaTreeNode(folderItem("$ROOT/$LIBRARY/$libraryId/tag", "By tag")) {
                    for (tag in tags) {
                      +mediaTreeNode(folderItem("$ROOT/$LIBRARY/$libraryId/tag/$tag", tag)) {
                        pagedChildren { page, pageSize ->
                          booksFromLibrary(
                            libraryId = libraryId,
                            page = page,
                            pageSize = pageSize,
                            extraFilter =
                              "tags" to tag,
                          )
                        }
                      }
                    }
                  }
                }
                library.filters?.authors?.let { authors ->
                  +mediaTreeNode(folderItem("$ROOT/$LIBRARY/$libraryId/author", "By author")) {
                    for (author in authors) {
                      +mediaTreeNode(folderItem("$ROOT/$LIBRARY/$libraryId/author/${author.id}", author.name)) {
                        pagedChildren { page, pageSize ->
                          booksFromLibrary(
                            libraryId = libraryId,
                            page = page,
                            pageSize = pageSize,
                            extraFilter =
                              "authors" to author.id,
                          )
                        }
                      }
                    }
                  }
                }
                library.filters?.series?.let { series ->
                  +mediaTreeNode(folderItem("$ROOT/$LIBRARY/$libraryId/series", "By series")) {
                    for (serie in series) {
                      +mediaTreeNode(folderItem("$ROOT/$LIBRARY/$libraryId/series/${serie.id}", serie.name)) {
                        pagedChildren { page, pageSize ->
                          booksFromLibrary(
                            libraryId = libraryId,
                            page = page,
                            pageSize = pageSize,
                            extraFilter =
                              "series" to serie.id,
                          )
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }

        +mediaTreeNode(folderItem("$ROOT/$DOWNLOADS", context.getString(R.string.tree_node_downloads))) {
          pagedChildren { page, pageSize, _ -> downloadedBooksItems(page, pageSize) }
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
      session: MediaLibrarySession,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
      scope
        .listenableFuture {
          navigateTo(path)
            ?.listChildren(page, pageSize, session)
            ?.let { LibraryResult.ofItemList(it, null) }
            ?: LibraryResult.ofError(SessionError.INFO_CANCELLED)
        }

    @OptIn(UnstableApi::class)
    fun getItem(path: String): ListenableFuture<LibraryResult<MediaItem>> =
      scope
        .listenableFuture {
          when {
            path.startsWith(ROOT) -> {
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
          libraryPreferences.getPreferredLibrary()?.id?.let { libraryId ->
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

    private fun recentBooksItems(session: MediaLibrarySession): List<MediaItem> =
      recentItemsCache.get().ifEmpty {
        val playingItem = playbackPreferences.getPlayingItem()
        val immediateItems = playingItem?.let { listOf(bookItem(it)) } ?: emptyList()
        scope.launch {
          val networkItems =
            libraryPreferences.getPreferredLibrary()?.id?.let { libraryId ->
              lissenMediaProvider
                .fetchRecentListenedBooks(libraryId)
                .fold(
                  onSuccess = { books ->
                    books
                      .asSequence()
                      .filter { it.id != playingItem?.id }
                      .map { bookItem(it) }
                      .toList()
                  },
                  onFailure = { emptyList() },
                )
            } ?: emptyList()

          val merged = immediateItems + networkItems
          recentItemsCache.set(merged)
          session.notifyChildrenChanged("$ROOT/$RECENT", merged.size, null)
        }
        immediateItems
      }

    private suspend fun libraryItems(): List<MediaItem> = libraries().map { libraryFolderItem("$ROOT/$LIBRARY/${it.id}", it) }

    private suspend fun resolveLibrary(libId: String): Library? = libraries().find { it.id == libId }

    private suspend fun booksFromLibrary(
      libraryId: String,
      page: Int,
      pageSize: Int,
      extraFilter: Pair<String, String>? = null,
    ): List<MediaItem> =
      lissenMediaProvider
        .fetchBooks(libraryId = libraryId, pageSize = pageSize, pageNumber = page, extraFilter = extraFilter)
        .fold(
          onSuccess = { paged -> paged.items.map { bookItem(it) } },
          onFailure = { emptyList() },
        )

    private suspend fun downloadedBooksItems(
      page: Int,
      pageSize: Int,
    ): List<MediaItem> =
      localCacheRepository
        .fetchDetailedItems(pageSize = pageSize, pageNumber = page)
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
