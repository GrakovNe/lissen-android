package org.grakovne.lissen.playback

import kotlinx.coroutines.flow.MutableStateFlow
import org.grakovne.lissen.common.EpisodeOrderingConfiguration
import org.grakovne.lissen.common.EpisodeOrderingOption
import org.grakovne.lissen.common.LibraryOrderingDirection
import org.grakovne.lissen.content.ordering.ChapterOrdering
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.BookmarkSyncState
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.DetailedItem.Companion.same
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.domain.PlayingChapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Mirrors the reorder flow and the bookmark coordinate translation of [MediaRepository]
 * over the real [ChapterOrdering] engine. The repository itself cannot be instantiated in
 * JVM unit tests (the media session token needs a live Android context), so the orchestration
 * around the ordering engine is reproduced here and asserted through plain state flows.
 */
class MediaRepositoryEpisodeOrderingTest {
  private fun chapter(
    id: String,
    index: Int,
    duration: Double,
    publishedAt: Long,
  ) = PlayingChapter(
    available = true,
    podcastEpisodeState = null,
    duration = duration,
    start = 0.0,
    end = duration,
    title = "Episode $id",
    id = id,
    index = index,
    publishedAt = publishedAt,
    season = null,
    episode = null,
    fileName = "$id.mp3",
  )

  /**
   * Canonical order c0 (30s), c1 (40s), c2 (50s): total 120s. The descending configuration
   * puts c2 first, so a position inside c1 moves by 20s and a position inside c0 moves to
   * the very end of the item.
   */
  private fun item(
    progress: MediaProgress? = null,
    id: String = "podcast",
  ): DetailedItem {
    val chapters =
      listOf(
        chapter(id = "c0", index = 0, duration = 30.0, publishedAt = 1L),
        chapter(id = "c1", index = 1, duration = 40.0, publishedAt = 2L),
        chapter(id = "c2", index = 2, duration = 50.0, publishedAt = 3L),
      )

    var accumulated = 0.0
    val bounded =
      chapters.map {
        val start = accumulated
        accumulated += it.duration
        it.copy(start = start, end = accumulated)
      }

    return DetailedItem(
      id = id,
      title = "Item",
      subtitle = null,
      author = null,
      narrator = null,
      publisher = null,
      series = emptyList(),
      year = null,
      abstract = null,
      files = bounded.map { BookFile(id = "file-${it.id}", name = it.title, duration = it.duration, size = null, mimeType = "audio/mpeg") },
      chapters = bounded,
      progress = progress,
      libraryId = "lib",
      libraryType = LibraryType.PODCAST,
      localProvided = false,
      createdAt = 0L,
      updatedAt = 0L,
    )
  }

  private fun descending() =
    EpisodeOrderingConfiguration(
      option = EpisodeOrderingOption.PUBLISHED_AT,
      direction = LibraryOrderingDirection.DESCENDING,
    )

  private fun bookmark(
    position: Double,
    itemId: String = "podcast",
  ) = Bookmark(
    libraryItemId = itemId,
    title = "note",
    totalPosition = position,
    createdAt = 1L,
    syncState = BookmarkSyncState.SYNCED,
  )

  // mirrors MediaRepository.reorderPlayingItem
  private fun reorderPlayingItem(
    configuration: EpisodeOrderingConfiguration?,
    playingBook: MutableStateFlow<DetailedItem?>,
    isPlaybackReady: MutableStateFlow<Boolean>,
    isPlaying: MutableStateFlow<Boolean>,
    totalPosition: MutableStateFlow<Double>,
    bookmarks: MutableStateFlow<List<Bookmark>>,
    playAfterPrepare: MutableStateFlow<Boolean>,
    controllerCalls: MutableList<String>,
    prepared: MutableList<DetailedItem>,
  ) {
    val book = playingBook.value ?: return

    if (isPlaybackReady.value.not()) return

    val wasPlaying = isPlaying.value
    val location = ChapterOrdering.locate(book, totalPosition.value)

    val reordered = ChapterOrdering.apply(book, configuration)
    if (reordered.same(book)) return

    controllerCalls.add("pause")
    isPlaybackReady.value = false

    val position =
      location
        ?.let { ChapterOrdering.position(reordered, it) }
        ?.let { position ->
          val total = reordered.chapters.sumOf { it.duration }
          position.coerceAtMost((total - RESTART_GUARD_SECONDS).coerceAtLeast(0.0))
        }

    val restored =
      position
        ?.let {
          reordered.copy(
            progress =
              MediaProgress(
                currentTime = it,
                isFinished = false,
                lastUpdate = System.currentTimeMillis(),
              ),
          )
        }
        ?: reordered

    bookmarks.value = bookmarks.value.map { it.copy(totalPosition = ChapterOrdering.translate(book, reordered, it.totalPosition)) }
    playAfterPrepare.value = wasPlaying
    prepared.add(restored)
    restored.progress?.let { totalPosition.value = it.currentTime }
  }

