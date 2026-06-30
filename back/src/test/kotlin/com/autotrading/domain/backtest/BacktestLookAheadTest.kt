package com.autotrading.domain.backtest

import com.autotrading.domain.indicator.IndicatorCalculator
import com.autotrading.domain.strategy.GoldenCrossStrategy
import com.autotrading.domain.type.CandleInterval
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ★ look-ahead 회귀 테스트 (TASK_05 §8-2, 필수) ★
 *
 * 미래 캔들을 끊어도 **과거 구간 결과가 변하지 않아야** 한다.
 * 시점 T 판단/체결이 T 이후 데이터에 의존하면, 전체 시리즈로 돌린 결과의 앞부분이
 * 잘라서 돌린 결과와 달라진다. 두 결과의 공통 prefix가 정확히 일치함을 검증해 미래 누수를 차단한다.
 */
class BacktestLookAheadTest {

    private val engine = BacktestEngine(listOf(GoldenCrossStrategy(IndicatorCalculator())))
    private val code = "005930"

    // 앞 구간(≤k)에 매매가 일어나도록 급등/급락을 배치.
    private val closes = listOf(100L, 100, 100, 100, 150, 150, 150, 70, 70, 70, 200, 200, 60, 60, 220, 220)

    private fun request() = BacktestRequest(
        stockCode = code,
        interval = CandleInterval.DAY_1,
        strategyName = "GOLDEN_CROSS",
        params = mapOf("shortPeriod" to 2, "longPeriod" to 3),
        initialCapital = BigDecimal("1000000"),
    )

    @Test
    fun `미래 캔들을 잘라도 과거 equity curve와 매매가 동일하다`() {
        val all = BacktestTestFixtures.candles(code, closes)
        val k = 8

        val full = engine.run(request(), all)
        val truncated = engine.run(request(), all.take(k))

        // 1) equity curve 앞 k개가 정확히 일치 (미래를 안 봤다는 증거).
        assertEquals(k, truncated.equityCurve.points.size)
        assertEquals(full.equityCurve.points.take(k), truncated.equityCurve.points)

        // 2) 잘린 기간 안의 체결도 동일 (시각·가격·수량·수수료까지).
        val cutoff = all[k - 1].candleTime
        val fullTradesInWindow = full.trades.filter { it.time <= cutoff }
        assertEquals(fullTradesInWindow, truncated.trades)
    }

    @Test
    fun `자르는 위치를 바꿔도 항상 prefix가 일치한다`() {
        val all = BacktestTestFixtures.candles(code, closes)
        val full = engine.run(request(), all)
        // 여러 절단점에서 모두 prefix 일치 → 어떤 시점에서도 미래 비참조.
        for (k in 5..closes.size) {
            val truncated = engine.run(request(), all.take(k))
            assertEquals(full.equityCurve.points.take(k), truncated.equityCurve.points, "절단점 k=$k 에서 prefix 불일치")
        }
        assertTrue(true)
    }
}
