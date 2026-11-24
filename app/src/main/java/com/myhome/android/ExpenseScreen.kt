package com.myhome.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import android.os.Bundle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search

// 생활비 화면에 사용할 색상과 페이지당 표시 개수다.
private val ExpenseNavy = Color(0xFF172B55)
private val ExpenseBackground = Color(0xFFF3F5FA)
private const val EXPENSE_PAGE_SIZE = 5

/**
 * 기기의 현재 날짜를 기준으로 YYYY-MM을 반환한다.
 * Android API 24에서도 사용할 수 있는 Calendar를 사용한다.
 */
fun currentExpenseMonth(): String {
    val calendar = Calendar.getInstance()

    return String.format(
        Locale.US,
        "%04d-%02d",
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1
    )
}

/**
 * 선택한 월에서 이전 달 또는 다음 달을 계산한다.
 * 월을 계산하기 전에 날짜를 1일로 맞춰 월말 날짜의 영향을 피한다.
 */
private fun moveExpenseMonth(month: String, amount: Int): String {
    val calendar = Calendar.getInstance().apply {
        clear()
        set(
            month.take(4).toInt(),
            month.takeLast(2).toInt() - 1,
            1
        )
        add(Calendar.MONTH, amount)
    }

    return String.format(
        Locale.US,
        "%04d-%02d",
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1
    )
}

/**
 * 원 단위 금액을 천 단위 쉼표와 함께 표시한다.
 * 소수 금액이 있으면 저장된 소수 자릿수도 표시한다.
 */
private fun expenseAmountText(amount: BigDecimal): String {
    val formatter = NumberFormat.getNumberInstance(Locale.KOREA).apply {
        maximumFractionDigits = amount.scale().coerceAtLeast(0)
    }
    return "${formatter.format(amount)}원"
}

/**
 * 월별 생활비 목록을 조회하고 한 페이지에 5개씩 표시한다.
 * 월 변경과 재시도 시 이전 결과를 비우고 새 결과를 표시한다.
 */
