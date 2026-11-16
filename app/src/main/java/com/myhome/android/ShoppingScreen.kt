package com.myhome.android

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myhome.android.ui.theme.MyHomeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * 장보기 화면의 현재 탭, 페이지, 검색 조건을 관리하고 서버에서 목록을 조회한다.
 * 물품을 선택하면 추가·수정 화면을 열고, 저장이나 삭제가 끝나면 같은 조건으로 다시 조회한다.
 */
@Composable
fun ShoppingRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 현재 탭과 페이지, 사용자가 '조회'를 눌러 적용한 조건을 기억한다.
    var showCompleted by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(1) }
    var searchName by rememberSaveable { mutableStateOf("") }
    var searchDate by rememberSaveable { mutableStateOf("") }

    // 같은 조건이라도 저장·삭제·재시도 후에는 다시 조회하기 위한 값이다.
    var reloadCount by remember { mutableStateOf(0) }

    // 체크박스 중복 클릭을 막고, 상태 변경 실패 시 목록 위에 오류를 표시한다.
    val scope = rememberCoroutineScope()
    var savingId by remember { mutableStateOf<Long?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }

    // null이면 목록, 빈 Bundle이면 추가, ID가 들어 있는 Bundle이면 수정 화면을 표시한다.
    var formArgs by rememberSaveable { mutableStateOf<Bundle?>(null) }

    // 탭·페이지·검색 조건이 바뀌면 새 목록을 처음부터 보여준다.
    // 같은 목록에서 수정 화면만 열었다 돌아오면 스크롤 위치는 유지한다.
    val listState = key(showCompleted, page, searchName, searchDate) {
        rememberLazyListState()
    }

    // 조회 조건이나 재조회 횟수가 달라지면 이전 결과 대신 로딩 상태에서 시작한다.
    key(showCompleted, page, searchName, searchDate, reloadCount) {
        var result by remember { mutableStateOf<ShoppingPage?>(null) }
        var loadError by remember { mutableStateOf<String?>(null) }

        // 이 key 블록이 생성될 때 한 번 조회한다.
        // 탭·페이지·검색 조건·reloadCount 중 하나가 바뀌면 새 블록이 생성되어 다시 조회한다.
        LaunchedEffect(Unit) {
            try {
                val response = ShoppingApi.fetchShoppingItems(
                    isCompleted = showCompleted,
                    page = page,
                    name = searchName,
                    purchaseDueDate = searchDate
                )

                // 마지막 페이지의 유일한 항목을 삭제하거나 완료 처리한 경우,
                // 남아 있는 마지막 페이지로 이동해 그 페이지를 다시 조회한다.
                val lastPage = response.totalPages.coerceAtLeast(1)
                if (page > lastPage) {
                    page = lastPage
                } else {
                    result = response
                }
            } catch (e: CancellationException) {
                // 다른 탭이나 페이지로 이동해 취소된 요청은 오류로 표시하지 않는다.
                throw e
            } catch (e: Exception) {
                loadError = e.localizedMessage ?: "장보기 목록을 불러오지 못했습니다."
            }
        }

        val initial = formArgs
        if (initial != null) {
            ShoppingForm(
                initial = initial,
                onBack = { formArgs = null },
                onSave = {
                    // ID가 없는 입력 화면에서 저장했다면 새 미구매 물품이 등록된 것이다.
                    // 구매 완료 탭에서 '다시 담기'로 들어온 경우도 여기에 해당한다.
                    if (!initial.containsKey("id")) {
                        showCompleted = false
                        page = 1
                        // 이전 검색 조건 때문에 새로 등록한 물품이 숨지 않도록 초기화한다.
                        searchName = ""
                        searchDate = ""
                    }
                    // 기존 물품을 수정하거나 삭제한 경우에는 보고 있던 탭과 검색 조건을 유지한다.
                    formArgs = null
                    saveError = null
                    reloadCount++
                },
                modifier = modifier
            )
        } else {
            ShoppingScreen(
                shoppingItems = result?.items.orEmpty(),
                onBack = onBack,
                modifier = modifier,
                // 결과도 오류도 없으면 아직 조회 중이다.
                // 조회에 성공해 물품이 0건인 경우에는 result가 존재하므로 빈 목록 안내가 표시된다.
                loading = result == null && loadError == null,
                errorMessage = loadError,
                onRetry = { reloadCount++ },
                showCompleted = showCompleted,
                onTabSelected = { selected ->
                    if (selected != showCompleted) {
                        showCompleted = selected
                        page = 1
                        saveError = null
                    }
                },
                searchName = searchName,
                searchDate = searchDate,
                onSearch = { name, date ->
                    // 새 검색은 항상 첫 페이지부터 조회한다.
                    searchName = name.trim()
                    searchDate = date
                    page = 1
                    saveError = null
                    reloadCount++
                },
                page = page,
                totalPages = result?.totalPages ?: 0,
                onPageChange = { page = it },
                listState = listState,
                savingId = savingId,
                saveError = saveError,
                onAdd = { formArgs = Bundle() },
                // 완료한 물품의 이름·수량·단위를 복사해 새 물품 입력 화면을 연다.
                onReadd = { item ->
                    formArgs = Bundle().apply {
                        putString("name", item.name)
                        putString("quantity", item.quantity.stripTrailingZeros().toPlainString())
                        putString("unit", item.unit)
                    }
                },
                onEdit = { item ->
                    // 수정 화면에 선택한 물품의 기존 값을 전달한다.
                    formArgs = Bundle().apply {
                        putLong("id", item.id)
                        putString("name", item.name)
                        putString("quantity", item.quantity.stripTrailingZeros().toPlainString())
                        putString("unit", item.unit)
                        putString("dueDate", item.purchaseDueDate)
                        putString("memo", item.memo)
                    }
                },
                onComplete = { item ->
                    if (savingId == null) {
                        savingId = item.id
                        saveError = null

                        scope.launch {
                            try {
                                // 미구매 항목은 완료로, 완료 항목은 다시 미구매로 변경한다.
                                ShoppingApi.completeShoppingItem(
                                    id = item.id,
                                    completed = !item.isCompleted
                                )
                                // 구매 상태가 바뀌면 이 물품이 현재 탭에서 빠지고 전체 페이지 수도 달라질 수 있다.
                                // 서버에서 저장 성공 응답을 받은 뒤 현재 목록을 다시 조회한다.
                                reloadCount++
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                saveError = e.localizedMessage ?: "구매 상태를 변경하지 못했습니다."
                            } finally {
                                savingId = null
                            }
                        }
                    }
                }
            )
        }
    }
}

