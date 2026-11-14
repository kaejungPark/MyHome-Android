package com.myhome.android

import java.math.BigDecimal

data class ShoppingItem(
    val id: Long,                   // 항목을 구분하는 ID. 수정이나 구매 완료 요청에 사용한다.
    val name: String,               // 목록에 표시할 물품명
    val quantity: BigDecimal,       // 1.5kg처럼 소수 수량도 정확하게 담는다.
    val unit: String,               // 수량 뒤에 표시할 단위. 예: 개, 팩, kg
    val isCompleted: Boolean,       // true면 구매 완료, false면 미구매
    val completedDate: String?,     // 미구매 상태에서는 실제 구매일이 아직 없다.
    val purchaseDueDate: String?,   // 예정일과 메모는 선택 입력이므로 서버에서 null이 올 수 있다.
    val memo: String?               // 메모
)