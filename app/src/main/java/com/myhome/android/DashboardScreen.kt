package com.myhome.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myhome.android.ui.theme.MyHomeTheme
import androidx.compose.material3.Button
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect

// 로그인 상태에 따라 로그인 폼 또는 장보기 버튼을 표시한다.
@Composable
fun DashboardScreen(
    isLoggedIn: Boolean,
    onLoginSuccess: () -> Unit,
    onOpenShopping: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 버튼을 눌렀을 때 요청을 시작하고, 화면을 벗어나면 해당 작업을 취소한다.
    val scope = rememberCoroutineScope()

    // 요청 중에는 중복 클릭을 막고, 완료되면 결과를 화면에 표시한다.
    var checking by remember { mutableStateOf(false) }
    var connectionMessage by remember { mutableStateOf("") }

    // Column은 안에 넣은 요소를 위에서 아래로 배치한다.
    Column(
        // 호출한 쪽에서 전달한 여백에 대시보드 자체 여백 16dp를 더한다.
        modifier = modifier.padding(16.dp),

        // 제목과 안내 문구 사이를 세로로 8dp 띄운다.
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "대시보드",

            // 앱 테마에 정의된 제목용 글자 크기와 굵기를 사용한다.
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "우리 집 생활 정보를 한눈에 확인하세요.",

            // 안내 문구에는 본문용 글자 스타일을 사용한다.
            style = MaterialTheme.typography.bodyMedium
        )

        // 장보기 버튼을 누르면 MainActivity에서 전달한 화면 이동 함수를 실행한다.
        Button(
            enabled = !checking,
            onClick = {
                checking = true
                connectionMessage = ""

                scope.launch {
                    try {
                        AuthApi.fetchCsrfToken()
                        connectionMessage = "서버 연결 성공"
                    } catch (e: CancellationException) {
                        // 화면 이동으로 취소한 요청은 오류로 표시하지 않는다.
                        throw e
                    } catch (e: Exception) {
                        connectionMessage = "서버 연결 실패: ${e.localizedMessage ?: "응답을 확인해 주세요."}"
                    } finally {
                        checking = false
                    }
                }
            }
        ) {
            Text(if (checking) "연결 확인 중…" else "서버 연결 확인")
        }

        // 서버 연결 결과를 한 번만 표시한다.
        if (connectionMessage.isNotEmpty()) {
            Text(text = connectionMessage)
        }

        if (isLoggedIn) {
            // null은 아직 조회 전, 0은 조회에 성공했고 살 물품이 없다는 뜻이다.
            var shoppingCount by remember { mutableStateOf<Long?>(null) }
            var countFailed by remember { mutableStateOf(false) }

            // 로그인 후 대시보드가 열리거나 장보기에서 돌아올 때 한 번 조회한다.
            LaunchedEffect(Unit) {
                try {
                    val result = ShoppingApi.fetchShoppingItems(
                        isCompleted = false, // 살 물품만 조회한다.
                        page = 1
                    )
                    shoppingCount = result.totalCount
                } catch (e: CancellationException) {
                    // 조회 중 장보기 화면으로 이동하면 대시보드의 요청을 취소한다.
                    throw e
                } catch (e: Exception) {
                    countFailed = true
                }
            }

            // 건수 조회에 실패해도 버튼을 눌러 장보기 목록을 열 수 있다.
            Button(onClick = onOpenShopping) {
                val label = when {
                    countFailed -> "장보기 · 건수 확인 실패"
                    shoppingCount == null -> "살 물품 확인 중…"
                    else -> "살 물품 ${shoppingCount}개  ›"
                }
                Text(label)
            }
        } else {
            // 로그인 폼이 성공을 알리면 MainActivity까지 전달한다.
            LoginForm(onLoginSuccess = onLoginSuccess)
        }
    }
}

// Android Studio에서 앱을 실행하지 않고 대시보드 모양을 확인하는 미리보기다.
@Preview(showBackground = true)
@Composable
fun DashboardPreview() {
    // 실제 앱과 같은 테마로 표시한다.
    MyHomeTheme {
        // 로그인 전 대시보드를 미리 본다. true로 바꾸면 장보기 버튼을 볼 수 있다.
        DashboardScreen(
            isLoggedIn = false,
            onLoginSuccess = {},
            onOpenShopping = {}
        )
    }
}
