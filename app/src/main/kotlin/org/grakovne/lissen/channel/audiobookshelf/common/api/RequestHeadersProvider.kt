package org.grakovne.lissen.channel.audiobookshelf.common.api

import org.grakovne.lissen.domain.connection.ServerRequestHeader
import org.grakovne.lissen.persistence.preferences.ConnectionPreferences
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RequestHeadersProvider
  @Inject
  constructor(
    private val connection: ConnectionPreferences,
  ) {
    fun fetchRequestHeaders(): List<ServerRequestHeader> {
      val usersHeaders = connection.getCustomHeaders()

      val userAgent = ServerRequestHeader("User-Agent", connection.getUserAgent())
      val headers = usersHeaders + userAgent

      Timber.d("Request headers: count=${headers.size}, customHeaders=${usersHeaders.map { it.name }}")
      return headers
    }
  }
