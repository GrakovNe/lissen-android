package org.grakovne.lissen.domain

import java.util.UUID

data class CustomHeader(
    val name: String,
    val value: String,
    val id: UUID = UUID.randomUUID()
) {

    companion object {
        fun empty() = CustomHeader("", "")
    }
}
