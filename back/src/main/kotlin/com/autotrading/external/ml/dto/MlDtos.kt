package com.autotrading.external.ml.dto

/**
 * ML 예측 서비스 요청/응답 DTO. 계약: ml/docs/ML_API_CONTRACT.md.
 * 가격은 문자열(decimal)로 전달해 정밀도를 보존한다.
 */
data class MlPredictRequest(
    val symbol: String,
    /** 오래된→최신 종가(decimal 문자열). */
    val closes: List<String>,
    val holdingQuantity: Int?,
)

data class MlPredictResponse(
    val symbol: String,
    /** 서비스 편의 신호(BUY/SELL/HOLD). 최종 결정은 백엔드가 score 로 한다. */
    val signal: String,
    /** 상승 확률 P(up) 0..1. 주값. */
    val score: Double,
    val modelVersion: String,
    val featuresUsed: Int,
    /** 모델 미로드·히스토리 부족 등으로 안전 기본값(HOLD/0.5)을 반환했는지. */
    val degraded: Boolean = false,
)
