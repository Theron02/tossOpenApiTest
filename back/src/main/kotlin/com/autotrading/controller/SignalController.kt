package com.autotrading.controller

import com.autotrading.common.ApiResponse
import com.autotrading.controller.dto.SignalResponse
import com.autotrading.service.HistoryQueryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 신호 로그 조회(추적성). 최신순 페이지네이션. */
@RestController
@RequestMapping("/api/v1")
class SignalController(private val historyQueryService: HistoryQueryService) {

    @GetMapping("/signals")
    fun signals(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<List<SignalResponse>> = ApiResponse.ok(historyQueryService.signals(page, size))
}
