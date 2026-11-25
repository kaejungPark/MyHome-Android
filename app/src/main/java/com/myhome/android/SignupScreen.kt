package com.myhome.android

import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 이메일 인증 후 회원가입을 진행하는 화면이다.
 * 가입 성공을 확인하면 로그인 화면으로 돌아간다.
 */
@Composable
fun SignupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    // 입력 정보와 인증 정보는 현재 화면의 메모리에 보관한다.
    var email by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var ageText by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf<String?>(null) }
    var genderExpanded by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var notificationEnabled by remember { mutableStateOf(false) }

    // 발송 요청 ID와 인증 완료 확인값을 구분해 보관한다.
    var requestId by remember { mutableStateOf<String?>(null) }
    var verificationToken by remember { mutableStateOf<String?>(null) }

    // 발송·확인·가입 요청이 겹치지 않도록 공통 요청 상태를 사용한다.
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var succeeded by remember { mutableStateOf(false) }

    // 요청 중에는 뒤로 가기로 진행 중인 화면을 닫지 않는다.
    BackHandler {
        if (!busy) onBack()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                enabled = !busy,
                onClick = onBack
            ) {
                Text("‹ 로그인")
            }

            Text(
                text = "회원가입",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it

                // 이메일이 달라지면 기존 발송 정보와 인증 결과를 초기화한다.
                requestId = null
                verificationToken = null
                code = ""
                message = ""
            },
            enabled = !busy,
            label = { Text("이메일 *") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email
            ),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedButton(
            enabled = !busy && email.isNotBlank(),
            onClick = {
                val targetEmail = email.trim()

                if (targetEmail.length > 254 ||
                    !Patterns.EMAIL_ADDRESS.matcher(targetEmail).matches()
                ) {
                    message = "올바른 이메일 주소를 입력해 주세요."
                } else if (!busy) {
                    busy = true
                    message = ""

                    // 새 발송을 요청하면 이전 인증 결과로 가입하지 않도록 한다.
                    requestId = null
                    verificationToken = null
                    code = ""

                    scope.launch {
                        try {
                            requestId = SignupApi.sendCode(targetEmail)
                            message =
                                "인증번호를 보냈습니다. 5분 안에 입력해 주세요."
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            message = e.localizedMessage
                                ?: "인증번호를 발송하지 못했습니다."
                        } finally {
                            busy = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("인증번호 발송 / 재발송")
        }

        Text(
            text = "재발송은 60초 후 가능해요. 가장 최근에 받은 번호를 입력하세요.",
            style = MaterialTheme.typography.bodySmall
        )

        if (requestId != null && verificationToken == null) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                enabled = !busy,
                label = { Text("인증번호 6자리") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                enabled = !busy && code.matches(Regex("[0-9]{6}")),
                onClick = {
                    val currentRequestId = requestId

                    if (!busy && currentRequestId != null) {
                        busy = true
                        message = ""

                        scope.launch {
                            try {
                                verificationToken = SignupApi.confirmCode(
                                    email = email,
                                    requestId = currentRequestId,
                                    code = code
                                )
                                code = ""
                                message =
                                    "이메일 인증이 완료됐습니다. 15분 안에 가입해 주세요."
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                message = e.localizedMessage
                                    ?: "인증번호를 확인하지 못했습니다."
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("인증번호 확인")
            }
        }

        if (verificationToken != null) {
            Text(
                text = "이메일 인증 완료",
                color = MaterialTheme.colorScheme.primary
            )
        }

        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            enabled = !busy,
            label = { Text("닉네임 *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // 이름은 선택 입력이며 서버에서 최대 100자까지 허용한다.
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            enabled = !busy,
            label = { Text("이름 (선택)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            enabled = !busy,
            label = { Text("비밀번호 *") },
            supportingText = { Text("12자 이상 입력해 주세요.") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password
            ),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = passwordConfirm,
            onValueChange = { passwordConfirm = it },
            enabled = !busy,
            label = { Text("비밀번호 확인 *") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // 나이와 성별은 선택 항목이며, 입력하지 않아도 가입할 수 있다.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 빈칸을 허용하기 위해 문자열로 보관하고, 가입할 때 숫자로 변환한다.
            OutlinedTextField(
                value = ageText,
                onValueChange = { ageText = it },
                enabled = !busy,
                label = { Text("나이 (선택)") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            // 버튼 아래에 성별 선택 메뉴를 표시한다.
            Box(
                modifier = Modifier.weight(1f)
            ) {
                OutlinedButton(
                    onClick = { genderExpanded = true },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text(
                        text = when (gender) {
                            "MALE" -> "남성"
                            "FEMALE" -> "여성"
                            else -> "성별 선택 안 함"
                        }
                    )
                }

                DropdownMenu(
                    expanded = genderExpanded,
                    onDismissRequest = { genderExpanded = false }
                ) {
                    // 선택을 해제하면 서버에는 null을 전달한다.
                    DropdownMenuItem(
                        text = { Text("선택 안 함") },
                        onClick = {
                            gender = null
                            genderExpanded = false
                        }
                    )

                    // 화면에는 한글을 표시하고, 서버에는 약속된 영문 값을 전달한다.
                    DropdownMenuItem(
                        text = { Text("남성") },
                        onClick = {
                            gender = "MALE"
                            genderExpanded = false
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("여성") },
                        onClick = {
                            gender = "FEMALE"
                            genderExpanded = false
                        }
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = notificationEnabled,
                onCheckedChange = { notificationEnabled = it },
                enabled = !busy
            )

            Text(
                text = "이메일 알림 수신 동의 (선택)",
                modifier = Modifier.weight(1f)
            )
        }

        if (message.isNotEmpty()) {
            // 발송 결과와 서버가 반환한 오류 안내를 표시한다.
            Text(message)
        }

        Button(
            enabled = !busy && verificationToken != null,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val token = verificationToken

                // 앞뒤 공백을 제거하고 나이를 숫자로 변환한다.
                // 빈칸이나 숫자가 아닌 값은 null이 되며, 아래 검증에서 구분한다.
                val trimmedAge = ageText.trim()
                val age = trimmedAge.toIntOrNull()

                // 서버의 닉네임·비밀번호 제한에 맞춰 입력값을 검사한다.
                val validationMessage = when {
                    token == null ->
                        "이메일 인증을 완료해 주세요."
                    nickname.trim().length !in 1..100 ->
                        "닉네임은 1~100자로 입력해 주세요."
                    password.isBlank() || password.length < 12 ->
                        "비밀번호는 12자 이상 입력해 주세요."
                    password.toByteArray(Charsets.UTF_8).size > 72 ->
                        "비밀번호는 UTF-8 기준 72바이트 이내로 입력해 주세요."
                    password != passwordConfirm ->
                        "비밀번호 확인이 일치하지 않습니다."
                    // 이름은 선택 항목이지만 입력했다면 최대 길이를 검사한다.
                    name.trim().length > 100 ->
                        "이름은 100자 이내로 입력해 주세요."

                    // 나이는 빈칸을 허용하고, 입력했다면 0~120의 정수만 허용한다.
                    trimmedAge.isNotEmpty() &&
                            (
                                    !trimmedAge.matches(Regex("[0-9]{1,3}")) ||
                                            age == null ||
                                            age !in 0..120
                                    ) ->
                        "나이는 0~120 사이의 정수로 입력해 주세요."
                    else -> null
                }

                if (validationMessage != null) {
                    message = validationMessage
                } else if (!busy && token != null) {
                    busy = true
                    message = ""

                    scope.launch {
                        try {
                            // 인증 정보와 입력값을 서버에 전달한다.
                            // 나이와 성별은 선택하지 않았다면 null로 전달한다.
                            SignupApi.signup(
                                email = email,
                                name = name,
                                nickname = nickname,
                                password = password,
                                age = age,
                                gender = gender,
                                verificationToken = token,
                                emailNotificationEnabled = notificationEnabled
                            )

                            // 가입에 사용한 비밀번호와 일회용 인증값을 비운다.
                            password = ""
                            passwordConfirm = ""
                            verificationToken = null
                            requestId = null
                            succeeded = true
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // 가입 확인값이 소모됐거나 만료됐을 수 있어 재인증하도록 한다.
                            verificationToken = null
                            requestId = null
                            code = ""

                            message =
                                "${e.localizedMessage ?: "가입 결과를 확인하지 못했습니다."}\n" +
                                        "인증번호를 다시 발송해 인증해 주세요. " +
                                        "이미 가입된 이메일이라고 나오면 로그인 화면에서 확인해 주세요."
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        ) {
            Text(if (busy) "처리 중…" else "회원가입")
        }
    }

    // 가입 성공 후 로그인 화면으로 돌아가도록 안내한다.
    if (succeeded) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("회원가입 완료") },
            text = { Text("가입한 이메일과 비밀번호로 로그인해 주세요.") },
            confirmButton = {
                TextButton(onClick = onBack) {
                    Text("로그인으로 이동")
                }
            }
        )
    }
}