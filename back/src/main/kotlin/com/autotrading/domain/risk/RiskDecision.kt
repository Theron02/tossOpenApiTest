package com.autotrading.domain.risk

/** 리스크 차단 사유. */
enum class RejectReason {
    MISSING_RISK_SETTING,
    KILL_SWITCH,
    DUPLICATE_ORDER,
    DAILY_LOSS_LIMIT,
    INSUFFICIENT_BALANCE,
    POSITION_LIMIT_EXCEEDED,
    INSUFFICIENT_POSITION,
}

/**
 * 리스크 판정 결과(부수효과 없는 순수 값). 실행과 분리한다.
 * 차단 시 [Rejected.reason]으로 사유를 전달한다(감사 추적·SignalLog 기록은 호출측이 수행).
 */
sealed interface RiskDecision {
    data object Approved : RiskDecision
    data class Rejected(val reason: RejectReason, val detail: String) : RiskDecision

    val isApproved: Boolean get() = this is Approved
}
