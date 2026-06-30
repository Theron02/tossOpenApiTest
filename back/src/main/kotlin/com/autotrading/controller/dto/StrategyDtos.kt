package com.autotrading.controller.dto

import com.autotrading.entity.StrategyConfig

data class StrategyResponse(
    val id: String,
    val strategyName: String,
    val stockCode: String,
    val params: Map<String, Any>,
    val enabled: Boolean,
)

/**
 * 전략 부분 변경(PATCH). null 필드는 변경하지 않는다.
 * [enabled]=true는 봇이 이 전략으로 **자동 주문을 시작**한다는 의미.
 */
data class StrategyUpdateRequest(
    val enabled: Boolean? = null,
    val params: Map<String, Any>? = null,
)

fun StrategyConfig.toResponse() = StrategyResponse(
    id = id.toString(),
    strategyName = strategyName,
    stockCode = stockCode,
    params = params,
    enabled = enabled,
)
