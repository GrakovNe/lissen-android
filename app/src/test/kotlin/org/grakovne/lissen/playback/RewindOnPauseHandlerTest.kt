package org.grakovne.lissen.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.domain.RewindOnPauseTime
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.service.RewindTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class RewindOnPauseHandlerTest {
  private val preferences = mockk<PlaybackPreferences>(relaxed = true)
  private val handler = RewindOnPauseHandler(preferences)

  private val chapterDurations = listOf(60_000L, 60_000L, 60_000L)

  private fun decide(
    setting: RewindOnPauseTime = RewindOnPauseTime(enabled = true, seconds = 30),
    storedLastActiveMillis: Long? = 100_000L,
    nowMillis: Long = 200_000L,
    chapterIndex: Int = 1,
    positionMillis: Long = 50_000L,
    chapterDurationsMillis: List<Long> = chapterDurations,
  ): RewindTarget? =
    decideRewindTarget(
      setting = setting,
      storedLastActiveMillis = storedLastActiveMillis,
      nowMillis = nowMillis,
      chapterIndex = chapterIndex,
      positionMillis = positionMillis,
      chapterDurationsMillis = chapterDurationsMillis,
    )

  @Nested
  inner class DecideRewindTarget {
    @Test
    fun `disabled setting means no rewind`() {
      assertNull(decide(setting = RewindOnPauseTime(enabled = false, seconds = 30)))
    }

    @Test
    fun `missing stored timestamp applies the full rewind`() {
      assertEquals(
        RewindTarget(chapterIndex = 1, positionMillis = 20_000L),
        decide(storedLastActiveMillis = null),
      )
    }

    @Test
    fun `pause within the window rewinds proportionally`() {
      // 150s of the 300s window with a 30s setting = 15s rewind.
      assertEquals(
        RewindTarget(chapterIndex = 1, positionMillis = 35_000L),
        decide(storedLastActiveMillis = 50_000L, nowMillis = 200_000L),
      )
    }

    @Test
    fun `pause at the full window applies the full rewind`() {
      assertEquals(
        RewindTarget(chapterIndex = 1, positionMillis = 20_000L),
        decide(storedLastActiveMillis = 0L, nowMillis = 300_000L),
      )
    }

    @Test
    fun `rewind can cross a chapter start`() {
      // Full 30s rewind from 10s into chapter 1: 10s back to its start,
      // then 20s into the end of the 60s-long chapter 0.
      assertEquals(
        RewindTarget(chapterIndex = 0, positionMillis = 40_000L),
        decide(storedLastActiveMillis = null, chapterIndex = 1, positionMillis = 10_000L),
      )
    }

    @Test
    fun `rewind clamps at the start of the book`() {
      assertEquals(
        RewindTarget(chapterIndex = 0, positionMillis = 0L),
        decide(storedLastActiveMillis = null, chapterIndex = 0, positionMillis = 5_000L),
      )
    }

    @Test
    fun `already at the book start means no seek`() {
      assertNull(decide(chapterIndex = 0, positionMillis = 0L))
    }

    @Test
    fun `stored timestamp in the future still applies the five second floor`() {
      // Clock skew can make the stored timestamp look like the future, which
      // reads as a zero-length pause and therefore gets the five second floor.
      assertEquals(
        RewindTarget(chapterIndex = 1, positionMillis = 45_000L),
        decide(storedLastActiveMillis = 300_000L, nowMillis = 200_000L),
      )
    }

    @Test
    fun `chapter index outside the timeline means no rewind`() {
      assertNull(decide(chapterIndex = -1))
      assertNull(decide(chapterIndex = 3))
    }

    @Test
    fun `empty chapter list means no rewind`() {
      assertNull(decide(chapterDurationsMillis = emptyList()))
    }

    @Test
    fun `chapter with a negative duration means no rewind`() {
      assertNull(decide(chapterDurationsMillis = listOf(60_000L, -1L)))
    }
  }

  @Nested
  inner class Attach {
    @Test
    fun `attach registers the handler on the player`() {
      val player = mockk<Player>(relaxed = true)

      handler.attach(player)

      verify { player.addListener(handler) }
    }

    @Test
    fun `attach is idempotent for the same player`() {
      val player = mockk<Player>(relaxed = true)

      handler.attach(player)
      handler.attach(player)

      verify(exactly = 1) { player.addListener(handler) }
    }
  }

  @Nested
  inner class Listener {
    @Test
    fun `playback start tracks and marks the playing item`() {
      // The resume path does not go through applyRewind (e.g. audio focus
      // regain), so the playing item is the fallback source of the book.
      every { preferences.getPlayingItem() } returns book("book-1")

      handler.onIsPlayingChanged(true)

      verify { preferences.markBookLastActive("book-1", any()) }
    }

    @Test
    fun `playback stop marks the tracked book`() {
      every { preferences.getPlayingItem() } returns book("book-1")
      handler.onIsPlayingChanged(true)

      handler.onIsPlayingChanged(false)

      verify(exactly = 2) { preferences.markBookLastActive("book-1", any()) }
    }

    @Test
    fun `stop during a book switch marks the stopping book while the playing item already holds the next one`() {
      // Regression: switching books saves the new playing item before the
      // player stops the old one, so preferences already hold B while the
      // player is still stopping A. The stop must mark A, not B.
      every { preferences.getPlayingItem() } returns book("book-A")
      handler.onIsPlayingChanged(true)

      every { preferences.getPlayingItem() } returns book("book-B")
      handler.onIsPlayingChanged(false)

      verify(exactly = 2) { preferences.markBookLastActive("book-A", any()) }
      verify(exactly = 0) { preferences.markBookLastActive("book-B", any()) }
    }

    @Test
    fun `missing playing item on start keeps the previously tracked book`() {
      every { preferences.getPlayingItem() } returns book("book-A")
      handler.onIsPlayingChanged(true)

      every { preferences.getPlayingItem() } returns null
      handler.onIsPlayingChanged(true)
      handler.onIsPlayingChanged(false)

      verify(exactly = 3) { preferences.markBookLastActive("book-A", any()) }
    }

    @Test
    fun `no tracked book and no playing item means nothing is marked`() {
      every { preferences.getPlayingItem() } returns null

      handler.onIsPlayingChanged(true)
      handler.onIsPlayingChanged(false)

      verify(exactly = 0) { preferences.markBookLastActive(any(), any()) }
    }
  }

  @Nested
  inner class SuppressionResume {
    @Test
    fun `stop with a suppression reason rewinds on the automatic resume`() {
      val player = mockk<Player>(relaxed = true)
      every { player.playbackSuppressionReason } returns Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
      handler.attach(player)

      every { preferences.getPlayingItem() } returns book("book-1", listOf(chapter(60.0), chapter(60.0)))
      handler.onIsPlayingChanged(true)

      // Transient audio focus loss: playWhenReady stays true, so the player
      // stops and resumes by itself without a play() reaching the handler.
      handler.onIsPlayingChanged(false)

      every { player.currentMediaItemIndex } returns 1
      every { player.currentPosition } returns 50_000L
      every { preferences.getRewindOnPauseTime() } returns RewindOnPauseTime(enabled = true, seconds = 30)
      every { preferences.getBookLastActive("book-1") } returns null

      handler.onIsPlayingChanged(true)

      verify { player.seekTo(1, 20_000L) }
    }

    @Test
    fun `stop without a suppression reason does not rewind on resume`() {
      // A normal pause or a buffering stall stops playback with the reason
      // still NONE, which must not arm the rewind.
      val player = mockk<Player>(relaxed = true)
      handler.attach(player)

      every { preferences.getPlayingItem() } returns book("book-1", listOf(chapter(60.0), chapter(60.0)))
      handler.onIsPlayingChanged(true)
      handler.onIsPlayingChanged(false)
      handler.onIsPlayingChanged(true)

      verify(exactly = 0) { player.seekTo(any(), any()) }
    }

    @Test
    fun `a play between the suppressed stop and the resume applies the rewind once`() {
      val player = mockk<Player>(relaxed = true)
      every { player.playbackSuppressionReason } returns Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
      handler.attach(player)

      every { preferences.getPlayingItem() } returns book("book-1", listOf(chapter(60.0), chapter(60.0)))
      handler.onIsPlayingChanged(true)

      handler.onIsPlayingChanged(false)

      every { player.currentMediaItemIndex } returns 1
      every { player.currentPosition } returns 50_000L
      every { preferences.getRewindOnPauseTime() } returns RewindOnPauseTime(enabled = true, seconds = 30)
      every { preferences.getBookLastActive("book-1") } returns null

      // The user plays before the automatic resume: the play() path applies
      // the rewind now and clears the pending state, so the resume that
      // follows must not rewind a second time.
      handler.applyRewind(player)

      handler.onIsPlayingChanged(true)

      verify(exactly = 1) { player.seekTo(1, 20_000L) }
    }
  }

  @Nested
  inner class ApplyRewind {
    @Test
    fun `disabled setting does not seek`() {
      val player = mockk<Player>(relaxed = true)
      every { preferences.getRewindOnPauseTime() } returns RewindOnPauseTime(enabled = false, seconds = 30)
      every { preferences.getPlayingItem() } returns book("book-1")

      handler.applyRewind(player)

      verify(exactly = 0) { player.seekTo(any(), any()) }
    }

    @Test
    fun `no playing book does not seek`() {
      val player = mockk<Player>(relaxed = true)
      every { preferences.getRewindOnPauseTime() } returns RewindOnPauseTime(enabled = true, seconds = 30)
      every { preferences.getPlayingItem() } returns null

      handler.applyRewind(player)

      verify(exactly = 0) { player.seekTo(any(), any()) }
    }

    @Test
    fun `missing timestamp seeks back the full rewind before playback`() {
      val player = mockk<Player>(relaxed = true)
      every { player.currentMediaItemIndex } returns 1
      every { player.currentPosition } returns 50_000L

      every { preferences.getRewindOnPauseTime() } returns RewindOnPauseTime(enabled = true, seconds = 30)
      every { preferences.getPlayingItem() } returns book("book-1", listOf(chapter(60.0), chapter(60.0)))
      every { preferences.getBookLastActive("book-1") } returns null

      handler.applyRewind(player)

      verify { player.seekTo(1, 20_000L) }
    }

    @Test
    fun `rewind is produced when the player timeline durations are unknown`() {
      // A fresh play: ExoPlayer reports C.TIME_UNSET for chapters it has not
      // prepared yet, so durations must come from the book, not the timeline.
      val timeline = mockk<Timeline>()
      every { timeline.windowCount } returns 2
      every { timeline.getWindow(any(), any()) } answers {
        secondArg<Timeline.Window>().apply { durationUs = C.TIME_UNSET }
      }

      val player = mockk<Player>(relaxed = true)
      every { player.currentTimeline } returns timeline
      every { player.currentMediaItemIndex } returns 1
      every { player.currentPosition } returns 10_000L

      every { preferences.getRewindOnPauseTime() } returns RewindOnPauseTime(enabled = true, seconds = 30)
      every { preferences.getPlayingItem() } returns book("book-1", listOf(chapter(30.0), chapter(60.0)))
      every { preferences.getBookLastActive("book-1") } returns null

      handler.applyRewind(player)

      // 10s into chapter 1: 10s back to its start, then 20s into the 30s-long
      // chapter 0, using the book's durations rather than the timeline's.
      verify { player.seekTo(0, 10_000L) }
    }

    @Test
    fun `book switch gives the next book the full rewind on play`() {
      // Regression: A played, then A stopped while the playing item already
      // held B, so A was marked and B has no stored timestamp. When B starts
      // playing it must get the full rewind.
      every { preferences.getPlayingItem() } returns book("book-A")
      handler.onIsPlayingChanged(true)

      every { preferences.getPlayingItem() } returns book("book-B", listOf(chapter(60.0), chapter(60.0)))
      handler.onIsPlayingChanged(false)

      verify(exactly = 2) { preferences.markBookLastActive("book-A", any()) }
      verify(exactly = 0) { preferences.markBookLastActive("book-B", any()) }

      val player = mockk<Player>(relaxed = true)
      every { player.currentMediaItemIndex } returns 1
      every { player.currentPosition } returns 50_000L

      every { preferences.getRewindOnPauseTime() } returns RewindOnPauseTime(enabled = true, seconds = 30)
      every { preferences.getBookLastActive("book-B") } returns null

      handler.applyRewind(player)

      verify { player.seekTo(1, 20_000L) }

      // B's playback start marks B, protecting its future pauses.
      handler.onIsPlayingChanged(true)
      verify { preferences.markBookLastActive("book-B", any()) }
    }

    @Test
    fun `applyRewind never writes the book mark`() {
      val player = mockk<Player>(relaxed = true)
      every { player.currentMediaItemIndex } returns 1
      every { player.currentPosition } returns 50_000L

      every { preferences.getRewindOnPauseTime() } returns RewindOnPauseTime(enabled = true, seconds = 30)
      every { preferences.getPlayingItem() } returns book("book-1", listOf(chapter(60.0), chapter(60.0)))
      every { preferences.getBookLastActive("book-1") } returns null

      handler.applyRewind(player)

      verify { player.seekTo(1, 20_000L) }
      verify(exactly = 0) { preferences.markBookLastActive(any(), any()) }
    }

    @Test
    fun `applyRewind tracks the playing book even when the setting is disabled`() {
      every { preferences.getRewindOnPauseTime() } returns RewindOnPauseTime(enabled = false, seconds = 30)
      every { preferences.getPlayingItem() } returns book("book-1")

      handler.applyRewind(mockk(relaxed = true))
      handler.onIsPlayingChanged(false)

      verify { preferences.markBookLastActive("book-1", any()) }
    }

    @Test
    fun `no rewind applied means no seek and no mark`() {
      val player = mockk<Player>(relaxed = true)
      every { player.currentMediaItemIndex } returns 0
      every { player.currentPosition } returns 0L

      every { preferences.getRewindOnPauseTime() } returns RewindOnPauseTime(enabled = true, seconds = 30)
      every { preferences.getPlayingItem() } returns book("book-1", listOf(chapter(60.0)))
      every { preferences.getBookLastActive("book-1") } returns null

      handler.applyRewind(player)

      verify(exactly = 0) { player.seekTo(any(), any()) }
      verify(exactly = 0) { preferences.markBookLastActive(any(), any()) }
    }
  }

  private fun book(
    id: String,
    chapters: List<PlayingChapter> = emptyList(),
  ) = DetailedItem(
    id = id,
    title = "My Book",
    subtitle = null,
    author = "Author",
    narrator = null,
    publisher = null,
    series = emptyList(),
    year = null,
    abstract = null,
    files = emptyList(),
    chapters = chapters,
    progress = null,
    libraryId = "lib-1",
    localProvided = false,
    createdAt = 0L,
    updatedAt = 0L,
  )

  private fun chapter(durationSeconds: Double) =
    PlayingChapter(
      available = true,
      podcastEpisodeState = null,
      duration = durationSeconds,
      start = 0.0,
      end = durationSeconds,
      title = "Chapter",
      id = "chapter-id",
    )
}
