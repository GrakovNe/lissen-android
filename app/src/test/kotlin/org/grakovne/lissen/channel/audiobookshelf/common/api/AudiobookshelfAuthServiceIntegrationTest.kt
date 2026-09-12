package org.grakovne.lissen.channel.audiobookshelf.common.api

import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.grakovne.lissen.channel.audiobookshelf.common.converter.AuthMethodResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LoginResponseConverter
import org.grakovne.lissen.channel.common.AuthData
import org.grakovne.lissen.channel.common.AuthMethod
import org.grakovne.lissen.channel.common.OAuthContextCache
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.domain.UserAccount
import org.grakovne.lissen.persistence.preferences.ConnectionPreferences
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Exercises [AudiobookshelfAuthService] credential login and auth-method discovery
 * against [MockWebServer] through the real [ApiClient] Retrofit/Moshi stack.
 * The OAuth browser round trip is out of scope here; everything up to the
 * first server exchange is real.
 */
class AudiobookshelfAuthServiceIntegrationTest {
  private val server = MockWebServer()

  private val context = mockk<Context>(relaxed = true)
  private val session = mockk<SessionPreferences>(relaxed = true)
  private val connection = mockk<ConnectionPreferences>(relaxed = true)
  private val requestHeadersProvider = mockk<RequestHeadersProvider>()
  private val contextCache = mockk<OAuthContextCache>(relaxed = true)

  private lateinit var service: AudiobookshelfAuthService

  private val loggedUser =
    """
    {"user":{"id":"u1","token":"tok","refreshToken":"rtok","accessToken":"atok","username":"demo"},
     "userDefaultLibraryId":"lib1"}
    """.trimIndent()

  @BeforeEach
  fun setUp() {
    server.start()

    every { requestHeadersProvider.fetchRequestHeaders() } returns emptyList()
    every { connection.getSslBypass() } returns false
    every { connection.getClientCertAlias() } returns null
    every { connection.getCustomHeaders() } returns emptyList()

    service =
      AudiobookshelfAuthService(
        context = context,
        loginResponseConverter = LoginResponseConverter(),
        requestHeadersProvider = requestHeadersProvider,
        session = session,
        connection = connection,
        contextCache = contextCache,
        authMethodResponseConverter = AuthMethodResponseConverter(),
      )
  }

  @AfterEach
  fun tearDown() {
    runCatching { server.close() }
    unmockkAll()
  }

  @Test
  fun `authorize posts credentials and maps the logged user`() =
    runBlocking {
      server.enqueue(json(loggedUser))

      var callback: UserAccount? = null
      val result = service.authorize(server.url("").toString(), "demo", "secret") { callback = it }

      val request = server.takeRequest()
      assertEquals("/login", request.url.encodedPath)
      assertTrue(request.body?.utf8()?.contains("\"demo\"") == true)

      val expected = UserAccount(token = "tok", accessToken = "atok", refreshToken = "rtok", username = "demo", preferredLibraryId = "lib1")
      assertEquals(OperationResult.Success(expected), result)
      assertEquals(expected, callback)
    }

  @Test
  fun `authorize rejects a malformed host without touching the network`() =
    runBlocking {
      val result = service.authorize("not a host", "demo", "secret") { }

      assertEquals(OperationResult.Error<UserAccount>(OperationError.InvalidCredentialsHost), result)
      assertEquals(0, server.requestCount)
    }

  @Test
  fun `authorize maps wrong credentials to unauthorized`() =
    runBlocking {
      server.enqueue(MockResponse.Builder().code(401).build())

      val result = service.authorize(server.url("").toString(), "demo", "wrong") { }

      assertEquals(OperationResult.Error<UserAccount>(OperationError.Unauthorized), result)
    }

  @Test
  fun `fetchAuthMethods reads the status endpoint`() =
    runBlocking {
      stubUriParsing()
      server.enqueue(json("""{"authMethods":["local","openid","saml"],"authFormData":{"authOpenIDButtonText":"Continue with SSO"}}"""))

      val result = service.fetchAuthMethods(server.url("").toString())

      assertEquals("/status", server.takeRequest().url.encodedPath)
      assertEquals(
        OperationResult.Success(
          AuthData(
            methods = listOf(AuthMethod.CREDENTIALS, AuthMethod.O_AUTH),
            oauthLoginText = "Continue with SSO",
          ),
        ),
        result,
      )
    }

  @Test
  fun `fetchAuthMethods falls back to empty data when the server fails`() =
    runBlocking {
      stubUriParsing()
      server.enqueue(MockResponse.Builder().code(500).build())

      val result = service.fetchAuthMethods(server.url("").toString())

      assertEquals(OperationResult.Success(AuthData.empty), result)
    }

  private fun json(body: String) =
    MockResponse
      .Builder()
      .code(200)
      .body(body)
      .addHeader("Content-Type", "application/json")
      .build()

  private fun stubUriParsing() {
    mockkStatic(Uri::class)

    every { Uri.parse(any()) } answers {
      val parts = mutableListOf(firstArg<String>().trimEnd('/'))

      val uri = mockk<Uri>()
      val builder = mockk<Uri.Builder>()
      every { uri.buildUpon() } returns builder
      every { builder.appendPath(any()) } answers {
        parts.add("/" + firstArg<String>())
        builder
      }
      every { builder.appendEncodedPath(any()) } answers {
        parts.add("/" + firstArg<String>())
        builder
      }
      every { builder.build() } answers {
        val built = mockk<Uri>()
        every { built.toString() } returns parts.joinToString("")
        built
      }

      uri
    }
  }
}
