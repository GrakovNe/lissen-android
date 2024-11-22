package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.user.LoggedUserResponse
import org.grakovne.lissen.domain.UserAccount
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoginResponseConverter @Inject constructor() {

    fun apply(response: LoggedUserResponse): UserAccount = UserAccount(
        token = response.user.token,
        preferredLibraryId = response.userDefaultLibraryId,
    )
}
