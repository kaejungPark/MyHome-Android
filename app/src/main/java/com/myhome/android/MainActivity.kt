package com.myhome.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.myhome.android.ui.theme.MyHomeTheme
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

// 일반 로그인 성공 여부를 보관한다. 화면 이동이나 회전 후에도 같은 값을 사용한다.
class DashboardSessionViewModel : ViewModel() {
    var isLoggedIn by mutableStateOf(false)
        private set

    // 서버가 일반 로그인 성공을 반환한 후에만 호출한다.
    fun markLoggedIn() {
        isLoggedIn = true
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 화면 회전으로 Activity가 다시 만들어져도 기존 ViewModel을 가져온다.
        val session = ViewModelProvider(this)[DashboardSessionViewModel::class.java]
        enableEdgeToEdge()
        setContent {
            // 대시보드와 장보기 화면에 MyHome의 공통 색상과 글자 스타일을 적용한다.
            MyHomeTheme {

                // false면 대시보드, true면 장보기 화면을 표시한다.
                // rememberSaveable을 사용해 화면을 회전한 뒤에도 현재 화면을 유지한다.
                var showShopping by rememberSaveable { mutableStateOf(false) }

                // 장보기 화면을 처음 열 때 선택할 탭을 기억한다.
                // false는 살 물품, true는 구매 완료다.
                var shoppingInitialCompleted by rememberSaveable { mutableStateOf(false) }

                // 장보기 이동 요청이 있고 현재 로그인한 경우에만 장보기 화면을 연다.
                val shoppingVisible = showShopping && session.isLoggedIn
                // 장보기 화면에서 휴대폰의 뒤로 가기를 누르면 대시보드로 돌아간다.
                BackHandler(enabled = shoppingVisible) { showShopping = false }

                // Scaffold는 화면 전체를 사용하고, 콘텐츠에 필요한 여백을 innerPadding으로 전달한다.
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                    // 어느 화면을 열어도 내용이 상태 표시줄이나 하단 탐색 영역과 겹치지 않게 한다.
                    val screenModifier = Modifier.padding(innerPadding)

                    // showShopping 값이 바뀌면 Compose가 해당하는 화면을 표시한다.
                    if (shoppingVisible) {
                        // 대시보드에서 선택한 카드에 맞춰 장보기의 첫 탭을 지정한다.
                        ShoppingRoute(
                            onBack = { showShopping = false },
                            modifier = screenModifier,
                            initialShowCompleted = shoppingInitialCompleted
                        )
                    } else {
                        DashboardScreen(
                            isLoggedIn = session.isLoggedIn,
                            onLoginSuccess = {
                                // 로그인 직후에는 대시보드에서 장보기 버튼을 보여준다.
                                showShopping = false
                                session.markLoggedIn()
                            },
                            onOpenShopping = { completed ->
                                // 선택한 탭을 저장한 뒤 장보기 화면을 연다.
                                shoppingInitialCompleted = completed
                                showShopping = true
                            },
                            modifier = screenModifier
                        )
                    }
                }
            }
        }
    }
}