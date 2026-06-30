package com.autotrading.domain.indicator

import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 지표 계산기. 종가 리스트(오래된→최신 순)를 입력으로 지표를 산출하는 **순수 함수**(부수효과 없음).
 * 모든 계산은 [BigDecimal]. Double 금지.
 */
@Component
class IndicatorCalculator {

    /** 단순이동평균(SMA). 마지막 [period]개 종가의 평균. 데이터가 부족하면 null. */
    fun sma(closes: List<BigDecimal>, period: Int): BigDecimal? {
        require(period > 0) { "period는 1 이상" }
        if (closes.size < period) return null
        val window = closes.subList(closes.size - period, closes.size)
        return window.reduce(BigDecimal::add).divide(BigDecimal(period), SCALE, RoundingMode.HALF_UP)
    }

    /**
     * RSI(상대강도지수). 마지막 [period]개 가격 변화 기준(단순 평균법).
     * 데이터가 period+1개 미만이면 null. 손실이 0이면 100.
     */
    fun rsi(closes: List<BigDecimal>, period: Int = 14): BigDecimal? {
        require(period > 0) { "period는 1 이상" }
        if (closes.size < period + 1) return null

        val recent = closes.subList(closes.size - period - 1, closes.size)
        var gain = BigDecimal.ZERO
        var loss = BigDecimal.ZERO
        for (i in 1 until recent.size) {
            val delta = recent[i].subtract(recent[i - 1])
            if (delta.signum() >= 0) gain = gain.add(delta) else loss = loss.add(delta.abs())
        }
        val avgGain = gain.divide(BigDecimal(period), SCALE, RoundingMode.HALF_UP)
        val avgLoss = loss.divide(BigDecimal(period), SCALE, RoundingMode.HALF_UP)

        if (avgLoss.signum() == 0) return BigDecimal(100).setScale(SCALE, RoundingMode.HALF_UP)
        val rs = avgGain.divide(avgLoss, SCALE, RoundingMode.HALF_UP)
        return BigDecimal(100).subtract(
            BigDecimal(100).divide(BigDecimal.ONE.add(rs), SCALE, RoundingMode.HALF_UP),
        ).setScale(SCALE, RoundingMode.HALF_UP)
    }

    companion object {
        const val SCALE = 4
    }
}
