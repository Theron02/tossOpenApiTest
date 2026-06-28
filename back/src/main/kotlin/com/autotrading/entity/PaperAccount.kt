package com.autotrading.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/** 모의투자 계좌. 금액은 모두 원 단위 정수([Long]). */
@Entity
@Table(name = "paper_account")
class PaperAccount(
    name: String,
    cashBalance: Long,
    initialSeed: Long,
    isLive: Boolean = false,
    @Id
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID = UUID.randomUUID(),
) : BaseEntity() {

    @Column(name = "name", nullable = false)
    var name: String = name
        protected set

    /** 현재 현금 잔고 (원). */
    @Column(name = "cash_balance", nullable = false)
    var cashBalance: Long = cashBalance
        protected set

    /** 최초 시드머니 (원). 수익률 산정 기준이므로 불변. */
    @Column(name = "initial_seed", nullable = false, updatable = false)
    val initialSeed: Long = initialSeed

    /** 실거래 여부. 기본은 모의투자(false). */
    @Column(name = "is_live", nullable = false)
    val isLive: Boolean = isLive

    /** 잔고 차감. */
    fun withdraw(amount: Long) {
        require(amount > 0) { "출금액은 0보다 커야 한다: $amount" }
        require(cashBalance >= amount) { "잔고 부족: balance=$cashBalance, amount=$amount" }
        cashBalance -= amount
    }

    /** 잔고 증가. */
    fun deposit(amount: Long) {
        require(amount > 0) { "입금액은 0보다 커야 한다: $amount" }
        cashBalance += amount
    }
}