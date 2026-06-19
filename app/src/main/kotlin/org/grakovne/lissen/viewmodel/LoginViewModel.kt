package org.grakovne.lissen.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.grakovne.lissen.channel.common.AuthMethod
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationError.MissingCredentialsHost
import org.grakovne.lissen.channel.common.OperationError.MissingCredentialsPassword
import org.grakovne.lissen.channel.common.OperationError.MissingCredentialsUsername
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LoginViewModel
  @Inject
  constructor(
    preferences: LissenSharedPreferences,
    private val mediaChannel: LissenMediaProvider,
  ) : ViewModel() {
    private val _host = MutableStateFlow(preferences.getHost() ?: "")
    val host: StateFlow<String> = _host.asStateFlow()

    private val _username = MutableStateFlow(preferences.getUsername() ?: "")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _authMethods = MutableStateFlow<List<AuthMethod>>(emptyList())
    val authMethods: StateFlow<List<AuthMethod>> = _authMethods.asStateFlow()

    private val _customOAuthLoginButtonText = MutableStateFlow<String?>(null)
    val customOAuthLoginButtonText: StateFlow<String?> = _customOAuthLoginButtonText.asStateFlow()

    fun updateAuthData() {
      Timber.d("User action: updateAuthData for host=${host.value}")
      viewModelScope
        .launch {
          val value = host.value.ifEmpty { return@launch }

          mediaChannel
            .provideAuthService()
            .fetchAuthMethods(host = value)
            .fold(
              onSuccess = {
                _authMethods.value = it.methods
                _customOAuthLoginButtonText.value = it.oauthLoginText
              },
              onFailure = {
                _authMethods.value = emptyList()
                _customOAuthLoginButtonText.value = null
              },
            )
        }
    }

    fun setHost(host: String) {
      _host.value = host
    }

    fun setUsername(username: String) {
      _username.value = username
    }

    fun setPassword(password: String) {
      _password.value = password
    }

    fun readyToLogin() {
      _loginState.value = LoginState.Idle
    }

    fun startOAuth() {
      Timber.d("User action: startOAuth for host=${host.value}")
      viewModelScope.launch {
        _loginState.value = LoginState.Loading

        val host =
          host.value.ifEmpty {
            _loginState.value = LoginState.Error(MissingCredentialsHost)
            return@launch
          }

        mediaChannel.startOAuth(
          host = host,
          onSuccess = { _loginState.value = LoginState.Idle },
          onFailure = { onLoginFailure(it) },
        )
      }
    }

    fun login() {
      Timber.d("User action: login for host=${host.value}")
      viewModelScope.launch {
        _loginState.value = LoginState.Loading

        val host =
          host.value.ifEmpty {
            _loginState.value = LoginState.Error(MissingCredentialsHost)
            return@launch
          }

        val username =
          username.value.ifEmpty {
            _loginState.value = LoginState.Error(MissingCredentialsUsername)
            return@launch
          }

        val password =
          password.value.ifEmpty {
            _loginState.value = LoginState.Error(MissingCredentialsPassword)
            return@launch
          }

        val result =
          mediaChannel
            .authorize(host, username, password)
            .foldAsync(
              onSuccess = { _ -> LoginState.Success },
              onFailure = { error -> onLoginFailure(error.code) },
            )
        _loginState.value = result
      }
    }

    private fun onLoginFailure(error: OperationError): LoginState.Error {
      viewModelScope.launch {
        _loginState.value = LoginState.Error(error)
      }
      return LoginState.Error(error)
    }

    sealed class LoginState {
      data object Idle : LoginState()

      data object Loading : LoginState()

      data object Success : LoginState()

      data class Error(
        val message: OperationError,
      ) : LoginState()
    }
  }
