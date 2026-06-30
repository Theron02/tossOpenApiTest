package com.autotrading.domain.strategy

import com.autotrading.domain.type.Signal

/**
 * 매매 전략. 규칙 기반으로 시작하고, 추후 ML 전략도 같은 인터페이스로 끼운다.
 * **엔진/스케줄러를 고치지 않고** 구현체 추가만으로 새 전략을 붙일 수 있어야 한다(전략 패턴).
 */
interface TradingStrategy {
    /** 전략 식별자. StrategyConfig.strategyName과 매칭된다(예: "GOLDEN_CROSS", "RSI"). */
    val name: String

    /** 시장 맥락을 평가해 매매 신호를 산출한다. */
    fun evaluate(context: MarketContext): Signal
}
