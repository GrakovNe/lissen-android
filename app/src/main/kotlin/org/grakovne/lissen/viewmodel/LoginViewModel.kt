package org.grakovne.lissen.viewmodel

import androidx.lifecycle.MutableLiveData
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
import javax.inject.Inject

@HiltViewModel
class LoginViewModel
  @Inject
  constructor(
    preferences: LissenSharedPreferences,
    private val mediaChannel: LissenMediaProvider,
  ) : ViewModel() {
    private val _host = MutableLiveData(preferences.getHost() ?: "")
    val host = _host

    private val _username = MutableLiveData(preferences.getUsername() ?: "")
    val username = _username

    private val _password = MutableLiveData("")
    val password = _password

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _authMethods = MutableLiveData<List<AuthMethod>>(emptyList())
    val authMethods = _authMethods

    fun updateAuthMethods() {
      viewModelScope
        .launch {
          val value = host.value ?: return@launch

          mediaChannel
            .provideAuthService()
            .fetchAuthMethods(host = value)
            .fold(
              onSuccess = { _authMethods.value = it },
              onFailure = { _authMethods.value = emptyList() },
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
      viewModelScope.launch {
        _loginState.value = LoginState.Loading

        val host =
          host.value ?: run {
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
      viewModelScope.launch {
        _loginState.value = LoginState.Loading

        val host =
          host.value ?: run {
            _loginState.value = LoginState.Error(MissingCredentialsHost)
            return@launch
          }

        val username =
          username.value ?: run {
            _loginState.value = LoginState.Error(MissingCredentialsUsername)
            return@launch
          }

        val password =
          password.value ?: run {
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
