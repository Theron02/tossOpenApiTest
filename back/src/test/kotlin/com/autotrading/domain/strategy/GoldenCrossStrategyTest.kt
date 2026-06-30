package com.autotrading.domain.strategy

import com.autotrading.domain.indicator.IndicatorCalculator
import com.autotrading.domain.type.Signal
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

/** 골든크로스/데드크로스 신호 산출을 고정 픽스처로 검증. short=2, long=3. */
class GoldenCrossStrategyTest {

    private val strategy = GoldenCrossStrategy(IndicatorCalculator())
    private val params = mapOf<String, Any>("shortPeriod" to 2, "longPeriod" to 3)

    private fun ctx(vararg closesInt: Int): MarketContext {
        val closes = closesInt.map { BigDecimal(it) }
        return MarketContext("005930", closes, closes.last(), null, params)
    }

    @Test
    fun `단기가 장기를 상향 돌파하면 BUY`() {
        // prev[10,10,10]: sma2=10, sma3=10 → 동률 / now[..,13]: sma2=11.5 > sma3=11.0
        assertEquals(Signal.BUY, strategy.evaluate(ctx(10, 10, 10, 13)))
    }

    @Test
    fun `단기가 장기를 하향 돌파하면 SELL`() {
        // prev 동률 / now[..,7]: sma2=8.5 < sma3=9.0
        assertEquals(Signal.SELL, strategy.evaluate(ctx(10, 10, 10, 7)))
    }

    @Test
    fun `돌파가 없으면 HOLD`() {
        assertEquals(Signal.HOLD, strategy.evaluate(ctx(10, 11, 12, 13)))
    }

    @Test
    fun `데이터가 부족하면 HOLD`() {
        assertEquals(Signal.HOLD, strategy.evaluate(ctx(10, 11, 12)))
    }
}
