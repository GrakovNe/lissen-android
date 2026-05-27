package org.grakovne.lissen.viewmodel

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.grakovne.lissen.channel.common.AuthData
import org.grakovne.lissen.channel.common.AuthMethod
import org.grakovne.lissen.channel.common.ChannelAuthService
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.lib.domain.UserAccount
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LoginViewModelTest {
  private val testDispatcher = UnconfinedTestDispatcher()
  private val preferences = mockk<LissenSharedPreferences>(relaxed = true)
  private val mediaChannel = mockk<LissenMediaProvider>(relaxed = true)
  private val authService = mockk<ChannelAuthService>(relaxed = true)
  private lateinit var viewModel: LoginViewModel

  @BeforeEach
  fun setup() {
    ArchTaskExecutor.getInstance().setDelegate(
      object : TaskExecutor() {
        override fun executeOnDiskIO(runnable: Runnable) = runnable.run()

        override fun postToMainThread(runnable: Runnable) = runnable.run()

        override fun isMainThread() = true
      },
    )
    Dispatchers.setMain(testDispatcher)
    every { preferences.getHost() } returns "http://example.com"
    every { preferences.getUsername() } returns "testuser"
    every { mediaChannel.provideAuthService() } returns authService
    viewModel = LoginViewModel(preferences, mediaChannel)
  }

  @AfterEach
  fun teardown() {
    Dispatchers.resetMain()
    ArchTaskExecutor.getInstance().setDelegate(null)
  }

  @Nested
  inner class InitialState {
    @Test
    fun `host is loaded from preferences`() {
      assertEquals("http://example.com", viewModel.host.value)
    }

    @Test
    fun `username is loaded from preferences`() {
      assertEquals("testuser", viewModel.username.value)
    }

    @Test
    fun `password is initially empty`() {
      assertEquals("", viewModel.password.value)
    }

    @Test
    fun `login state is initially Idle`() {
      assertEquals(LoginViewModel.LoginState.Idle, viewModel.loginState.value)
    }

    @Test
    fun `auth methods are initially empty`() {
      assertEquals(emptyList<AuthMethod>(), viewModel.authMethods.value)
    }
  }

  @Nested
  inner class FieldUpdates {
    @Test
    fun `setHost updates host value`() {
      viewModel.setHost("http://new-host.com")
      assertEquals("http://new-host.com", viewModel.host.value)
    }

    @Test
    fun `setUsername updates username value`() {
      viewModel.setUsername("newuser")
      assertEquals("newuser", viewModel.username.value)
    }

    @Test
    fun `setPassword updates password value`() {
      viewModel.setPassword("secret123")
      assertEquals("secret123", viewModel.password.value)
    }

    @Test
    fun `readyToLogin resets state to Idle`() {
      viewModel.readyToLogin()
      assertEquals(LoginViewModel.LoginState.Idle, viewModel.loginState.value)
    }
  }

  @Nested
  inner class Login {
    @Test
    fun `login succeeds and sets Success state`() {
      viewModel.setPassword("pass")
      val account =
        UserAccount(
          token = "tok",
          accessToken = null,
          refreshToken = null,
          username = "testuser",
          preferredLibraryId = null,
        )
      coEvery { mediaChannel.authorize(any(), any(), any()) } returns OperationResult.Success(account)

      viewModel.login()

      assertEquals(LoginViewModel.LoginState.Success, viewModel.loginState.value)
    }

    @Test
    fun `login sets Error on Unauthorized`() {
      viewModel.setPassword("wrong")
      coEvery { mediaChannel.authorize(any(), any(), any()) } returns
        OperationResult.Error(OperationError.Unauthorized)

      viewModel.login()

      val state = viewModel.loginState.value
      assertInstanceOf(LoginViewModel.LoginState.Error::class.java, state)
      assertEquals(
        OperationError.Unauthorized,
        (state as LoginViewModel.LoginState.Error).message,
      )
    }

    @Test
    fun `login sets Error on NetworkError`() {
      viewModel.setPassword("pass")
      coEvery { mediaChannel.authorize(any(), any(), any()) } returns
        OperationResult.Error(OperationError.NetworkError)

      viewModel.login()

      val state = viewModel.loginState.value
      assertInstanceOf(LoginViewModel.LoginState.Error::class.java, state)
      assertEquals(
        OperationError.NetworkError,
        (state as LoginViewModel.LoginState.Error).message,
      )
    }

    @Test
    fun `login passes host username and password to authorize`() {
      viewModel.setHost("http://srv.com")
      viewModel.setUsername("alice")
      viewModel.setPassword("pw")
      val account = UserAccount(null, null, null, "alice", null)
      coEvery { mediaChannel.authorize("http://srv.com", "alice", "pw") } returns
        OperationResult.Success(account)

      viewModel.login()

      coVerify { mediaChannel.authorize("http://srv.com", "alice", "pw") }
    }
  }

  @Nested
  inner class UpdateAuthData {
    @Test
    fun `updateAuthData populates auth methods on success`() {
      val methods = listOf(AuthMethod.CREDENTIALS, AuthMethod.O_AUTH)
      val authData = AuthData(methods = methods, oauthLoginText = "Login with OAuth")
      coEvery { authService.fetchAuthMethods(any()) } returns OperationResult.Success(authData)

      viewModel.updateAuthData()

      assertEquals(methods, viewModel.authMethods.value)
    }

    @Test
    fun `updateAuthData sets OAuth button text on success`() {
      val authData =
        AuthData(
          methods = listOf(AuthMethod.O_AUTH),
          oauthLoginText = "Custom OAuth",
        )
      coEvery { authService.fetchAuthMethods(any()) } returns OperationResult.Success(authData)

      viewModel.updateAuthData()

      assertEquals("Custom OAuth", viewModel.customOAuthLoginButtonText.value)
    }

    @Test
    fun `updateAuthData clears auth methods on failure`() {
      coEvery { authService.fetchAuthMethods(any()) } returns
        OperationResult.Error(OperationError.NetworkError)

      viewModel.updateAuthData()

      assertEquals(emptyList<AuthMethod>(), viewModel.authMethods.value)
    }

    @Test
    fun `updateAuthData clears OAuth button text on failure`() {
      coEvery { authService.fetchAuthMethods(any()) } returns
        OperationResult.Error(OperationError.NetworkError)

      viewModel.updateAuthData()

      assertEquals(null, viewModel.customOAuthLoginButtonText.value)
    }
  }
}
