package com.autotrading.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 자동매매 루프(스케줄러) 설정.
 *
 * @property enabled          마스터 스위치. **기본 false** — 명시적으로 켜야 자동 루프가 돈다(안전 우선).
 * @property ignoreMarketHours true면 장 운영시간을 무시하고 항상 실행(로컬 테스트용).
 * @property pollIntervalMs   루프 주기(ms). 이전 실행 종료 후 이 간격만큼 대기(fixedDelay).
 * @property initialDelayMs   앱 기동 후 첫 실행까지 지연(ms).
 * @property candleCount      매 루프마다 종목별로 수집할 캔들 개수.
 */
@ConfigurationProperties(prefix = "trading")
data class TradingProperties(
    val enabled: Boolean = false,
    val ignoreMarketHours: Boolean = false,
    val pollIntervalMs: Long = 60_000,
    val initialDelayMs: Long = 10_000,
    val candleCount: Int = 200,
    /** true면 기동 시 데모 데이터(계좌·리스크설정·활성 전략)를 시드한다(로컬 테스트용, 기본 false). */
    val seedDemo: Boolean = false,
)
