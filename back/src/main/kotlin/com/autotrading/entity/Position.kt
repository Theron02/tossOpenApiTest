package com.autotrading.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

/** 보유 포지션. 계좌+종목 당 하나(유니크). */
@Entity
@Table(
    name = "position",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_position_account_stock", columnNames = ["account_id", "stock_code"]),
    ],
)
class Position(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    val account: PaperAccount,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_code", nullable = false)
    val stock: Stock,

    quantity: Int,
    avgPrice: Long,

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID = UUID.randomUUID(),
) : BaseEntity() {

    /** 보유 수량. */
    @Column(name = "quantity", nullable = false)
    var quantity: Int = quantity
        protected set

    /** 평균 매입 단가 (원). */
    @Column(name = "avg_price", nullable = false)
    var avgPrice: Long = avgPrice
        protected set

    /** 보유 수량이 0인지. */
    val isEmpty: Boolean
        get() = quantity == 0

    /** 매수 체결 반영. 평단가를 가중평균(정수 나눗셈)으로 갱신한다. */
    fun addBuy(filledQty: Int, filledPrice: Long) {
        require(filledQty > 0) { "체결 수량은 0보다 커야 한다: $filledQty" }
        require(filledPrice > 0) { "체결 단가는 0보다 커야 한다: $filledPrice" }
        val newQty = quantity + filledQty
        avgPrice = (avgPrice * quantity + filledPrice * filledQty) / newQty
        quantity = newQty
    }

    /** 매도 체결 반영. 수량만 차감하고 평단가는 유지한다. */
    fun reduceSell(filledQty: Int) {
        require(filledQty > 0) { "체결 수량은 0보다 커야 한다: $filledQty" }
        require(quantity >= filledQty) { "보유 수량 부족: quantity=$quantity, sell=$filledQty" }
        quantity -= filledQty
    }
}