  // mirrors MediaRepository.inPlayingOrder
  private fun inPlayingOrder(
    bookmarks: List<Bookmark>,
    book: DetailedItem?,
  ): List<Bookmark> {
    if (book == null) return bookmarks

    return bookmarks.map {
      when (it.libraryItemId == book.id) {
        true -> it.copy(totalPosition = ChapterOrdering.fromCanonicalPosition(book, it.totalPosition))
        false -> it
      }
    }
  }

  // mirrors the coordinate handling of MediaRepository.dropBookmark
  private fun storedBookmark(
    playingBook: DetailedItem?,
    bookmark: Bookmark,
  ): Bookmark =
    when (playingBook?.id == bookmark.libraryItemId) {
      true -> bookmark.copy(totalPosition = ChapterOrdering.toCanonicalPosition(playingBook, bookmark.totalPosition))
      false -> bookmark
    }

  @Nested
  inner class ReorderPlayingItem {
    private lateinit var playingBook: MutableStateFlow<DetailedItem?>
    private lateinit var isPlaybackReady: MutableStateFlow<Boolean>
    private lateinit var isPlaying: MutableStateFlow<Boolean>
    private lateinit var totalPosition: MutableStateFlow<Double>
    private lateinit var bookmarks: MutableStateFlow<List<Bookmark>>
    private lateinit var playAfterPrepare: MutableStateFlow<Boolean>
    private val controllerCalls = mutableListOf<String>()
    private val prepared = mutableListOf<DetailedItem>()

    private fun state(
      book: DetailedItem?,
      ready: Boolean = true,
      playing: Boolean = false,
      position: Double = 0.0,
      stored: List<Bookmark> = emptyList(),
    ) {
      playingBook = MutableStateFlow(book)
      isPlaybackReady = MutableStateFlow(ready)
      isPlaying = MutableStateFlow(playing)
      totalPosition = MutableStateFlow(position)
      bookmarks = MutableStateFlow(stored)
      playAfterPrepare = MutableStateFlow(false)
    }

    private fun reorder(configuration: EpisodeOrderingConfiguration? = descending()) =
      reorderPlayingItem(
        configuration = configuration,
        playingBook = playingBook,
        isPlaybackReady = isPlaybackReady,
        isPlaying = isPlaying,
        totalPosition = totalPosition,
        bookmarks = bookmarks,
        playAfterPrepare = playAfterPrepare,
        controllerCalls = controllerCalls,
        prepared = prepared,
      )

    @Test
    fun `reorder keeps the listener on the same chapter and offset and rebuilds the queue`() {
      // 35s is 5s into c1; in the descending order c1 starts at 50s
      state(book = item(), position = 35.0)

      reorder()

      val restored = prepared.single()
      assertEquals(listOf("c2", "c1", "c0"), restored.chapters.map { it.id })
      assertEquals(listOf("file-c2", "file-c1", "file-c0"), restored.files.map { it.id })
      assertEquals(55.0, restored.progress?.currentTime)
      assertEquals(55.0, totalPosition.value)
      assertEquals(listOf("pause"), controllerCalls)
      assertEquals(false, isPlaybackReady.value)
    }

    @Test
    fun `restored position never lands in the restart guard window`() {
      // 28s is 28s into c0; c0 ends up last (90..120s) and 118s would look like "finished,
      // start over" to the service, so the position is clamped to total minus the threshold
      state(book = item(), position = 28.0)

      reorder()

      assertEquals(115.0, prepared.single().progress?.currentTime)
      assertEquals(115.0, totalPosition.value)
    }

    @Test
    fun `a second tap while the queue is rebuilding is ignored`() {
      state(book = item(), ready = false, position = 35.0)

      reorder()

      assertTrue(prepared.isEmpty())
      assertTrue(controllerCalls.isEmpty())
      assertEquals(35.0, totalPosition.value)
    }

    @Test
    fun `the default ordering over the canonical item does not touch playback`() {
      state(book = item(), playing = true, position = 35.0)

      reorder(configuration = EpisodeOrderingConfiguration.default)

      assertTrue(prepared.isEmpty())
      assertTrue(controllerCalls.isEmpty())
      assertEquals(35.0, totalPosition.value)
      assertEquals(true, isPlaying.value)
    }

    @Test
    fun `reorder without a playing item does nothing`() {
      state(book = null)

      reorder()

      assertTrue(prepared.isEmpty())
      assertTrue(controllerCalls.isEmpty())
    }

    @Test
    fun `reorder resumes playback after the queue is rebuilt when it was playing`() {
      state(book = item(), playing = true, position = 35.0)

      reorder()

      assertEquals(true, playAfterPrepare.value)
    }

    @Test
    fun `reorder stays paused after the queue is rebuilt when it was paused`() {
      state(book = item(), playing = false, position = 35.0)

      reorder()

      assertEquals(false, playAfterPrepare.value)
    }

    @Test
    fun `bookmarks move with the chapters they point at`() {
      state(book = item(), position = 35.0, stored = listOf(bookmark(position = 28.0)))

      reorder()

      // 28s into c0, which the descending order moves to the end of the item
      assertEquals(118.0, bookmarks.value.single().totalPosition)
    }
  }

