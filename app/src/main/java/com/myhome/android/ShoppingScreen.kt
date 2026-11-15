package com.myhome.android

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
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button

// 장보기 화면을 열면 로그인 세션으로 목록을 조회하고, 그 결과를 화면에 전달한다.
@Composable
fun ShoppingRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 처음에는 '살 물품'을 열고, 화면을 회전해도 선택한 탭을 유지한다.
    var showCompleted by rememberSaveable { mutableStateOf(false) }

    // 탭을 바꾸면 새 목록을 받을 때까지 이전 탭의 항목이나 오류가 보이지 않도록 초기화한다.
    var loading by remember(showCompleted) { mutableStateOf(true) }

    var shoppingItems by remember(showCompleted) { mutableStateOf(emptyList<ShoppingItem>()) }
    var errorMessage by remember(showCompleted) { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableStateOf(0) }

    // 체크박스를 눌렀을 때 화면에 연결된 코루틴에서 저장 요청을 실행한다.
    val scope = rememberCoroutineScope()
    // 저장 중인 항목 ID를 보관해 중복 클릭을 막는다. null이면 저장 중인 항목이 없다.
    var savingId by remember { mutableStateOf<Long?>(null) }
    // 저장 오류는 목록과 함께 표시해 사용자가 같은 항목을 다시 누를 수 있게 한다.
    var saveError by remember(showCompleted) { mutableStateOf<String?>(null) }

    // 탭이 바뀌거나 '다시 시도'를 누르면 해당 탭의 목록을 조회한다.
    LaunchedEffect(showCompleted, retryCount) {
        loading = true
        errorMessage = null

        try {
            // 현재 선택한 탭에 해당하는 목록을 장보기 API에서 가져온다.
            shoppingItems = ShoppingApi.fetchShoppingItems(isCompleted = showCompleted)
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

    // 목록 위치를 여기서 기억해 입력 화면을 닫으면 원래 위치로 돌아간다.
    val listState = rememberLazyListState()

    // null이면 목록, 빈 Bundle이면 추가 화면, ID가 들어 있는 Bundle이면 수정 화면이다.
    var formArgs by rememberSaveable { mutableStateOf<Bundle?>(null) }
    var initial = formArgs

    if (initial != null) {
        ShoppingForm(
            initial = initial,
            onBack = { formArgs = null },
            onSave = {
                // 현재 탭을 유지하고, 저장된 내용을 서버에서 다시 읽는다.
                saveError = null
                loading = true
                retryCount++
                formArgs = null
            },
            modifier = modifier
        )
        return // 입력 화면을 표시한 동안 아래의 목록 화면은 그리지 않는다.
    }

    // 조회 결과와 버튼 동작을 기존 장보기 화면에 전달한다.
    ShoppingScreen(
        shoppingItems = shoppingItems,
        onBack = onBack,
        modifier = modifier,
        loading = loading,
        errorMessage = errorMessage,
        onRetry = { retryCount++ },
        // 화면에 현재 탭을 알려주고, 다른 탭을 누르면 선택 상태를 변경한다.
        showCompleted = showCompleted,
        onTabSelected = { selected -> showCompleted = selected },
        savingId = savingId,
        saveError = saveError,
        listState = listState,

        // 빈 값을 전달하면 입력 화면이 기본값으로 열린다.
        onAdd = { formArgs = Bundle() },

        // 물품명을 누르면 해당 행의 ID와 값을 복사해 수정 화면으로 전달한다.
        onEdit = { item ->
            formArgs = Bundle().apply {
                putLong("id", item.id)
                putString("name", item.name)
                putString("quantity", item.quantity.stripTrailingZeros().toPlainString())
                putString("unit", item.unit)
                putString("dueDate", item.purchaseDueDate)
                putString("memo", item.memo)
            }
        },

        onComplete = {
            id -> if (savingId == null) {
                savingId = id
                saveError = null

                scope.launch {
                    try {
                        // 살 물품 탭에서는 완료로, 구매 완료 탭에서는 미구매로 변경한다.
                        ShoppingApi.completeShoppingItem(id, completed = !showCompleted)
                        // 저장 성공 후 현재 탭을 다시 조회해 서버의 최신 상태를 표시한다.
                        loading = true
                        retryCount++
                    } catch (e: CancellationException) {
                        // 화면을 벗어나 취소된 작업은 오류로 표시하지 않는다.
                        throw e
                    } catch (e: Exception) {
                        saveError = e.localizedMessage ?: "구매 완료를 다시 시도해 주세요."
                    } finally {
                        savingId = null
                    }
                }
            }
        }
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
    onRetry: () -> Unit = {},
    showCompleted: Boolean = false,
    onTabSelected: (Boolean) -> Unit = {},
    savingId: Long? = null,
    saveError: String? = null,
    listState: LazyListState = rememberLazyListState(),
    onAdd: () -> Unit = {},
    onEdit: (ShoppingItem) -> Unit = {},
    onComplete: (Long) -> Unit = {}
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

        // 두 탭 중 하나를 선택한다. 선택된 탭은 색상으로 구분된다.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !showCompleted,
                // 저장 응답을 기다리는 동안 현재 탭을 유지한다.
                enabled = savingId == null,
                onClick = { onTabSelected(false) },
                label = { Text("살 물품") }
            )
            FilterChip(
                selected = showCompleted,
                // 저장 응답을 기다리는 동안 현재 탭을 유지한다.
                enabled = savingId == null,
                onClick = { onTabSelected(true) },
                label = { Text("구매 완료") }
            )
        }

        // 새 물품은 미구매로 등록되므로 '살 물품' 탭에서 추가 버튼을 보여준다.
        if (!showCompleted) {
            Button(onClick = onAdd, enabled = savingId == null) {
                Text("+ 물품 추가")
            }
        }

        // 저장하지 못한 이유를 목록 위에 표시한다.
        if (saveError != null) {
            Text(text = saveError, color = MaterialTheme.colorScheme.error)
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
            // 수정 화면에서 돌아왔을 때 이전 스크롤 위치를 사용한다.
            LazyColumn(state = listState) {
                // 같은 이름의 물품도 서로 구별할 수 있도록 서버에서 받은 ID를 사용한다.
                items(shoppingItems, key = { item -> item.id}) {
                    // 한 행에 구매 상태, 물품명, 수량을 왼쪽부터 차례로 배치한다.
                    item -> Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 물품명은 수정 화면을 열고, 체크박스는 구매 상태만 변경한다.
                        Checkbox(
                            checked = item.isCompleted,
                            enabled = savingId == null,
                            onCheckedChange = { onComplete(item.id) }
                        )
                        // 물품명이 남은 너비를 사용한다. 긴 이름은 한 줄로 줄여 수량이 밀리지 않게 한다.
                        Text(
                            text = item.name,
                            modifier = Modifier.weight(1f).clickable(enabled = savingId == null) { onEdit(item) },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // 1.00은 1로, 1.50은 1.5로 표시하고 오른쪽에 단위를 붙인다.
                        val quantity = item.quantity.stripTrailingZeros().toPlainString()
                        Text(
                            text = "$quantity${item.unit}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    // 행 사이에 얇은 구분선을 넣어 각 물품을 구별한다.
                    HorizontalDivider()
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