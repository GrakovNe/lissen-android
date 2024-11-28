package org.grakovne.lissen.persistence.preferences

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.grakovne.lissen.channel.common.ChannelCode
import org.grakovne.lissen.channel.common.LibraryType
import org.grakovne.lissen.common.ColorScheme
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.connection.ServerRequestHeader
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LissenSharedPreferences @Inject constructor(@ApplicationContext context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)

    fun hasCredentials(): Boolean {
        val host = getHost()
        val username = getUsername()
        val token = getToken()

        return try {
            host != null && username != null && token != null
        } catch (ex: Exception) {
            false
        }
    }

    fun clearPreferences() {
        sharedPreferences.edit().apply {
            remove(KEY_HOST)
            remove(KEY_USERNAME)
            remove(KEY_TOKEN)

            remove(KEY_SERVER_VERSION)

            remove(CACHE_FORCE_ENABLED)

            remove(KEY_PREFERRED_LIBRARY_ID)
            remove(KEY_PREFERRED_LIBRARY_NAME)

            remove(KEY_PREFERRED_PLAYBACK_SPEED)
        }.apply()
    }

    fun saveHost(host: String) = sharedPreferences.edit().putString(KEY_HOST, host).apply()
    fun getHost(): String? = sharedPreferences.getString(KEY_HOST, null)

    fun getDeviceId(): String {
        val existingDeviceId = sharedPreferences.getString(KEY_DEVICE_ID, null)

        if (existingDeviceId != null) {
            return existingDeviceId
        }

        return UUID
            .randomUUID()
            .toString()
            .also { sharedPreferences.edit().putString(KEY_DEVICE_ID, it).apply() }
    }

    // Once the different channel will supported, this shall be extended
    fun getChannel() = ChannelCode.AUDIOBOOKSHELF

    fun getPreferredLibrary(): Library? {
        val id = getPreferredLibraryId() ?: return null
        val name = getPreferredLibraryName() ?: return null

        val type = getPreferredLibraryType()

        return Library(
            id = id,
            title = name,
            type = type,
        )
    }

    fun savePreferredLibrary(library: Library) {
        saveActiveLibraryId(library.id)
        saveActiveLibraryName(library.title)
        saveActiveLibraryType(library.type)
    }

    fun saveColorScheme(colorScheme: ColorScheme) =
        sharedPreferences.edit().putString(KEY_PREFERRED_COLOR_SCHEME, colorScheme.name).apply()

    fun getColorScheme(): ColorScheme =
        sharedPreferences.getString(KEY_PREFERRED_COLOR_SCHEME, ColorScheme.FOLLOW_SYSTEM.name)
            ?.let { ColorScheme.valueOf(it) }
            ?: ColorScheme.FOLLOW_SYSTEM

    fun savePlaybackSpeed(factor: Float) =
        sharedPreferences.edit().putFloat(KEY_PREFERRED_PLAYBACK_SPEED, factor).apply()

    fun getPlaybackSpeed(): Float =
        sharedPreferences.getFloat(KEY_PREFERRED_PLAYBACK_SPEED, 1f)

    val colorSchemeFlow: Flow<ColorScheme> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_PREFERRED_COLOR_SCHEME) {
                trySend(getColorScheme())
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(getColorScheme())
        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    private fun saveActiveLibraryId(host: String) =
        sharedPreferences.edit().putString(KEY_PREFERRED_LIBRARY_ID, host).apply()

    private fun getPreferredLibraryId(): String? =
        sharedPreferences.getString(KEY_PREFERRED_LIBRARY_ID, null)

    private fun saveActiveLibraryName(host: String) =
        sharedPreferences.edit().putString(KEY_PREFERRED_LIBRARY_NAME, host).apply()

    private fun getPreferredLibraryType(): LibraryType =
        sharedPreferences
            .getString(KEY_PREFERRED_LIBRARY_TYPE, null)
            ?.let { LibraryType.valueOf(it) }
            ?: LibraryType.LIBRARY // We have to set the library type AUDIOBOOKSHELF_LIBRARY for backward compatibility

    private fun saveActiveLibraryType(type: LibraryType) =
        sharedPreferences.edit().putString(KEY_PREFERRED_LIBRARY_TYPE, type.name).apply()

    private fun getPreferredLibraryName(): String? =
        sharedPreferences.getString(KEY_PREFERRED_LIBRARY_NAME, null)

    fun enableForceCache() =
        sharedPreferences.edit().putBoolean(CACHE_FORCE_ENABLED, true).apply()

    fun disableForceCache() =
        sharedPreferences.edit().putBoolean(CACHE_FORCE_ENABLED, false).apply()

    fun isForceCache(): Boolean {
        return sharedPreferences.getBoolean(CACHE_FORCE_ENABLED, false)
    }

    fun saveUsername(username: String) =
        sharedPreferences.edit().putString(KEY_USERNAME, username).apply()

    fun getUsername(): String? = sharedPreferences.getString(KEY_USERNAME, null)

    fun saveServerVersion(version: String) =
        sharedPreferences.edit().putString(KEY_SERVER_VERSION, version).apply()

    fun getServerVersion(): String? = sharedPreferences.getString(KEY_SERVER_VERSION, null)

    fun saveToken(password: String) {
        val encrypted = encrypt(password)
        sharedPreferences.edit().putString(KEY_TOKEN, encrypted).apply()
    }

    fun getToken(): String? {
        val encrypted = sharedPreferences.getString(KEY_TOKEN, null) ?: return null
        return decrypt(encrypted)
    }

    fun saveCustomHeaders(headers: List<ServerRequestHeader>) {
        val editor = sharedPreferences.edit()

        val json = gson.toJson(headers)
        editor.putString(KEY_CUSTOM_HEADERS, json)
        editor.apply()
    }

    fun getCustomHeaders(): List<ServerRequestHeader> {
        val json = sharedPreferences.getString(KEY_CUSTOM_HEADERS, null)
        val type = object : TypeToken<MutableList<ServerRequestHeader>>() {}.type

        return when (json == null) {
            true -> emptyList()
            false -> gson.fromJson(json, type)
        }
    }

    companion object {

        private const val KEY_ALIAS = "secure_key_alias"
        private const val KEY_HOST = "host"
        private const val KEY_USERNAME = "username"
        private const val KEY_TOKEN = "token"
        private const val CACHE_FORCE_ENABLED = "cache_force_enabled"

        private const val KEY_SERVER_VERSION = "server_version"

        private const val KEY_DEVICE_ID = "device_id"

        private const val KEY_PREFERRED_LIBRARY_ID = "preferred_library_id"
        private const val KEY_PREFERRED_LIBRARY_NAME = "preferred_library_name"
        private const val KEY_PREFERRED_LIBRARY_TYPE = "preferred_library_type"

        private const val KEY_PREFERRED_PLAYBACK_SPEED = "preferred_playback_speed"

        private const val KEY_PREFERRED_COLOR_SCHEME = "preferred_color_scheme"

        private const val KEY_CUSTOM_HEADERS = "custom_headers"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        private fun getSecretKey(): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            keyStore.getKey(KEY_ALIAS, null)?.let {
                return it as SecretKey
            }

            val keyGenerator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
            keyGenerator.init(keyGenParameterSpec)
            return keyGenerator.generateKey()
        }

        private fun encrypt(data: String): String {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())

            val cipherText = cipher.doFinal(data.toByteArray())
            val ivAndCipherText = cipher.iv + cipherText

            return Base64.encodeToString(ivAndCipherText, Base64.DEFAULT)
        }

        private fun decrypt(data: String): String? {
            val decodedData = Base64.decode(data, Base64.DEFAULT)
            val iv = decodedData.sliceArray(0 until 12)
            val cipherText = decodedData.sliceArray(12 until decodedData.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)

            return try {
                String(cipher.doFinal(cipherText))
            } catch (ex: Exception) {
                null
            }
        }

        private val gson = Gson()
    }
}
