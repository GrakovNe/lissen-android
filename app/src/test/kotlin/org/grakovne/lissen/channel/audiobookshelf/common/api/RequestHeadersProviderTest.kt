package org.grakovne.lissen.channel.audiobookshelf.common.api

import io.mockk.every
import io.mockk.mockk
import org.grakovne.lissen.channel.common.DEFAULT_USER_AGENT
import org.grakovne.lissen.domain.connection.ServerRequestHeader
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class RequestHeadersProviderTest {
  private val preferences = mockk<LissenSharedPreferences>(relaxed = true)
  private lateinit var provider: RequestHeadersProvider

  @BeforeEach
  fun setup() {
    every { preferences.getUserAgent() } returns DEFAULT_USER_AGENT
    every { preferences.getCustomHeaders() } returns emptyList()
    provider = RequestHeadersProvider(preferences)
  }

  @Nested
  inner class UserAgentHeader {
    @Test
    fun `fetchRequestHeaders always includes User-Agent header`() {
      val headers = provider.fetchRequestHeaders()
      assertTrue(headers.any { it.name == "User-Agent" })
    }

    @Test
    fun `fetchRequestHeaders includes exactly one User-Agent header`() {
      val headers = provider.fetchRequestHeaders()
      assertEquals(1, headers.count { it.name == "User-Agent" })
    }

    @Test
    fun `fetchRequestHeaders uses default User-Agent when none customized`() {
      val headers = provider.fetchRequestHeaders()
      assertEquals(DEFAULT_USER_AGENT, headers.first { it.name == "User-Agent" }.value)
    }

    @Test
    fun `fetchRequestHeaders uses custom User-Agent from preferences`() {
      val customAgent = "MyApp/2.0 (custom)"
      every { preferences.getUserAgent() } returns customAgent

      val headers = provider.fetchRequestHeaders()

      assertEquals(customAgent, headers.first { it.name == "User-Agent" }.value)
    }
  }

  @Nested
  inner class CustomHeaders {
    @Test
    fun `fetchRequestHeaders includes custom headers alongside User-Agent`() {
      every { preferences.getCustomHeaders() } returns
        listOf(ServerRequestHeader("X-Token", "abc123"))

      val headers = provider.fetchRequestHeaders()

      assertTrue(headers.any { it.name == "X-Token" })
      assertTrue(headers.any { it.name == "User-Agent" })
    }

    @Test
    fun `fetchRequestHeaders preserves all custom headers`() {
      val customHeaders =
        listOf(
          ServerRequestHeader("X-Token", "abc"),
          ServerRequestHeader("X-Client", "lissen"),
        )
      every { preferences.getCustomHeaders() } returns customHeaders

      val headers = provider.fetchRequestHeaders()

      assertEquals(customHeaders.size + 1, headers.size)
    }

    @Test
    fun `fetchRequestHeaders returns only User-Agent when no custom headers`() {
      val headers = provider.fetchRequestHeaders()
      assertEquals(1, headers.size)
    }
  }
}
