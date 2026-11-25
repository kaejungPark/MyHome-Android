package com.myhome.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 이메일과 비밀번호로 로그인한다.
 * 이메일의 엔터는 비밀번호로 이동하고, 비밀번호의 엔터는 로그인을 실행한다.
 */
@Composable
fun LoginForm(
    onLoginSuccess: () -> Unit,
    onOpenSignup: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    // 이메일에서 다음 키를 누르면 비밀번호 입력칸으로 이동한다.
    val passwordFocus = remember { FocusRequester() }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    /**
     * 버튼과 키보드가 같은 로그인 처리를 사용한다.
     * 요청 중이거나 필수값이 없으면 새 요청을 시작하지 않는다.
     */
    fun submitLogin() {
        if (submitting) return

        if (email.isBlank() || password.isEmpty()) {
            message = "이메일과 비밀번호를 입력해 주세요."
            return
        }

        // 요청에 사용할 입력값을 확정하고 키보드를 닫는다.
        val loginEmail = email.trim()
        val loginPassword = password
        submitting = true
        message = ""
        keyboard?.hide()

        scope.launch {
            try {
                val needsPasswordChange = AuthApi.login(
                    loginEmail,
                    loginPassword
                )
                password = ""

                if (needsPasswordChange) {
                    message =
                        "임시 비밀번호로 로그인했습니다. 웹에서 비밀번호를 변경해 주세요."
                } else {
                    onLoginSuccess()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.localizedMessage ?: "로그인하지 못했습니다."
            } finally {
                submitting = false
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("이메일") },
            singleLine = true,
            enabled = !submitting,
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    // PC 키보드의 엔터는 비밀번호 입력칸으로 이동시킨다.
                    if (event.key == Key.Enter ||
                        event.key == Key.NumPadEnter
                    ) {
                        if (event.type == KeyEventType.KeyUp && !submitting) {
                            passwordFocus.requestFocus()
                        }
                        true
                    } else {
                        false
                    }
                },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                // 휴대폰 키보드의 다음 버튼을 처리한다.
                onNext = { passwordFocus.requestFocus() }
            )
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("비밀번호") },
            singleLine = true,
            enabled = !submitting,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(passwordFocus)
                .onPreviewKeyEvent { event ->
                    // PC 키보드의 엔터는 한 번만 로그인 요청을 실행한다.
                    if (event.key == Key.Enter ||
                        event.key == Key.NumPadEnter
                    ) {
                        if (event.type == KeyEventType.KeyUp) {
                            submitLogin()
                        }
                        true
                    } else {
                        false
                    }
                },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                // 휴대폰 키보드의 완료 버튼도 로그인 버튼과 같은 처리를 한다.
                onDone = { submitLogin() }
            )
        )

        Button(
            enabled = !submitting &&
                    email.isNotBlank() &&
                    password.isNotEmpty(),
            onClick = { submitLogin() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (submitting) "로그인 중…" else "로그인")
        }

        if (message.isNotEmpty()) {
            Text(message)
        }

        TextButton(
            enabled = !submitting,
            onClick = onOpenSignup,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("회원가입")
        }
    }
}