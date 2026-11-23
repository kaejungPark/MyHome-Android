package com.myhome.android

import android.net.Uri
import java.math.BigDecimal
import org.json.JSONObject

/**
 * 생활비 목록과 수정 화면에서 사용할 지출 정보다.
 * 분류 ID, 지출 유형, 결제 수단도 함께 보관해 수정 화면에 복원한다.
 */
data class ExpenseItem(
    val id: Long,
    val title: String,
    val categoryId: Long,
    val categoryName: String,
    val amount: BigDecimal,
    val expenseDate: String,
    val expenseType: String,
    val paymentMethod: String?,
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
                categoryId = row.getLong("categoryId"),
                categoryName = row.getString("categoryName"),
                amount = row.get("amount").toString().toBigDecimal(),
                expenseDate = row.getString("expenseDate"),
                expenseType = row.getString("expenseType"),
                paymentMethod = if (row.isNull("paymentMethod")) {
                    null
                } else {
                    row.getString("paymentMethod")
                },
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

    /**
     * ID가 없으면 지출을 등록하고, ID가 있으면 해당 지출을 수정한다.
     * 서버에서 로그인 사용자와 수정 가능 여부를 다시 검사한다.
     */
    suspend fun saveExpense(
        id: Long?,
        categoryId: Long,
        amount: BigDecimal,
        expenseDate: String,
        expenseType: String,
        paymentMethod: String,
        title: String,
        memo: String
    ) {
        // 선택 입력의 빈 문자열은 JSON null로 전달한다.
        val body = JSONObject().apply {
            put("categoryId", categoryId)
            put("amount", amount)
            put("expenseDate", expenseDate)
            put("expenseType", expenseType)
            put("title", title.trim())
            put(
                "paymentMethod",
                paymentMethod.trim().ifBlank { null } ?: JSONObject.NULL
            )
            put(
                "memo",
                memo.trim().ifBlank { null } ?: JSONObject.NULL
            )
        }

        // 등록과 수정 모두 서버 데이터를 변경하므로 CSRF 토큰이 필요하다.
        ApiClient.request(
            method = if (id == null) "POST" else "PUT",
            path = if (id == null) "/api/expenses" else "/api/expenses/$id",
            body = body.toString(),
            contentType = "application/json; charset=UTF-8",
            csrf = AuthApi.fetchCsrfToken()
        )
    }

    /**
     * 로그인한 사용자의 지출을 삭제한다.
     * 서버가 소유자와 고정 지출 납부 연결 여부를 검사한다.
     * 삭제는 서버 데이터를 변경하므로 CSRF 토큰을 함께 전달한다.
     */
    suspend fun deleteExpense(id: Long) {
        require(id > 0) {
            "삭제할 지출 ID가 올바르지 않습니다."
        }

        ApiClient.request(
            method = "DELETE",
            path = "/api/expenses/$id",
            csrf = AuthApi.fetchCsrfToken()
        )
    }
}