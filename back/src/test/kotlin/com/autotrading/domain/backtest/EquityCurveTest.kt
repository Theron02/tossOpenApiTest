package com.autotrading.domain.backtest

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals

/** MDD(최대 낙폭) 계산 검증. 고정 곡선으로 정확한 값을 확인한다. */
class EquityCurveTest {

    private fun curve(vararg equities: Long): EquityCurve {
        val base = Instant.parse("2026-01-01T00:00:00Z")
        return EquityCurve(equities.mapIndexed { i, e -> EquityPoint(base.plusSeconds(i * 60L), BigDecimal(e)) })
    }

    @Test
    fun `직전 고점 대비 최대 낙폭을 계산한다`() {
        // 100 → 120(고점) → 90(낙폭 25%) → 150(신고점) : MDD = 0.25
        assertEquals(BigDecimal("0.250000"), curve(100, 120, 90, 150).maxDrawdown())
    }

    @Test
    fun `계속 상승하면 MDD는 0`() {
        assertEquals(BigDecimal.ZERO, curve(100, 110, 120, 130).maxDrawdown().stripTrailingZeros().let { if (it.signum() == 0) BigDecimal.ZERO else it })
    }

    @Test
    fun `빈 곡선은 MDD 0`() {
        assertEquals(BigDecimal.ZERO, EquityCurve(emptyList()).maxDrawdown())
    }
}
