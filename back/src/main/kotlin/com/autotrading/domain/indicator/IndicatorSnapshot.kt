package com.autotrading.domain.indicator

import java.math.BigDecimal

/**
 * 특정 시점의 지표값 묶음(불변). SignalLog의 indicator_snapshot(jsonb)에 그대로 기록한다.
 * 값은 [BigDecimal](원·지수). 키 예: "price", "sma5", "sma20", "rsi14".
 */
data class IndicatorSnapshot(val values: Map<String, BigDecimal>) {
    /** SignalLog 기록용 맵(jsonb). */
    fun asLogMap(): Map<String, Any> = values
}
