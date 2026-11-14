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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException

// 장보기 화면을 열면 로그인 세션으로 목록을 조회하고, 그 결과를 화면에 전달한다.
@Composable
fun ShoppingRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 요청이 끝나기 전에는 '등록된 항목이 없습니다' 대신 로딩 문구를 보여준다.
    var loading by remember { mutableStateOf(true) }
    var shoppingItems by remember {
        mutableStateOf(emptyList<ShoppingItem>())
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 처음 화면을 열 때 조회한다. 다시 시도 버튼으로 값이 바뀌면 한 번 더 조회한다.
    var retryCount by remember { mutableStateOf(0) }
    LaunchedEffect(retryCount) {
        loading = true
        errorMessage = null

        try {
            // 로그인할 때 저장한 쿠키를 사용해 내 장보기 목록의 첫 페이지를 가져온다.
            shoppingItems = AuthApi.fetchShoppingItems()
        } catch (e: CancellationException) {
            // 조회 중 대시보드로 돌아가면 화면 이동에 따른 취소를 그대로 전달한다.
            throw  e
        } catch (e: Exception) {
            // 로그인 만료나 연결 실패 메시지를 화면에 표시한다.
            errorMessage = e.localizedMessage ?: "장보기 목록을 불러오지 못했습니다."
        } finally {
            loading = false
        }

    }

    // 조회 결과와 버튼 동작을 기존 장보기 화면에 전달한다.
    ShoppingScreen(
        shoppingItems = shoppingItems,
        onBack = onBack,
        modifier = modifier,
        loading = loading,
        errorMessage = errorMessage,
        onRetry = { retryCount++ }
    )

}

// 전달받은 조회 상태에 따라 로딩, 오류, 빈 목록 또는 장보기 항목을 표시한다.
@Composable
fun ShoppingScreen(
    shoppingItems: List<ShoppingItem>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {}
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

        // 조회가 끝난 뒤 실제 결과가 0건일 때만 빈 목록 문구를 보여준다.
        if (loading) {
            Text("장보기 목록을 불러오는 중…")
        } else if (errorMessage != null) {
            Text(errorMessage)

            // 같은 화면에서 서버에 다시 요청한다.
            TextButton(onClick = onRetry) {
                Text("다시 시도")
            }
        } else if (shoppingItems.isEmpty()) {
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