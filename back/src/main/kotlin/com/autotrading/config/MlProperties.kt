package com.autotrading.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * ML 예측 서비스(Python) 연동 설정. 계약: ml/docs/ML_API_CONTRACT.md.
 * 내부 서비스라 인증 없음. base-url 은 환경변수로 주입(기본 로컬).
 *
 * @property buyThreshold  score(P(up)) 가 이 값 이상이면 BUY (전략 params 로 개별 오버라이드 가능)
 * @property sellThreshold score 가 이 값 이하이면 SELL
 * @property minCloses     예측 호출에 필요한 최소 종가 개수(피처 계산 하한). 미만이면 호출 없이 HOLD
 * @property maxCloses     전송할 최대 종가 개수(최근값 위주)
 */
@ConfigurationProperties(prefix = "ml")
data class MlProperties(
    val baseUrl: String = "http://localhost:8000",
    val connectTimeoutMs: Long = 1000,
    val readTimeoutMs: Long = 2000,
    val buyThreshold: Double = 0.6,
    val sellThreshold: Double = 0.4,
    val minCloses: Int = 21,
    val maxCloses: Int = 200,
)