  @Nested
  inner class BookmarkCoordinates {
    @Test
    fun `bookmarks from the channel are translated into the playing order`() {
      val reordered = ChapterOrdering.apply(item(), descending())

      // 15s canonical is 15s into c0, which starts at 90s in the descending order
      val translated = inPlayingOrder(listOf(bookmark(position = 15.0)), reordered)

      assertEquals(105.0, translated.single().totalPosition)
    }

    @Test
    fun `a created bookmark is sent in canonical coordinates`() {
      val reordered = ChapterOrdering.apply(item(), descending())

      val sent = ChapterOrdering.toCanonicalPosition(reordered, 105.0)

      assertEquals(15.0, sent)
    }

    @Test
    fun `a dropped bookmark is deleted at its canonical position`() {
      val reordered = ChapterOrdering.apply(item(), descending())

      val stored = storedBookmark(playingBook = reordered, bookmark = bookmark(position = 105.0))

      assertEquals(15.0, stored.totalPosition)
    }

    @Test
    fun `a bookmark of another item is passed through untranslated`() {
      val reordered = ChapterOrdering.apply(item(), descending())

      val stored = storedBookmark(playingBook = reordered, bookmark = bookmark(position = 105.0, itemId = "other"))
      val translated = inPlayingOrder(listOf(bookmark(position = 105.0, itemId = "other")), reordered)

      assertEquals(105.0, stored.totalPosition)
      assertEquals(105.0, translated.single().totalPosition)
    }

    @Test
    fun `bookmarks survive a round trip through the canonical order`() {
      val reordered = ChapterOrdering.apply(item(), descending())

      listOf(0.0, 15.0, 55.0, 105.0, 119.0).forEach { position ->
        val canonical = ChapterOrdering.toCanonicalPosition(reordered, position)
        assertEquals(position, ChapterOrdering.fromCanonicalPosition(reordered, canonical), "round trip of ${position}s")
      }
    }
  }

  private companion object {
    // mirrors MediaRepository.RESTART_GUARD_SECONDS
    private const val RESTART_GUARD_SECONDS = 5.0
  }
}
