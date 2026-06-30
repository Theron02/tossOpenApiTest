package com.autotrading.controller.dto

import com.autotrading.domain.backtest.BacktestResult
import jakarta.validation.constraints.NotBlank

/**
 * 백테스트 실행 요청. 영속 backtest_run 테이블이 없어 GET/{id} 대신 즉시 실행(POST)으로 결과를 반환한다.
 * 금액·율은 문자열로 받아 정밀도를 보존한다(서비스에서 BigDecimal 파싱).
 */
data class BacktestRunRequest(
    @field:NotBlank val symbol: String,
    @field:NotBlank val interval: String,
    @field:NotBlank val strategy: String,
    val params: Map<String, Any> = emptyMap(),
    @field:NotBlank val initialCapital: String,
    val commissionRate: String? = null,
    val taxRate: String? = null,
    val slippageRate: String? = null,
    val positionSizePct: Int? = null,
)

data class EquityPointDto(val time: String, val equity: String)

data class BacktestResultResponse(
    val initialCapital: String,
    val finalEquity: String,
    val totalReturn: String,
    val cagr: String,
    val maxDrawdown: String,
    val winRate: String,
    val profitFactor: String?,
    val totalTrades: Int,
    val closedTrades: Int,
    val totalCommission: String,
    val totalTax: String,
    val equityCurve: List<EquityPointDto>,
    val warnings: List<String>,
)

fun BacktestResult.toResponse() = BacktestResultResponse(
    initialCapital = initialCapital.toPlainString(),
    finalEquity = finalEquity.toPlainString(),
    totalReturn = totalReturn.toPlainString(),
    cagr = cagr.toPlainString(),
    maxDrawdown = maxDrawdown.toPlainString(),
    winRate = winRate.toPlainString(),
    profitFactor = profitFactor?.toPlainString(),
    totalTrades = totalTrades,
    closedTrades = closedTrades,
    totalCommission = totalCommission.toPlainString(),
    totalTax = totalTax.toPlainString(),
    equityCurve = equityCurve.points.map { EquityPointDto(it.time.toString(), it.equity.toPlainString()) },
    warnings = warnings,
)
