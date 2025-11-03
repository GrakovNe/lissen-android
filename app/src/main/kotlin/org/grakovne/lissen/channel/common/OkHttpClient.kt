package org.grakovne.lissen.channel.common

import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.grakovne.lissen.common.withSslBypass
import org.grakovne.lissen.common.withTrustedCertificates
import org.grakovne.lissen.lib.domain.connection.ServerRequestHeader
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import java.util.concurrent.TimeUnit

fun createOkHttpClient(
  requestHeaders: List<ServerRequestHeader>?,
  preferences: LissenSharedPreferences,
  cache: Cache? = null,
): OkHttpClient {
  var builder = OkHttpClient.Builder()
  cache?.let { builder.cache(it) }

  builder =
    when (preferences.getSslBypass()) {
      true -> builder.withSslBypass()
      false -> builder.withTrustedCertificates()
    }

  return builder
    .addInterceptor(loggingInterceptor())
    .addInterceptor { chain -> authInterceptor(chain, preferences, requestHeaders) }
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .build()
}

private fun loggingInterceptor() =
  HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.NONE
  }

private fun authInterceptor(
  chain: Interceptor.Chain,
  preferences: LissenSharedPreferences,
  requestHeaders: List<ServerRequestHeader>?,
): Response {
  val original: Request = chain.request()
  val requestBuilder: Request.Builder = original.newBuilder()

  val bearer = preferences.getAccessToken() ?: preferences.getToken()
  bearer?.let { requestBuilder.header("Authorization", "Bearer $it") }

  requestHeaders
    ?.filter { it.name.isNotEmpty() }
    ?.filter { it.value.isNotEmpty() }
    ?.forEach { requestBuilder.header(it.name, it.value) }

  return chain.proceed(requestBuilder.build())
}
