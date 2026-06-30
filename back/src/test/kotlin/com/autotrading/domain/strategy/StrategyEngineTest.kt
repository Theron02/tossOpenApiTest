package com.autotrading.domain.strategy

import com.autotrading.domain.indicator.IndicatorCalculator
import com.autotrading.domain.order.OrderCommand
import com.autotrading.domain.order.OrderExecutor
import com.autotrading.domain.risk.RejectReason
import com.autotrading.domain.risk.RiskRejectedException
import com.autotrading.domain.type.CandleInterval
import com.autotrading.domain.type.Market
import com.autotrading.domain.type.OrderSide
import com.autotrading.domain.type.OrderType
import com.autotrading.domain.type.Signal
import com.autotrading.entity.Candle
import com.autotrading.entity.PaperAccount
import com.autotrading.entity.SignalLog
import com.autotrading.entity.StrategyConfig
import com.autotrading.repository.CandleRepository
import com.autotrading.repository.PositionRepository
import com.autotrading.repository.SignalLogRepository
import com.autotrading.repository.StrategyConfigRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

/**
 * StrategyEngine 단위 테스트. 실제 전략·지표 + Mock 저장소/실행기로,
 * 신호 산출 → SignalLog 기록 → BUY/SELL의 주문 위임을 검증한다.
 */
class StrategyEngineTest {

    private val candleRepository = mockk<CandleRepository>()
    private val positionRepository = mockk<PositionRepository>(relaxed = true)
    private val signalLogRepository = mockk<SignalLogRepository>()
    private val strategyConfigRepository = mockk<StrategyConfigRepository>(relaxed = true)
    private val orderExecutor = mockk<OrderExecutor>(relaxed = true)

    private val indicators = IndicatorCalculator()
    private val engine = StrategyEngine(
        strategies = listOf(GoldenCrossStrategy(indicators)),
        indicators = indicators,
        candleRepository = candleRepository,
        positionRepository = positionRepository,
        signalLogRepository = signalLogRepository,
        strategyConfigRepository = strategyConfigRepository,
        orderExecutor = orderExecutor,
    )

    private val code = "005930"
    private val account = PaperAccount(name = "t", cashBalance = 10_000_000, initialSeed = 10_000_000)

    private fun config() = StrategyConfig(
        account = account, strategyName = "GOLDEN_CROSS", stockCode = code,
        params = mapOf("shortPeriod" to 2, "longPeriod" to 3),
    )

    /** 종가 리스트(오래된→최신)를 받아 candleTime 내림차순(newest-first) 캔들 리스트로 만든다. */
    private fun candlesDesc(closesOldToNew: List<Long>): List<Candle> {
        val base = Instant.parse("2026-06-01T00:00:00Z")
        return closesOldToNew.mapIndexed { i, close ->
            Candle(
                stockCode = code, candleInterval = CandleInterval.DAY_1,
                open = close, high = close, low = close, close = close, volume = 1,
                candleTime = base.plusSeconds(i * 86_400L),
            )
        }.reversed() // newest-first
    }

    private fun stubCandles(closesOldToNew: List<Long>) {
        every {
            candleRepository.findByStockCodeAndCandleIntervalOrderByCandleTimeDesc(code, CandleInterval.DAY_1, any())
        } returns candlesDesc(closesOldToNew)
        every { positionRepository.findByAccountIdAndStockCode(account.id, code) } returns null
        every { signalLogRepository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `골든크로스면 BUY 신호를 기록하고 시장가 매수 주문을 위임한다`() {
        stubCandles(listOf(10, 10, 10, 13)) // 상향 돌파

        val savedLog = slot<SignalLog>()
        val command = slot<OrderCommand>()
        every { signalLogRepository.save(capture(savedLog)) } answers { firstArg() }
        every { orderExecutor.execute(capture(command)) } returns mockk(relaxed = true)

        val signal = engine.evaluate(config())

        assertEquals(Signal.BUY, signal)
        assertEquals(Signal.BUY, savedLog.captured.signal)
        assertEquals(OrderSide.BUY, command.captured.side)
        assertEquals(OrderType.MARKET, command.captured.orderType)
        assertEquals(1, command.captured.quantity) // 기본 orderQuantity
    }

    @Test
    fun `HOLD면 SignalLog만 남기고 주문하지 않는다`() {
        stubCandles(listOf(10, 11, 12, 13)) // 추세 지속, 돌파 없음

        val signal = engine.evaluate(config())

        assertEquals(Signal.HOLD, signal)
        verify(exactly = 1) { signalLogRepository.save(any()) }
        verify(exactly = 0) { orderExecutor.execute(any()) }
    }

    @Test
    fun `리스크로 거부돼도 예외를 전파하지 않고 SignalLog는 남는다`() {
        stubCandles(listOf(10, 10, 10, 13))
        every { orderExecutor.execute(any()) } throws RiskRejectedException(RejectReason.KILL_SWITCH, "kill")

        val signal = engine.evaluate(config()) // 예외 없이 반환되어야 함

        assertEquals(Signal.BUY, signal)
        verify(exactly = 1) { signalLogRepository.save(any()) }
    }
}
