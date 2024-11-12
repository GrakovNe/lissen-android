package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.connection.ConnectionInfoResponse
import org.grakovne.lissen.channel.common.ConnectionInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionInfoResponseConverter @Inject constructor() {

    fun apply(response: ConnectionInfoResponse): ConnectionInfo = ConnectionInfo(
        username = response.user.username,
        serverVersion = response.serverSettings.version,
        buildNumber = response.serverSettings.buildNumber
    )
}
