package com.myhome.android

import android.net.Uri
import java.math.BigDecimal

/**
 * 생활비 목록에 표시할 지출 한 건이다.
 * 금액은 소수 계산 오차를 피하기 위해 BigDecimal로 보관한다.
 * 지출일은 서버가 반환한 yyyy-MM-dd 문자열을 사용한다.
 */
data class ExpenseItem(
    val id: Long,
    val title: String,
    val categoryName: String,
    val amount: BigDecimal,
    val expenseDate: String,
    val memo: String?
)

/**
 * 선택한 월의 전체 지출과 합계를 보관한다.
 * 화면에서 페이지를 바꿔도 totalAmount는 월 전체 합계를 유지한다.
 */
data class MonthlyExpenses(
    val items: List<ExpenseItem>,
    val totalAmount: BigDecimal
)

/**
 * 생활비 조회 API를 담당한다.
 * 세션 쿠키, 네트워크 실행, 인증 실패 처리는 ApiClient를 사용한다.
 */
object ExpenseApi {

    /**
     * YYYY-MM에 해당하는 월의 전체 지출과 합계를 가져온다.
     * 조회 결과가 없으면 빈 목록과 합계 0을 반환한다.
     */
    suspend fun fetchMonthlyExpenses(month: String): MonthlyExpenses {
        // 잘못된 월 문자열을 서버에 보내기 전에 확인한다.
        require(
            month.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])")) &&
                    month.take(4).toInt() in 1..9998
        ) {
            "조회 월은 YYYY-MM 형식으로 입력해 주세요."
        }

        // 월을 경로에 안전하게 붙여 조회 주소를 만든다.
        val path = Uri.parse("/api/expenses/monthly")
            .buildUpon()
            .appendPath(month)
            .build()
            .toString()

        // GET 조회에는 CSRF 토큰이 필요하지 않다.
        val response = ApiClient.request(
            method = "GET",
            path = path,
            unauthorizedMessage = "생활비를 보려면 다시 로그인해 주세요."
        )

        // 월별 API의 data는 지출 항목 배열이다.
        val rows = response.getJSONArray("data")

        val items = List(rows.length()) { index ->
            val row = rows.getJSONObject(index)

            ExpenseItem(
                id = row.getLong("id"),
                title = row.getString("title"),
                categoryName = row.getString("categoryName"),
                amount = row.get("amount").toString().toBigDecimal(),
                expenseDate = row.getString("expenseDate"),
                // JSON null을 문자 "null"로 표시하지 않도록 변환한다.
                memo = if (row.isNull("memo")) {
                    null
                } else {
                    row.getString("memo")
                }
            )
        }

        // 월 전체 항목을 합산한다. 빈 목록이면 BigDecimal.ZERO가 된다.
        val totalAmount = items.fold(BigDecimal.ZERO) { total, item ->
            total.add(item.amount)
        }

        return MonthlyExpenses(
            items = items,
            totalAmount = totalAmount
        )
    }
}