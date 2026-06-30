package com.autotrading.domain.indicator

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 지표 계산 단위 테스트. 고정 픽스처로 SMA·RSI를 결정론적으로 검증한다. */
class IndicatorCalculatorTest {

    private val calc = IndicatorCalculator()

    private fun closes(vararg v: Int) = v.map { BigDecimal(it) }

    @Test
    fun `SMA - 마지막 N개 종가 평균`() {
        val c = closes(1, 2, 3, 4, 5)
        assertEquals(BigDecimal("4.0000"), calc.sma(c, 3)) // (3+4+5)/3
        assertEquals(BigDecimal("3.0000"), calc.sma(c, 5)) // 전체 평균
    }

    @Test
    fun `SMA - 데이터가 부족하면 null`() {
        assertNull(calc.sma(closes(1, 2), 3))
    }

    @Test
    fun `RSI - 계속 상승하면 100`() {
        assertEquals(BigDecimal("100.0000"), calc.rsi((1..15).toList().let { it.map { n -> BigDecimal(n) } }, 14))
    }

    @Test
    fun `RSI - 계속 하락하면 0`() {
        assertEquals(BigDecimal("0.0000"), calc.rsi((15 downTo 1).toList().map { BigDecimal(it) }, 14))
    }

    @Test
    fun `RSI - 상승과 하락이 균형이면 50`() {
        // 100, 101, 100, 101 ... 14개 변화(7회 +1, 7회 -1) → avgGain=avgLoss → RSI 50
        val series = mutableListOf(BigDecimal(100))
        repeat(14) { i -> series.add(series.last().add(if (i % 2 == 0) BigDecimal.ONE else BigDecimal(-1))) }
        assertEquals(BigDecimal("50.0000"), calc.rsi(series, 14))
    }

    @Test
    fun `RSI - 데이터가 period+1 미만이면 null`() {
        assertNull(calc.rsi(closes(1, 2, 3), 14))
    }
}
