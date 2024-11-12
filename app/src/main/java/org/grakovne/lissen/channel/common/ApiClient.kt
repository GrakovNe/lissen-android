package org.grakovne.lissen.channel.common

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.grakovne.lissen.common.withTrustedCertificates
import org.grakovne.lissen.domain.connection.ServerRequestHeader
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ApiClient(
    host: String,
    requestHeaders: List<ServerRequestHeader>?,
    token: String? = null
) {

    private val httpClient = OkHttpClient
        .Builder()
        .withTrustedCertificates()
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.NONE
            }
        )
        .addInterceptor { chain: Interceptor.Chain ->
            val original: Request = chain.request()
            val requestBuilder: Request.Builder = original.newBuilder()

            if (token != null) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            requestHeaders
                ?.filter { it.name.isNotEmpty() }
                ?.filter { it.value.isNotEmpty() }
                ?.forEach { requestBuilder.header(it.name, it.value) }

            val request: Request = requestBuilder.build()
            chain.proceed(request)
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val retrofit: Retrofit =
        Retrofit.Builder()
            .baseUrl(host)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
}
