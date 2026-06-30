package com.autotrading.controller

import com.autotrading.common.ApiResponse
import com.autotrading.controller.dto.ExecutionResponse
import com.autotrading.controller.dto.OrderResponse
import com.autotrading.domain.type.OrderStatus
import com.autotrading.service.HistoryQueryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 주문·체결 내역 조회(최신순, 페이지네이션). */
@RestController
@RequestMapping("/api/v1")
class OrderController(private val historyQueryService: HistoryQueryService) {

    @GetMapping("/orders")
    fun orders(
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<List<OrderResponse>> {
        val parsed = status?.let { OrderStatus.valueOf(it.uppercase()) } // 잘못된 값 → 400(advice)
        return ApiResponse.ok(historyQueryService.orders(parsed, page, size))
    }

    @GetMapping("/executions")
    fun executions(@RequestParam(defaultValue = "50") limit: Int): ApiResponse<List<ExecutionResponse>> =
        ApiResponse.ok(historyQueryService.executions(limit))
}
