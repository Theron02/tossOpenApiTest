package com.autotrading.domain.backtest

import com.autotrading.domain.strategy.MarketContext
import com.autotrading.domain.strategy.TradingStrategy
import com.autotrading.domain.type.OrderSide
import com.autotrading.domain.type.Signal
import com.autotrading.entity.Candle
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant

/**
 * 백테스트 핵심 루프. 실시간과 **같은 전략·지표 코드**([TradingStrategy])를 재사용하고 입력 소스만 바꾼다.
 *
 * look-ahead 차단(핵심):
 * - 캔들을 오름차순으로 하나씩 흘리며, 시점 T 평가엔 candles[0..T] 윈도우만 준다.
 * - 시점 T의 신호는 **다음 봉(T+1) 시가로 체결**한다(종가 판단→종가 체결은 미래 참조이므로 금지).
 *   → 구현상 "직전 봉 신호를 현재 봉 시가에 체결"로 처리한다.
 * - 마지막 봉에서 난 신호는 체결할 다음 봉이 없으므로 실행되지 않는다.
 */
@Component
class BacktestEngine(
    strategies: List<TradingStrategy>,
) {
    private val strategyByName: Map<String, TradingStrategy> = strategies.associateBy { it.name }

    fun run(request: BacktestRequest, candlesAsc: List<Candle>): BacktestResult {
        val strategy = strategyByName[request.strategyName]
            ?: throw IllegalArgumentException("미등록 전략: ${request.strategyName}")
        require(candlesAsc.size >= 2) { "백테스트엔 최소 2개 이상의 캔들이 필요하다" }

        val closes = candlesAsc.map { BigDecimal(it.close) } // 오름차순 종가(미리 인덱싱 X, subList 뷰만 사용)
        val executor = BacktestExecutor(
            stockCode = request.stockCode,
            initialCapital = request.initialCapital,
            commissionRate = request.commissionRate,
            taxRate = request.taxRate,
            slippageRate = request.slippageRate,
            positionSizePct = request.positionSizePct,
        )

        var pending = Signal.HOLD
        for (i in candlesAsc.indices) {
            val candle = candlesAsc[i]

            // 1) 직전 봉에서 난 신호를 '이번 봉 시가'로 체결 (다음-봉-시가 체결 모델).
            when (pending) {
                Signal.BUY -> executor.buy(candle.candleTime, candle.open)
                Signal.SELL -> executor.sell(candle.candleTime, candle.open)
                Signal.HOLD -> Unit
            }

            // 2) 이번 봉까지의 윈도우로만 전략 평가 → 다음 봉에서 체결할 신호.
            val window = closes.subList(0, i + 1)
            val context = MarketContext(
                stockCode = request.stockCode,
                closes = window,
                currentPrice = closes[i],
                position = executor.currentPosition(),
                params = request.params,
            )
            pending = strategy.evaluate(context)

            // 3) 이번 봉 종가로 평가자산 기록.
            executor.mark(candle.candleTime, candle.close)
        }

        return aggregate(request, executor, candlesAsc.first().candleTime, candlesAsc.last().candleTime)
    }

    private fun aggregate(
        request: BacktestRequest,
        executor: BacktestExecutor,
        firstTime: Instant,
        lastTime: Instant,
    ): BacktestResult {
        val initial = request.initialCapital
        val finalEquity = executor.finalEquity()
        val totalReturn = finalEquity.subtract(initial).divide(initial, 6, RoundingMode.HALF_UP)

        val sells = executor.trades.filter { it.side == OrderSide.SELL && it.realizedPnl != null }
        val wins = sells.filter { it.realizedPnl!! > BigDecimal.ZERO }
        val losses = sells.filter { it.realizedPnl!! < BigDecimal.ZERO }
        val winRate = if (sells.isEmpty()) BigDecimal.ZERO
        else BigDecimal(wins.size).divide(BigDecimal(sells.size), 4, RoundingMode.HALF_UP)

        val sumWin = wins.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.realizedPnl) }
        val sumLoss = losses.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.realizedPnl!!.abs()) }
        val profitFactor = if (sumLoss.signum() == 0) null else sumWin.divide(sumLoss, 4, RoundingMode.HALF_UP)

        return BacktestResult(
            initialCapital = initial,
            finalEquity = finalEquity,
            totalReturn = totalReturn,
            cagr = cagr(initial, finalEquity, firstTime, lastTime),
            maxDrawdown = executor.equityCurve().maxDrawdown(),
            winRate = winRate,
            profitFactor = profitFactor,
            totalTrades = executor.trades.size,
            closedTrades = sells.size,
            totalCommission = executor.totalCommission,
            totalTax = executor.totalTax,
            equityCurve = executor.equityCurve(),
            trades = executor.trades.toList(),
            warnings = WARNINGS,
        )
    }

    /**
     * 연환산 수익률. 비율 통계라 거듭제곱은 double로 계산하고 BigDecimal로 환산(돈 계산 아님).
     * 기간이 0 이하면 0 반환.
     */
    private fun cagr(initial: BigDecimal, finalEquity: BigDecimal, firstTime: Instant, lastTime: Instant): BigDecimal {
        val seconds = Duration.between(firstTime, lastTime).seconds
        if (seconds <= 0 || initial.signum() <= 0 || finalEquity.signum() <= 0) return BigDecimal.ZERO
        val years = seconds.toDouble() / SECONDS_PER_YEAR
        val ratio = finalEquity.toDouble() / initial.toDouble()
        val value = Math.pow(ratio, 1.0 / years) - 1.0
        return BigDecimal(value).setScale(6, RoundingMode.HALF_UP)
    }

    companion object {
        private const val SECONDS_PER_YEAR = 365.25 * 24 * 60 * 60
        private val WARNINGS = listOf(
            "생존편향: 상장폐지·거래정지 종목이 데이터에 없으면 결과가 낙관적으로 편향된다.",
            "과최적화: 같은 데이터로 파라미터를 반복 조정하면 과거에만 맞는 전략이 된다.",
            "단순 체결모델: 다음 봉 시가 전량 체결, 부분체결·호가·유동성·시장충격 미반영.",
        )
    }
}
