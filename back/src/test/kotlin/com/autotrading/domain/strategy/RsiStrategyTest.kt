package com.autotrading.domain.strategy

import com.autotrading.domain.indicator.IndicatorCalculator
import com.autotrading.domain.type.Signal
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

/** RSI 경계값에서의 신호 산출 검증(period=14, oversold=30, overbought=70). */
class RsiStrategyTest {

    private val strategy = RsiStrategy(IndicatorCalculator())

    private fun ctx(closes: List<BigDecimal>): MarketContext =
        MarketContext("005930", closes, closes.last(), null, emptyMap())

    @Test
    fun `과매도(RSI 낮음)면 BUY`() {
        // 계속 하락 → RSI 0 ≤ 30 → BUY
        assertEquals(Signal.BUY, strategy.evaluate(ctx((15 downTo 1).map { BigDecimal(it) })))
    }

    @Test
    fun `과매수(RSI 높음)면 SELL`() {
        // 계속 상승 → RSI 100 ≥ 70 → SELL
        assertEquals(Signal.SELL, strategy.evaluate(ctx((1..15).map { BigDecimal(it) })))
    }

    @Test
    fun `중립 구간이면 HOLD`() {
        val series = mutableListOf(BigDecimal(100))
        repeat(14) { i -> series.add(series.last().add(if (i % 2 == 0) BigDecimal.ONE else BigDecimal(-1))) }
        assertEquals(Signal.HOLD, strategy.evaluate(ctx(series))) // RSI 50
    }

    @Test
    fun `데이터가 부족하면 HOLD`() {
        assertEquals(Signal.HOLD, strategy.evaluate(ctx(listOf(BigDecimal(1), BigDecimal(2)))))
    }
}
