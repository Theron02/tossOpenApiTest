package com.autotrading.domain.backtest

import com.autotrading.domain.indicator.IndicatorCalculator
import com.autotrading.domain.strategy.GoldenCrossStrategy
import com.autotrading.domain.type.OrderSide
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 백테스트 엔진 단위 테스트. 결정론성 + 수수료/세금 반영 + 매매 발생을 검증한다.
 * 전략·지표는 실제 코드(GoldenCrossStrategy/IndicatorCalculator)를 재사용한다.
 */
class BacktestEngineTest {

    private val engine = BacktestEngine(listOf(GoldenCrossStrategy(IndicatorCalculator())))
    private val code = "005930"

    // 평탄 → 급등(골든크로스 BUY) → 급락(데드크로스 SELL) → 반등 ... 라운드트립이 발생하는 시리즈.
    private val closes = listOf(100L, 100, 100, 100, 140, 140, 140, 140, 80, 80, 80, 80, 160, 160)

    private fun request(commission: BigDecimal, tax: BigDecimal) = BacktestRequest(
        stockCode = code,
        interval = com.autotrading.domain.type.CandleInterval.DAY_1,
        strategyName = "GOLDEN_CROSS",
        params = mapOf("shortPeriod" to 2, "longPeriod" to 3),
        initialCapital = BigDecimal("1000000"),
        commissionRate = commission,
        taxRate = tax,
    )

    @Test
    fun `같은 입력은 같은 결과를 낸다 (결정론적)`() {
        val candles = BacktestTestFixtures.candles(code, closes)
        val a = engine.run(request(BigDecimal("0.00015"), BigDecimal("0.0018")), candles)
        val b = engine.run(request(BigDecimal("0.00015"), BigDecimal("0.0018")), candles)
        assertEquals(a, b)
    }

    @Test
    fun `매수 후 매도 라운드트립이 발생한다`() {
        val candles = BacktestTestFixtures.candles(code, closes)
        val result = engine.run(request(BigDecimal.ZERO, BigDecimal.ZERO), candles)
        assertTrue(result.trades.any { it.side == OrderSide.BUY }, "BUY 체결 있어야 함")
        assertTrue(result.trades.any { it.side == OrderSide.SELL }, "SELL 체결 있어야 함")
    }

    @Test
    fun `수수료-세금을 반영하면 최종 자산이 무비용보다 작고, 누적 수수료-세금이 잡힌다`() {
        val candles = BacktestTestFixtures.candles(code, closes)
        val noCost = engine.run(request(BigDecimal.ZERO, BigDecimal.ZERO), candles)
        val withCost = engine.run(request(BigDecimal("0.00015"), BigDecimal("0.0018")), candles)

        assertTrue(withCost.finalEquity < noCost.finalEquity, "비용 반영 시 최종 자산이 더 작아야 한다")
        assertTrue(withCost.totalCommission > BigDecimal.ZERO, "누적 수수료 > 0")
        assertTrue(withCost.totalTax > BigDecimal.ZERO, "누적 세금 > 0 (매도 발생)")
        assertEquals(BigDecimal.ZERO, noCost.totalCommission)
    }

    @Test
    fun `성과 지표가 채워진다 (MDD 0 이상, 매매횟수 양수)`() {
        val candles = BacktestTestFixtures.candles(code, closes)
        val result = engine.run(request(BigDecimal("0.00015"), BigDecimal("0.0018")), candles)
        assertTrue(result.maxDrawdown >= BigDecimal.ZERO)
        assertTrue(result.totalTrades > 0)
        assertEquals(closes.size, result.equityCurve.points.size) // 봉마다 1점
        assertTrue(result.warnings.isNotEmpty()) // 한계 경고 명시
    }
}
