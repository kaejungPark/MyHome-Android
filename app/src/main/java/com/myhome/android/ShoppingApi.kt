package com.myhome.android

import org.json.JSONObject

// 장보기 화면에서 사용하는 조회·등록·수정·구매 상태 변경 요청을 담당한다.
object ShoppingApi {

    private const val PATH = "/api/shopping-items"

    /**
     * 선택한 탭의 첫 페이지를 조회한다.
     * isCompleted가 false면 살 물품, true면 구매 완료 항목을 가져온다.
     */
    suspend fun fetchShoppingItems(isCompleted: Boolean): List<ShoppingItem> {
        val response = ApiClient.request(
            method = "GET",
            path = "$PATH?page=1&size=10&isCompleted=$isCompleted",
            unauthorizedMessage = "장보기 목록을 보려면 다시 로그인해 주세요."
        )

        // 백엔드 페이지 응답에서 실제 물품 배열은 data.items에 들어 있다.
        val items = response.getJSONObject("data").getJSONArray("items")
        return List(items.length()) { index ->
            val item = items.getJSONObject(index)

            ShoppingItem(
                id = item.getLong("id"),
                name = item.getString("name"),
                quantity = item.get("quantity").toString().toBigDecimal(),
                unit = item.getString("unit"),
                isCompleted = item.getBoolean("isCompleted"),
                completedDate = item.optionalText("completedDate"),
                purchaseDueDate = item.optionalText("purchaseDueDate"),
                memo = item.optionalText("memo")
            )
        }
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
}