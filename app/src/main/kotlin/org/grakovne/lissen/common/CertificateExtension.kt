package org.grakovne.lissen.common

import android.annotation.SuppressLint
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.TrustManagerFactory.getInstance
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

fun OkHttpClient.Builder.withTrustedCertificates(): OkHttpClient.Builder =
  try {
    sslSocketFactory(systemSSLContext.socketFactory, systemTrustManager)
  } catch (ex: Exception) {
    this
  }

@SuppressLint("TrustAllX509TrustManager", "CustomX509TrustManager")
fun OkHttpClient.Builder.withSslBypass(): OkHttpClient.Builder {
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

  val sslContext =
    SSLContext.getInstance("TLS").apply {
      init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
    }

  return this
    .sslSocketFactory(sslContext.socketFactory, trustAll)
    .hostnameVerifier { _, _ -> true }
}
