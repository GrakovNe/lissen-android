package org.grakovne.lissen.common

import okhttp3.OkHttpClient
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.TrustManagerFactory.getInstance
import javax.net.ssl.X509TrustManager

fun OkHttpClient.Builder.withTrustedCertificates(): OkHttpClient.Builder {
    return try {
        val trustManager = getSystemTrustManager()
        val sslContext = getSystemSSLContext(trustManager)
        this.sslSocketFactory(sslContext.socketFactory, trustManager)

        this
    } catch (ex: Exception) {
        this
    }
}

private fun getSystemTrustManager(): X509TrustManager {
    val keyStore = KeyStore.getInstance("AndroidCAStore")
    keyStore.load(null)

    val trustManagerFactory = getInstance(TrustManagerFactory.getDefaultAlgorithm())
    trustManagerFactory.init(keyStore)

    return trustManagerFactory.trustManagers.first { it is X509TrustManager } as X509TrustManager
}

private fun getSystemSSLContext(trustManager: X509TrustManager): SSLContext {
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, arrayOf(trustManager), null)
    return sslContext
}
