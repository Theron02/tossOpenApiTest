package com.autotrading.repository

import com.autotrading.domain.type.OrderStatus
import com.autotrading.entity.TradeOrder
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TradeOrderRepository : JpaRepository<TradeOrder, UUID> {
    fun findByAccountIdAndStatus(accountId: UUID, status: OrderStatus): List<TradeOrder>

    // 조회 API용 — 최신순 페이지네이션
    fun findByAccountIdOrderByCreatedAtDesc(accountId: UUID, pageable: Pageable): List<TradeOrder>

    fun findByAccountIdAndStatusOrderByCreatedAtDesc(accountId: UUID, status: OrderStatus, pageable: Pageable): List<TradeOrder>

    // 멱등성 확인 — 주문 중복 방지의 핵심
    fun existsByIdempotencyKey(idempotencyKey: String): Boolean

    // 멱등 재요청 시 기존 주문을 그대로 반환하기 위한 조회
    fun findByIdempotencyKey(idempotencyKey: String): TradeOrder?

    fun findByStockCodeAndStatusIn(stockCode: String, statuses: Collection<OrderStatus>): List<TradeOrder>
}