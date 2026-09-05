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
import java.util.UUID

class SessionPreferencesTest {
  private val store = mockk<SecurePreferenceStore>(relaxed = true)
  private lateinit var preferences: SessionPreferences

  @BeforeEach
  fun setup() {
    preferences = SessionPreferences(store)
  }

  @Nested
  inner class DeviceId {
    @Test
    fun `generates and persists a device id when none is stored`() {
      val stored = slot<String>()
      every { store.getString("device_id") } returns null
      every { store.putString("device_id", capture(stored)) } answers { }

      val deviceId = preferences.getDeviceId()

      assertEquals(deviceId, stored.captured)
      assertEquals(deviceId, UUID.fromString(stored.captured).toString())
      verify(exactly = 1) { store.putString("device_id", any()) }
    }

    @Test
    fun `returns the stored device id without writing`() {
      every { store.getString("device_id") } returns "existing-id"

      assertEquals("existing-id", preferences.getDeviceId())
      verify(exactly = 0) { store.putString(any(), any()) }
    }
  }

  @Nested
  inner class PlainValues {
    @Test
    fun `host round trips through the store`() {
      preferences.saveHost("https://example.org")
      verify { store.putString("host", "https://example.org") }

      every { store.getString("host") } returns "https://example.org"
      assertEquals("https://example.org", preferences.getHost())
    }

    @Test
    fun `username round trips through the store`() {
      preferences.saveUsername("user")
      verify { store.putString("username", "user") }

      every { store.getString("username") } returns "user"
      assertEquals("user", preferences.getUsername())
    }

    @Test
    fun `server version round trips through the store`() {
      preferences.saveServerVersion("0.4.10")
      verify { store.putString("server_version", "0.4.10") }

      every { store.getString("server_version") } returns "0.4.10"
      assertEquals("0.4.10", preferences.getServerVersion())
    }
  }

  @Nested
  inner class Secrets {
    @Test
    fun `saving a token writes it as a secret`() {
      preferences.saveToken("token-value")
      verify { store.writeSecret("token", "token-value") }
    }

    @Test
    fun `saving an access token writes it as a secret`() {
      preferences.saveAccessToken("access-value")
      verify { store.writeSecret("access_token", "access-value") }
    }

    @Test
    fun `saving a refresh token writes it as a secret`() {
      preferences.saveRefreshToken("refresh-value")
      verify { store.writeSecret("refresh_token", "refresh-value") }
    }

    @Test
    fun `token is read through the store and cached between reads`() {
      every { store.readSecret("token") } returns "secret"

      assertEquals("secret", preferences.getToken())
      assertEquals("secret", preferences.getToken())

      verify(exactly = 1) { store.readSecret("token") }
    }

    @Test
    fun `saving a new token invalidates the cached value`() {
      every { store.readSecret("token") } returns "first" andThen "second"

      assertEquals("first", preferences.getToken())

      preferences.saveToken("second")

      assertEquals("second", preferences.getToken())
      verify(exactly = 2) { store.readSecret("token") }
    }

    @Test
    fun `missing secrets read as null`() {
      every { store.readSecret(any()) } returns null

      assertNull(preferences.getToken())
      assertNull(preferences.getAccessToken())
      assertNull(preferences.getRefreshToken())
    }
  }

  @Nested
  inner class CredentialsState {
    @Test
    fun `has credentials requires host, username and at least one token`() {
      every { store.getString("host") } returns "https://example.org"
      every { store.getString("username") } returns "user"
      every { store.readSecret("token") } returns "token"
      every { store.readSecret("access_token") } returns null

      assertTrue(preferences.hasCredentials())
    }

    @Test
    fun `access token alone is enough for the token part`() {
      every { store.getString("host") } returns "https://example.org"
      every { store.getString("username") } returns "user"
      every { store.readSecret("token") } returns null
      every { store.readSecret("access_token") } returns "access"

      assertTrue(preferences.hasCredentials())
    }

    @Test
    fun `no credentials when host is missing`() {
      every { store.getString("host") } returns null
      every { store.getString("username") } returns "user"
      every { store.readSecret("token") } returns "token"

      assertFalse(preferences.hasCredentials())
    }

    @Test
    fun `no credentials when username is missing`() {
      every { store.getString("host") } returns "https://example.org"
      every { store.getString("username") } returns null
      every { store.readSecret("token") } returns "token"

      assertFalse(preferences.hasCredentials())
    }

    @Test
    fun `no credentials when both tokens are missing`() {
      every { store.getString("host") } returns "https://example.org"
      every { store.getString("username") } returns "user"
      every { store.readSecret("token") } returns null
      every { store.readSecret("access_token") } returns null

      assertFalse(preferences.hasCredentials())
    }
  }

  @Nested
  inner class Clearing {
    @Test
    fun `clear credentials removes only token keys`() {
      preferences.clearCredentials()

      verify { store.remove(listOf("token", "access_token", "refresh_token")) }
      verify(exactly = 0) { store.remove(listOf("host", "username", "token", "access_token", "refresh_token", "server_version")) }
    }

    @Test
    fun `clear removes all session keys`() {
      preferences.clear()

      verify {
        store.remove(
          listOf("host", "username", "token", "access_token", "refresh_token", "server_version"),
        )
      }
    }

    @Test
    fun `clear invalidates cached tokens`() {
      every { store.readSecret("token") } returns "first" andThen null

      assertEquals("first", preferences.getToken())

      preferences.clear()

      assertNull(preferences.getToken())
    }
  }
}
