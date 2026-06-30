package com.autotrading.repository

import com.autotrading.entity.StrategyConfig
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StrategyConfigRepository : JpaRepository<StrategyConfig, UUID> {
    fun findByEnabledTrue(): List<StrategyConfig>

    fun findByAccountIdAndEnabledTrue(accountId: UUID): List<StrategyConfig>
}