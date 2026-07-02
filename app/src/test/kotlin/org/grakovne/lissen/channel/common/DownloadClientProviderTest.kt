package org.grakovne.lissen.channel.common

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.grakovne.lissen.channel.audiobookshelf.common.api.RequestHeadersProvider
import org.grakovne.lissen.domain.connection.ServerRequestHeader
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class DownloadClientProviderTest {
  private val context = mockk<Context>(relaxed = true)
  private val preferences = mockk<LissenSharedPreferences>()
  private val headersProvider = mockk<RequestHeadersProvider>()

  private val factoryCalls = AtomicInteger(0)

  private lateinit var provider: DownloadClientProvider

  @BeforeEach
  fun setup() {
    every { preferences.getSslBypass() } returns false
    every { preferences.getClientCertAlias() } returns null
    every { headersProvider.fetchRequestHeaders() } answers {
      listOf(ServerRequestHeader("User-Agent", "Lissen"))
    }

    provider = DownloadClientProvider(context, preferences, headersProvider)
    provider.clientFactory = {
      factoryCalls.incrementAndGet()
      OkHttpClient()
    }
  }

  @Test
  fun `repeated downloads reuse the same client`() {
    val first = provider.provideClient()
    val second = provider.provideClient()

    assertSame(first, second)
    assertEquals(1, factoryCalls.get())
  }

  @Test
  fun `client survives header instances with fresh identity`() {
    val first = provider.provideClient()
    val second = provider.provideClient()

    assertSame(first, second)
  }

  @Test
  fun `changed ssl policy rebuilds the client`() {
    val first = provider.provideClient()

    every { preferences.getSslBypass() } returns true
    val second = provider.provideClient()

    assertNotSame(first, second)
    assertEquals(2, factoryCalls.get())
  }

  @Test
  fun `changed client certificate rebuilds the client`() {
    val first = provider.provideClient()

    every { preferences.getClientCertAlias() } returns "alias"
    val second = provider.provideClient()

    assertNotSame(first, second)
  }
}
