package org.grakovne.lissen.channel.audiobookshelf.api

import org.grakovne.lissen.channel.common.USER_AGENT
import org.grakovne.lissen.domain.connection.ServerCustomHeader
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RequestHeadersProvider @Inject constructor(
    private val preferences: LissenSharedPreferences
) {

    fun fetchRequestHeaders(): List<ServerCustomHeader> {
        val usersHeaders = preferences
            .getCustomHeaders()
            ?: emptyList()

        val userAgent = ServerCustomHeader("User-Agent", USER_AGENT)
        return usersHeaders + userAgent
    }
}
