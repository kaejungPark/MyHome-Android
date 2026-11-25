package com.myhome.android

import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 비밀번호 찾기 또는 임시 비밀번호 변경 화면을 표시한다.
 *
 * changeMode=false: 이메일로 임시 비밀번호 발송을 요청한다.
 * changeMode=true: 임시 비밀번호와 새 비밀번호를 서버에 전달한다.
 *
 * 비밀번호는 remember로만 보관하고 저장 상태에는 남기지 않는다.
 * 화면이 재생성되면 비밀번호를 다시 입력해야 한다.
 */
@Composable
fun PasswordHelpScreen(
    changeMode: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    // 찾기 화면에서 사용할 가입 이메일이다.
    var email by remember { mutableStateOf("") }

    // 변경 화면에서 사용할 비밀번호이며 원문을 그대로 유지한다.
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    // 요청 중 중복 실행을 막고, 결과와 오류를 구분해서 표시한다.
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var changed by remember { mutableStateOf(false) }

    // 요청 중에는 뒤로 이동하지 않아 결과 처리가 중간에 끊기지 않게 한다.
    BackHandler {
        if (!busy) onBack()
    }

    /**
     * 화면 종류에 맞는 입력값을 검사한 뒤 서버 요청을 실행한다.
     * 버튼을 연속으로 눌러도 진행 중인 요청은 하나만 유지한다.
     */
    fun submit() {
        if (busy || changed) return

        // 입력 오류는 서버 요청 전에 안내한다.
        val validationError = if (changeMode) {
            when {
                currentPassword.isBlank() ->
                    "이메일로 받은 임시 비밀번호를 입력해 주세요."

                currentPassword.toByteArray(Charsets.UTF_8).size > 72 ->
                    "임시 비밀번호를 다시 확인해 주세요."

                newPassword.isBlank() || newPassword.length < 12 ->
                    "새 비밀번호는 12자 이상 입력해 주세요."

                newPassword.toByteArray(Charsets.UTF_8).size > 72 ->
                    "새 비밀번호는 UTF-8 기준 72바이트 이내로 입력해 주세요."

                newPassword != confirmPassword ->
                    "새 비밀번호와 확인 값이 일치하지 않습니다."

                newPassword == currentPassword ->
                    "임시 비밀번호와 다른 새 비밀번호를 입력해 주세요."

                else -> null
            }
        } else {
            when {
                email.trim().length > 254 ||
                        !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() ->
                    "올바른 이메일 주소를 입력해 주세요."

                else -> null
            }
        }

        if (validationError != null) {
            error = validationError
            message = ""
            return
        }

        // 실행 전에 상태를 변경해 연속 클릭을 차단한다.
        busy = true
        error = null
        message = ""

        scope.launch {
            try {
                if (changeMode) {
                    // 서버는 임시 비밀번호와 기존 비밀번호 재사용 여부도 검사한다.
                    AuthApi.changePassword(
                        currentPassword = currentPassword,
                        newPassword = newPassword
                    )

                    // 성공 후 입력값을 지우고 재로그인 안내를 표시한다.
                    currentPassword = ""
                    newPassword = ""
                    confirmPassword = ""
                    changed = true
                } else {
                    // 계정 존재 여부를 추측하지 않고 서버의 공통 안내를 사용한다.
                    message = AuthApi.requestPasswordReset(email)
                }
            } catch (e: CancellationException) {
                // 화면 종료 등에 의한 취소는 일반 오류로 표시하지 않는다.
                throw e
            } catch (e: Exception) {
                error = e.localizedMessage ?: "요청을 처리하지 못했습니다."
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(
            onClick = onBack,
            enabled = !busy && !changed
        ) {
            Text("‹ 로그인으로")
        }

        Text(
            text = if (changeMode) "새 비밀번호 설정" else "비밀번호 찾기",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = if (changeMode) {
                "임시 비밀번호로 로그인했습니다. 새 비밀번호를 설정해 주세요."
            } else {
                "가입한 이메일로 임시 비밀번호를 요청합니다."
            }
        )

        if (changeMode) {
            // 이메일로 받은 임시 비밀번호를 다시 확인한다.
            OutlinedTextField(
                value = currentPassword,
                onValueChange = { currentPassword = it },
                label = { Text("임시 비밀번호") },
                enabled = !busy && !changed,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // 새 비밀번호는 가입할 때와 같은 최소 길이를 적용한다.
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("새 비밀번호") },
                supportingText = { Text("12자 이상 입력해 주세요.") },
                enabled = !busy && !changed,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // 확인 값은 앱에서 비교하며 서버에는 전달하지 않는다.
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("새 비밀번호 확인") },
                enabled = !busy && !changed,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                ),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // 임시 비밀번호를 받을 가입 이메일을 입력한다.
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("가입 이메일") },
                enabled = !busy,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                "임시 비밀번호는 발송 처리 완료 후 15분 동안 유효합니다. " +
                        "재요청은 1분 후 가능하며, 메일함과 스팸함을 확인해 주세요.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        // 통신 실패와 입력 오류를 사용자에게 안내한다.
        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )
        }

        // 발송 요청 결과는 오류와 구분해서 표시한다.
        if (message.isNotBlank()) {
            Text(message)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { submit() },
                enabled = !busy && !changed
            ) {
                Text(
                    when {
                        busy -> "처리 중…"
                        changeMode -> "비밀번호 변경"
                        else -> "임시 비밀번호 요청"
                    }
                )
            }

            TextButton(
                onClick = onBack,
                enabled = !busy && !changed
            ) {
                Text("취소")
            }
        }
    }

    // 변경 성공 시 서버가 이미 로그아웃했으므로 로그인 화면으로 돌아간다.
    if (changed) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("비밀번호 변경 완료") },
            text = { Text("새 비밀번호로 다시 로그인해 주세요.") },
            confirmButton = {
                TextButton(onClick = onBack) {
                    Text("로그인으로")
                }
            }
        )
    }
}