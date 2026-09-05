package org.grakovne.lissen.persistence.preferences

import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionPreferences
  @Inject
  constructor(
    private val store: SecurePreferenceStore,
  ) {
    private val deviceIdLock = Any()

    private val tokenCache = CachedValue { store.readSecret(KEY_TOKEN) }
    private val accessTokenCache = CachedValue { store.readSecret(KEY_ACCESS_TOKEN) }
    private val refreshTokenCache = CachedValue { store.readSecret(KEY_REFRESH_TOKEN) }

    fun getDeviceId(): String =
      synchronized(deviceIdLock) {
        store.getString(KEY_DEVICE_ID)
          ?: UUID
            .randomUUID()
            .toString()
            .also { store.putString(KEY_DEVICE_ID, it) }
      }

    fun getHost(): String? = store.getString(KEY_HOST)

    fun saveHost(host: String) = store.putString(KEY_HOST, host)

    fun getUsername(): String? = store.getString(KEY_USERNAME)

    fun saveUsername(username: String) = store.putString(KEY_USERNAME, username)

    fun getServerVersion(): String? = store.getString(KEY_SERVER_VERSION)

    fun saveServerVersion(version: String) = store.putString(KEY_SERVER_VERSION, version)

    fun saveToken(token: String) = saveSecret(KEY_TOKEN, token, tokenCache)

    fun saveAccessToken(accessToken: String) = saveSecret(KEY_ACCESS_TOKEN, accessToken, accessTokenCache)

    fun saveRefreshToken(refreshToken: String) = saveSecret(KEY_REFRESH_TOKEN, refreshToken, refreshTokenCache)

    fun getToken(): String? = tokenCache.get()

    fun getAccessToken(): String? = accessTokenCache.get()

    fun getRefreshToken(): String? = refreshTokenCache.get()

    fun hasCredentials(): Boolean {
      val host = getHost()
      val username = getUsername()
      val hasToken = getToken() != null || getAccessToken() != null

      return try {
        host != null && username != null && hasToken
      } catch (ex: Exception) {
        Timber.w("Unable to resolve credentials state due to: ${ex.message}")
        false
      }
    }

    fun clearCredentials() {
      store.remove(listOf(KEY_TOKEN, KEY_ACCESS_TOKEN, KEY_REFRESH_TOKEN))
      invalidateTokenCaches()
    }

    fun clear() {
      store.remove(
        listOf(
          KEY_HOST,
          KEY_USERNAME,
          KEY_TOKEN,
          KEY_ACCESS_TOKEN,
          KEY_REFRESH_TOKEN,
          KEY_SERVER_VERSION,
        ),
      )
      invalidateTokenCaches()
    }

    private fun saveSecret(
      key: String,
      value: String,
      cache: CachedValue<String?>,
    ) {
      store.writeSecret(key, value)
      cache.invalidate()
    }

    private fun invalidateTokenCaches() {
      tokenCache.invalidate()
      accessTokenCache.invalidate()
      refreshTokenCache.invalidate()
    }

    companion object {
      private const val KEY_HOST = "host"
      private const val KEY_USERNAME = "username"
      private const val KEY_ACCESS_TOKEN = "access_token"
      private const val KEY_REFRESH_TOKEN = "refresh_token"
      private const val KEY_TOKEN = "token"
      private const val KEY_SERVER_VERSION = "server_version"
      private const val KEY_DEVICE_ID = "device_id"
    }
  }
