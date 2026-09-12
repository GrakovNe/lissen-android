package org.grakovne.lissen.persistence.preferences

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SessionPreferencesTest {
  private val store = mockk<SecurePreferenceStore>(relaxed = true)

  private lateinit var preferences: SessionPreferences

  @BeforeEach
  fun setUp() {
    preferences = SessionPreferences(store)
  }

  @Nested
  inner class DeviceId {
    @Test
    fun `returns the stored device id`() {
      every { store.getString("device_id") } returns "stored-id"

      assertEquals("stored-id", preferences.getDeviceId())
      verify(exactly = 0) { store.putString("device_id", any()) }
    }

    @Test
    fun `generates and persists a device id when it is absent`() {
      val stored = slot<String>()
      every { store.getString("device_id") } returnsMany listOf(null, "generated")
      every { store.putString("device_id", capture(stored)) } returns Unit

      val deviceId = preferences.getDeviceId()

      assertEquals(stored.captured, deviceId)
      assertTrue(deviceId.matches(Regex("[0-9a-fA-F-]{36}")))
    }
  }

  @Nested
  inner class PlainValues {
    @Test
    fun `host username and server version round trip`() {
      var host: String? = null
      var username: String? = null
      var version: String? = null

      every { store.getString("host") } answers { host }
      every { store.putString("host", any()) } answers { host = secondArg() }
      every { store.getString("username") } answers { username }
      every { store.putString("username", any()) } answers { username = secondArg() }
      every { store.getString("server_version") } answers { version }
      every { store.putString("server_version", any()) } answers { version = secondArg() }

      assertNull(preferences.getHost())
      preferences.saveHost("https://shelf.example")
      preferences.saveUsername("listener")
      preferences.saveServerVersion("2.23.1")

      assertEquals("https://shelf.example", preferences.getHost())
      assertEquals("listener", preferences.getUsername())
      assertEquals("2.23.1", preferences.getServerVersion())
    }
  }

  @Nested
  inner class Secrets {
    @Test
    fun `token is cached and invalidated on save`() {
      every { store.readSecret("token") } returnsMany listOf("first", "second")

      assertEquals("first", preferences.getToken())
      assertEquals("first", preferences.getToken())
      verify(exactly = 1) { store.readSecret("token") }

      preferences.saveToken("second")
      assertEquals("second", preferences.getToken())
      verify(exactly = 2) { store.readSecret("token") }
      verify { store.writeSecret("token", "second") }
    }

    @Test
    fun `access and refresh tokens use their own caches`() {
      every { store.readSecret("access_token") } returns "access"
      every { store.readSecret("refresh_token") } returns "refresh"

      assertEquals("access", preferences.getAccessToken())
      assertEquals("refresh", preferences.getRefreshToken())

      preferences.saveAccessToken("access-2")
      verify { store.writeSecret("access_token", "access-2") }
      verify(exactly = 0) { store.writeSecret("refresh_token", any()) }
    }
  }

  @Nested
  inner class Credentials {
    @Test
    fun `requires host, username and any token`() {
      var host: String? = null
      var username: String? = null
      var token: String? = null

      every { store.getString("host") } answers { host }
      every { store.getString("username") } answers { username }
      every { store.readSecret("token") } answers { token }
      every { store.readSecret("access_token") } returns null

      assertFalse(preferences.hasCredentials())

      host = "https://shelf.example"
      assertFalse(preferences.hasCredentials())

      username = "listener"
      assertFalse(preferences.hasCredentials())

      token = "jwt"
      preferences.saveToken("jwt")
      assertTrue(preferences.hasCredentials())
    }

    @Test
    fun `an access token is sufficient as a credential`() {
      var accessToken: String? = null
      every { store.getString("host") } returns "https://shelf.example"
      every { store.getString("username") } returns "listener"
      every { store.readSecret("token") } returns null
      every { store.readSecret("access_token") } answers { accessToken }

      assertFalse(preferences.hasCredentials())

      accessToken = "oauth-access"
      preferences.saveAccessToken("oauth-access")
      assertTrue(preferences.hasCredentials())
    }

    @Test
    fun `clearCredentials removes secrets and keeps host and username`() {
      preferences.clearCredentials()

      verify { store.remove(listOf("token", "access_token", "refresh_token")) }
      verify(exactly = 0) { store.remove(listOf("host", "username", "token", "access_token", "refresh_token", "server_version")) }
    }

    @Test
    fun `clear wipes the whole session`() {
      preferences.clear()

      verify {
        store.remove(
          listOf("host", "username", "token", "access_token", "refresh_token", "server_version"),
        )
      }
    }

    @Test
    fun `clearCredentials invalidates the token caches`() {
      every { store.readSecret("token") } returnsMany listOf("first", null)
      every { store.readSecret("access_token") } returnsMany listOf("access", null)
      every { store.readSecret("refresh_token") } returnsMany listOf("refresh", null)

      assertEquals("first", preferences.getToken())
      assertEquals("access", preferences.getAccessToken())
      assertEquals("refresh", preferences.getRefreshToken())

      preferences.clearCredentials()

      assertNull(preferences.getToken())
      assertNull(preferences.getAccessToken())
      assertNull(preferences.getRefreshToken())
    }
  }
}
