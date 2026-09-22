package org.grakovne.lissen.playback.service

import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlaybackSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncStateTest {
  private val book = item("book")
  private val other = item("other-book")

  private fun item(id: String) =
    DetailedItem(
      id = id,
      title = "Dune",
      subtitle = null,
      author = "Frank Herbert",
      narrator = null,
      publisher = null,
      series = emptyList(),
      year = null,
      abstract = null,
      files = emptyList(),
      chapters = emptyList(),
      progress = null,
      libraryId = "lib",
      libraryType = null,
      localProvided = true,
      createdAt = 0L,
      updatedAt = 0L,
    )

  private fun playing(
    session: PlaybackSession? = null,
    chapterIndex: Int = 1,
  ) = SyncState().start(book).let { state -> session?.let { state.adopt(it, chapterIndex) } ?: state }

  @Test
  fun `start remembers the item and forgets the previous session and chapter`() {
    val started = playing(PlaybackSession.local("book")).start(other)

    assertEquals(SyncState(item = other), started)
  }

  @Test
  fun `cancel drops everything`() {
    assertEquals(SyncState(), playing(PlaybackSession.local("book")).cancel())
  }

  @Test
  fun `adopting a session for another item is ignored`() {
    val state = playing()

    assertSame(state, state.adopt(PlaybackSession.local("other-book"), 1))
  }

  @Test
  fun `adopting on an empty state is ignored`() {
    assertEquals(SyncState(), SyncState().adopt(PlaybackSession.remote("r", "book"), 1))
  }

  @Test
  fun `adopting records the chapter the session was opened for`() {
    val remote = PlaybackSession.remote("remote", "book")

    val state = playing().adopt(remote, 3)

    assertEquals(remote, state.session)
    assertEquals(3, state.chapterIndex)
  }

  @Test
  fun `failed retry on the same chapter keeps the existing local session`() {
    val previous = PlaybackSession.local("book")
    val state = playing(previous, chapterIndex = 1)

    assertSame(state, state.adopt(PlaybackSession.local("book"), 1))
  }

  @Test
  fun `chapter transition adopts a fresh local session`() {
    val next = PlaybackSession.local("book")

    val state = playing(PlaybackSession.local("book"), chapterIndex = 1).adopt(next, 2)

    assertEquals(next, state.session)
    assertEquals(2, state.chapterIndex)
  }

  @Test
  fun `remote recovery replaces the local session`() {
    val remote = PlaybackSession.remote("remote", "book")

    val state = playing(PlaybackSession.local("book"), chapterIndex = 1).adopt(remote, 1)

    assertEquals(remote, state.session)
  }

  @Test
  fun `a dead remote session is replaced by a local one even on the same chapter`() {
    val local = PlaybackSession.local("book")

    val state = playing(PlaybackSession.remote("remote", "book"), chapterIndex = 1).adopt(local, 1)

    assertEquals(local, state.session)
  }

  @Test
  fun `releaseLocal clears a local session and keeps a remote one`() {
    val remote = PlaybackSession.remote("remote", "book")

    assertNull(playing(PlaybackSession.local("book")).releaseLocal().session)
    assertEquals(remote, playing(remote).releaseLocal().session)
  }

  @Test
  fun `localSession is only a local one`() {
    val local = PlaybackSession.local("book")

    assertEquals(local, playing(local).localSession)
    assertNull(playing(PlaybackSession.remote("remote", "book")).localSession)
  }

  @Test
  fun `session is stale without a session, for another item or another chapter`() {
    val state = playing(PlaybackSession.remote("remote", "book"), chapterIndex = 1)

    assertTrue(playing().sessionStale("book", 1))
    assertTrue(state.sessionStale("other-book", 1))
    assertTrue(state.sessionStale("book", 2))
    assertFalse(state.sessionStale("book", 1))
  }

  @Test
  fun `store applies transitions and exposes the result`() {
    val store = SyncStateStore()

    val updated = store.update { it.start(book) }

    assertEquals(SyncState(item = book), updated)
    assertEquals(updated, store.value)
    assertEquals(updated, store.state.value)
  }
}
