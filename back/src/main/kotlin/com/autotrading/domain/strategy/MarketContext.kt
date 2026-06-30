package com.autotrading.domain.strategy

import com.autotrading.entity.Position
import java.math.BigDecimal

/**
 * 전략 평가 입력(불변). 한 종목·한 시점의 판단에 필요한 모든 것을 담는다.
 *
 * @property closes 종가 시계열(오래된→최신 순). 지표 계산용.
 * @property currentPrice 최신 종가(현재가 근사).
 * @property position 현재 보유 포지션(없으면 null).
 * @property params 전략 파라미터(StrategyConfig.params). 키는 전략별 정의.
 */
data class MarketContext(
    val stockCode: String,
    val closes: List<BigDecimal>,
    val currentPrice: BigDecimal,
    val position: Position?,
    val params: Map<String, Any>,
) {
    /** params에서 Int 파라미터를 읽는다(숫자/문자 모두 허용). 없으면 [default]. */
    fun intParam(key: String, default: Int): Int = when (val v = params[key]) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull() ?: default
        else -> default
    }
}
