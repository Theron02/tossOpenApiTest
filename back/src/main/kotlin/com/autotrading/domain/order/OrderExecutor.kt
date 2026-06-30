package com.autotrading.domain.order

import com.autotrading.entity.TradeOrder

/**
 * 주문 실행기. 모의/실거래를 같은 인터페이스로 둔다.
 *
 * - [com.autotrading.domain.order.PaperOrderExecutor] (기본): 토스로 전송하지 않고 현재가로 가상 체결.
 * - TossOrderExecutor (실거래): `toss.live-trading-enabled=true`일 때만. 명시적 승인 전까지 미구현.
 *
 * 구현체는 **반드시 RiskManager를 경유**한 뒤에만 주문을 생성한다(우회 금지).
 */
interface OrderExecutor {
    fun execute(command: OrderCommand): TradeOrder
}
