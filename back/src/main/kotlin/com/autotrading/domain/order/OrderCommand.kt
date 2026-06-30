package com.autotrading.domain.order

import com.autotrading.domain.type.OrderSide
import com.autotrading.domain.type.OrderType
import java.util.UUID

/**
 * 주문 실행 요청. [OrderExecutor]의 입력.
 *
 * 금액은 원 단위 정수([Long]). [price]는 LIMIT일 때만 사용(MARKET이면 null).
 * [idempotencyKey]로 중복 실행을 차단한다(같은 키면 기존 주문을 그대로 반환).
 */
data class OrderCommand(
    val accountId: UUID,
    val stockCode: String,
    val side: OrderSide,
    val orderType: OrderType,
    val quantity: Int,
    val price: Long? = null,
    val idempotencyKey: String,
    /** 유발 신호(signal_log) 참조. 수동/테스트 주문이면 null. */
    val signalId: UUID? = null,
)
