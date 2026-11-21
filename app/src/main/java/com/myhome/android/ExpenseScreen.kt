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
            // 상단에서 대시보드로 돌아갈 수 있다.
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text("‹ 홈")
                }

                Text(
                    text = "생활비",
                    color = ExpenseNavy,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
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

            val data = result

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

                    if (data.items.isEmpty()) {
                        Text("선택한 월에 등록된 지출이 없습니다.")
                    } else {
                        // 서버에서 받은 월별 전체 목록을 5개씩 나눠 표시한다.
                        val totalPages =
                            (data.items.size - 1) / EXPENSE_PAGE_SIZE + 1

                        val currentPage = page.coerceIn(1, totalPages)
                        val visibleItems = data.items
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
                                    ExpenseRow(item)
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
 * 지출 제목, 날짜, 분류, 금액과 메모를 표시한다.
 */
@Composable
private fun ExpenseRow(item: ExpenseItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        contentColor = ExpenseNavy
    ) {
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

            // 메모가 있는 항목만 추가 설명을 표시한다.
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