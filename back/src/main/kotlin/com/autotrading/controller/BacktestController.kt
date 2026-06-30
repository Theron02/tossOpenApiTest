package com.autotrading.controller

import com.autotrading.common.ApiResponse
import com.autotrading.controller.dto.BacktestResultResponse
import com.autotrading.controller.dto.BacktestRunRequest
import com.autotrading.controller.dto.toResponse
import com.autotrading.domain.backtest.BacktestRequest
import com.autotrading.domain.type.CandleInterval
import com.autotrading.service.BacktestService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

/**
 * 백테스트 실행. backtest_run 영속 테이블이 없어 GET/{id} 대신 즉시 실행(POST)으로 결과를 반환한다.
 * 백테스트는 실주문과 완전히 격리된 경로다(토스 주문 호출 없음).
 */
@RestController
@RequestMapping("/api/v1/backtests")
class BacktestController(private val backtestService: BacktestService) {

    @PostMapping
    fun run(@Valid @RequestBody request: BacktestRunRequest): ApiResponse<BacktestResultResponse> {
        val result = backtestService.run(request.toDomain())
        return ApiResponse.ok(result.toResponse())
    }

    private fun BacktestRunRequest.toDomain(): BacktestRequest {
        var req = BacktestRequest(
            stockCode = symbol,
            interval = CandleInterval.valueOf(interval.uppercase()), // 잘못된 값 → 400(advice)
            strategyName = strategy,
            params = params,
            initialCapital = BigDecimal(initialCapital),
        )
        commissionRate?.let { req = req.copy(commissionRate = BigDecimal(it)) }
        taxRate?.let { req = req.copy(taxRate = BigDecimal(it)) }
        slippageRate?.let { req = req.copy(slippageRate = BigDecimal(it)) }
        positionSizePct?.let { req = req.copy(positionSizePct = it) }
        return req
    }
}
