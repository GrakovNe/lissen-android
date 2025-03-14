package org.grakovne.lissen.ui.screens.login

import android.content.Context
import android.util.Log
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.error.LoginError
import org.grakovne.lissen.domain.error.LoginError.InternalError
import org.grakovne.lissen.domain.error.LoginError.InvalidCredentialsHost
import org.grakovne.lissen.domain.error.LoginError.MissingCredentialsHost
import org.grakovne.lissen.domain.error.LoginError.MissingCredentialsPassword
import org.grakovne.lissen.domain.error.LoginError.MissingCredentialsUsername
import org.grakovne.lissen.domain.error.LoginError.NetworkError
import org.grakovne.lissen.domain.error.LoginError.Unauthorized
import org.grakovne.lissen.ui.extensions.withMinimumTime
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.viewmodel.LoginViewModel
import org.grakovne.lissen.viewmodel.LoginViewModel.LoginState

@Composable
fun LoginScreen(
    navController: AppNavigationService,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val loginState by viewModel.loginState.collectAsState()
    val loginError by viewModel.loginError.observeAsState()

    val host by viewModel.host.observeAsState("")
    val username by viewModel.username.observeAsState("")
    val password by viewModel.password.observeAsState("")

    var showPassword by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LaunchedEffect(loginState) {
        if (loginState is LoginState.Loading) {
            return@LaunchedEffect
        }

        withMinimumTime(300) {
            Log.d(TAG, "Tried to log in with result $loginState and possible error is $loginError")
        }

        when (loginState) {
            is LoginState.Success -> navController.showLibrary(clearHistory = true)
            is LoginState.Error -> loginError?.let { Toast.makeText(context, it.makeText(context), LENGTH_SHORT).show() }
            else -> {}
        }
        viewModel.readyToLogin()
    }

    Scaffold(
        modifier = Modifier
            .systemBarsPadding()
            .fillMaxSize(),
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.8f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.login_screen_title),
                        style = TextStyle(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.5.sp,
                            textAlign = TextAlign.Center,
                        ),
                        modifier = Modifier.padding(vertical = 32.dp),
                    )

                    OutlinedTextField(
                        value = host,
                        onValueChange = { viewModel.setHost(it.trim()) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        label = { Text(stringResource(R.string.login_screen_server_url_input)) },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("hostInput"),
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = { viewModel.setUsername(it.trim()) },
                        label = { Text(stringResource(R.string.login_screen_login_input)) },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .testTag("usernameInput"),
                    )

                    OutlinedTextField(
                        value = password,
                        visualTransformation = if (!showPassword) PasswordVisualTransformation() else VisualTransformation.None,
                        onValueChange = { viewModel.setPassword(it) },
                        trailingIcon = {
                            IconButton(
                                onClick = { showPassword = !showPassword },
                            ) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = stringResource(R.string.login_screen_show_password_hint),
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        label = { Text(stringResource(R.string.login_screen_password_input)) },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("passwordInput"),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                    ) {
                        Button(
                            onClick = { viewModel.login() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("loginButton"),
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                bottomStart = 16.dp,
                                topEnd = 0.dp,
                                bottomEnd = 0.dp,
                            ),
                        ) {
                            Spacer(modifier = Modifier.width(28.dp))
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.login_screen_connect_button_text),
                                    fontSize = 16.sp,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(1.dp))

                        Button(
                            onClick = {
                                navController.showSettings()
                            },
                            modifier = Modifier.width(56.dp),
                            shape = RoundedCornerShape(
                                topStart = 0.dp,
                                bottomStart = 0.dp,
                                topEnd = 16.dp,
                                bottomEnd = 16.dp,
                            ),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }

                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp,
                        modifier = Modifier
                            .padding(vertical = 32.dp)
                            .alpha(if (loginState !is LoginState.Idle) 1f else 0f),
                    )
                }

                Text(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .alpha(0.6f)
                        .padding(bottom = 32.dp),
                    text = stringResource(R.string.audiobookshelf_server_is_required),
                    style = typography.bodySmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                        color = colorScheme.onBackground,
                        letterSpacing = 0.6.sp,
                        lineHeight = 32.sp,
                    ),
                    textAlign = TextAlign.Center,
                )
            }
        },
    )
}

private const val TAG: String = "LoginScreen"

private fun LoginError.makeText(context: Context) = when (this) {
    InternalError -> context.getString(R.string.login_error_host_is_down)
    MissingCredentialsHost -> context.getString(R.string.login_error_host_url_is_missing)
    MissingCredentialsPassword -> context.getString(R.string.login_error_username_is_missing)
    MissingCredentialsUsername -> context.getString(R.string.login_error_password_is_missing)
    Unauthorized -> context.getString(R.string.login_error_credentials_are_invalid)
    InvalidCredentialsHost -> context.getString(R.string.login_error_host_url_shall_be_https_or_http)
    NetworkError -> context.getString(R.string.login_error_connection_error)
}
