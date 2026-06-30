package com.autotrading.domain.strategy

import com.autotrading.domain.indicator.IndicatorCalculator
import com.autotrading.domain.type.Signal
import org.springframework.stereotype.Component

/**
 * 골든크로스 전략. 단기 SMA가 장기 SMA를 **상향 돌파**하면 BUY, **하향 돌파**(데드크로스)면 SELL.
 * 파라미터: `shortPeriod`(기본 5), `longPeriod`(기본 20).
 *
 * 돌파 판정은 직전 봉과 현재 봉의 단기·장기 SMA 대소관계 변화를 본다.
 */
@Component
class GoldenCrossStrategy(
    private val indicators: IndicatorCalculator,
) : TradingStrategy {

    override val name = "GOLDEN_CROSS"

    override fun evaluate(context: MarketContext): Signal {
        val short = context.intParam("shortPeriod", 5)
        val long = context.intParam("longPeriod", 20)
        if (short >= long) return Signal.HOLD

        val closes = context.closes
        // 직전 봉 기준 SMA를 구하려면 long+1개가 필요하다.
        if (closes.size < long + 1) return Signal.HOLD
        val prevCloses = closes.subList(0, closes.size - 1)

        val shortNow = indicators.sma(closes, short) ?: return Signal.HOLD
        val longNow = indicators.sma(closes, long) ?: return Signal.HOLD
        val shortPrev = indicators.sma(prevCloses, short) ?: return Signal.HOLD
        val longPrev = indicators.sma(prevCloses, long) ?: return Signal.HOLD

        val crossedUp = shortPrev <= longPrev && shortNow > longNow
        val crossedDown = shortPrev >= longPrev && shortNow < longNow
        return when {
            crossedUp -> Signal.BUY
            crossedDown -> Signal.SELL
            else -> Signal.HOLD
        }
    }
}
