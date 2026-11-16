package com.myhome.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.IOException

// 이메일과 비밀번호로 로그인하고, 일반 로그인에 성공하면 상위 화면에 알린다.
@Composable
fun LoginForm(onLoginSuccess: () -> Unit) {
    // 버튼을 눌렀을 때 로그인 요청을 시작한다. 화면을 벗어나면 요청 작업도 취소된다.
    val scope = rememberCoroutineScope()

    // remember는 입력할 때마다 화면이 다시 그려져도 입력값을 유지한다.
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // submitting은 요청 진행 여부, message는 화면에 보여줄 결과 문구다.
    var submitting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    // 이메일 입력란, 비밀번호 입력란, 로그인 버튼을 위에서 아래로 배치한다.
    Column {
        // 이메일을 입력하면 변경된 값이 email에 저장된다.
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("이메일") },
            singleLine = true,
            enabled = !submitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        // 비밀번호는 입력한 그대로 저장하고 화면에서는 글자를 가려서 보여준다.
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("비밀번호") },
            singleLine = true,
            enabled = !submitting,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Button(
            // 두 항목을 모두 입력해야 버튼이 활성화된다. 요청 중에는 중복 클릭을 막는다.
            enabled = email.isNotBlank() && password.isNotEmpty() && !submitting,
            onClick = {
                // 새 요청을 시작할 때 이전 결과 문구를 지운다.
                submitting = true
                message = ""

                // suspend 함수인 AuthApi.login()을 호출하기 위해 코루틴을 시작한다.
                scope.launch {
                    try {
                        // 로그인 응답의 true는 임시 비밀번호 변경이 필요하다는 뜻이다.
                        val needsPasswordChange = AuthApi.login(email, password)
                        password = ""

                        if (needsPasswordChange) {
                            // 임시 비밀번호로 로그인하면 안내를 유지한다.
                            message = "임시 비밀번호로 로그인했습니다. 새 비밀번호로 변경해 주세요."
                        } else {
                            // 일반 로그인 성공을 알려 대시보드에 장보기 버튼을 표시한다.
                            onLoginSuccess()
                        }
                    } catch (e: CancellationException) {
                        // 화면 이동으로 작업이 취소되면 코루틴이 종료되도록 예외를 다시 전달한다.
                        throw e
                    } catch (e: IOException) {
                        // 로그인 거부 또는 연결 실패 시 AuthApi에서 전달한 메시지를 보여준다.
                        message = e.localizedMessage ?: "서버 연결을 확인해 주세요."
                    } catch (e: org.json.JSONException) {
                        // 서버 응답에서 필요한 로그인 정보를 읽지 못한 경우다.
                        message = "서버의 로그인 응답을 읽을 수 없습니다."
                    } finally {
                        // 요청이 끝나면 다시 입력하고 로그인할 수 있도록 잠금을 푼다.
                        submitting = false
                    }
                }
            }
        ) {
            // 서버 응답을 기다리는 동안 버튼 문구를 바꾼다.
            Text(if (submitting) "로그인 중…" else "로그인")
        }

        // 결과 문구가 생기면 로그인 버튼 아래에 표시한다.
        if (message.isNotEmpty()) {
            Text(message)
        }
    }
}