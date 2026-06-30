package com.autotrading.controller

import com.autotrading.common.ApiResponse
import com.autotrading.controller.dto.PortfolioResponse
import com.autotrading.controller.dto.PositionResponse
import com.autotrading.service.PortfolioService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 포트폴리오 요약·보유 포지션 조회. */
@RestController
@RequestMapping("/api/v1")
class PortfolioController(private val portfolioService: PortfolioService) {

    @GetMapping("/portfolio")
    fun portfolio(): ApiResponse<PortfolioResponse> = ApiResponse.ok(portfolioService.portfolio())

    @GetMapping("/positions")
    fun positions(): ApiResponse<List<PositionResponse>> = ApiResponse.ok(portfolioService.positions())
}
