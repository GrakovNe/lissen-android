package org.grakovne.lissen.playback

import kotlinx.coroutines.flow.MutableStateFlow
import org.grakovne.lissen.common.EpisodeOrderingConfiguration
import org.grakovne.lissen.content.ordering.ChapterOrdering
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.DetailedItem.Companion.same
import org.grakovne.lissen.domain.MediaProgress
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
      state(book = podcast(), position = 35.0)

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
      state(book = podcast(), position = 28.0)

      reorder()

      assertEquals(115.0, prepared.single().progress?.currentTime)
      assertEquals(115.0, totalPosition.value)
    }

    @Test
    fun `a second tap while the queue is rebuilding is ignored`() {
      state(book = podcast(), ready = false, position = 35.0)

      reorder()

      assertTrue(prepared.isEmpty())
      assertTrue(controllerCalls.isEmpty())
      assertEquals(35.0, totalPosition.value)
    }

    @Test
    fun `the default ordering over the canonical item does not touch playback`() {
      state(book = podcast(), playing = true, position = 35.0)

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
      state(book = podcast(), playing = true, position = 35.0)

      reorder()

      assertEquals(true, playAfterPrepare.value)
    }

    @Test
    fun `reorder stays paused after the queue is rebuilt when it was paused`() {
      state(book = podcast(), playing = false, position = 35.0)

      reorder()

      assertEquals(false, playAfterPrepare.value)
    }

    @Test
    fun `bookmarks move with the chapters they point at`() {
      state(book = podcast(), position = 35.0, stored = listOf(bookmark(position = 28.0)))

      reorder()

      // 28s into c0, which the descending order moves to the end of the item
      assertEquals(118.0, bookmarks.value.single().totalPosition)
    }
  }

  private companion object {
    // mirrors MediaRepository.RESTART_GUARD_SECONDS
    private const val RESTART_GUARD_SECONDS = 5.0
  }
}
