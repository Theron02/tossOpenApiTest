package com.autotrading.domain.strategy

import com.autotrading.config.MlProperties
import com.autotrading.external.ml.MlPredictionClient
import com.autotrading.external.ml.dto.MlPredictRequest
import com.autotrading.external.ml.dto.MlPredictResponse
import com.autotrading.domain.type.Signal
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

/**
 * MlStrategy 신호 매핑 검증. 예측 호출은 MockK로 격리한다.
 * score(P(up)) → 임계값(기본 buy 0.6 / sell 0.4) → BUY/SELL/HOLD, 그리고 실패·열화·부족 시 HOLD.
 */
class MlStrategyTest {

    private val client = mockk<MlPredictionClient>()
    private val props = MlProperties() // 기본값: buy 0.6, sell 0.4, minCloses 21
    private val strategy = MlStrategy(client, props)

    private fun closes(n: Int = 30): List<BigDecimal> =
        (1..n).map { BigDecimal(10_000 + it) }

    private fun ctx(closes: List<BigDecimal>, params: Map<String, Any> = emptyMap()) =
        MarketContext("005930", closes, closes.last(), null, params)

    private fun resp(score: Double, degraded: Boolean = false) =
        MlPredictResponse("005930", "HOLD", score, "test-model", 9, degraded)

    @Test
    fun `score가 buyThreshold 이상이면 BUY`() {
        every { client.predict(any()) } returns resp(0.72)
        assertEquals(Signal.BUY, strategy.evaluate(ctx(closes())))
    }

    @Test
    fun `score가 sellThreshold 이하이면 SELL`() {
        every { client.predict(any()) } returns resp(0.30)
        assertEquals(Signal.SELL, strategy.evaluate(ctx(closes())))
    }

    @Test
    fun `중간 score면 HOLD`() {
        every { client.predict(any()) } returns resp(0.5)
        assertEquals(Signal.HOLD, strategy.evaluate(ctx(closes())))
    }

    @Test
    fun `params로 임계값을 오버라이드한다`() {
        every { client.predict(any()) } returns resp(0.55)
        // buyThreshold 0.5 로 낮추면 0.55 는 BUY
        val params = mapOf("buyThreshold" to 0.5, "sellThreshold" to 0.2)
        assertEquals(Signal.BUY, strategy.evaluate(ctx(closes(), params)))
    }

    @Test
    fun `degraded 응답이면 HOLD`() {
        every { client.predict(any()) } returns resp(0.9, degraded = true)
        assertEquals(Signal.HOLD, strategy.evaluate(ctx(closes())))
    }

    @Test
    fun `예측 호출이 실패하면 HOLD로 안전 처리`() {
        every { client.predict(any()) } throws RuntimeException("connection refused")
        assertEquals(Signal.HOLD, strategy.evaluate(ctx(closes())))
    }

    @Test
    fun `종가가 부족하면 호출 없이 HOLD`() {
        val result = strategy.evaluate(ctx(closes(10))) // minCloses(21) 미만
        assertEquals(Signal.HOLD, result)
        verify(exactly = 0) { client.predict(any()) }
    }
}
