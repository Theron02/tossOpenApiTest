package com.autotrading.external.ml

import com.autotrading.external.ml.dto.MlPredictRequest
import com.autotrading.external.ml.dto.MlPredictResponse
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/** ML 예측 서비스 호출 클라이언트. 실패 시 예외를 던지며, 호출부(MlStrategy)가 HOLD 로 안전 처리한다. */
@Component
class MlPredictionClient(
    private val mlRestClient: RestClient,
) {
    fun predict(request: MlPredictRequest): MlPredictResponse =
        mlRestClient.post()
            .uri("/predict")
            .body(request)
            .retrieve()
            .body(MlPredictResponse::class.java)
            ?: throw IllegalStateException("ML 예측 응답이 비어 있음")
}
