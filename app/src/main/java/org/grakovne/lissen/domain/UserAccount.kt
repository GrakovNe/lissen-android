package org.grakovne.lissen.domain

data class UserAccount(
    val token: String,
    val username: String,
    val preferredLibraryId: String?,
)
