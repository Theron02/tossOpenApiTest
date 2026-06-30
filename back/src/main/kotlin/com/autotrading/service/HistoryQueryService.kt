package com.autotrading.service

import com.autotrading.controller.dto.ExecutionResponse
import com.autotrading.controller.dto.OrderResponse
import com.autotrading.controller.dto.SignalResponse
import com.autotrading.controller.dto.toResponse
import com.autotrading.domain.type.OrderStatus
import com.autotrading.repository.ExecutionRepository
import com.autotrading.repository.SignalLogRepository
import com.autotrading.repository.TradeOrderRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 주문·체결·신호 조회(읽기 전용). 모두 운영 계정 기준, 최신순. */
@Service
class HistoryQueryService(
    private val accountResolver: AccountResolver,
    private val orderRepository: TradeOrderRepository,
    private val executionRepository: ExecutionRepository,
    private val signalLogRepository: SignalLogRepository,
) {
    @Transactional(readOnly = true)
    fun orders(status: OrderStatus?, page: Int, size: Int): List<OrderResponse> {
        val accountId = accountResolver.resolve().id
        val pageable = PageRequest.of(page, size)
        val orders = if (status == null) {
            orderRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable)
        } else {
            orderRepository.findByAccountIdAndStatusOrderByCreatedAtDesc(accountId, status, pageable)
        }
        return orders.map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun executions(limit: Int): List<ExecutionResponse> {
        val accountId = accountResolver.resolve().id
        // 시간 오름차순 전체 → 최신순으로 뒤집어 limit (간이 페이지네이션).
        return executionRepository.findByAccountIdOrderByExecutedAt(accountId)
            .asReversed()
            .take(limit)
            .map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun signals(page: Int, size: Int): List<SignalResponse> {
        accountResolver.resolve() // 계정 존재 확인(단일 계정 전제)
        return signalLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
            .map { it.toResponse() }
    }
}
