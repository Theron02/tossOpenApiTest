package com.autotrading.entity

import com.autotrading.domain.type.CandleInterval
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

/**
 * OHLCV 봉 데이터. IndicatorCalculator가 기간 조회해 MA/RSI/Bollinger를 산출한다.
 * 가격은 원 단위 정수([Long]).
 */
@Entity
@Table(
    name = "candle",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_candle_stock_interval_time",
            columnNames = ["stock_code", "candle_interval", "candle_time"],
        ),
    ],
    indexes = [
        Index(
            name = "idx_candle_stock_interval_time",
            columnList = "stock_code, candle_interval, candle_time",
        ),
    ],
)
class Candle(
    @Column(name = "stock_code", length = 6, nullable = false)
    val stockCode: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "candle_interval", length = 8, nullable = false)
    val candleInterval: CandleInterval,

    @Column(name = "open", nullable = false)
    val open: Long,

    @Column(name = "high", nullable = false)
    val high: Long,

    @Column(name = "low", nullable = false)
    val low: Long,

    @Column(name = "close", nullable = false)
    val close: Long,

    @Column(name = "volume", nullable = false)
    val volume: Long,

    /** 봉 시작 시각 (UTC). */
    @Column(name = "candle_time", nullable = false)
    val candleTime: Instant,

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    val id: UUID = UUID.randomUUID(),
) : BaseEntity()