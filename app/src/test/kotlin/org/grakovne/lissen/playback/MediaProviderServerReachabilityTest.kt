package org.grakovne.lissen.playback

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfHostProvider
import org.grakovne.lissen.channel.audiobookshelf.Host
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MediaProviderServerReachabilityTest {
  private val mediaProvider = mockk<LissenMediaProvider>()
  private val hostProvider = mockk<AudiobookshelfHostProvider>(relaxed = true)

  private lateinit var reachability: MediaProviderServerReachability

  /** The host the next connection info request goes to, as the real host provider would serve it. */
  private var current: Host? = Host.external(EXTERNAL)

  /** The hosts that answer. */
  private val answering = mutableSetOf<String>()

  @BeforeEach
  fun setUp() {
    every { hostProvider.provideHost() } answers { current }
    every { hostProvider.confirmRoute(any()) } answers {
      current = firstArg<String?>()?.let(Host::internal) ?: Host.external(EXTERNAL)
    }
    every { hostProvider.routesForUnnamedWifi() } returns emptyList()

    coEvery { mediaProvider.fetchConnectionInfo() } answers {
      when (current?.url in answering) {
        true -> OperationResult.Success(mockk())
        false -> OperationResult.Error(OperationError.NetworkError)
      }
    }

    reachability = MediaProviderServerReachability(mediaProvider, hostProvider)
  }

  @Test
  fun `reachable when the current host answers`() =
    runTest {
      answering += EXTERNAL

      assertTrue(reachability.isReachable())
      verify(exactly = 0) { hostProvider.confirmRoute(any()) }
    }

  @Test
  fun `unreachable without trying local routes when the wifi name is known or on cellular`() =
    runTest {
      answering += LOCAL

      assertFalse(reachability.isReachable())
      verify(exactly = 0) { hostProvider.confirmRoute(any()) }
    }

  @Test
  fun `keeps the local route that answers on a wifi network with a hidden name`() =
    runTest {
      every { hostProvider.routesForUnnamedWifi() } returns listOf(OFFICE, LOCAL)
      answering += LOCAL

      assertTrue(reachability.isReachable())
      verifyOrder {
        hostProvider.confirmRoute(OFFICE)
        hostProvider.confirmRoute(LOCAL)
      }
      assertEquals(Host.internal(LOCAL), current)
    }

  @Test
  fun `forgets the tried routes when none answers`() =
    runTest {
      every { hostProvider.routesForUnnamedWifi() } returns listOf(LOCAL)

      assertFalse(reachability.isReachable())
      verifyOrder {
        hostProvider.confirmRoute(LOCAL)
        hostProvider.confirmRoute(null)
      }
    }

  @Test
  fun `falls back to the external host when a confirmed route stops answering`() =
    runTest {
      current = Host.internal(LOCAL)
      every { hostProvider.routesForUnnamedWifi() } returns listOf(LOCAL)
      answering += EXTERNAL

      assertTrue(reachability.isReachable())
      verify(exactly = 0) { hostProvider.confirmRoute(LOCAL) }
    }

  @Test
  fun `does not retry a confirmed route that just failed`() =
    runTest {
      current = Host.internal(LOCAL)
      every { hostProvider.routesForUnnamedWifi() } returns listOf(LOCAL, OFFICE)
      answering += OFFICE

      assertTrue(reachability.isReachable())
      verify(exactly = 0) { hostProvider.confirmRoute(LOCAL) }
      verify { hostProvider.confirmRoute(OFFICE) }
    }

  private companion object {
    const val EXTERNAL = "https://external.example"
    const val LOCAL = "http://local.example"
    const val OFFICE = "http://office.example"
  }
}
