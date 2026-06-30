package com.autotrading.controller

import com.autotrading.common.ApiResponse
import com.autotrading.controller.dto.KillSwitchRequest
import com.autotrading.controller.dto.RiskSettingResponse
import com.autotrading.controller.dto.RiskSettingUpdateRequest
import com.autotrading.service.RiskSettingService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 리스크 설정 조회·변경 + kill switch(위험 동작, confirm 필수). */
@RestController
@RequestMapping("/api/v1/risk-setting")
class RiskSettingController(private val riskSettingService: RiskSettingService) {

    @GetMapping
    fun get(): ApiResponse<RiskSettingResponse> = ApiResponse.ok(riskSettingService.get())

    @PatchMapping
    fun update(@Valid @RequestBody request: RiskSettingUpdateRequest): ApiResponse<RiskSettingResponse> =
        ApiResponse.ok(riskSettingService.update(request))

    @PostMapping("/kill-switch")
    fun killSwitch(@RequestBody request: KillSwitchRequest): ApiResponse<RiskSettingResponse> =
        ApiResponse.ok(riskSettingService.killSwitch(request))
}
