package org.grakovne.lissen.domain.connection

import java.util.UUID

data class ServerCustomHeader(
    val name: String,
    val value: String,
    val id: UUID = UUID.randomUUID()
) {

    companion object {
        fun empty() = ServerCustomHeader("", "")

        fun String.clean(): String {
            var sanitized = this.replace(Regex("[\\r\\n]"), "")
            sanitized = sanitized.replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]"), "")
            sanitized = sanitized.trim()

            return sanitized
        }
    }
}
