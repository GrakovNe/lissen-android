package org.grakovne.lissen.playback.service

import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.OfflineSessionOwner
import org.grakovne.lissen.domain.PlaybackSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SyncStateTest {
  private val owner = OfflineSessionOwner("https://abs.example", "reader")
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

  private fun playing(session: PlaybackSession? = null) =
    SyncState().start(book, owner).let { state ->
      session?.let { state.adopt(it, LocalSessionPolicy.REPLACE) }
        ?: state
    }

  @Nested
  inner class Transitions {
    @Test
    fun `start remembers the item and the owner and forgets the previous session`() {
      val started = playing(PlaybackSession.local("book")).start(other, owner)

      assertSame(other, started.item)
      assertEquals(owner, started.owner)
      assertNull(started.session)
      assertNull(started.chapterIndex)
    }

    @Test
    fun `cancel drops everything`() {
      assertEquals(SyncState(), playing(PlaybackSession.local("book")).withChapter(2).cancel())
    }

    @Test
    fun `adopting a session for another item is ignored`() {
      val state = playing()

      assertSame(state, state.adopt(PlaybackSession.local("other-book"), LocalSessionPolicy.REPLACE))
    }

    @Test
    fun `adopting on an empty state is ignored`() {
      assertEquals(SyncState(), SyncState().adopt(PlaybackSession.remote("r", "book"), LocalSessionPolicy.REPLACE))
    }

    @Test
    fun `failed retry keeps the existing local session`() {
      val previous = PlaybackSession.local("book")

      val state = playing(previous).adopt(PlaybackSession.local("book"), LocalSessionPolicy.KEEP)

      assertEquals(previous, state.session)
    }

    @Test
    fun `chapter transition adopts a fresh local session`() {
      val next = PlaybackSession.local("book")

      val state = playing(PlaybackSession.local("book")).adopt(next, LocalSessionPolicy.REPLACE)

      assertEquals(next, state.session)
    }

    @Test
    fun `remote recovery replaces the local session`() {
      val remote = PlaybackSession.remote("remote", "book")

      val state = playing(PlaybackSession.local("book")).adopt(remote, LocalSessionPolicy.KEEP)

      assertEquals(remote, state.session)
    }

    @Test
    fun `releaseLocal clears a local session and keeps a remote one`() {
      val remote = PlaybackSession.remote("remote", "book")

      assertNull(playing(PlaybackSession.local("book")).releaseLocal().session)
      assertEquals(remote, playing(remote).releaseLocal().session)
    }

    @Test
    fun `session is stale without a session, for another item or another chapter`() {
      val state = playing(PlaybackSession.remote("remote", "book")).withChapter(1)

      assertTrue(playing().sessionStale("book", 1))
      assertTrue(state.sessionStale("other-book", 1))
      assertTrue(state.sessionStale("book", 2))
      assertFalse(state.sessionStale("book", 1))
    }
  }

  @Nested
  inner class Effects {
    @Test
    fun `nothing changes for the uploader between remote sessions`() {
      val before = playing(PlaybackSession.remote("a", "book"))
      val after = before.adopt(PlaybackSession.remote("b", "book"), LocalSessionPolicy.REPLACE)

      assertEquals(emptyList<UploaderEffect>(), uploaderEffects(before, after))
    }

    @Test
    fun `a new local session is activated`() {
      val local = PlaybackSession.local("book")
      val before = playing()
      val after = before.adopt(local, LocalSessionPolicy.REPLACE)

      assertEquals(listOf(UploaderEffect.Activate(local.sessionId)), uploaderEffects(before, after))
    }

    @Test
    fun `keeping the local session on a retry has no effect`() {
      val local = PlaybackSession.local("book")
      val before = playing(local)
      val after = before.adopt(PlaybackSession.local("book"), LocalSessionPolicy.KEEP)

      assertEquals(emptyList<UploaderEffect>(), uploaderEffects(before, after))
    }

    @Test
    fun `remote recovery releases the local session`() {
      val local = PlaybackSession.local("book")
      val before = playing(local)
      val after = before.adopt(PlaybackSession.remote("remote", "book"), LocalSessionPolicy.KEEP)

      assertEquals(listOf(UploaderEffect.Release(local.sessionId)), uploaderEffects(before, after))
    }

    @Test
    fun `chapter change releases the old row before activating the new one`() {
      val previous = PlaybackSession.local("book")
      val next = PlaybackSession.local("book")
      val before = playing(previous)
      val after = before.adopt(next, LocalSessionPolicy.REPLACE)

      assertEquals(
        listOf(UploaderEffect.Release(previous.sessionId), UploaderEffect.Activate(next.sessionId)),
        uploaderEffects(before, after),
      )
    }

    @Test
    fun `pause, cancel and a new item all release the local session`() {
      val local = PlaybackSession.local("book")
      val before = playing(local)
      val release = listOf(UploaderEffect.Release(local.sessionId))

      assertEquals(release, uploaderEffects(before, before.releaseLocal()))
      assertEquals(release, uploaderEffects(before, before.cancel()))
      assertEquals(release, uploaderEffects(before, before.start(other, owner)))
    }

    @Test
    fun `a release that ran before a late activation is not undone`() {
      // the cancel from the main thread wins: the late adoption sees no item and stays a no-op
      val cancelled = playing().cancel()
      val late = cancelled.adopt(PlaybackSession.local("book"), LocalSessionPolicy.REPLACE)

      assertEquals(emptyList<UploaderEffect>(), uploaderEffects(cancelled, late))
    }
  }
}
