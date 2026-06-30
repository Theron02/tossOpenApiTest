package com.autotrading.repository

import com.autotrading.entity.StrategyConfig
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StrategyConfigRepository : JpaRepository<StrategyConfig, UUID> {
    fun findByEnabledTrue(): List<StrategyConfig>

    fun findByAccountIdAndEnabledTrue(accountId: UUID): List<StrategyConfig>

    // 조회 API용 — 계정의 전체 전략 설정
    fun findByAccountId(accountId: UUID): List<StrategyConfig>
}