package com.autotrading.repository

import com.autotrading.entity.SignalLog
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SignalLogRepository : JpaRepository<SignalLog, UUID> {
    // SignalLog의 연관 필드명이 `strategy`(→ strategy_config) 이므로 strategyId 로 탐색.
    fun findByStrategyIdOrderByCreatedAtDesc(strategyId: UUID): List<SignalLog>

    // 조회 API용 — 전체 신호 최신순 페이지네이션(단일 계정 전제)
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): List<SignalLog>
}
