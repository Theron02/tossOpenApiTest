package com.autotrading.service

import com.autotrading.domain.type.CandleInterval
import com.autotrading.entity.Candle
import com.autotrading.external.toss.TossMarketDataClient
import com.autotrading.external.toss.dto.CandleItem
import com.autotrading.repository.CandleRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 토스 캔들을 수집해 `candle` 테이블에 저장한다(지표 계산용).
 *
 * 토스 interval은 `1m`/`1d`만 지원하므로 우리 [CandleInterval] 중 [MIN_1]/[DAY_1]만 매핑한다.
 * 가격은 KR 정수 기준으로 [Long] 저장(미국 소수점 종목은 추후 BigDecimal 분기 — 이번 범위 밖).
 */
@Service
class CandleCollector(
    private val marketDataClient: TossMarketDataClient,
    private val candleRepository: CandleRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** [count]개 캔들을 수집해 신규 봉만 저장하고, 저장된 개수를 반환한다. */
    @Transactional
    fun collect(stockCode: String, interval: CandleInterval, count: Int): Int {
        val tossInterval = interval.toTossInterval()
        val result = marketDataClient.getCandles(stockCode, tossInterval, count)

        // 이미 저장된 봉 시각(유니크: stock_code+interval+candle_time)은 건너뛴다.
        val existing = candleRepository
            .findByStockCodeAndCandleIntervalOrderByCandleTimeDesc(stockCode, interval, PageRequest.of(0, count))
            .map { it.candleTime }
            .toSet()

        val toSave = result.candles
            .map { it.toEntity(stockCode, interval) }
            .filter { it.candleTime !in existing }

        candleRepository.saveAll(toSave)
        log.info("캔들 수집: stock={} interval={} 수신={} 저장={}", stockCode, interval, result.candles.size, toSave.size)
        return toSave.size
    }

    private fun CandleItem.toEntity(stockCode: String, interval: CandleInterval) = Candle(
        stockCode = stockCode,
        candleInterval = interval,
        open = open.toLong(),
        high = high.toLong(),
        low = low.toLong(),
        close = close.toLong(),
        volume = volumeValue.toLong(),
        candleTime = parseTimestamp(timestamp),
    )

    private fun CandleInterval.toTossInterval(): String = when (this) {
        CandleInterval.MIN_1 -> "1m"
        CandleInterval.DAY_1 -> "1d"
        else -> throw IllegalArgumentException("토스는 1m/1d만 지원한다. 미지원 interval: $this")
    }

    /** timestamp 포맷 방어적 파싱: ISO-8601 → epoch millis → epoch seconds. */
    private fun parseTimestamp(raw: String): Instant =
        runCatching { Instant.parse(raw) }.getOrNull()
            ?: raw.toLongOrNull()?.let { num ->
                if (num > 1_000_000_000_000L) Instant.ofEpochMilli(num) else Instant.ofEpochSecond(num)
            }
            ?: throw IllegalArgumentException("캔들 timestamp 파싱 실패: $raw")
}
