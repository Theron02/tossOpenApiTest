package com.autotrading.domain.type

/** 전략이 산출하는 매매 신호. */
enum class Signal { BUY, SELL, HOLD }

/** 주문 방향. */
enum class OrderSide { BUY, SELL }

/** 주문 유형. LIMIT은 지정가(price 필수), MARKET은 시장가. */
enum class OrderType { MARKET, LIMIT }

/** 거래 시장. */
enum class Market { KOSPI, KOSDAQ }

/** 캔들(봉) 주기. */
enum class CandleInterval { MIN_1, MIN_5, MIN_15, MIN_30, HOUR_1, DAY_1 }

/**
 * 주문 상태머신.
 *
 * ```
 * PENDING → PARTIAL / FILLED / CANCELLED / REJECTED
 * PARTIAL → FILLED / CANCELLED
 * FILLED, CANCELLED, REJECTED → (종료, 전이 불가)
 * ```
 */
enum class OrderStatus {
    PENDING,
    PARTIAL,
    FILLED,
    CANCELLED,
    REJECTED;

    /** 종료 상태(더 이상 전이 불가)인지. */
    val isTerminal: Boolean
        get() = this in TERMINAL

    /** [target]으로의 전이가 허용되는지. */
    fun canTransitionTo(target: OrderStatus): Boolean = target in allowedTransitions()

    private fun allowedTransitions(): Set<OrderStatus> = when (this) {
        PENDING -> setOf(PARTIAL, FILLED, CANCELLED, REJECTED)
        PARTIAL -> setOf(FILLED, CANCELLED)
        FILLED, CANCELLED, REJECTED -> emptySet()
    }

    companion object {
        private val TERMINAL = setOf(FILLED, CANCELLED, REJECTED)
    }
}