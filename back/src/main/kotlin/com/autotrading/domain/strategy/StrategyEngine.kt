package com.autotrading.domain.strategy

import com.autotrading.domain.indicator.IndicatorCalculator
import com.autotrading.domain.indicator.IndicatorSnapshot
import com.autotrading.domain.order.OrderCommand
import com.autotrading.domain.order.OrderExecutor
import com.autotrading.domain.risk.RiskRejectedException
import com.autotrading.domain.type.CandleInterval
import com.autotrading.domain.type.OrderSide
import com.autotrading.domain.type.OrderType
import com.autotrading.domain.type.Signal
import com.autotrading.entity.SignalLog
import com.autotrading.entity.StrategyConfig
import com.autotrading.repository.CandleRepository
import com.autotrading.repository.PositionRepository
import com.autotrading.repository.SignalLogRepository
import com.autotrading.repository.StrategyConfigRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * 전략 엔진. 활성 전략(`StrategyConfig.enabled=true`)을 평가해 신호를 산출하고,
 * 신호마다 **SignalLog를 기록**(추적성)한 뒤 BUY/SELL은 [OrderExecutor]로 넘긴다(RiskManager 경유).
 *
 * 새 전략은 [TradingStrategy] 구현 추가만으로 붙는다(엔진 수정 불필요).
 *
 * 트랜잭션 주의: 메서드 단위 트랜잭션을 두지 않는다. SignalLog 저장과 주문 실행을 **분리된 트랜잭션**으로
 * 두어, 주문이 리스크로 거부(rollback)돼도 SignalLog는 남도록 한다("신호는 버리되 로그는 남긴다").
 */
@Service
class StrategyEngine(
    strategies: List<TradingStrategy>,
    private val indicators: IndicatorCalculator,
    private val candleRepository: CandleRepository,
    private val positionRepository: PositionRepository,
    private val signalLogRepository: SignalLogRepository,
    private val strategyConfigRepository: StrategyConfigRepository,
    private val orderExecutor: OrderExecutor,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val strategyByName: Map<String, TradingStrategy> = strategies.associateBy { it.name }

    /** 활성 전략 전체를 평가한다. 한 전략 실패가 다른 전략을 막지 않도록 격리한다. */
    fun evaluateEnabled() {
        strategyConfigRepository.findByEnabledTrue().forEach { config ->
            runCatching { evaluate(config) }
                .onFailure { log.error("전략 평가 실패 config={} stock={}: {}", config.id, config.stockCode, it.message, it) }
        }
    }

    /** 단일 전략 설정을 평가하고 신호를 반환한다. 신호는 SignalLog로 기록하고 BUY/SELL은 주문으로 넘긴다. */
    fun evaluate(config: StrategyConfig): Signal {
        val strategy = strategyByName[config.strategyName]
        if (strategy == null) {
            log.warn("미등록 전략: {} (config={})", config.strategyName, config.id)
            return Signal.HOLD
        }

        val interval = resolveInterval(config)
        val candlesDesc = candleRepository.findByStockCodeAndCandleIntervalOrderByCandleTimeDesc(
            config.stockCode, interval, PageRequest.of(0, CANDLE_LIMIT),
        )
        if (candlesDesc.isEmpty()) {
            log.warn("캔들 없음 — 평가 스킵. stock={} interval={}", config.stockCode, interval)
            return Signal.HOLD
        }

        val closes = candlesDesc.asReversed().map { BigDecimal(it.close) } // 오래된→최신
        val currentPrice = closes.last()
        val position = positionRepository.findByAccountIdAndStockCode(config.account.id, config.stockCode)

        val context = MarketContext(config.stockCode, closes, currentPrice, position, config.params)
        val signal = strategy.evaluate(context)

        val snapshot = buildSnapshot(closes, currentPrice)
        val signalLog = signalLogRepository.save(
            SignalLog(strategy = config, stockCode = config.stockCode, signal = signal, indicatorSnapshot = snapshot.asLogMap()),
        )
        log.info("신호 산출 strategy={} stock={} signal={} snapshot={}", config.strategyName, config.stockCode, signal, snapshot.values)

        if (signal == Signal.BUY || signal == Signal.SELL) {
            placeOrder(config, signal, position?.quantity ?: 0, candlesDesc.first().candleTime.epochSecond, signalLog.id)
        }
        return signal
    }

    private fun placeOrder(config: StrategyConfig, signal: Signal, heldQty: Int, candleEpoch: Long, signalId: java.util.UUID) {
        val side = if (signal == Signal.BUY) OrderSide.BUY else OrderSide.SELL
        val quantity = if (signal == Signal.BUY) buyQuantity(config) else heldQty
        if (quantity <= 0) {
            log.info("주문 스킵(수량 0) stock={} signal={}", config.stockCode, signal)
            return
        }

        val command = OrderCommand(
            accountId = config.account.id,
            stockCode = config.stockCode,
            side = side,
            orderType = OrderType.MARKET,
            quantity = quantity,
            idempotencyKey = "${config.id}:$candleEpoch:$signal",
            signalId = signalId,
        )
        runCatching { orderExecutor.execute(command) }
            .onFailure { e ->
                if (e is RiskRejectedException) {
                    log.warn("리스크 차단 — 신호 폐기 stock={} reason={}", config.stockCode, e.reason)
                } else {
                    throw e
                }
            }
    }

    private fun buyQuantity(config: StrategyConfig): Int =
        (config.params["orderQuantity"] as? Number)?.toInt()
            ?: (config.params["orderQuantity"] as? String)?.toIntOrNull()
            ?: DEFAULT_BUY_QTY

    private fun buildSnapshot(closes: List<BigDecimal>, price: BigDecimal): IndicatorSnapshot {
        val values = linkedMapOf("price" to price)
        indicators.sma(closes, 5)?.let { values["sma5"] = it }
        indicators.sma(closes, 20)?.let { values["sma20"] = it }
        indicators.rsi(closes, 14)?.let { values["rsi14"] = it }
        return IndicatorSnapshot(values)
    }

    private fun resolveInterval(config: StrategyConfig): CandleInterval {
        val raw = config.params["candleInterval"] as? String
        return CandleInterval.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CandleInterval.DAY_1
    }

    companion object {
        const val CANDLE_LIMIT = 200
        const val DEFAULT_BUY_QTY = 1
    }
}
