package com.autotrading.domain.backtest

import com.autotrading.domain.type.OrderSide
import java.math.BigDecimal
import java.time.Instant

/** 백테스트 체결 1건(시뮬). 매도 시 [realizedPnl]은 수수료·세금 차감 후 실현손익. */
data class BacktestTrade(
    val side: OrderSide,
    val time: Instant,
    val price: Long,
    val quantity: Int,
    val commission: BigDecimal,
    val tax: BigDecimal,
    val realizedPnl: BigDecimal?,
)

/**
 * 백테스트 성과 지표. 수익률뿐 아니라 **MDD·매매횟수·수수료/세금**까지 봐야 현실성을 판단할 수 있다.
 *
 * 비율 값(totalReturn/cagr/maxDrawdown/winRate)은 소수비율(0.1 = 10%).
 * [profitFactor]는 손실 트레이드가 없으면 null(정의 불가).
 */
data class BacktestResult(
    val initialCapital: BigDecimal,
    val finalEquity: BigDecimal,
    val totalReturn: BigDecimal,
    val cagr: BigDecimal,
    val maxDrawdown: BigDecimal,
    val winRate: BigDecimal,
    val profitFactor: BigDecimal?,
    /** 총 체결 횟수(매수+매도). */
    val totalTrades: Int,
    /** 청산(매도) 트레이드 수 — 승률 분모. */
    val closedTrades: Int,
    val totalCommission: BigDecimal,
    val totalTax: BigDecimal,
    val equityCurve: EquityCurve,
    val trades: List<BacktestTrade>,
    /** 해석 한계 경고(생존편향·과최적화·단순 체결모델 등). */
    val warnings: List<String>,
)