/**
 * 조회 상태에 따라 로딩, 오류, 빈 목록 또는 장보기 목록을 표시한다.
 * 사용자가 탭·검색·물품·체크박스를 누르면 전달받은 콜백으로 ShoppingRoute에 알린다.
 * 목록 영역만 스크롤하고, 두 페이지 이상이면 화면 하단에 이전·다음 버튼을 표시한다.
 */
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
    searchName: String = "",
    searchDate: String = "",
    onSearch: (String, String) -> Unit = { _, _ -> },
    page: Int = 1,
    totalPages: Int = 0,
    onPageChange: (Int) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    savingId: Long? = null,
    saveError: String? = null,
    onAdd: () -> Unit = {},
    // 다시 담을 물품을 선택하면 그 항목을 ShoppingRoute에 전달한다.
    onReadd: (ShoppingItem) -> Unit = {},
    onEdit: (ShoppingItem) -> Unit = {},
    onComplete: (ShoppingItem) -> Unit = {}
) {
    val hasFilter = searchName.isNotEmpty() || searchDate.isNotEmpty()

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 한 줄에 뒤로가기, 화면 제목, 검색을 배치해 목록이 더 위에서 시작하게 한다.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.semantics {
                    contentDescription = "대시보드로 돌아가기"
                }
            ) {
                Text("←", style = MaterialTheme.typography.headlineSmall)
            }

            // 제목이 남은 공간을 차지하면 검색 버튼이 오른쪽 끝에 놓인다.
            Text(
                text = "장보기",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall
            )

            ShoppingSearchButton(
                appliedName = searchName,
                appliedDate = searchDate,
                enabled = savingId == null,
                onSearch = onSearch
            )
        }

        // 두 탭이 화면 너비를 반씩 사용한다. 선택된 쪽만 배경색을 강조한다.
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !showCompleted,
                onClick = { onTabSelected(false) },
                enabled = savingId == null,
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {}
            ) {
                Text("살 물품")
            }

            SegmentedButton(
                selected = showCompleted,
                onClick = { onTabSelected(true) },
                enabled = savingId == null,
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {}
            ) {
                Text("구매 완료")
            }
        }

        // 새 물품은 미구매로 등록한다. 살 물품 탭에서만 목록 위 오른쪽에 버튼을 둔다.
        if (!showCompleted) {
            Button(
                onClick = onAdd,
                enabled = savingId == null,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("+ 물품 추가")
            }
        }

        when {
            loading -> Text("장보기 목록을 불러오는 중…")

            errorMessage != null -> {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("다시 시도") }
            }

            shoppingItems.isEmpty() -> {
                Text(
                    if (hasFilter) "검색 조건에 맞는 물품이 없습니다."
                    else "표시할 장보기 항목이 없습니다."
                )
            }

            else -> {
                // 목록만 스크롤하고, 페이지 버튼이 들어갈 자리는 화면 하단에 남긴다.
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f)
                ) {
                    items(shoppingItems, key = { it.id }) { item ->
                        ShoppingItemRow(
                            item = item,
                            enabled = savingId == null,
                            onEdit = { onEdit(item) },
                            onReadd = { onReadd(item) },
                            onToggle = { onComplete(item) }
                        )
                        HorizontalDivider()
                    }
                }

                // 목록만 스크롤하고 페이지 이동은 하단 중앙의 같은 자리에 표시한다.
                if (totalPages > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            enabled = page > 1 && savingId == null,
                            onClick = { onPageChange(page - 1) },
                            modifier = Modifier.semantics {
                                contentDescription = "이전 페이지"
                            }
                        ) {
                            Text("‹", style = MaterialTheme.typography.headlineSmall)
                        }

                        Text(
                            text = "$page / $totalPages",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        IconButton(
                            enabled = page < totalPages && savingId == null,
                            onClick = { onPageChange(page + 1) },
                            modifier = Modifier.semantics {
                                contentDescription = "다음 페이지"
                            }
                        ) {
                            Text("›", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 물품 한 건을 표시한다.
 * 체크박스를 누르면 구매 상태가 바뀌고, 오른쪽 물품 정보를 누르면 수정 화면이 열린다.
 */
@Composable
private fun ShoppingItemRow(
    item: ShoppingItem,
    enabled: Boolean,
    onEdit: () -> Unit,
    // 현재 행의 물품을 복사해 추가 화면을 여는 동작이다.
    onReadd: () -> Unit,
    onToggle: () -> Unit
) {
    // 2.00팩은 2팩, 1.50kg은 1.5kg으로 표시한다.
    val quantity = item.quantity.stripTrailingZeros().toPlainString()

    // 값이 있는 날짜만 한 줄에 모아 표시한다.
    val dates = listOfNotNull(
        item.purchaseDueDate?.let { "예정 $it" },
        item.completedDate?.let { "구매 $it" }
    ).joinToString(" · ")

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = item.isCompleted,
            enabled = enabled,
            onCheckedChange = { onToggle() }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = enabled, onClick = onEdit)
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // 물품명은 왼쪽, 수량은 오른쪽 끝에 표시한다.
            // 구매 상태는 위쪽의 선택된 탭과 행 왼쪽의 체크박스로 확인한다.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 긴 물품명은 한 줄로 줄여도 오른쪽 수량은 항상 보이게 한다.
                Text(
                    text = item.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 앞에서 정리한 수량과 단위를 붙여 '2팩', '1.5kg'처럼 표시한다.
                Text(
                    text = "$quantity${item.unit}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // 날짜와 메모가 없으면 줄을 생략해 짧은 목록은 한눈에 보이게 한다.
            if (dates.isNotEmpty()) {
                Text(
                    text = dates,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val memo = item.memo
            if (!memo.isNullOrBlank()) {
                Text(
                    text = memo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 구매 이력이 있는 물품만 다시 담을 수 있다.
            // 버튼을 눌렀을 때는 입력 화면만 열고, 사용자가 저장해야 새 항목이 생성된다.
            if (item.isCompleted) {
                TextButton(
                    onClick = onReadd,
                    enabled = enabled,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("다시 담기")
                }
            }
        }
    }
}

/**
 * 물품명과 구매 예정일을 입력하는 검색창이다.
 * appliedName과 appliedDate는 현재 목록에 적용된 값이고, name과 date는 창에서 편집 중인 값이다.
 * 조회를 누르면 입력값을 적용하고, 취소하면 기존 조건을 유지하며, 초기화하면 두 조건을 해제한다.
 */
@Composable
private fun ShoppingSearchButton(
    appliedName: String,
    appliedDate: String,
    enabled: Boolean,
    onSearch: (String, String) -> Unit
) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }
    val hasFilter = appliedName.isNotEmpty() || appliedDate.isNotEmpty()

    // 적용된 조건이 있으면 돋보기 옆에 점을 표시해 필터가 켜져 있음을 알려준다.
    val searchLabel = if (hasFilter) "검색 조건 적용 중, 검색 열기" else "장보기 검색"
    val iconColor = LocalContentColor.current

    IconButton(
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = searchLabel },
        onClick = {
            // 기존 조건을 입력란에 채운 뒤 검색창을 연다.
            name = appliedName
            date = appliedDate
            open = true
        }
    ) {
        BadgedBox(
            badge = { if (hasFilter) Badge() }
        ) {
            // 원과 짧은 대각선으로 돋보기를 그린다.
            Canvas(modifier = Modifier.size(24.dp)) {
                val stroke = 2.dp.toPx()
                drawCircle(
                    color = iconColor,
                    radius = 6.5.dp.toPx(),
                    center = Offset(10.dp.toPx(), 10.dp.toPx()),
                    style = Stroke(width = stroke)
                )
                drawLine(
                    color = iconColor,
                    start = Offset(15.dp.toPx(), 15.dp.toPx()),
                    end = Offset(21.dp.toPx(), 21.dp.toPx()),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("장보기 검색") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("물품명") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 날짜를 고르면 백엔드 검색 형식인 yyyy-MM-dd로 보관한다.
                    TextButton(onClick = {
                        val calendar = Calendar.getInstance()
                        if (date.isNotEmpty()) {
                            val (y, m, d) = date.split("-").map { it.toInt() }
                            calendar.set(y, m - 1, d)
                        }

                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                date = String.format(Locale.ROOT, "%04d-%02d-%02d", y, m + 1, d)
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }) {
                        Text(date.ifEmpty { "구매 예정일 선택" })
                    }

                    if (date.isNotEmpty()) {
                        TextButton(onClick = { date = "" }) { Text("날짜 지우기") }
                    }

                    if (hasFilter || name.isNotEmpty() || date.isNotEmpty()) {
                        TextButton(onClick = {
                            // 검색어와 날짜를 모두 해제하고 현재 탭의 첫 페이지로 돌아간다.
                            open = false
                            onSearch("", "")
                        }) { Text("검색 조건 초기화") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    open = false
                    onSearch(name, date)
                }) { Text("조회") }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("취소") }
            }
        )
    }
}

// Android Studio에서 서버 접속 없이 빈 장보기 화면의 배치를 확인한다.
@Preview(showBackground = true)
@Composable
fun ShoppingPreview() {
    MyHomeTheme {
        ShoppingScreen(shoppingItems = emptyList(), onBack = {})
    }
}