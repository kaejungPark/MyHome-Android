package com.myhome.android

/**
 * 지출 입력 화면에서 선택할 분류 정보다.
 */
data class ExpenseCategory(
    val id: Long,
    val name: String
)

/**
 * 생활비와 고정 지출에서 사용하는 공통 분류 목록을 조회한다.
 */
object CategoryApi {

    /**
     * 서버에 등록된 분류를 서버가 정한 순서대로 반환한다.
     */
    suspend fun fetchCategories(): List<ExpenseCategory> {
        val response = ApiClient.request(
            method = "GET",
            path = "/api/categories"
        )

        val rows = response.getJSONArray("data")

        return List(rows.length()) { index ->
            val row = rows.getJSONObject(index)

            ExpenseCategory(
                id = row.getLong("id"),
                name = row.getString("name")
            )
        }
    }
}