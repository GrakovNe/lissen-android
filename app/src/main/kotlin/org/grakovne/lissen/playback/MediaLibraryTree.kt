package org.grakovne.lissen.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
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
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.lib.domain.RecentBook
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.util.asListenableFuture
import javax.inject.Inject
import javax.inject.Singleton

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
    private var treeNodes: MutableMap<String, MediaItemNode> = mutableMapOf()

    companion object {
      private val ROOT_ID = "[rootID]"

      private val CONTINUE_ID = "[continueID]"
      private val RECENT_ID = "[recentID]"
      private val LIBRARY_ID = "[libraryID]"
      private val DOWNLOADS_ID = "[downloadsID]"
      private val BOOK_ID = "[bookID]"
    }

    class MediaItemNode(
      val item: MediaItem,
      val tree: MediaLibraryTree,
    ) {
      private val children: MutableList<MediaItem> = ArrayList()

      fun addChild(childID: String) = children.add(tree.treeNodes[childID]!!.item)

      fun getChildren(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        Futures.immediateFuture(LibraryResult.ofItemList(children, null))
    }

    private fun bookToMediaItem(book: Book) =
      buildMediaItem(
        title = book.title,
        artist = book.author,
        mediaId = "$BOOK_ID${book.id}",
        isPlayable = true,
        isBrowsable = true,
        mediaType = MediaMetadata.MEDIA_TYPE_AUDIO_BOOK,
        imageUri = ExternalCoverProvider.coverUri(book.id, 900),
      )

    private fun bookToMediaItem(book: DetailedItem) =
      buildMediaItem(
        title = book.title,
        artist = book.author,
        mediaId = "$BOOK_ID${book.id}",
        isPlayable = true,
        isBrowsable = true,
        mediaType = MediaMetadata.MEDIA_TYPE_AUDIO_BOOK,
        imageUri = ExternalCoverProvider.coverUri(book.id, 900),
      )

    private fun bookToMediaItem(book: RecentBook) =
      buildMediaItem(
        title = book.title,
        artist = book.author,
        mediaId = "$BOOK_ID${book.id}",
        isPlayable = true,
        isBrowsable = true,
        mediaType = MediaMetadata.MEDIA_TYPE_AUDIO_BOOK,
        imageUri = ExternalCoverProvider.coverUri(book.id, 900),
      )

    private fun buildMediaItem(
      title: String,
      mediaId: String,
      isPlayable: Boolean,
      isBrowsable: Boolean,
      mediaType: @MediaMetadata.MediaType Int,
      subtitleConfigurations: List<SubtitleConfiguration> = mutableListOf(),
      album: String? = null,
      artist: String? = null,
      genre: String? = null,
      sourceUri: Uri? = null,
      imageUri: Uri? = null,
    ): MediaItem {
      val metadata =
        MediaMetadata
          .Builder()
          .setAlbumTitle(album)
          .setTitle(title)
          .setArtist(artist)
          .setGenre(genre)
          .setIsBrowsable(isBrowsable)
          .setIsPlayable(isPlayable)
          .setArtworkUri(imageUri)
          .setMediaType(mediaType)
          .build()

      return MediaItem
        .Builder()
        .setMediaId(mediaId)
        .setSubtitleConfigurations(subtitleConfigurations)
        .setMediaMetadata(metadata)
        .setUri(sourceUri)
        .build()
    }

    init {
      treeNodes[ROOT_ID] =
        MediaItemNode(
          buildMediaItem(
            title = context.getString(R.string.tree_node_root),
            mediaId = ROOT_ID,
            isPlayable = false,
            isBrowsable = true,
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
          ),
          this,
        )
      treeNodes[CONTINUE_ID] =
        MediaItemNode(
          buildMediaItem(
            title = context.getString(R.string.tree_node_continue),
            mediaId = CONTINUE_ID,
            isPlayable = false,
            isBrowsable = true,
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
          ),
          this,
        )

      treeNodes[RECENT_ID] =
        MediaItemNode(
          buildMediaItem(
            title = context.getString(R.string.tree_node_recent),
            mediaId = RECENT_ID,
            isPlayable = false,
            isBrowsable = true,
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
          ),
          this,
        )

      treeNodes[LIBRARY_ID] =
        MediaItemNode(
          buildMediaItem(
            title = context.getString(R.string.tree_node_library),
            mediaId = LIBRARY_ID,
            isPlayable = false,
            isBrowsable = true,
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
          ),
          this,
        )

      treeNodes[DOWNLOADS_ID] =
        MediaItemNode(
          buildMediaItem(
            title = context.getString(R.string.tree_node_downloads),
            mediaId = DOWNLOADS_ID,
            isPlayable = false,
            isBrowsable = true,
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
          ),
          this,
        )

      val root = requireNotNull(treeNodes[ROOT_ID])

      root.addChild(CONTINUE_ID)
      root.addChild(RECENT_ID)
      root.addChild(LIBRARY_ID)
      root.addChild(DOWNLOADS_ID)
    }

    fun getRootItem(): ListenableFuture<LibraryResult<MediaItem>> =
      Futures.immediateFuture(LibraryResult.ofItem(treeNodes[ROOT_ID]!!.item, null))

    val futureScope = CoroutineScope(Dispatchers.Default)

    @OptIn(UnstableApi::class)
    fun getLibraries() =
      futureScope
        .future {
          val libraries =
            lissenMediaProvider
              .fetchLibraries()
              .fold(
                onSuccess = { libs ->
                  libs.map { lib ->
                    buildMediaItem(
                      title = lib.title,
                      mediaId = "$LIBRARY_ID${lib.id}",
                      isPlayable = false,
                      isBrowsable = true,
                      mediaType =
                        when (lib.type) {
                          LibraryType.LIBRARY -> MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS
                          LibraryType.PODCAST -> MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS
                          LibraryType.UNKNOWN -> MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                        },
                    )
                  }
                },
                onFailure = { listOf() },
              )
          LibraryResult.ofItemList(libraries, null)
        }.asListenableFuture()

    fun getBooksFromLibrary(
      libId: String,
      pageSize: Int,
      pageNumber: Int,
    ) = futureScope
      .future {
        lissenMediaProvider
          .fetchBooks(
            libId,
            pageSize,
            pageNumber,
          ).fold(
            onSuccess = {
              it.items
                .map {
                  bookToMediaItem(it)
                }
            },
            onFailure = { listOf() },
          ).let { LibraryResult.ofItemList(it, null) }
      }.asListenableFuture()

    private suspend fun getBookItem(bookId: String) =
      lissenMediaProvider
        .fetchBook(
          bookId,
        ).fold(
          onSuccess = { bookToMediaItem(it) },
          onFailure = { null },
        )

    // TODO: return chapters, not a single book
    @OptIn(UnstableApi::class)
    fun getBook(bookId: String) =
      futureScope
        .future {
          getBookItem(bookId)?.let {
            LibraryResult.ofItemList(listOf(it), null)
          } ?: LibraryResult.ofError(SessionError.INFO_CANCELLED)
        }.asListenableFuture()

    @OptIn(UnstableApi::class)
    fun getBookSingle(bookId: String) =
      futureScope
        .future {
          getBookItem(bookId)?.let {
            LibraryResult.ofItem(it, null)
          } ?: LibraryResult.ofError(SessionError.INFO_CANCELLED)
        }.asListenableFuture()

    private fun getContinueListening(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
      futureScope
        .future {
          preferences
            .getPlayingBook()
            ?.let {
              LibraryResult.ofItemList(listOf(bookToMediaItem(it)), null)
            }
            ?: LibraryResult.ofItemList(emptyList(), null)
        }.asListenableFuture()

    private fun getRecentBooks(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
      futureScope
        .future {
          preferences.getPreferredLibrary()?.id?.let { libraryId ->
            lissenMediaProvider
              .fetchRecentListenedBooks(libraryId)
              .fold(
                onSuccess = {
                  it
                    .map {
                      bookToMediaItem(it)
                    }
                },
                onFailure = { emptyList() },
              ).let { LibraryResult.ofItemList(it, null) }
          } ?: LibraryResult.ofItemList(emptyList<MediaItem>(), null)
        }.asListenableFuture()

    private fun getDownloadedBooks(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
      futureScope
        .future {
          localCacheRepository
            .fetchDetailedItems()
            .fold(
              onSuccess = {
                it.items
                  .map {
                    bookToMediaItem(it)
                  }
              },
              onFailure = { emptyList() },
            ).let {
              LibraryResult.ofItemList(it, null)
            }
        }.asListenableFuture()

    fun searchBooks(query: String): ListenableFuture<List<MediaItem>> =
      futureScope
        .future {
          preferences.getPreferredLibrary()?.id?.let { libraryId ->
            lissenMediaProvider
              .searchBooks(libraryId, query, limit = 20)
              .fold(
                onSuccess = {
                  it
                    .map {
                      bookToMediaItem(it)
                    }
                },
                onFailure = { emptyList() },
              )
          } ?: emptyList()
        }.asListenableFuture()

    @OptIn(UnstableApi::class)
    fun getChildren(
      id: String,
      pageSize: Int,
      pageNumber: Int,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
      when (id) {
        ROOT_ID -> {
          treeNodes[ROOT_ID]!!.getChildren()
        }

        CONTINUE_ID -> {
          getContinueListening()
        }

        RECENT_ID -> {
          getRecentBooks()
        }

        LIBRARY_ID -> {
          getLibraries()
        }

        DOWNLOADS_ID -> {
          getDownloadedBooks()
        }

        else -> {
          if (id.startsWith(LIBRARY_ID)) {
            getBooksFromLibrary(id.removePrefix(LIBRARY_ID), pageNumber, pageSize)
          } else if (id.startsWith(BOOK_ID)) {
            getBook(id.removePrefix(BOOK_ID))
          } else {
            Futures.immediateFuture(LibraryResult.ofError(SessionError.INFO_CANCELLED))
          }
        }
      }

    @OptIn(UnstableApi::class)
    fun getItem(mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
      if (mediaId in treeNodes) {
        return Futures.immediateFuture(LibraryResult.ofItem(treeNodes[mediaId]!!.item, null))
      } else if (mediaId.startsWith(LIBRARY_ID)) {
        val libId = mediaId.removePrefix(LIBRARY_ID)
        return futureScope
          .future {
            lissenMediaProvider
              .fetchLibraries()
              .fold(
                onSuccess = { it.find { it.id == libId } },
                onFailure = { null },
              )?.let {
                buildMediaItem(
                  title = it.title,
                  mediaId = mediaId,
                  isPlayable = false,
                  isBrowsable = true,
                  mediaType =
                    if (it.type ==
                      LibraryType.LIBRARY
                    ) {
                      MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS
                    } else {
                      MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS
                    },
                ).let { LibraryResult.ofItem(it, null) }
              }
              ?: LibraryResult.ofError(SessionError.INFO_CANCELLED)
          }.asListenableFuture()
      } else if (mediaId.startsWith(BOOK_ID)) {
        val bookId = mediaId.removePrefix(BOOK_ID)
        return getBookSingle(bookId)
      } else {
        return Futures.immediateFuture(LibraryResult.ofError(SessionError.INFO_CANCELLED))
      }
    }
  }
