package org.grakovne.lissen.common

import android.annotation.SuppressLint
import android.content.Context
import android.security.KeyChain
import okhttp3.OkHttpClient
import timber.log.Timber
import java.net.Socket
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.TrustManagerFactory.getInstance
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

private val systemTrustManager: X509TrustManager by lazy {
  val keyStore = KeyStore.getInstance("AndroidCAStore")
  keyStore.load(null)

  val trustManagerFactory = getInstance(TrustManagerFactory.getDefaultAlgorithm())
  trustManagerFactory.init(keyStore)

  trustManagerFactory
    .trustManagers
    .first { it is X509TrustManager } as X509TrustManager
}

private val systemSSLContext: SSLContext by lazy {
  SSLContext.getInstance("TLS").apply {
    init(null, arrayOf(systemTrustManager), null)
  }
}

fun OkHttpClient.Builder.withTrustedCertificates(
  context: Context,
  clientCertAlias: String?,
): OkHttpClient.Builder =
  try {
    val keyManagers = buildKeyManagers(context, clientCertAlias)
    if (keyManagers != null) {
      val sslContext =
        SSLContext.getInstance("TLS").apply {
          init(keyManagers, arrayOf(systemTrustManager), null)
        }
      sslSocketFactory(sslContext.socketFactory, systemTrustManager)
    } else {
      sslSocketFactory(systemSSLContext.socketFactory, systemTrustManager)
    }
  } catch (ex: Exception) {
    Timber.e(ex, "Failed to configure trusted certificates with client cert")
    this
  }

@SuppressLint("TrustAllX509TrustManager", "CustomX509TrustManager")
fun OkHttpClient.Builder.withSslBypass(
  context: Context,
  clientCertAlias: String?,
): OkHttpClient.Builder {
  val trustAll =
    object : X509TrustManager {
      override fun checkClientTrusted(
        chain: Array<X509Certificate>,
        authType: String,
      ) {}

      override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
      ) {}

      override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

  val keyManagers = buildKeyManagers(context, clientCertAlias)

  val sslContext =
    SSLContext.getInstance("TLS").apply {
      init(keyManagers, arrayOf<TrustManager>(trustAll), SecureRandom())
    }

  return this
    .sslSocketFactory(sslContext.socketFactory, trustAll)
    .hostnameVerifier { _, _ -> true }
}

private fun buildKeyManagers(
  context: Context,
  alias: String?,
): Array<KeyManager>? {
  if (alias == null) return null
  val appContext = context.applicationContext
  return arrayOf(
    ClientCertKeyManager(
      alias = alias,
      privateKeyLoader = {
        try {
          KeyChain.getPrivateKey(appContext, alias)
        } catch (ex: Exception) {
          Timber.e(ex, "Failed to load private key for alias: $alias")
          null
        }
      },
      certChainLoader = {
        try {
          KeyChain.getCertificateChain(appContext, alias)
        } catch (ex: Exception) {
          Timber.e(ex, "Failed to load cert chain for alias: $alias")
          null
        }
      },
    ),
  )
}

internal class ClientCertKeyManager(
  private val alias: String,
  privateKeyLoader: () -> PrivateKey?,
  certChainLoader: () -> Array<X509Certificate>?,
) : X509ExtendedKeyManager() {
  private val privateKey: PrivateKey? by lazy { privateKeyLoader() }
  private val certChain: Array<X509Certificate>? by lazy { certChainLoader() }

  private val isReady: Boolean
    get() = privateKey != null && certChain != null

  override fun chooseClientAlias(
    keyType: Array<out String>?,
    issuers: Array<out Principal>?,
    socket: Socket?,
  ): String? = if (isReady) alias else null

  override fun chooseEngineClientAlias(
    keyType: Array<out String>?,
    issuers: Array<out Principal>?,
    engine: SSLEngine?,
  ): String? = if (isReady) alias else null

  override fun getCertificateChain(alias: String?): Array<X509Certificate>? = if (alias == this.alias) certChain else null

  override fun getPrivateKey(alias: String?): PrivateKey? = if (alias == this.alias) privateKey else null

  override fun getClientAliases(
    keyType: String?,
    issuers: Array<out Principal>?,
  ): Array<String>? = if (isReady) arrayOf(alias) else null

  override fun chooseServerAlias(
    keyType: String?,
    issuers: Array<out Principal>?,
    socket: Socket?,
  ): String? = null

  override fun chooseEngineServerAlias(
    keyType: String?,
    issuers: Array<out Principal>?,
    engine: SSLEngine?,
  ): String? = null

  override fun getServerAliases(
    keyType: String?,
    issuers: Array<out Principal>?,
  ): Array<String>? = null
}
