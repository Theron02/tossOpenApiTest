package com.autotrading.domain.backtest

import com.autotrading.domain.type.CandleInterval
import java.math.BigDecimal

/**
 * 백테스트 입력. 수수료·세금·슬리피지는 결과 현실성에 직결되므로 명시적으로 받는다(미반영 시 과대평가).
 *
 * 기본 수수료/세금율은 **예시 근사값**이다. 실제 증권사·연도별 요율로 반드시 덮어쓴다.
 * (국내 매도세는 거래세로 매도 시에만 부과, 매수엔 없음.)
 *
 * @property commissionRate 체결 대금 대비 수수료율(매수·매도 양쪽). 예: 0.00015 = 0.015%.
 * @property taxRate 매도 거래세율(매도에만). 예: 0.0018 = 0.18%.
 * @property slippageRate 체결가 불리 보정율(매수 +, 매도 −). 기본 0.
 * @property positionSizePct 1회 매수 시 가용 현금의 몇 %를 투입할지(1~100). 기본 100.
 */
data class BacktestRequest(
    val stockCode: String,
    val interval: CandleInterval,
    val strategyName: String,
    val params: Map<String, Any> = emptyMap(),
    val initialCapital: BigDecimal,
    val commissionRate: BigDecimal = BigDecimal("0.00015"),
    val taxRate: BigDecimal = BigDecimal("0.0018"),
    val slippageRate: BigDecimal = BigDecimal.ZERO,
    val positionSizePct: Int = 100,
) {
    init {
        require(initialCapital > BigDecimal.ZERO) { "초기 자본은 0보다 커야 한다" }
        require(positionSizePct in 1..100) { "positionSizePct는 1..100" }
        require(commissionRate >= BigDecimal.ZERO && taxRate >= BigDecimal.ZERO && slippageRate >= BigDecimal.ZERO) {
            "수수료·세금·슬리피지율은 음수일 수 없다"
        }
    }
}
