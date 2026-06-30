package com.autotrading.domain.risk

import com.autotrading.entity.PaperAccount
import com.autotrading.entity.Position
import com.autotrading.entity.RiskSetting
import com.autotrading.domain.type.OrderSide
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** 리스크 차단 사유. */
enum class RejectReason {
    MISSING_RISK_SETTING,
    KILL_SWITCH,
    INSUFFICIENT_BALANCE,
    POSITION_LIMIT_EXCEEDED,
    INSUFFICIENT_POSITION,
}

/** 리스크 가드에 막혀 주문이 거부됨. */
class RiskRejectedException(val reason: RejectReason, message: String) : RuntimeException(message)

/** 주문 직전 리스크 검사 입력(불변). 가격은 원 단위 정수([Long]). */
data class RiskCheckContext(
    val account: PaperAccount,
    val riskSetting: RiskSetting?,
    val side: OrderSide,
    val quantity: Int,
    /** 검사 기준가(LIMIT이면 지정가, MARKET이면 현재가). */
    val price: Long,
    val existingPosition: Position?,
)

/**
 * 주문 직전 가드. **모든 주문은 이 검사를 통과해야 한다(우회 금지).**
 * 위반 시 [RiskRejectedException]을 던지며, 모든 차단은 구조화 로그로 남긴다(감사 추적).
 *
 * 이번 라운드 적용 가드: kill switch · 매수 잔고부족 · 종목당 비중한도 · 매도 수량부족.
 * 일일 손실 한도는 실현손익 원장 미구축이라 아직 강제하지 않는다(TODO).
 */
@Component
class RiskManager {
    private val log = LoggerFactory.getLogger(javaClass)

    fun check(ctx: RiskCheckContext) {
        // 리스크 설정이 없으면 fail-safe: 차단. (계좌 1:1 RiskSetting 필수)
        val risk = ctx.riskSetting
            ?: reject(ctx, RejectReason.MISSING_RISK_SETTING, "리스크 설정이 없어 주문을 차단한다")

        if (risk.killSwitch) {
            reject(ctx, RejectReason.KILL_SWITCH, "kill switch 활성 — 모든 신규 주문 차단")
        }

        when (ctx.side) {
            OrderSide.BUY -> checkBuy(ctx, risk)
            OrderSide.SELL -> checkSell(ctx)
        }

        // TODO(일일 손실 한도): 오늘 실현손익 집계 모델 구축 후 risk.dailyLossLimit 강제.
    }

    private fun checkBuy(ctx: RiskCheckContext, risk: RiskSetting) {
        val cost = ctx.price * ctx.quantity
        if (ctx.account.cashBalance < cost) {
            reject(ctx, RejectReason.INSUFFICIENT_BALANCE, "잔고 부족: balance=${ctx.account.cashBalance}, cost=$cost")
        }

        // 종목당 비중 한도. 총자산을 (현금 + 해당 종목 평가액)으로 보수적으로 근사한다.
        // (다른 보유종목은 미산입 → 분모가 작아져 더 엄격. 추후 전체 포트폴리오 평가로 정교화.)
        val existingQty = ctx.existingPosition?.quantity ?: 0
        val postNotional = (existingQty + ctx.quantity).toLong() * ctx.price
        val equity = ctx.account.cashBalance + existingQty.toLong() * ctx.price
        if (postNotional * 100 > risk.maxPositionPct.toLong() * equity) {
            reject(
                ctx,
                RejectReason.POSITION_LIMIT_EXCEEDED,
                "종목당 비중 초과: postNotional=$postNotional, equity=$equity, limit=${risk.maxPositionPct}%",
            )
        }
    }

    private fun checkSell(ctx: RiskCheckContext) {
        val heldQty = ctx.existingPosition?.quantity ?: 0
        if (heldQty < ctx.quantity) {
            reject(ctx, RejectReason.INSUFFICIENT_POSITION, "보유 수량 부족: held=$heldQty, sell=${ctx.quantity}")
        }
    }

    private fun reject(ctx: RiskCheckContext, reason: RejectReason, detail: String): Nothing {
        log.warn(
            "리스크 차단 reason={} account={} side={} qty={} price={} detail={}",
            reason, ctx.account.id, ctx.side, ctx.quantity, ctx.price, detail,
        )
        throw RiskRejectedException(reason, detail)
    }
}