@Composable
fun ExpenseRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 화면 회전 후에도 선택한 월과 페이지를 복원한다.
    var month by rememberSaveable {
        mutableStateOf(currentExpenseMonth())
    }

    var page by rememberSaveable {
        mutableStateOf(1)
    }

    var reloadCount by remember {
        mutableStateOf(0)
    }

    // 조회 버튼으로 확정한 검색 조건이다.
    // 화면 회전이나 수정 화면을 다녀온 뒤에도 유지한다.
    var searchTitle by rememberSaveable {
        mutableStateOf("")
    }
    var searchCategoryId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }
    var searchType by rememberSaveable {
        mutableStateOf("")
    }

    // null이면 목록, 빈 Bundle이면 추가, ID가 있으면 수정 화면을 연다.
    // Bundle은 화면 회전 후에도 복원할 수 있다.
    var formArgs by rememberSaveable {
        mutableStateOf<Bundle?>(null)
    }

    val initial = formArgs

    if (initial != null) {
        ExpenseForm(
            initial = initial,
            onBack = {
                // 취소하면 기존 월과 페이지로 돌아간다.
                formArgs = null
            },
            onSaved = { savedDate ->
                // 새 지출은 이전 검색 조건에 가려지지 않도록 조건을 초기화한다.
                // 기존 지출 수정 시에는 사용자가 적용한 검색 조건을 유지한다.
                if (!initial.containsKey("id")) {
                    searchTitle = ""
                    searchCategoryId = null
                    searchType = ""
                }

                // 저장한 지출의 월에서 첫 페이지를 다시 조회한다.
                month = savedDate.take(7)
                page = 1
                reloadCount++
                formArgs = null
            },
            onDeleted = {
                // 삭제 전 보고 있던 월과 페이지를 유지하고 목록·합계를 다시 조회한다.
                // 마지막 페이지가 없어지면 기존 currentPage 계산이 남은 마지막 페이지를 표시한다.
                reloadCount++
                formArgs = null
            },
            modifier = modifier
        )

        // 입력 화면이 열린 동안 아래 목록 화면은 구성하지 않는다.
        return
    }

    // 월 또는 재조회 횟수가 바뀌면 로딩 상태부터 새로 시작한다.
    key(month, reloadCount) {
        var result by remember {
            mutableStateOf<MonthlyExpenses?>(null)
        }
        var error by remember {
            mutableStateOf<String?>(null)
        }

        LaunchedEffect(Unit) {
            try {
                result = ExpenseApi.fetchMonthlyExpenses(month)
            } catch (e: CancellationException) {
                // 다른 월이나 화면으로 이동해 취소된 작업은 취소를 유지한다.
                throw e
            } catch (e: Exception) {
                error = e.localizedMessage
                    ?: "생활비를 불러오지 못했습니다."
            }
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(ExpenseBackground)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 장보기처럼 뒤로 가기, 제목, 검색 버튼을 한 줄에 배치한다.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text("‹ 홈")
                }

                Text(
                    text = "생활비",
                    modifier = Modifier.weight(1f),
                    color = ExpenseNavy,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                ExpenseSearchButton(
                    appliedTitle = searchTitle,
                    appliedCategoryId = searchCategoryId,
                    appliedType = searchType,
                    onSearch = { title, categoryId, type ->
                        // 확정한 조건을 적용하고 첫 페이지부터 표시한다.
                        searchTitle = title.trim()
                        searchCategoryId = categoryId
                        searchType = type
                        page = 1

                        // 웹에서 변경한 지출도 반영할 수 있도록 서버 데이터를 새로 조회한다.
                        reloadCount++
                    }
                )
            }

            // 이전 달과 다음 달을 선택한다.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    enabled = month > "0001-01",
                    onClick = {
                        month = moveExpenseMonth(month, -1)
                        page = 1
                    }
                ) {
                    Text("‹ 이전 달")
                }

                Text(
                    text = "${month.take(4)}년 ${month.takeLast(2).toInt()}월",
                    fontWeight = FontWeight.Bold
                )

                TextButton(
                    enabled = month < "9998-12",
                    onClick = {
                        month = moveExpenseMonth(month, 1)
                        page = 1
                    }
                ) {
                    Text("다음 달 ›")
                }
            }

            // 장보기와 동일하게 추가 버튼을 목록 위 오른쪽에 배치한다.
            Button(
                onClick = { formArgs = Bundle() },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("+ 지출 추가")
            }

            // 서버 조회 결과를 현재 화면에서 사용할 값으로 가져온다.
            val data = result

            // 제목은 대소문자를 구분하지 않는 부분 일치로 검색한다.
            // 분류와 유형 조건이 비어 있으면 해당 조건으로 제한하지 않는다.
            val filteredItems = remember(
                data,
                searchTitle,
                searchCategoryId,
                searchType
            ) {
                data?.items.orEmpty().filter { item ->
                    item.title.contains(searchTitle, ignoreCase = true) &&
                            (searchCategoryId == null || item.categoryId == searchCategoryId) &&
                            (searchType.isEmpty() || item.expenseType == searchType)
                }
            }

            // 필터 적용 여부에 따라 빈 목록 안내를 구분한다.
            val hasFilter =
                searchTitle.isNotEmpty() ||
                        searchCategoryId != null ||
                        searchType.isNotEmpty()

            when {
                error != null -> {
                    // 서버 연결 실패 시 현재 월로 다시 조회한다.
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error
                    )

                    Button(
                        onClick = {
                            page = 1
                            reloadCount++
                        }
                    ) {
                        Text("다시 시도")
                    }
                }

                data == null -> {
                    CircularProgressIndicator()
                    Text("생활비를 불러오는 중…")
                }

                else -> {
                    // 합계는 현재 페이지가 아닌 선택한 월 전체 지출 기준이다.
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = ExpenseNavy,
                        contentColor = Color.White
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("이번 조회 월 총지출")

                            Text(
                                text = expenseAmountText(data.totalAmount),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text("${data.items.size}건")
                        }
                    }

                    // 합계 카드의 건수는 월 전체 건수이고, 이 문구는 검색 결과 건수다.
                    if (hasFilter) {
                        Text(
                            text = "검색 결과 ${filteredItems.size}건",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (filteredItems.isEmpty()) {
                        Text(
                            text = if (hasFilter) {
                                "검색 조건에 맞는 지출이 없습니다."
                            } else {
                                "선택한 월에 등록된 지출이 없습니다."
                            }
                        )
                    } else {
                        // 검색 결과를 기준으로 페이지 수와 현재 표시할 항목을 계산한다.
                        val totalPages =
                            (filteredItems.size - 1) / EXPENSE_PAGE_SIZE + 1

                        val currentPage = page.coerceIn(1, totalPages)
                        val visibleItems = filteredItems
                            .drop((currentPage - 1) * EXPENSE_PAGE_SIZE)
                            .take(EXPENSE_PAGE_SIZE)

                        // 페이지를 바꾸면 목록 스크롤도 처음부터 시작한다.
                        key(currentPage) {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    items = visibleItems,
                                    key = { it.id }
                                ) { item ->
                                    ExpenseRow(
                                        item = item,
                                        onClick = {
                                            // 서버에서 받은 값을 수정 화면으로 복사한다.
                                            formArgs = Bundle().apply {
                                                putLong("id", item.id)
                                                putString("title", item.title)
                                                putLong("categoryId", item.categoryId)
                                                putString(
                                                    "amount",
                                                    item.amount.stripTrailingZeros().toPlainString()
                                                )
                                                putString("expenseDate", item.expenseDate)
                                                putString("expenseType", item.expenseType)
                                                putString("paymentMethod", item.paymentMethod)
                                                putString("memo", item.memo)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // 목록 아래에 페이지 이동 버튼을 고정한다.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                enabled = currentPage > 1,
                                onClick = { page = currentPage - 1 }
                            ) {
                                Text("이전")
                            }

                            Text("$currentPage / $totalPages")

                            TextButton(
                                enabled = currentPage < totalPages,
                                onClick = { page = currentPage + 1 }
                            ) {
                                Text("다음")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 지출 정보를 카드에 표시하고 누르면 수정 화면을 연다.
 */
@Composable
private fun ExpenseRow(
    item: ExpenseItem,
    onClick: () -> Unit
) {
    // 카드 전체를 누르면 전달받은 수정 화면 열기 함수를 실행한다.
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        contentColor = ExpenseNavy
    ) {
        // 카드 안에 제목, 날짜·분류, 금액, 메모를 세로로 배치한다.
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = item.title,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${item.expenseDate} · ${item.categoryName}",
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = expenseAmountText(item.amount),
                fontWeight = FontWeight.Bold
            )

            // 내용이 있는 메모만 표시한다.
            item.memo?.takeIf { it.isNotBlank() }?.let { memo ->
                Text(
                    text = memo,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * 대시보드에 이번 달 생활비 합계를 표시한다.
 * 대시보드에 다시 들어오면 최신 데이터를 조회한다.
 */
@Composable
fun ExpenseSummaryCard(
    onOpenExpense: () -> Unit
) {
    val month = currentExpenseMonth()

    var reloadCount by remember { mutableStateOf(0) }
    var result by remember(month) {
        mutableStateOf<MonthlyExpenses?>(null)
    }
    var loading by remember(month) { mutableStateOf(true) }
    var failed by remember(month) { mutableStateOf(false) }

    LaunchedEffect(month, reloadCount) {
        loading = true
        failed = false

        try {
            result = ExpenseApi.fetchMonthlyExpenses(month)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }

    Card(
        onClick = onOpenExpense,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
            contentColor = ExpenseNavy
        )
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "이번 달 생활비",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // 조회 실패를 실제 지출 0원과 구분한다.
            val amountText = when {
                loading -> "조회 중…"
                failed -> "합계를 불러오지 못했습니다."
                else -> result?.let {
                    expenseAmountText(it.totalAmount)
                }.orEmpty()
            }

            Text(
                text = amountText,
                style = MaterialTheme.typography.headlineSmall
            )

            if (failed) {
                TextButton(onClick = { reloadCount++ }) {
                    Text("다시 시도")
                }
            }

            Text("지출 내역 보기 ›")
        }
    }
}

/**
 * 생활비 제목·분류·유형 검색창을 연다.
 * 입력 중인 조건은 조회를 누를 때 목록에 적용한다.
 * 취소하면 적용된 조건을 유지하고, 초기화하면 모든 검색 조건을 해제한다.
 */
@Composable
private fun ExpenseSearchButton(
    appliedTitle: String,
    appliedCategoryId: Long?,
    appliedType: String,
    onSearch: (String, Long?, String) -> Unit
) {
    // 검색창 표시 여부와 창 안에서 편집하는 값을 보관한다.
    var open by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }
    var type by rememberSaveable { mutableStateOf("") }

    // 적용된 검색 조건이 있으면 돋보기 옆에 점을 표시한다.
    val hasFilter =
        appliedTitle.isNotEmpty() ||
                appliedCategoryId != null ||
                appliedType.isNotEmpty()

    IconButton(
        onClick = {
            // 검색창을 열 때 현재 목록에 적용된 조건을 복원한다.
            title = appliedTitle
            categoryId = appliedCategoryId
            type = appliedType
            open = true
        }
    ) {
        BadgedBox(
            badge = {
                if (hasFilter) {
                    Badge()
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = if (hasFilter) {
                    "검색 조건 적용 중, 생활비 검색 열기"
                } else {
                    "생활비 검색"
                }
            )
        }
    }

    if (open) {
        // 분류 조회 상태는 검색창을 열 때마다 새로 구성한다.
        var categories by remember {
            mutableStateOf<List<ExpenseCategory>>(emptyList())
        }
        var loading by remember { mutableStateOf(true) }
        var loadError by remember { mutableStateOf<String?>(null) }
        var reloadCount by remember { mutableStateOf(0) }
        var categoryExpanded by remember { mutableStateOf(false) }

        // 현재 서버에 등록된 분류를 불러온다.
        LaunchedEffect(reloadCount) {
            loading = true
            loadError = null

            try {
                categories = CategoryApi.fetchCategories()
            } catch (e: CancellationException) {
                // 창이 닫혀 취소된 요청은 취소 상태를 유지한다.
                throw e
            } catch (e: Exception) {
                loadError = "분류를 불러오지 못했습니다."
            } finally {
                loading = false
            }
        }

        AlertDialog(
            onDismissRequest = { open = false },
            title = {
                Text("생활비 검색")
            },
            text = {
                // 작은 화면에서도 모든 검색 조건을 확인할 수 있게 한다.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("지출 제목") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 분류 선택을 해제하면 모든 분류가 검색 대상이 된다.
                    Box {
                        OutlinedButton(
                            enabled = !loading && loadError == null,
                            onClick = { categoryExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val selectedName = categories
                                .firstOrNull { it.id == categoryId }
                                ?.name

                            Text(
                                text = when {
                                    loading -> "분류를 불러오는 중…"
                                    categoryId == null -> "전체 분류"
                                    selectedName != null -> selectedName
                                    else -> "기존 선택 분류"
                                }
                            )
                        }

                        DropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = {
                                categoryExpanded = false
                            }
                        ) {
                            DropdownMenuItem(
                                text = { Text("전체 분류") },
                                onClick = {
                                    categoryId = null
                                    categoryExpanded = false
                                }
                            )

                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = {
                                        categoryId = category.id
                                        categoryExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    loadError?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error
                        )

                        TextButton(
                            onClick = { reloadCount++ }
                        ) {
                            Text("분류 다시 불러오기")
                        }
                    }

                    Text("지출 유형")

                    // 서버에서 사용하는 FIXED·VARIABLE 값으로 조건을 보관한다.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = type.isEmpty(),
                            onClick = { type = "" },
                            label = { Text("전체") },
                            modifier = Modifier.weight(1f)
                        )

                        FilterChip(
                            selected = type == "FIXED",
                            onClick = { type = "FIXED" },
                            label = { Text("고정") },
                            modifier = Modifier.weight(1f)
                        )

                        FilterChip(
                            selected = type == "VARIABLE",
                            onClick = { type = "VARIABLE" },
                            label = { Text("변동") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    TextButton(
                        onClick = {
                            // 현재 선택한 월은 유지하고 검색 조건만 해제한다.
                            open = false
                            onSearch("", null, "")
                        }
                    ) {
                        Text("검색 조건 초기화")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 입력값을 확정한 뒤 검색창을 닫는다.
                        open = false
                        onSearch(title.trim(), categoryId, type)
                    }
                ) {
                    Text("조회")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        // 편집 중인 검색 조건을 적용하지 않고 닫는다.
                        open = false
                    }
                ) {
                    Text("취소")
                }
            }
        )
    }
}
