package org.grakovne.lissen.channel.audiobookshelf.common.api

import android.content.Context
import com.squareup.moshi.Moshi
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfHostProvider
import org.grakovne.lissen.channel.audiobookshelf.Host
import org.grakovne.lissen.channel.audiobookshelf.common.client.AudiobookshelfApiClient
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LoginResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.bookmark.BookmarksItemResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.bookmark.BookmarksResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.metadata.LibraryResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.UserResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.UserStateResponse
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.persistence.preferences.ConnectionPreferences
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Drives [ConditionalCache] end to end through a real Retrofit + Moshi + OkHttp stack
 * against [MockWebServer]. The `@Cacheable` tag on `fetchUserState` makes the
 * [ConditionalCacheInterceptor] revalidate it, so the `If-None-Match` / `304` handshake,
 * the "downloaded once, revalidated afterwards" guarantee and [ConditionalCache.invalidateAll]
 * are exercised rather than mocked.
 */
class ConditionalCacheIntegrationTest {
  private val server = MockWebServer()

  private val context = mockk<Context>(relaxed = true)
  private val hostProvider = mockk<AudiobookshelfHostProvider>()
  private val session = mockk<SessionPreferences>(relaxed = true)
  private val connection = mockk<ConnectionPreferences>(relaxed = true)
  private val requestHeadersProvider = mockk<RequestHeadersProvider>()
  private val loginResponseConverter = mockk<LoginResponseConverter>(relaxed = true)

  private val cache = ConditionalCache()

  private lateinit var service: AudioBookShelfApiService
  private lateinit var repository: AudioBookshelfRepository

  private val progress = MediaProgressResponse("book-1", null, 42.0, false, 1000L, 0.5)
  private val bookmark = BookmarksItemResponse("book-1", 42.0, "My mark", 2000L)
  private val state = UserStateResponse(listOf(progress), listOf(bookmark))

  private val json =
    """
    {"mediaProgress":[{"libraryItemId":"book-1","episodeId":null,"currentTime":42.0,"isFinished":false,"lastUpdate":1000,"progress":0.5}],
     "bookmarks":[{"libraryItemId":"book-1","time":42.0,"title":"My mark","createdAt":2000}]}
    """.trimIndent()

  @BeforeEach
  fun setup() {
    server.start()

    every { hostProvider.provideHost() } returns Host.external(server.url("/").toString())
    every { session.getAccessToken() } returns "token"
    every { connection.getSslBypass() } returns false
    every { connection.getClientCertAlias() } returns null
    every { requestHeadersProvider.fetchRequestHeaders() } returns emptyList()

    service =
      AudioBookShelfApiService(
        context = context,
        hostProvider = hostProvider,
        session = session,
        connection = connection,
        requestHeadersProvider = requestHeadersProvider,
        loginResponseConverter = loginResponseConverter,
        conditionalCache = cache,
      )

    val http =
      OkHttpClient
        .Builder()
        .addInterceptor(ConditionalCacheInterceptor(cache))
        .build()

    val api =
      Retrofit
        .Builder()
        .baseUrl(server.url("/"))
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
  }

  private suspend fun fetchUserState() = service.makeRequest { it.fetchUserState() }

  private fun ok(
    body: String,
    etag: String,
  ) = MockResponse
    .Builder()
    .code(200)
    .body(body)
    .addHeader("Content-Type", "application/json")
    .addHeader("ETag", etag)
    .build()

  private fun notModified(etag: String) =
    MockResponse
      .Builder()
      .code(304)
      .addHeader("ETag", etag)
      .build()

  @Test
  fun `retrofit deserializes the whole user state object`() =
    runTest {
      server.enqueue(ok(json, "\"v1\""))

      val result = fetchUserState()

      assertTrue(result is OperationResult.Success)
      assertEquals(state, (result as OperationResult.Success).data)

      val request = server.takeRequest()
      assertEquals("GET", request.method)
      assertNull(request.headers["If-None-Match"])
    }

  @Test
  fun `revalidates with If-None-Match and serves the cached object on 304 without re-downloading`() =
    runTest {
      server.enqueue(ok(json, "\"v1\""))
      fetchUserState()
      server.takeRequest()

      server.enqueue(notModified("\"v1\""))
      val result = fetchUserState()

      assertEquals(OperationResult.Success(state), result)

      val second = server.takeRequest()
      assertEquals("\"v1\"", second.headers["If-None-Match"])
      assertEquals(2, server.requestCount)
    }

  @Test
  fun `the payload is downloaded once and only revalidated afterwards`() =
    runTest {
      server.enqueue(ok(json, "\"v1\""))
      server.enqueue(notModified("\"v1\""))
      server.enqueue(notModified("\"v1\""))

      val user = repository.fetchUserInfoResponse()
      val bookmarks = repository.fetchBookmarks()
      val again = fetchUserState()

      assertEquals(OperationResult.Success(UserResponse(listOf(progress))), user)
      assertEquals(OperationResult.Success(BookmarksResponse(listOf(bookmark))), bookmarks)
      assertEquals(OperationResult.Success(state), again)

      // Only the very first read transfers the body; every later read is a
      // conditional request answered with 304, so the JSON is never re-downloaded.
      assertNull(server.takeRequest().headers["If-None-Match"])
      assertEquals("\"v1\"", server.takeRequest().headers["If-None-Match"])
      assertEquals("\"v1\"", server.takeRequest().headers["If-None-Match"])
    }

  @Test
  fun `a changed payload replaces the cached object`() =
    runTest {
      server.enqueue(ok(json, "\"v1\""))
      fetchUserState()
      server.takeRequest()

      val changed =
        """
        {"mediaProgress":[],"bookmarks":[]}
        """.trimIndent()
      server.enqueue(ok(changed, "\"v2\""))

      val result = fetchUserState()

      assertEquals(OperationResult.Success(UserStateResponse(emptyList(), emptyList())), result)
      assertEquals("\"v1\"", server.takeRequest().headers["If-None-Match"])
    }

  @Test
  fun `invalidateAll drops the etag so the next read fetches unconditionally`() =
    runTest {
      server.enqueue(ok(json, "\"v1\""))
      fetchUserState()
      server.takeRequest()

      cache.invalidateAll()

      server.enqueue(ok(json, "\"v2\""))
      fetchUserState()

      assertNull(server.takeRequest().headers["If-None-Match"])
    }

  @Test
  fun `a tagged endpoint with path and query params revalidates too`() =
    runTest {
      val libraryJson =
        """
        {"library":{"id":"lib1","name":"Audio","mediaType":"book","displayOrder":null},
         "filterdata":{"authors":[],"genres":[],"tags":[],"series":[]}}
        """.trimIndent()

      server.enqueue(ok(libraryJson, "\"v1\""))
      val first = repository.fetchLibrary("lib1")
      assertTrue(first is OperationResult.Success)
      val firstRequest = server.takeRequest()
      assertEquals("/api/libraries/lib1", firstRequest.url.encodedPath)
      assertNull(firstRequest.headers["If-None-Match"])

      server.enqueue(notModified("\"v1\""))
      val second = repository.fetchLibrary("lib1")

      // The 304 is served from the cache: same object, and the validator went out on the wire.
      assertEquals(first, second)
      assertEquals("\"v1\"", server.takeRequest().headers["If-None-Match"])
    }
}
