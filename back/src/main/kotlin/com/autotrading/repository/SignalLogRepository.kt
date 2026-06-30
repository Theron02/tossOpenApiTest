package com.autotrading.repository

import com.autotrading.entity.SignalLog
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SignalLogRepository : JpaRepository<SignalLog, UUID> {
    // SignalLog의 연관 필드명이 `strategy`(→ strategy_config) 이므로 strategyId 로 탐색.
    fun findByStrategyIdOrderByCreatedAtDesc(strategyId: UUID): List<SignalLog>
}
