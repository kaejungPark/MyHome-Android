package com.myhome.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myhome.android.ui.theme.MyHomeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyHomeTheme {
                // Scaffold가 화면 전체를 차지하고 콘텐츠에 필요한 기본 여백을 알려준다.
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    // 대시보드가 상태 표시줄이나 하단 탐색 영역과 겹치지 않도록 여백을 적용한다.
                    innerPadding -> DashboardScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

// 앱의 첫 화면에 대시보드 제목과 안내 문구를 표시한다.
@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
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
    }
}

// Android Studio에서 앱을 실행하지 않고 대시보드 모양을 확인하는 미리보기다.
@Preview(showBackground = true)
@Composable
fun DashboardPreview() {
    // 실제 앱과 같은 테마로 표시한다.
    MyHomeTheme {
        DashboardScreen()
    }
}
