package com.autotrading.controller

import com.autotrading.common.ApiResponse
import com.autotrading.controller.dto.StrategyResponse
import com.autotrading.controller.dto.StrategyUpdateRequest
import com.autotrading.service.StrategyService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** 전략 설정 조회·변경. PATCH enabled=true는 자동 주문 시작을 의미. */
@RestController
@RequestMapping("/api/v1/strategies")
class StrategyController(private val strategyService: StrategyService) {

    @GetMapping
    fun list(): ApiResponse<List<StrategyResponse>> = ApiResponse.ok(strategyService.list())

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: StrategyUpdateRequest,
    ): ApiResponse<StrategyResponse> = ApiResponse.ok(strategyService.update(id, request))
}
