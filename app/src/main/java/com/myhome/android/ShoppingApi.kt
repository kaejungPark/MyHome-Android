package com.myhome.android

import org.json.JSONObject

// items는 현재 페이지의 물품이고, totalCount는 같은 조건에 맞는 전체 물품 수다.
data class ShoppingPage(
    val items: List<ShoppingItem>,
    val totalPages: Int,
    val totalCount: Long
)

/**
 * 장보기 목록 조회·검색, 물품 등록·수정·삭제, 구매 완료·취소를 담당한다.
 * 각 함수는 요청 경로와 데이터를 정하고, 세션 쿠키 전송과 공통 응답 처리는 ApiClient에 맡긴다.
 */
object ShoppingApi {

    private const val PATH = "/api/shopping-items"

    /**
     * 현재 탭과 검색 조건에 맞는 장보기 목록을 한 페이지에 최대 5개씩 조회한다.
     *
     * @param isCompleted false면 살 물품, true면 구매 완료 항목을 조회한다.
     * @param page 조회할 페이지 번호. 첫 페이지는 1이다.
     * @param name 물품명 검색어. 비워 두면 이름 조건을 적용하지 않는다.
     * @param purchaseDueDate 구매 예정일(yyyy-MM-dd). 비워 두면 날짜 조건을 적용하지 않는다.
     * @return 현재 페이지의 물품 목록과 같은 조건으로 조회한 전체 페이지 수.
     */
    suspend fun fetchShoppingItems(
        isCompleted: Boolean,
        page: Int,
        name: String = "",
        purchaseDueDate: String = ""
    ): ShoppingPage {
        // 한글, 공백, &가 들어간 물품명도 검색할 수 있도록 URL을 조립한다.
        val path = android.net.Uri.parse(PATH).buildUpon()
            .appendQueryParameter("page", page.toString())
            .appendQueryParameter("size", "5")
            .appendQueryParameter("isCompleted", isCompleted.toString())
            .apply {
                if (name.isNotBlank()) {
                    appendQueryParameter("name", name.trim())
                }
                if (purchaseDueDate.isNotBlank()) {
                    appendQueryParameter("purchaseDueDate", purchaseDueDate)
                }
            }
            .build()
            .toString()

        val response = ApiClient.request(
            method = "GET",
            path = path,
            unauthorizedMessage = "장보기 목록을 보려면 다시 로그인해 주세요."
        )

        // 서버는 검색 조건에 맞는 현재 페이지의 항목과 전체 페이지 수를 함께 내려준다.
        val data = response.getJSONObject("data")
        val rows = data.getJSONArray("items")

        // 서버가 반환한 JSON 배열의 각 행을 화면에서 사용하는 ShoppingItem으로 변환한다.
        // 조회 결과가 0건이면 빈 목록이 되어 화면에서 빈 목록 안내를 표시한다.
        val items = List(rows.length()) { index ->
            val item = rows.getJSONObject(index)

            ShoppingItem(
                id = item.getLong("id"),
                name = item.getString("name"),
                // 수량에는 1.5kg 같은 소수가 들어올 수 있어 BigDecimal로 읽는다.
                quantity = item.get("quantity").toString().toBigDecimal(),
                unit = item.getString("unit"),
                isCompleted = item.getBoolean("isCompleted"),
                completedDate = item.optionalText("completedDate"),
                purchaseDueDate = item.optionalText("purchaseDueDate"),
                memo = item.optionalText("memo")
            )
        }

        return ShoppingPage(
            items = items,
            totalPages = data.getInt("totalPages"),
            // 대시보드에서 첫 페이지의 5개를 넘어서는 물품까지 정확하게 셀 수 있다.
            totalCount = data.getLong("totalCount")
        )
    }

    // 구매일·예정일·메모의 JSON null을 Kotlin의 null로 읽는다.
    private fun JSONObject.optionalText(key: String): String? {
        return if (isNull(key)) null else getString(key)
    }

    /**
     * 체크하면 구매 완료, 체크를 풀면 완료 취소를 요청한다.
     * 실제 구매일의 설정과 초기화는 서버가 처리한다.
     */
    suspend fun completeShoppingItem(id: Long, completed: Boolean = true) {
        val action = if (completed) "completed" else "cancel"

        ApiClient.request(
            method = "PUT",
            path = "$PATH/$id/$action",
            csrf = AuthApi.fetchCsrfToken()
        )
    }

    /**
     * id가 null이면 새 물품을 등록하고, 값이 있으면 해당 물품을 수정한다.
     * 수량은 입력 화면에서 형식을 검사한 뒤 전달한다.
     */
    suspend fun saveShoppingItem(
        id: Long?,
        name: String,
        quantity: String,
        unit: String,
        purchaseDueDate: String,
        memo: String
    ) {
        // 선택 항목을 비우면 서버에도 null을 보내 기존 날짜나 메모를 지울 수 있게 한다.
        val body = JSONObject().apply {
            put("name", name.trim())
            put("quantity", quantity.trim().toBigDecimal())
            put("unit", unit.trim())
            put("purchaseDueDate", if (purchaseDueDate.isBlank()) JSONObject.NULL else purchaseDueDate)
            put("memo", if (memo.isBlank()) JSONObject.NULL else memo.trim())
        }

        ApiClient.request(
            method = if (id == null) "POST" else "PUT",
            path = if (id == null) PATH else "$PATH/$id",
            body = body.toString(),
            contentType = "application/json; charset=UTF-8",
            csrf = AuthApi.fetchCsrfToken()
        )
    }

    /**
     * 로그인한 사용자의 장보기 항목 한 건을 삭제한다.
     * 이름이 같은 물품이 있을 수 있으므로 목록에서 선택한 항목의 ID를 사용한다.
     */
    suspend fun deleteShoppingItem(id: Long) {
        // 삭제 대상은 URL로 전달하고, 데이터 변경에 필요한 CSRF 토큰을 함께 보낸다.
        ApiClient.request(
            method = "DELETE",
            path = "$PATH/$id",
            csrf = AuthApi.fetchCsrfToken()
        )
    }
}