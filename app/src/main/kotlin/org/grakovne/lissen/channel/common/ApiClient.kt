package org.grakovne.lissen.channel.common

import android.content.Context
import com.squareup.moshi.Moshi
import org.grakovne.lissen.domain.connection.ServerRequestHeader
import org.grakovne.lissen.domain.fixUriScheme
import org.grakovne.lissen.persistence.preferences.ConnectionPreferences
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class ApiClient(
  host: String,
  requestHeaders: List<ServerRequestHeader>?,
  session: SessionPreferences,
  connection: ConnectionPreferences,
  context: Context,
) {
  val httpClient = createOkHttpClient(requestHeaders, session = session, connection = connection, context = context)

  val retrofit: Retrofit? =
    runCatching {
      Retrofit
        .Builder()
        .baseUrl(host.fixUriScheme())
        .client(httpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
    }.getOrNull()

  companion object {
    private val moshi: Moshi =
      Moshi
        .Builder()
        .build()
  }
}
