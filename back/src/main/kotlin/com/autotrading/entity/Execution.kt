package com.autotrading.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** 체결 내역. 주문 1건이 여러 번 나눠 체결될 수 있어 trade_order : execution = 1 : N. */
@Entity
@Table(name = "execution")
class Execution(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    val order: TradeOrder,

    @Column(name = "filled_qty", nullable = false)
    val filledQty: Int,

    @Column(name = "filled_price", nullable = false)
    val filledPrice: Long,

    @Column(name = "fee", nullable = false)
    val fee: Long,

    @Column(name = "executed_at", nullable = false)
    val executedAt: Instant,

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID = UUID.randomUUID(),
) : BaseEntity() {

    init {
        require(filledQty > 0) { "체결 수량은 0보다 커야 한다: $filledQty" }
        require(filledPrice > 0) { "체결 단가는 0보다 커야 한다: $filledPrice" }
        require(fee >= 0) { "수수료는 음수일 수 없다: $fee" }
    }

    /** 수수료 제외 체결 대금 (원). */
    val grossAmount: Long
        get() = filledPrice * filledQty
}