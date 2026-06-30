package com.autotrading.domain.backtest

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/** 평가자산 시계열의 한 점(봉 종가 기준 mark-to-market). */
data class EquityPoint(val time: Instant, val equity: BigDecimal)

/** 시점별 평가자산 곡선. MDD(최대 낙폭) 산출의 근거. */
data class EquityCurve(val points: List<EquityPoint>) {

    /**
     * 최대 낙폭(Max Drawdown). 직전 고점 대비 최대 하락 비율(0..1).
     * peak를 갱신하며 (peak - equity)/peak의 최댓값을 찾는다.
     */
    fun maxDrawdown(): BigDecimal {
        if (points.isEmpty()) return BigDecimal.ZERO
        var peak = points.first().equity
        var mdd = BigDecimal.ZERO
        for (p in points) {
            if (p.equity > peak) peak = p.equity
            if (peak > BigDecimal.ZERO) {
                val dd = peak.subtract(p.equity).divide(peak, 6, RoundingMode.HALF_UP)
                if (dd > mdd) mdd = dd
            }
        }
        return mdd
    }
}
