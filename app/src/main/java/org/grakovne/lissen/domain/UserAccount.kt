package org.grakovne.lissen.domain

data class UserAccount(
    val token: String,
    val preferredLibraryId: String?
)
