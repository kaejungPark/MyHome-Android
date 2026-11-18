package com.myhome.android

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.myhome.android.ui.theme.MyHomeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

/**
 * 로그인 상태와 서버 확인 결과를 보관한다.
 * 화면이 회전해 Activity가 다시 생성되어도 같은 ViewModel을 사용한다.
 */
class DashboardSessionViewModel : ViewModel() {
    // 일반 로그인 여부이며 외부에서는 함수를 통해 변경한다.
    var isLoggedIn by mutableStateOf(false)
        private set

    // 서버 확인이 끝날 때까지 로그인 폼이나 서비스 화면 표시를 기다린다.
    var checking by mutableStateOf(true)
        private set

    // 연결 실패 또는 비밀번호 변경 필요 안내를 보관한다.
    var checkError by mutableStateOf<String?>(null)
        private set

    /**
     * 서버에 현재 세션의 로그인 상태를 확인한다.
     * 통신 실패 시에는 오류를 표시하고 재시도할 수 있게 한다.
     */
    suspend fun checkSession() {
        checking = true
        checkError = null

        try {
            // 401 응답은 false로 반환되고, 연결 실패는 예외로 전달된다.
            isLoggedIn = AuthApi.checkLoginStatus()
        } catch (e: CancellationException) {
            // Activity 종료나 재생성으로 취소된 요청은 취소 처리를 이어간다.
            throw e
        } catch (e: Exception) {
            checkError = e.localizedMessage
                ?: "로그인 상태를 확인하지 못했습니다."
        } finally {
            checking = false
        }
    }

    /**
     * 로그인 API가 일반 로그인 성공을 반환한 뒤 호출한다.
     */
    fun markLoggedIn() {
        isLoggedIn = true
        checkError = null
        checking = false
    }

    /**
     * 로그아웃 API가 성공한 뒤 앱의 로그인 상태를 초기화한다.
     */
    fun markLoggedOut() {
        isLoggedIn = false
        checkError = null
        checking = false
    }
}

/**
 * 로그인 확인 결과에 따라 대시보드 또는 장보기 화면을 표시한다.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 화면 회전 후에도 기존 로그인 상태를 보관하는 ViewModel을 가져온다.
        val session = ViewModelProvider(this)[
            DashboardSessionViewModel::class.java
        ]

        enableEdgeToEdge()

        setContent {
            MyHomeTheme {
                // 대시보드와 장보기 중 어떤 화면을 표시할지 기억한다.
                var showShopping by rememberSaveable {
                    mutableStateOf(false)
                }

                // 장보기 진입 시 선택할 탭을 기억한다.
                var shoppingInitialCompleted by rememberSaveable {
                    mutableStateOf(false)
                }

                // 다시 시도 버튼을 누를 때 값을 증가시켜 로그인 상태를 재조회한다.
                var checkAttempt by remember {
                    mutableStateOf(0)
                }

                // 세션 만료로 로그인 화면에 돌아왔음을 안내한다.
                // 화면 회전 후에도 안내 상태를 유지한다.
                var sessionExpiredNotice by rememberSaveable {
                    mutableStateOf(false)
                }

                // 장보기 등 보호된 API의 인증 실패를 화면이 열려 있는 동안 관찰한다.
                LaunchedEffect(session) {
                    ApiClient.sessionExpired.collect { expired ->
                        if (expired) {
                            // 같은 알림을 중복 처리하지 않도록 먼저 초기화한다.
                            ApiClient.clearSessionExpired()

                            // 로그인한 사용자에게 발생한 인증 실패만 화면 전환으로 처리한다.
                            if (session.isLoggedIn) {
                                showShopping = false
                                shoppingInitialCompleted = false
                                session.markLoggedOut()

                                // 로그인 화면으로 이동한 이유를 안내한다.
                                sessionExpiredNotice = true
                            }
                        }
                    }
                }

                // Activity의 화면이 처음 구성되거나 재시도할 때 서버를 확인한다.
                LaunchedEffect(session, checkAttempt) {
                    session.checkSession()

                    // 비로그인 상태가 확인되면 이전 장보기 이동 상태를 초기화한다.
                    if (session.checkError == null && !session.isLoggedIn) {
                        showShopping = false
                        shoppingInitialCompleted = false
                    }
                }

                // 서버 확인을 마쳤고 일반 로그인한 경우에만 장보기를 표시한다.
                val shoppingVisible =
                    !session.checking &&
                            session.checkError == null &&
                            session.isLoggedIn &&
                            showShopping

                // 장보기에서 휴대폰 뒤로 가기를 누르면 대시보드로 이동한다.
                BackHandler(enabled = shoppingVisible) {
                    showShopping = false
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    // 콘텐츠가 상태 표시줄과 하단 시스템 영역에 겹치지 않게 한다.
                    val screenModifier = Modifier.padding(innerPadding)

                    when {
                        session.checking -> {
                            // 로그인 확인 중에는 중앙에 진행 상태를 표시한다.
                            Column(
                                modifier = screenModifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()

                                Spacer(modifier = Modifier.height(16.dp))

                                Text("로그인 상태를 확인하고 있어요.")
                            }
                        }

                        session.checkError != null -> {
                            // 연결 실패 시 원인과 재시도 버튼을 표시한다.
                            Column(
                                modifier = screenModifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = session.checkError.orEmpty(),
                                    color = MaterialTheme.colorScheme.error
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        // 횟수를 변경해 LaunchedEffect를 다시 실행한다.
                                        checkAttempt++
                                    }
                                ) {
                                    Text("다시 시도")
                                }
                            }
                        }

                        shoppingVisible -> {
                            // 대시보드에서 선택한 상태의 장보기 탭을 연다.
                            ShoppingRoute(
                                onBack = { showShopping = false },
                                modifier = screenModifier,
                                initialShowCompleted = shoppingInitialCompleted
                            )
                        }

                        else -> {
                            // 확인된 로그인 상태에 맞춰 대시보드를 표시한다.
                            DashboardScreen(
                                isLoggedIn = session.isLoggedIn,
                                onLoginSuccess = {
                                    // 새 로그인 성공 후 이전 만료 안내와 알림을 초기화한다.
                                    ApiClient.clearSessionExpired()
                                    sessionExpiredNotice = false

                                    // 대시보드의 로그인 상태를 갱신한다.
                                    showShopping = false
                                    session.markLoggedIn()
                                },
                                onLogoutSuccess = {
                                    // 로그아웃 후 이전 화면과 탭 선택을 초기화한다.
                                    showShopping = false
                                    shoppingInitialCompleted = false
                                    session.markLoggedOut()
                                },
                                onOpenShopping = { completed ->
                                    shoppingInitialCompleted = completed
                                    showShopping = true
                                },
                                modifier = screenModifier
                            )
                        }
                    }
                }
                // 로그인 화면으로 돌아온 이유를 사용자에게 알려준다.
                if (sessionExpiredNotice) {
                    AlertDialog(
                        onDismissRequest = {
                            // 창 밖을 누르거나 뒤로 가기를 누르면 안내를 닫는다.
                            sessionExpiredNotice = false
                        },
                        title = {
                            Text("다시 로그인해 주세요")
                        },
                        text = {
                            Text("로그인 세션이 만료되었습니다.")
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    // 안내를 닫으면 뒤에 있는 로그인 폼을 사용할 수 있다.
                                    sessionExpiredNotice = false
                                }
                            ) {
                                Text("확인")
                            }
                        }
                    )
                }
            }
        }
    }
}