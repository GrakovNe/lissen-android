package org.grakovne.lissen.channel.common

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.grakovne.lissen.domain.connection.ServerRequestHeader
import retrofit2.Retrofit
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class BinaryApiClient(
    host: String,
    requestHeaders: List<ServerRequestHeader>?,
    token: String
) {

    fun getSystemTrustManager(): X509TrustManager {
        val keyStore = KeyStore.getInstance("AndroidCAStore")
        keyStore.load(null)

        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(keyStore)

        return trustManagerFactory.trustManagers.first { it is X509TrustManager } as X509TrustManager
    }

    fun getSystemSSLContext(trustManager: X509TrustManager): SSLContext {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), null)
        return sslContext
    }

    private val httpClient = OkHttpClient
        .Builder()
        .sslSocketFactory(getSystemSSLContext(getSystemTrustManager()).socketFactory, getSystemTrustManager())
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.NONE
            }
        )
        .addInterceptor { chain: Interceptor.Chain ->
            val request = chain
                .request()
                .newBuilder()
                .header("Authorization", "Bearer $token")

            requestHeaders
                ?.filter { it.name.isNotEmpty() }
                ?.filter { it.value.isNotEmpty() }
                ?.forEach { request.header(it.name, it.value) }

            chain.proceed(request.build())
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(host)
        .client(httpClient)
        .build()
}
