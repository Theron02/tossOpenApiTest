package com.autotrading.repository

import com.autotrading.entity.Position
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PositionRepository : JpaRepository<Position, UUID> {
    fun findByAccountId(accountId: UUID): List<Position>

    // stockCode → stock.code 로 탐색 (Position은 Stock과 @ManyToOne)
    fun findByAccountIdAndStockCode(accountId: UUID, stockCode: String): Position?
}