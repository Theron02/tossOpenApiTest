package com.autotrading.domain.backtest

import com.autotrading.domain.type.CandleInterval
import com.autotrading.entity.Candle
import java.time.Instant

/** 백테스트 테스트용 캔들 픽스처. 단순화를 위해 open=high=low=close, 일봉 간격으로 만든다. */
object BacktestTestFixtures {

    private val base: Instant = Instant.parse("2026-01-01T00:00:00Z")

    /** 종가 리스트(오래된→최신)를 오름차순 일봉 캔들로 변환. open=close. */
    fun candles(stockCode: String, closesOldToNew: List<Long>): List<Candle> =
        closesOldToNew.mapIndexed { i, close ->
            Candle(
                stockCode = stockCode,
                candleInterval = CandleInterval.DAY_1,
                open = close,
                high = close,
                low = close,
                close = close,
                volume = 1,
                candleTime = base.plusSeconds(i * 86_400L),
            )
        }
}
