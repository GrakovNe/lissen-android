package org.grakovne.lissen.domain

import android.net.Uri
import org.grakovne.lissen.domain.connection.ServerCustomHeader

data class RequestUri (
    val uri: Uri,
    val headers: List<ServerCustomHeader> = emptyList()
)