package com.myhome.android

import android.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.myhome.android.ui.theme.MyHomeTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items


// 전달받은 장보기 목록을 표시한다. 항목이 없으면 빈 목록 안내를 보여준다.
@Composable
fun ShoppingScreen(
    shoppingItems: List<ShoppingItem>,
    // 돌아가기 버튼을 눌렀을 때 실행할 함수를 MainActivity에서 전달받는다.
    onBack: () -> Unit,
    // MainActivity에서 정한 화면 여백을 장보기 화면에도 적용한다.
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "장보기",
            style = MaterialTheme.typography.headlineMedium
        )

        // 버튼을 누르면 이 화면을 열어 준 쪽에 돌아가기를 요청한다.
        TextButton(onBack) {
            Text("대시보드로 돌아가기")
        }

        if (shoppingItems.isEmpty()) {
            Text("표시할 장보기 항목이 없습니다.")
        } else {
            // 항목이 많아지면 목록 영역을 위아래로 스크롤 생성
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // ID로 각 행을 구분한다. 같은 이름의 물품도 별도 항목으로 표시된다.
                items(shoppingItems, key = { item -> item.id }) {
                    item -> Column {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium
                            )
                    // DB에서 1.50으로 내려온 수량은 화면에서 1.5로 표시한다.
                    val quantity = item.quantity.stripTrailingZeros().toPlainString()
                    Text("$quantity ${item.unit}")
                    Text(if (item.isCompleted) "구매 완료" else "미구매")
                    }
                }
            }
        }
    }
}

// Android Studio의 Preview 창에 장보기 화면을 표시한다.
@Preview(showBackground = true)
@Composable
fun ShoppingPreview() {
    MyHomeTheme {
        // 빈 목록일 때의 화면을 미리 본다.
        ShoppingScreen(shoppingItems = emptyList(), onBack = {})
    }
}