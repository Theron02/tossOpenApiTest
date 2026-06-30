package com.autotrading.domain.backtest

import com.autotrading.domain.type.Market
import com.autotrading.domain.type.OrderSide
import com.autotrading.entity.PaperAccount
import com.autotrading.entity.Position
import com.autotrading.entity.Stock
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * 백테스트 체결 시뮬레이터(가상). 토스로 주문을 보내지 않는다.
 *
 * - 잔고/평단가는 도메인 로직 [Position.addBuy]/[Position.reduceSell]를 **재사용**(실거래와 동일 회계).
 * - 현금·수수료·세금·평가자산은 [BigDecimal]로 계산(Double 금지). 수수료/세금은 원 단위로 반올림.
 * - 단순 포지션 모델: 보유 중이면 추가 매수 안 함(피라미딩 없음), 매도는 전량 청산. → 깔끔한 라운드트립.
 */
class BacktestExecutor(
    stockCode: String,
    private val initialCapital: BigDecimal,
    private val commissionRate: BigDecimal,
    private val taxRate: BigDecimal,
    private val slippageRate: BigDecimal,
    private val positionSizePct: Int,
) {
    private val account = PaperAccount(name = "backtest", cashBalance = 0, initialSeed = 0)
    private val stock = Stock(code = stockCode, name = stockCode, market = Market.KOSPI)
    private val position = Position(account = account, stock = stock, quantity = 0, avgPrice = 0)

    private var cash: BigDecimal = initialCapital
    private val curve = mutableListOf<EquityPoint>()

    val trades = mutableListOf<BacktestTrade>()
    var totalCommission: BigDecimal = BigDecimal.ZERO
        private set
    var totalTax: BigDecimal = BigDecimal.ZERO
        private set

    /** 전략 입력용 현재 포지션(보유 없으면 null). */
    fun currentPosition(): Position? = position.takeIf { !it.isEmpty }

    /** 신호 다음 봉 시가로 매수. 이미 보유 중이면 무시(피라미딩 없음). */
    fun buy(time: Instant, openPrice: Long) {
        if (!position.isEmpty) return
        val fillPrice = fillPrice(openPrice, buy = true)
        val budget = cash.multiply(BigDecimal(positionSizePct)).divide(BigDecimal(100), 8, RoundingMode.DOWN)
        // 수수료까지 감안해 살 수 있는 최대 수량.
        val perShare = BigDecimal(fillPrice).multiply(BigDecimal.ONE.add(commissionRate))
        val qty = budget.divide(perShare, 0, RoundingMode.DOWN).toInt()
        if (qty <= 0) return

        val notional = BigDecimal(fillPrice).multiply(BigDecimal(qty))
        val commission = roundWon(notional.multiply(commissionRate))
        val total = notional.add(commission)
        if (total > cash) return // 안전장치(반올림 경계)

        cash = cash.subtract(total)
        position.addBuy(qty, fillPrice)
        totalCommission = totalCommission.add(commission)
        trades.add(BacktestTrade(OrderSide.BUY, time, fillPrice, qty, commission, BigDecimal.ZERO, null))
    }

    /** 신호 다음 봉 시가로 전량 매도. 보유 없으면 무시. */
    fun sell(time: Instant, openPrice: Long) {
        if (position.isEmpty) return
        val qty = position.quantity
        val avg = position.avgPrice
        val fillPrice = fillPrice(openPrice, buy = false)

        val notional = BigDecimal(fillPrice).multiply(BigDecimal(qty))
        val commission = roundWon(notional.multiply(commissionRate))
        val tax = roundWon(notional.multiply(taxRate))
        val realized = BigDecimal((fillPrice - avg) * qty).subtract(commission).subtract(tax)

        cash = cash.add(notional).subtract(commission).subtract(tax)
        position.reduceSell(qty)
        totalCommission = totalCommission.add(commission)
        totalTax = totalTax.add(tax)
        trades.add(BacktestTrade(OrderSide.SELL, time, fillPrice, qty, commission, tax, realized))
    }

    /** 봉 종가 기준 평가자산을 시계열에 기록. */
    fun mark(time: Instant, closePrice: Long) {
        val holdings = BigDecimal(closePrice).multiply(BigDecimal(position.quantity))
        curve.add(EquityPoint(time, cash.add(holdings)))
    }

    fun equityCurve(): EquityCurve = EquityCurve(curve.toList())

    fun finalEquity(): BigDecimal = curve.lastOrNull()?.equity ?: initialCapital

    /** 슬리피지 반영 체결가(원). 매수는 불리하게 +, 매도는 −. */
    private fun fillPrice(openPrice: Long, buy: Boolean): Long {
        if (slippageRate.signum() == 0) return openPrice
        val factor = if (buy) BigDecimal.ONE.add(slippageRate) else BigDecimal.ONE.subtract(slippageRate)
        return BigDecimal(openPrice).multiply(factor).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    private fun roundWon(value: BigDecimal): BigDecimal = value.setScale(0, RoundingMode.HALF_UP)
}
