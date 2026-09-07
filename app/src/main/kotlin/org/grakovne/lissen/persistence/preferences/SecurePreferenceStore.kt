package org.grakovne.lissen.persistence.preferences

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePreferenceStore
  @Inject
  constructor(
    @ApplicationContext context: Context,
  ) {
    val preferences: SharedPreferences =
      context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)

    fun getString(
      key: String,
      default: String? = null,
    ): String? = preferences.getString(key, default)

    fun putString(
      key: String,
      value: String,
      commit: Boolean = false,
    ) = preferences.edit(commit = commit) { putString(key, value) }

    fun getBoolean(
      key: String,
      default: Boolean,
    ): Boolean = preferences.getBoolean(key, default)

    fun putBoolean(
      key: String,
      value: Boolean,
    ) = preferences.edit { putBoolean(key, value) }

    fun getInt(
      key: String,
      default: Int,
    ): Int = preferences.getInt(key, default)

    fun putInt(
      key: String,
      value: Int,
    ) = preferences.edit { putInt(key, value) }

    fun getFloat(
      key: String,
      default: Float,
    ): Float = preferences.getFloat(key, default)

    fun putFloat(
      key: String,
      value: Float,
    ) = preferences.edit { putFloat(key, value) }

    fun remove(
      key: String,
      commit: Boolean = false,
    ) = preferences.edit(commit = commit) { remove(key) }

    fun remove(keys: List<String>) =
      preferences.edit {
        keys.forEach { remove(it) }
      }

    fun readSecret(key: String): String? {
      val encrypted = preferences.getString(key, null) ?: return null
      return decrypt(encrypted)
    }

    fun writeSecret(
      key: String,
      value: String,
    ) = preferences.edit { putString(key, encrypt(value)) }

    fun <T> asFlow(
      key: String,
      getter: () -> T,
    ): Flow<T> =
      callbackFlow {
        val listener =
          SharedPreferences.OnSharedPreferenceChangeListener { _, changeKey ->
            if (changeKey == key) {
              trySend(getter())
            }
          }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(getter())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
      }.distinctUntilChanged()

    private fun encrypt(data: String): String {
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.ENCRYPT_MODE, secretKey)

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
      cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

      return try {
        String(cipher.doFinal(cipherText))
      } catch (ex: Exception) {
        Timber.w("Unable to decrypt stored value due to: ${ex.message}")
        null
      }
    }

    companion object {
      private const val KEY_ALIAS = "secure_key_alias"
      private const val ANDROID_KEYSTORE = "AndroidKeyStore"
      private const val TRANSFORMATION = "AES/GCM/NoPadding"

      private val secretKey: SecretKey by lazy { loadOrGenerateSecretKey() }

      private fun loadOrGenerateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        keyStore.getKey(KEY_ALIAS, null)?.let {
          return it as SecretKey
        }

        val keyGenerator =
          KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keyGenParameterSpec =
          KeyGenParameterSpec
            .Builder(
              KEY_ALIAS,
              KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
      }
    }
  }
