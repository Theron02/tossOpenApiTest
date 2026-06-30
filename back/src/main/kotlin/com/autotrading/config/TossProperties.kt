package com.autotrading.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 토스증권 Open API 설정. 비밀값([clientId]/[clientSecret])은 환경변수로만 주입한다.
 * (application.yml의 toss.* → 환경변수 참조)
 */
@ConfigurationProperties(prefix = "toss")
data class TossProperties(
    val baseUrl: String,
    val clientId: String,
    val clientSecret: String,
    /** 토큰 만료 임박 판단 여유(초). 잔여가 이 값 이하면 사전 재발급. */
    val tokenRefreshSkewSeconds: Long = 60,
    val connectTimeoutMs: Long = 5000,
    val readTimeoutMs: Long = 10000,
)
