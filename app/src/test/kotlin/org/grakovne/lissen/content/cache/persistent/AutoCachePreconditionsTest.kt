package org.grakovne.lissen.content.cache.persistent

import org.grakovne.lissen.common.NetworkTypeAutoCache
import org.grakovne.lissen.domain.NetworkType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoCachePreconditionsTest {
  @Test
  fun `blocked when force cache enabled`() {
    assertFalse(
      autoCachePreconditionsMet(
        isPlaying = true,
        isForceCache = true,
        isNetworkAvailable = true,
        currentNetwork = NetworkType.WIFI,
        preferredNetwork = NetworkTypeAutoCache.WIFI_ONLY,
      ),
    )
  }

  @Test
  fun `blocked when not playing`() {
    assertFalse(
      autoCachePreconditionsMet(
        isPlaying = false,
        isForceCache = false,
        isNetworkAvailable = true,
        currentNetwork = NetworkType.WIFI,
        preferredNetwork = NetworkTypeAutoCache.WIFI_ONLY,
      ),
    )
  }

  @Test
  fun `blocked when network unavailable`() {
    assertFalse(
      autoCachePreconditionsMet(
        isPlaying = true,
        isForceCache = false,
        isNetworkAvailable = false,
        currentNetwork = NetworkType.WIFI,
        preferredNetwork = NetworkTypeAutoCache.WIFI_ONLY,
      ),
    )
  }

  @Test
  fun `blocked when network type unknown`() {
    assertFalse(
      autoCachePreconditionsMet(
        isPlaying = true,
        isForceCache = false,
        isNetworkAvailable = true,
        currentNetwork = null,
        preferredNetwork = NetworkTypeAutoCache.WIFI_OR_CELLULAR,
      ),
    )
  }

  @Test
  fun `blocked on cellular when wifi only preferred`() {
    assertFalse(
      autoCachePreconditionsMet(
        isPlaying = true,
        isForceCache = false,
        isNetworkAvailable = true,
        currentNetwork = NetworkType.CELLULAR,
        preferredNetwork = NetworkTypeAutoCache.WIFI_ONLY,
      ),
    )
  }

  @Test
  fun `allowed on wifi when wifi only preferred`() {
    assertTrue(
      autoCachePreconditionsMet(
        isPlaying = true,
        isForceCache = false,
        isNetworkAvailable = true,
        currentNetwork = NetworkType.WIFI,
        preferredNetwork = NetworkTypeAutoCache.WIFI_ONLY,
      ),
    )
  }

  @Test
  fun `allowed on cellular when wifi or cellular preferred`() {
    assertTrue(
      autoCachePreconditionsMet(
        isPlaying = true,
        isForceCache = false,
        isNetworkAvailable = true,
        currentNetwork = NetworkType.CELLULAR,
        preferredNetwork = NetworkTypeAutoCache.WIFI_OR_CELLULAR,
      ),
    )
  }
}
