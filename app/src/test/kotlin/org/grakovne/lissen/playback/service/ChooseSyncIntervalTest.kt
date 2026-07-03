package org.grakovne.lissen.playback.service

import androidx.media3.common.C
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChooseSyncIntervalTest {
  @Test
  fun `middle of a long chapter syncs with the long interval`() {
    val interval = chooseSyncInterval(durationMs = 600_000L, positionMs = 300_000L)

    assertEquals(SYNC_INTERVAL_LONG, interval)
  }

  @Test
  fun `position near chapter start syncs with the short interval`() {
    val interval = chooseSyncInterval(durationMs = 600_000L, positionMs = 10_000L)

    assertEquals(SYNC_INTERVAL_SHORT, interval)
  }

  @Test
  fun `position near chapter end syncs with the short interval`() {
    val interval = chooseSyncInterval(durationMs = 600_000L, positionMs = 590_000L)

    assertEquals(SYNC_INTERVAL_SHORT, interval)
  }

  @Test
  fun `unknown duration does not force the short interval`() {
    val interval = chooseSyncInterval(durationMs = C.TIME_UNSET, positionMs = 300_000L)

    assertEquals(SYNC_INTERVAL_LONG, interval)
  }

  @Test
  fun `unknown duration still honors the near-start window`() {
    val interval = chooseSyncInterval(durationMs = C.TIME_UNSET, positionMs = 10_000L)

    assertEquals(SYNC_INTERVAL_SHORT, interval)
  }
}
