package com.autotrading.domain.strategy

import com.autotrading.config.MlProperties
import com.autotrading.domain.type.Signal
import com.autotrading.external.ml.MlPredictionClient
import com.autotrading.external.ml.dto.MlPredictRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * ML 예측 전략. Python 서비스가 낸 상승확률(score=P(up))로 신호를 결정한다.
 * 다른 규칙 전략과 **동일한 [TradingStrategy] 인터페이스**로 엔진에 붙으며,
 * 산출 신호는 규칙 전략과 똑같이 엔진 → RiskManager → OrderExecutor 를 거친다(우회 없음).
 *
 * 결정은 백엔드가 갖는다: score >= buyThreshold → BUY, score <= sellThreshold → SELL, 그 외 HOLD.
 * 임계값은 전략 params(`buyThreshold`/`sellThreshold`)로 종목별 오버라이드 가능.
 *
 * 안전: 히스토리 부족·예측 실패(네트워크·degraded)는 모두 HOLD 로 안전 처리한다.
 */
@Component
class MlStrategy(
    private val client: MlPredictionClient,
    private val props: MlProperties,
) : TradingStrategy {

    private val log = LoggerFactory.getLogger(javaClass)

    override val name = "ML"

    override fun evaluate(context: MarketContext): Signal {
        if (context.closes.size < props.minCloses) return Signal.HOLD

        val closes = context.closes.takeLast(props.maxCloses).map { it.toPlainString() }
        val request = MlPredictRequest(
            symbol = context.stockCode,
            closes = closes,
            holdingQuantity = context.position?.quantity,
        )

        val response = runCatching { client.predict(request) }
            .getOrElse { e ->
                log.warn("ML 예측 호출 실패 — HOLD 처리 stock={}: {}", context.stockCode, e.message)
                return Signal.HOLD
            }

        // 모델 미로드·히스토리 부족 등 열화 응답은 신호를 내지 않는다.
        if (response.degraded) {
            log.info("ML 예측 degraded — HOLD stock={} ver={}", context.stockCode, response.modelVersion)
            return Signal.HOLD
        }

        val buyThreshold = context.doubleParam("buyThreshold", props.buyThreshold)
        val sellThreshold = context.doubleParam("sellThreshold", props.sellThreshold)
        return when {
            response.score >= buyThreshold -> Signal.BUY
            response.score <= sellThreshold -> Signal.SELL
            else -> Signal.HOLD
        }
    }
}

/** params 에서 Double 파라미터를 읽는다(숫자/문자 허용). 없으면 [default]. */
private fun MarketContext.doubleParam(key: String, default: Double): Double = when (val v = params[key]) {
    is Number -> v.toDouble()
    is String -> v.toDoubleOrNull() ?: default
    else -> default
}
