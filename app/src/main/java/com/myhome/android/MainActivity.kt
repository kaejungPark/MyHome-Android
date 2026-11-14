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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 대시보드와 장보기 화면에 MyHome의 공통 색상과 글자 스타일을 적용한다.
            MyHomeTheme {

                // false면 대시보드, true면 장보기 화면을 표시한다.
                // rememberSaveable을 사용해 화면을 회전한 뒤에도 현재 화면을 유지한다.
                var showShopping by rememberSaveable { mutableStateOf(false) }

                // 장보기 화면에서만 휴대폰의 뒤로 가기를 처리한다.
                // 대시보드에서는 안드로이드의 기본 뒤로 가기가 동작한다.
                BackHandler(enabled =  showShopping) {
                    showShopping = false
                }

                // Scaffold는 화면 전체를 사용하고, 콘텐츠에 필요한 여백을 innerPadding으로 전달한다.
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                    // 어느 화면을 열어도 내용이 상태 표시줄이나 하단 탐색 영역과 겹치지 않게 한다.
                    val screenModifier = Modifier.padding(innerPadding)

                    // showShopping 값이 바뀌면 Compose가 해당하는 화면을 표시한다.
                    if (showShopping) {
                        ShoppingScreen(
                            // API 연결 전에는 빈 목록을 전달한다. 연결 후에는 서버에서 조회한 목록을 넣는다.
                            shoppingItems = emptyList(),
                            onBack = { showShopping = false },
                            modifier = screenModifier
                        )
                    } else {
                        DashboardScreen(
                            // 대시보드의 '장보기' 버튼을 눌렀을 때 실행된다.
                            onOpenShopping = { showShopping = true },
                            modifier = screenModifier
                        )
                    }
                }
            }
        }
    }
}