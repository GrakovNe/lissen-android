package org.grakovne.lissen.channel.audiobookshelf.common.api

import android.content.Context
import android.os.StatFs
import com.squareup.moshi.Moshi
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfHostProvider
import org.grakovne.lissen.channel.audiobookshelf.Host
import org.grakovne.lissen.channel.audiobookshelf.common.client.AudiobookshelfApiClient
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LoginResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.model.metadata.LibrariesResponse
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.persistence.preferences.ConnectionPreferences
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File

/**
 * Drives [AudioBookshelfRepository] through the real Retrofit + Moshi + OkHttp stack
 * against [MockWebServer]: endpoint error mapping, library payloads and the bounded
 * cover download into the cache directory ([writeBounded] with a stubbed [StatFs])
 * are executed for real instead of mocked.
 */
class AudioBookshelfRepositoryIntegrationTest {
  private val server = MockWebServer()

  @TempDir
  lateinit var cacheDir: File

  private val context = mockk<Context>(relaxed = true)
  private val hostProvider = mockk<AudiobookshelfHostProvider>()
  private val session = mockk<SessionPreferences>(relaxed = true)
  private val connection = mockk<ConnectionPreferences>(relaxed = true)
  private val requestHeadersProvider = mockk<RequestHeadersProvider>()
  private val loginResponseConverter = mockk<LoginResponseConverter>(relaxed = true)

  private lateinit var repository: AudioBookshelfRepository

  @BeforeEach
  fun setUp() {
    server.start()

    every { context.cacheDir } returns this@AudioBookshelfRepositoryIntegrationTest.cacheDir
    every { hostProvider.provideHost() } returns Host.external(server.url("").toString())
    every { session.getAccessToken() } returns "token"
    every { connection.getSslBypass() } returns false
    every { connection.getClientCertAlias() } returns null
    every { requestHeadersProvider.fetchRequestHeaders() } returns emptyList()

    val service =
      AudioBookShelfApiService(
        context = context,
        hostProvider = hostProvider,
        session = session,
        connection = connection,
        requestHeadersProvider = requestHeadersProvider,
        loginResponseConverter = loginResponseConverter,
        conditionalCache = ConditionalCache(),
      )

    val http = OkHttpClient.Builder().build()

    val api =
      Retrofit
        .Builder()
        .baseUrl(server.url(""))
        .client(http)
        .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
        .build()
        .create(AudiobookshelfApiClient::class.java)

    service.clientFactory = { AudioBookShelfApiService.ChannelClients(api = api, http = http) }

    repository = AudioBookshelfRepository(context, service)
  }

  @AfterEach
  fun tearDown() {
    runCatching { server.close() }
    unmockkAll()
  }

  @Test
  fun `fetchLibraries maps the payload`() =
    runBlocking {
      server.enqueue(
        json(
          """{"libraries":[{"id":"b","name":"Books","mediaType":"book","displayOrder":2},{"id":"a","name":"Audio","mediaType":"book","displayOrder":1}]}""",
        ),
      )

      val result = repository.fetchLibraries()

      assertEquals("/api/libraries", server.takeRequest().url.encodedPath)
      val success = result as OperationResult.Success
      assertEquals(listOf("b", "a"), success.data.libraries.map { it.id })
    }

  @Test
  fun `fetchLibraries maps status codes to operation errors`() =
    runBlocking {
      every { session.getRefreshToken() } returns null

      server.enqueue(MockResponse.Builder().code(404).build())
      server.enqueue(MockResponse.Builder().code(500).build())
      server.enqueue(MockResponse.Builder().code(401).build())
      server.enqueue(MockResponse.Builder().code(401).build())

      assertEquals(OperationResult.Error<LibrariesResponse>(OperationError.NotFoundError), repository.fetchLibraries())
      assertEquals(OperationResult.Error<LibrariesResponse>(OperationError.InternalError), repository.fetchLibraries())
      assertEquals(OperationResult.Error<LibrariesResponse>(OperationError.Unauthorized), repository.fetchLibraries())
    }

  @Test
  fun `fetchBookCover stores the response body in a cache file`() =
    runBlocking {
      mockDisk(availableBytes = 10L * 1024 * 1024 * 1024)
      server.enqueue(
        MockResponse
          .Builder()
          .code(200)
          .body("cover-bytes")
          .build(),
      )

      val result = repository.fetchBookCover("book-1", null)

      assertEquals("/api/items/book-1/cover", server.takeRequest().url.encodedPath)
      val success = result as OperationResult.Success
      assertEquals("cover-bytes", success.data.readText())
      assertTrue(success.data.delete())
    }

  @Test
  fun `fetchBookCover refuses a body larger than the free disk space`() =
    runBlocking {
      mockDisk(availableBytes = 1)
      server.enqueue(
        MockResponse
          .Builder()
          .code(200)
          .body("cover-bytes")
          .build(),
      )

      val result = repository.fetchBookCover("book-1", null)

      assertEquals(OperationResult.Error<File>(OperationError.InternalError, "not enough disk space"), result)
    }

  @Test
  fun `fetchBookCover maps a missing cover to not found`() =
    runBlocking {
      server.enqueue(MockResponse.Builder().code(404).build())

      assertEquals(OperationResult.Error<File>(OperationError.NotFoundError), repository.fetchBookCover("book-1", null))
    }

  @Test
  fun `fetchAuthorImage stores the response body in a cache file`() =
    runBlocking {
      mockDisk(availableBytes = 10L * 1024 * 1024 * 1024)
      server.enqueue(
        MockResponse
          .Builder()
          .code(200)
          .body("author-bytes")
          .build(),
      )

      val result = repository.fetchAuthorImage("author-1", null)

      val success = result as OperationResult.Success
      assertEquals("author-bytes", success.data.readText())
      assertTrue(success.data.delete())
    }

  private fun mockDisk(availableBytes: Long) {
    mockkConstructor(StatFs::class)
    every { anyConstructed<StatFs>().availableBytes } returns availableBytes
  }

  private fun json(body: String) =
    MockResponse
      .Builder()
      .code(200)
      .body(body)
      .addHeader("Content-Type", "application/json")
      .build()
}
