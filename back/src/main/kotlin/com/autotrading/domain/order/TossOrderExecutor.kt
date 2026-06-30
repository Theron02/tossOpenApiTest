package com.autotrading.domain.order

import com.autotrading.entity.TradeOrder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 실거래 주문 실행기 (토스 `POST /api/v1/orders`).
 *
 * ⚠️ 미구현. 실거래는 실제 금전 거래를 발생시키므로 **사용자의 명시적 승인 전까지 호출 코드를 작성하지 않는다.**
 * 기본 설정에서는 빈으로 등록되지 않으며(`toss.live-trading-enabled=true`일 때만 생성), 호출 시 예외를 던진다.
 *
 * TODO(승인 후 구현):
 * - `POST /api/v1/orders` 호출. Header `X-Tossinvest-Account: {accountSeq}`.
 * - Body: clientOrderId(=idempotencyKey, ≤36자/패턴/10분 유효), symbol, side, orderType,
 *   quantity(string), price(string, LIMIT만), timeInForce(DAY/CLS).
 * - 응답 `result.{orderId, clientOrderId}` ↔ 내부 TradeOrder 매핑, 토스 OrderStatus(10종) → 내부 상태 변환.
 * - 체결은 비동기(폴링/주문조회)로 반영. RiskManager 경유는 Paper와 동일하게 선행.
 */
@Component
@ConditionalOnProperty(prefix = "toss", name = ["live-trading-enabled"], havingValue = "true")
class TossOrderExecutor : OrderExecutor {
    override fun execute(command: OrderCommand): TradeOrder =
        throw UnsupportedOperationException("실거래(TossOrderExecutor)는 아직 구현되지 않았다. 명시적 승인 후 구현 예정.")
}
