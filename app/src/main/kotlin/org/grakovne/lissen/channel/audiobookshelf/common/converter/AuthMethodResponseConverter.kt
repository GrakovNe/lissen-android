package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.auth.AuthMethodResponse
import org.grakovne.lissen.channel.common.AuthData
import org.grakovne.lissen.channel.common.AuthMethod
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthMethodResponseConverter
  @Inject
  constructor() {
    fun apply(response: AuthMethodResponse): AuthData {
      val methods =
        response
          .authMethods
          .mapNotNull {
            when (it) {
              "local" -> AuthMethod.CREDENTIALS
              "openid" -> AuthMethod.O_AUTH
              else -> null
            }
          }

      return AuthData(
        methods = methods,
        oauthLoginText = response.authFormData?.authOpenIDButtonText,
      )
    }
  }
