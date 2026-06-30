package com.autotrading.domain.strategy

import com.autotrading.domain.indicator.IndicatorCalculator
import com.autotrading.domain.type.Signal
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * RSI 전략. RSI ≤ 과매도 임계면 BUY, RSI ≥ 과매수 임계면 SELL, 그 외 HOLD.
 * 파라미터: `period`(기본 14), `oversold`(기본 30), `overbought`(기본 70).
 */
@Component
class RsiStrategy(
    private val indicators: IndicatorCalculator,
) : TradingStrategy {

    override val name = "RSI"

    override fun evaluate(context: MarketContext): Signal {
        val period = context.intParam("period", 14)
        val oversold = BigDecimal(context.intParam("oversold", 30))
        val overbought = BigDecimal(context.intParam("overbought", 70))

        val rsi = indicators.rsi(context.closes, period) ?: return Signal.HOLD
        return when {
            rsi <= oversold -> Signal.BUY
            rsi >= overbought -> Signal.SELL
            else -> Signal.HOLD
        }
    }
}
