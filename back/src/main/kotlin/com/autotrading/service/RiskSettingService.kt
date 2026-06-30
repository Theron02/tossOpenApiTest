package com.autotrading.service

import com.autotrading.common.ConflictException
import com.autotrading.common.ResourceNotFoundException
import com.autotrading.controller.dto.KillSwitchRequest
import com.autotrading.controller.dto.RiskSettingResponse
import com.autotrading.controller.dto.RiskSettingUpdateRequest
import com.autotrading.controller.dto.toResponse
import com.autotrading.entity.RiskSetting
import com.autotrading.repository.RiskSettingRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 리스크 설정 조회·변경 + kill switch. 제어 동작은 감사 로그를 남긴다. */
@Service
class RiskSettingService(
    private val accountResolver: AccountResolver,
    private val riskSettingRepository: RiskSettingRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun get(): RiskSettingResponse = current().toResponse()

    @Transactional
    fun update(request: RiskSettingUpdateRequest): RiskSettingResponse {
        val rs = current()
        request.dailyLossLimit?.let {
            require(it >= 0) { "dailyLossLimit는 음수일 수 없다" }
            rs.dailyLossLimit = it
        }
        request.maxPositionPct?.let {
            require(it in 1..100) { "maxPositionPct는 1..100" }
            rs.maxPositionPct = it
        }
        riskSettingRepository.save(rs)
        log.info("리스크 설정 변경 audit: account={} dailyLossLimit={} maxPositionPct={}", rs.account.id, rs.dailyLossLimit, rs.maxPositionPct)
        return rs.toResponse()
    }

    @Transactional
    fun killSwitch(request: KillSwitchRequest): RiskSettingResponse {
        if (!request.confirm) {
            throw ConflictException("kill switch는 위험 동작입니다. confirm=true가 필요합니다")
        }
        val rs = current()
        if (request.enabled) rs.activateKillSwitch() else rs.deactivateKillSwitch()
        riskSettingRepository.save(rs)
        log.warn("kill switch audit: account={} enabled={} (신규 주문 {})", rs.account.id, request.enabled, if (request.enabled) "전면 차단" else "재개")
        return rs.toResponse()
    }

    private fun current(): RiskSetting {
        val accountId = accountResolver.resolve().id
        return riskSettingRepository.findByAccountId(accountId)
            ?: throw ResourceNotFoundException("리스크 설정이 없습니다(account=$accountId)")
    }
}
