package org.grakovne.lissen.channel.audiobookshelf.common.api

import org.grakovne.lissen.channel.common.USER_AGENT
import org.grakovne.lissen.lib.domain.connection.ServerRequestHeader
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RequestHeadersProvider
  @Inject
  constructor(
    private val preferences: LissenSharedPreferences,
  ) {
    fun fetchRequestHeaders(): List<ServerRequestHeader> {
      val usersHeaders = preferences.getCustomHeaders()

      val userAgent = ServerRequestHeader("User-Agent", USER_AGENT)
      val headers = usersHeaders + userAgent

      Timber.d("Request headers: count=${headers.size}, customHeaders=${usersHeaders.map { it.name }}")
      return headers
    }
  }
