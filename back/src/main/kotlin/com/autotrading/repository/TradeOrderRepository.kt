package com.autotrading.repository

import com.autotrading.domain.type.OrderStatus
import com.autotrading.entity.TradeOrder
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TradeOrderRepository : JpaRepository<TradeOrder, UUID> {
    fun findByAccountIdAndStatus(accountId: UUID, status: OrderStatus): List<TradeOrder>

    // 멱등성 확인 — 주문 중복 방지의 핵심
    fun existsByIdempotencyKey(idempotencyKey: String): Boolean

    fun findByStockCodeAndStatusIn(stockCode: String, statuses: Collection<OrderStatus>): List<TradeOrder>
}