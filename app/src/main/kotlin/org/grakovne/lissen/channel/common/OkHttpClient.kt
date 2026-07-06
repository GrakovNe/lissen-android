package org.grakovne.lissen.channel.common

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.grakovne.lissen.common.withSslBypass
import org.grakovne.lissen.common.withTrustedCertificates
import org.grakovne.lissen.domain.connection.ServerRequestHeader
import org.grakovne.lissen.persistence.preferences.ConnectionPreferences
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import timber.log.Timber
import java.util.concurrent.TimeUnit

fun createOkHttpClient(
  requestHeaders: List<ServerRequestHeader>?,
  session: SessionPreferences,
  connection: ConnectionPreferences,
  context: Context,
): OkHttpClient {
  val clientCertAlias = connection.getClientCertAlias()

  var builder = OkHttpClient.Builder()

  builder =
    when (connection.getSslBypass()) {
      true -> builder.withSslBypass(context, clientCertAlias)
      false -> builder.withTrustedCertificates(context, clientCertAlias)
    }

  return builder
    .addInterceptor(loggingInterceptor())
    .addInterceptor { chain -> authInterceptor(chain, session, requestHeaders) }
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .writeTimeout(120, TimeUnit.SECONDS)
    .build()
}

private fun loggingInterceptor() =
  HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.NONE
  }

private fun authInterceptor(
  chain: Interceptor.Chain,
  session: SessionPreferences,
  requestHeaders: List<ServerRequestHeader>?,
): Response {
  val original: Request = chain.request()
  val requestBuilder: Request.Builder = original.newBuilder()

  val bearer = session.getAccessToken() ?: session.getToken()
  try {
    bearer?.let { requestBuilder.header("Authorization", "Bearer $it") }
  } catch (e: IllegalArgumentException) {
    Timber.w("Skipping invalid Authorization header: ${e.message}")
  }

  requestHeaders
    ?.filter { it.name.isNotEmpty() }
    ?.filter { it.value.isNotEmpty() }
    ?.forEach { header ->
      try {
        requestBuilder.header(header.name, header.value)
      } catch (e: IllegalArgumentException) {
        Timber.w("Skipping invalid header '${header.name}': ${e.message}")
      }
    }

  return chain.proceed(requestBuilder.build())
}
