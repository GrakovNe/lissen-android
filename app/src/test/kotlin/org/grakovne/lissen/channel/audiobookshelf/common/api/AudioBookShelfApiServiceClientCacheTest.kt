package org.grakovne.lissen.channel.audiobookshelf.common.api

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfHostProvider
import org.grakovne.lissen.channel.audiobookshelf.Host
import org.grakovne.lissen.channel.audiobookshelf.common.client.AudiobookshelfApiClient
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LoginResponseConverter
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.domain.connection.ServerRequestHeader
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger

class AudioBookShelfApiServiceClientCacheTest {
  private val context = mockk<Context>(relaxed = true)
  private val hostProvider = mockk<AudiobookshelfHostProvider>()
  private val preferences = mockk<LissenSharedPreferences>(relaxed = true)
  private val requestHeadersProvider = mockk<RequestHeadersProvider>()
  private val loginResponseConverter = mockk<LoginResponseConverter>(relaxed = true)

  private val factoryCalls = AtomicInteger(0)

  private lateinit var service: AudioBookShelfApiService

  @BeforeEach
  fun setup() {
    every { hostProvider.provideHost() } returns Host.external("https://example.org")
    every { preferences.getAccessToken() } returns "access-token"
    every { preferences.getSslBypass() } returns false
    every { preferences.getClientCertAlias() } returns null
    every { requestHeadersProvider.fetchRequestHeaders() } answers {
      listOf(ServerRequestHeader("User-Agent", "Lissen"))
    }

    service =
      AudioBookShelfApiService(
        context = context,
        hostProvider = hostProvider,
        preferences = preferences,
        requestHeadersProvider = requestHeadersProvider,
        loginResponseConverter = loginResponseConverter,
      )

    service.clientFactory = {
      factoryCalls.incrementAndGet()
      AudioBookShelfApiService.ChannelClients(
        api = mockk<AudiobookshelfApiClient>(),
        http = OkHttpClient(),
      )
    }
  }

  private suspend fun makeSuccessfulRequest() = service.makeRequest { Response.success("ok") }

  @Test
  fun `repeated requests reuse the same client despite fresh header identity`() =
    runTest {
      repeat(5) {
        assertTrue(makeSuccessfulRequest() is OperationResult.Success)
      }

      assertEquals(1, factoryCalls.get())
    }

  @Test
  fun `token refresh does not rebuild the client`() =
    runTest {
      makeSuccessfulRequest()

      every { preferences.getAccessToken() } returns "refreshed-token"
      makeSuccessfulRequest()

      assertEquals(1, factoryCalls.get())
    }

  @Test
  fun `host change rebuilds the client`() =
    runTest {
      makeSuccessfulRequest()

      every { hostProvider.provideHost() } returns Host.external("https://another.example.org")
      makeSuccessfulRequest()

      assertEquals(2, factoryCalls.get())
    }

  @Test
  fun `download client shares the cached api client instead of building its own`() =
    runTest {
      makeSuccessfulRequest()
      val downloadClient = service.provideHttpClient()

      makeSuccessfulRequest()

      assertSame(downloadClient, service.provideHttpClient())
      assertEquals(1, factoryCalls.get())
    }

  @Test
  fun `concurrent requests create the client exactly once`() =
    runTest {
      withContext(Dispatchers.Default) {
        (1..16)
          .map { async { makeSuccessfulRequest() } }
          .awaitAll()
      }

      assertEquals(1, factoryCalls.get())
    }
}
