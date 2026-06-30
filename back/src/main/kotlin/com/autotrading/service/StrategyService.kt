package com.autotrading.service

import com.autotrading.common.ResourceNotFoundException
import com.autotrading.controller.dto.StrategyResponse
import com.autotrading.controller.dto.StrategyUpdateRequest
import com.autotrading.controller.dto.toResponse
import com.autotrading.repository.StrategyConfigRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 전략 설정 조회·변경. enabled=true 전환은 봇이 그 전략으로 자동 주문을 시작함을 의미하므로 감사 로그를 남긴다.
 */
@Service
class StrategyService(
    private val accountResolver: AccountResolver,
    private val strategyConfigRepository: StrategyConfigRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun list(): List<StrategyResponse> =
        strategyConfigRepository.findByAccountId(accountResolver.resolve().id).map { it.toResponse() }

    @Transactional
    fun update(id: UUID, request: StrategyUpdateRequest): StrategyResponse {
        val config = strategyConfigRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("전략 설정 없음: $id") }

        request.params?.let { config.params = it }
        request.enabled?.let {
            if (it) config.enable() else config.disable()
            log.info("전략 토글 audit: id={} stock={} strategy={} enabled={}", id, config.stockCode, config.strategyName, it)
        }
        strategyConfigRepository.save(config)
        return config.toResponse()
    }
}
