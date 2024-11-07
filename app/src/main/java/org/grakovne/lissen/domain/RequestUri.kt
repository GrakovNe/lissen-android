package org.grakovne.lissen.domain

import android.net.Uri
import org.grakovne.lissen.domain.connection.ServerRequestHeader

data class RequestUri(
    val uri: Uri,
    val headers: List<ServerRequestHeader> = emptyList()
)
