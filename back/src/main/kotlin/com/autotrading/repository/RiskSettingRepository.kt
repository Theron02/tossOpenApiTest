package com.autotrading.repository

import com.autotrading.entity.RiskSetting
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RiskSettingRepository : JpaRepository<RiskSetting, UUID> {
    fun findByAccountId(accountId: UUID): RiskSetting?
}