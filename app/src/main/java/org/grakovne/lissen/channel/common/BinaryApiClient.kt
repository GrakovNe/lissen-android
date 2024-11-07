package org.grakovne.lissen.channel.common

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.grakovne.lissen.domain.connection.ServerRequestHeader
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class BinaryApiClient(
    host: String,
    requestHeaders: List<ServerRequestHeader>?,
    token: String
) {

    private val httpClient = OkHttpClient.Builder()
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
