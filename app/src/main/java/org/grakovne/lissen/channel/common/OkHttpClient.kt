package org.grakovne.lissen.channel.common

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.grakovne.lissen.common.withTrustedCertificates
import org.grakovne.lissen.domain.connection.ServerRequestHeader
import java.util.concurrent.TimeUnit

fun createOkHttpClient(
  requestHeaders: List<ServerRequestHeader>?,
  token: String? = null,
  accessToken: String? = null,
): OkHttpClient =
  OkHttpClient
    .Builder()
    .withTrustedCertificates()
    .addInterceptor(loggingInterceptor())
    .addInterceptor { chain -> authInterceptor(chain, token, accessToken, requestHeaders) }
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(90, TimeUnit.SECONDS)
    .build()

private fun loggingInterceptor() =
  HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.NONE
  }

private fun authInterceptor(
  chain: Interceptor.Chain,
  token: String? = null,
  accessToken: String? = null,
  requestHeaders: List<ServerRequestHeader>?,
): Response {
  val original: Request = chain.request()
  val requestBuilder: Request.Builder = original.newBuilder()

  val bearer = accessToken ?: token
  bearer?.let { requestBuilder.header("Authorization", "Bearer $it") }

  requestHeaders
    ?.filter { it.name.isNotEmpty() }
    ?.filter { it.value.isNotEmpty() }
    ?.forEach { requestBuilder.header(it.name, it.value) }

  return chain.proceed(requestBuilder.build())
}
