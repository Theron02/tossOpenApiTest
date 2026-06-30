package com.autotrading.controller.dto

import com.autotrading.entity.RiskSetting
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Max

data class RiskSettingResponse(
    val id: String,
    val accountId: String,
    val dailyLossLimit: String,
    val maxPositionPct: Int,
    val killSwitch: Boolean,
)

/** 리스크 설정 변경(PATCH). null은 미변경. 범위 검증 필수. */
data class RiskSettingUpdateRequest(
    @field:Min(0) val dailyLossLimit: Long? = null,
    @field:Min(1) @field:Max(100) val maxPositionPct: Int? = null,
)

/**
 * kill switch 토글. **위험 동작** — [confirm]=true가 아니면 거부한다(오작동 방지).
 * on이면 모든 신규 주문이 즉시 차단된다.
 */
data class KillSwitchRequest(
    val enabled: Boolean,
    val confirm: Boolean = false,
)

fun RiskSetting.toResponse() = RiskSettingResponse(
    id = id.toString(),
    accountId = account.id.toString(),
    dailyLossLimit = dailyLossLimit.toString(),
    maxPositionPct = maxPositionPct,
    killSwitch = killSwitch,
)
