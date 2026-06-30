package com.autotrading.domain.risk

import com.autotrading.domain.type.OrderSide
import com.autotrading.entity.PaperAccount
import com.autotrading.entity.Position
import com.autotrading.entity.RiskSetting
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** 리스크 가드에 막혀 주문이 거부됨(실행 계층에서 사용). */
class RiskRejectedException(val reason: RejectReason, message: String) : RuntimeException(message)

/**
 * 주문 직전 리스크 검사 입력(불변). 가격·금액은 원 단위 정수([Long]).
 *
 * 일일손실/중복주문 판정에 필요한 외부 데이터는 호출측이 조회해 넣는다(RiskManager는 순수 유지).
 */
data class RiskCheckContext(
    val account: PaperAccount,
    val riskSetting: RiskSetting?,
    val side: OrderSide,
    val quantity: Int,
    /** 검사 기준가(LIMIT이면 지정가, MARKET이면 현재가). */
    val price: Long,
    val existingPosition: Position?,
    /** 오늘(KST) 누적 실현손익. 음수면 손실. 일일 손실 한도 판정에 사용. */
    val todayRealizedPnl: Long = 0,
    /** 동일 종목·동일 방향 미체결 주문이 이미 있는지. 중복 주문 차단에 사용. */
    val hasOpenOrderSameSide: Boolean = false,
)

/**
 * 주문 직전 가드. **모든 주문은 이 검사를 통과해야 한다(우회 금지).**
 * 부수효과 없는 순수 판정으로 [RiskDecision]을 반환한다(실행은 호출측). 차단은 감사 로그로 남긴다.
 *
 * 가드: 리스크설정 누락 · kill switch · 중복주문 · (매수) 일일손실한도·잔고·종목비중 · (매도) 수량.
 */
@Component
class RiskManager {
    private val log = LoggerFactory.getLogger(javaClass)

    fun check(ctx: RiskCheckContext): RiskDecision {
        // 리스크 설정이 없으면 fail-safe: 차단. (계좌 1:1 RiskSetting 필수)
        val risk = ctx.riskSetting
            ?: return reject(ctx, RejectReason.MISSING_RISK_SETTING, "리스크 설정이 없어 주문을 차단한다")

        if (risk.killSwitch) {
            return reject(ctx, RejectReason.KILL_SWITCH, "kill switch 활성 — 모든 신규 주문 차단")
        }
        if (ctx.hasOpenOrderSameSide) {
            return reject(ctx, RejectReason.DUPLICATE_ORDER, "동일 종목·방향 미체결 주문 존재 — 중복 주문 차단")
        }

        return when (ctx.side) {
            OrderSide.BUY -> checkBuy(ctx, risk)
            OrderSide.SELL -> checkSell(ctx)
        }
    }

    private fun checkBuy(ctx: RiskCheckContext, risk: RiskSetting): RiskDecision {
        // 일일 손실 한도: 당일 누적 실현손실이 한도 도달 시 신규 매수 차단.
        if (-ctx.todayRealizedPnl >= risk.dailyLossLimit) {
            return reject(
                ctx, RejectReason.DAILY_LOSS_LIMIT,
                "일일 손실 한도 도달: realizedPnl=${ctx.todayRealizedPnl}, limit=${risk.dailyLossLimit}",
            )
        }

        val cost = ctx.price * ctx.quantity
        if (ctx.account.cashBalance < cost) {
            return reject(ctx, RejectReason.INSUFFICIENT_BALANCE, "잔고 부족: balance=${ctx.account.cashBalance}, cost=$cost")
        }

        // 종목당 비중 한도. 총자산을 (현금 + 해당 종목 평가액)으로 보수적으로 근사한다.
        // (다른 보유종목은 미산입 → 분모가 작아져 더 엄격. 추후 전체 포트폴리오 평가로 정교화.)
        val existingQty = ctx.existingPosition?.quantity ?: 0
        val postNotional = (existingQty + ctx.quantity).toLong() * ctx.price
        val equity = ctx.account.cashBalance + existingQty.toLong() * ctx.price
        if (postNotional * 100 > risk.maxPositionPct.toLong() * equity) {
            return reject(
                ctx, RejectReason.POSITION_LIMIT_EXCEEDED,
                "종목당 비중 초과: postNotional=$postNotional, equity=$equity, limit=${risk.maxPositionPct}%",
            )
        }
        return RiskDecision.Approved
    }

    private fun checkSell(ctx: RiskCheckContext): RiskDecision {
        val heldQty = ctx.existingPosition?.quantity ?: 0
        if (heldQty < ctx.quantity) {
            return reject(ctx, RejectReason.INSUFFICIENT_POSITION, "보유 수량 부족: held=$heldQty, sell=${ctx.quantity}")
        }
        return RiskDecision.Approved
    }

    private fun reject(ctx: RiskCheckContext, reason: RejectReason, detail: String): RiskDecision.Rejected {
        log.warn(
            "리스크 차단 reason={} account={} side={} qty={} price={} detail={}",
            reason, ctx.account.id, ctx.side, ctx.quantity, ctx.price, detail,
        )
        return RiskDecision.Rejected(reason, detail)
    }
}
