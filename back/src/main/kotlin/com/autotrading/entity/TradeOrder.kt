package com.autotrading.entity

import com.autotrading.domain.type.OrderSide
import com.autotrading.domain.type.OrderStatus
import com.autotrading.domain.type.OrderType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

/**
 * 주문. `order`는 SQL 예약어라 테이블명은 `trade_order`.
 *
 * 모든 주문은 RiskManager 통과 후에만 생성된다(Service 계층 보장).
 */
@Entity
@Table(name = "trade_order")
class TradeOrder(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    val account: PaperAccount,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_code", nullable = false)
    val stock: Stock,

    @Enumerated(EnumType.STRING)
    @Column(name = "side", length = 8, nullable = false)
    val side: OrderSide,

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", length = 8, nullable = false)
    val orderType: OrderType,

    @Column(name = "quantity", nullable = false)
    val quantity: Int,

    /** 지정가(LIMIT)일 때만. 시장가면 null. */
    @Column(name = "price")
    val price: Long? = null,

    /** 중복 주문 방지 멱등키. */
    @Column(name = "idempotency_key", nullable = false, unique = true)
    val idempotencyKey: String,

    /** 유발 신호(signal_log) 참조. 수동 주문이면 null. */
    @Column(name = "signal_id", columnDefinition = "uuid")
    val signalId: UUID? = null,

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID = UUID.randomUUID(),
) : BaseEntity() {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    var status: OrderStatus = OrderStatus.PENDING
        protected set

    /** 누적 체결 수량. */
    @Column(name = "filled_quantity", nullable = false)
    var filledQuantity: Int = 0
        protected set

    init {
        require(quantity > 0) { "주문 수량은 0보다 커야 한다: $quantity" }
        if (orderType == OrderType.LIMIT) {
            val limitPrice = price
            require(limitPrice != null && limitPrice > 0) { "지정가 주문은 price가 필요하다" }
        }
    }

    /** 미체결 잔량. */
    val remainingQuantity: Int
        get() = quantity - filledQuantity

    /** 상태 전이. 불법 전이는 예외. */
    fun transitionTo(next: OrderStatus) {
        require(status.canTransitionTo(next)) { "불법 상태 전이: $status -> $next" }
        status = next
    }

    /** 체결 누적 + 상태 자동 전이. 전량 체결→FILLED, 일부 체결→PARTIAL. */
    fun applyFill(filledQty: Int) {
        require(filledQty > 0) { "체결 수량은 0보다 커야 한다: $filledQty" }
        val newFilled = filledQuantity + filledQty
        require(newFilled <= quantity) {
            "체결 누적이 주문 수량을 초과한다: filled=$newFilled, quantity=$quantity"
        }
        filledQuantity = newFilled
        val target = if (filledQuantity == quantity) OrderStatus.FILLED else OrderStatus.PARTIAL
        if (status != target) transitionTo(target)
    }
}