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
  token: String? = null,
  accessToken: String? = null,
) {
  private val httpClient = createOkHttpClient(requestHeaders, token, accessToken)
  
  val retrofit: Retrofit =
    Retrofit
      .Builder()
      .baseUrl(host)
      .client(httpClient)
      .addConverterFactory(GsonConverterFactory.create())
      .build()
}
