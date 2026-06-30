package com.autotrading.repository

import com.autotrading.domain.type.CandleInterval
import com.autotrading.entity.Candle
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CandleRepository : JpaRepository<Candle, UUID> {
    // 지표 계산 시 최근 N개 봉 조회. Pageable로 limit (전체 스캔 금지).
    // 엔티티 필드명이 `candleInterval` 이므로 메서드명도 CandleInterval 사용.
    fun findByStockCodeAndCandleIntervalOrderByCandleTimeDesc(
        stockCode: String,
        candleInterval: CandleInterval,
        pageable: Pageable,
    ): List<Candle>
}