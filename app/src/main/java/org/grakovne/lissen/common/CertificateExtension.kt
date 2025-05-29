package org.grakovne.lissen.common

import okhttp3.OkHttpClient
import java.security.KeyStore
import javax.net.ssl.SSLContext
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
