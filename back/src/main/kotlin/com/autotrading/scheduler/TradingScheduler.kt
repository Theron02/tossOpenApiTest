package com.autotrading.scheduler

import com.autotrading.config.TradingProperties
import com.autotrading.domain.strategy.StrategyEngine
import com.autotrading.domain.type.CandleInterval
import com.autotrading.entity.StrategyConfig
import com.autotrading.repository.StrategyConfigRepository
import com.autotrading.service.CandleCollector
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 자동매매 판단 루프. 주기마다 활성 전략 종목의 캔들을 수집한 뒤 [StrategyEngine.evaluateEnabled]를
 * 호출한다(시세→지표→전략→신호→RiskManager→OrderExecutor 는 엔진이 수행).
 *
 * 안전:
 * - `trading.enabled=false`(기본)면 아무것도 하지 않는다. 명시적으로 켜야 돈다.
 * - 장 운영시간 밖이면 건너뛴다(`trading.ignore-market-hours=true`로 로컬 테스트 시 바이패스).
 * - 한 종목 수집 실패가 다른 종목/평가를 막지 않도록 격리한다.
 * - 주문 자체의 리스크 차단·모의/실거래 분리는 엔진·RiskManager·OrderExecutor 가 담당(여기서 우회 없음).
 */
@Component
class TradingScheduler(
    private val strategyConfigRepository: StrategyConfigRepository,
    private val candleCollector: CandleCollector,
    private val strategyEngine: StrategyEngine,
    private val props: TradingProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedDelayString = "\${trading.poll-interval-ms:60000}",
        initialDelayString = "\${trading.initial-delay-ms:10000}",
    )
    fun tick() {
        if (!props.enabled) return
        if (!props.ignoreMarketHours && !MarketHours.nowIsOpen()) {
            log.debug("장 운영시간 아님 — 루프 스킵")
            return
        }

        val configs = strategyConfigRepository.findByEnabledTrue()
        if (configs.isEmpty()) {
            log.debug("활성 전략 없음 — 루프 스킵")
            return
        }

        collectCandles(configs)
        strategyEngine.evaluateEnabled()
    }

    /** 활성 전략의 (종목, 주기) 조합별로 캔들을 1회씩 수집한다(중복 제거). */
    private fun collectCandles(configs: List<StrategyConfig>) {
        configs.map { it.stockCode to resolveInterval(it) }
            .distinct()
            .forEach { (stockCode, interval) ->
                runCatching { candleCollector.collect(stockCode, interval, props.candleCount) }
                    .onFailure { log.warn("캔들 수집 실패 stock={} interval={}: {}", stockCode, interval, it.message) }
            }
    }

    /** 엔진과 동일한 규칙으로 전략의 캔들 주기를 해석한다(params.candleInterval, 기본 DAY_1). */
    private fun resolveInterval(config: StrategyConfig): CandleInterval {
        val raw = config.params["candleInterval"] as? String
        return CandleInterval.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CandleInterval.DAY_1
    }
}
