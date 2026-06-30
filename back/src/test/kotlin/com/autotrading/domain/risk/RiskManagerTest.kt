package com.autotrading.domain.risk

import com.autotrading.domain.type.Market
import com.autotrading.domain.type.OrderSide
import com.autotrading.entity.PaperAccount
import com.autotrading.entity.Position
import com.autotrading.entity.RiskSetting
import com.autotrading.entity.Stock
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 리스크 가드 단위 테스트. 외부 의존 없이 엔티티를 직접 구성해 결정론적으로 검증한다.
 * RiskManager는 순수 판정 — [RiskDecision]을 반환한다(예외 아님).
 */
class RiskManagerTest {

    private val riskManager = RiskManager()
    private val stock = Stock(code = "005930", name = "삼성전자", market = Market.KOSPI)

    private fun account(balance: Long) =
        PaperAccount(name = "test", cashBalance = balance, initialSeed = balance)

    private fun risk(acc: PaperAccount, maxPct: Int = 100, dailyLoss: Long = 1_000_000, kill: Boolean = false) =
        RiskSetting(account = acc, dailyLossLimit = dailyLoss, maxPositionPct = maxPct).apply {
            if (kill) activateKillSwitch()
        }

    private fun ctx(
        acc: PaperAccount,
        risk: RiskSetting?,
        side: OrderSide,
        qty: Int,
        price: Long,
        position: Position? = null,
        todayRealizedPnl: Long = 0,
        hasOpenOrderSameSide: Boolean = false,
    ) = RiskCheckContext(acc, risk, side, qty, price, position, todayRealizedPnl, hasOpenOrderSameSide)

    private fun rejectReason(d: RiskDecision): RejectReason = assertIs<RiskDecision.Rejected>(d).reason

    @Test
    fun `리스크 설정이 없으면 차단한다`() {
        val acc = account(1_000_000)
        assertEquals(RejectReason.MISSING_RISK_SETTING, rejectReason(riskManager.check(ctx(acc, null, OrderSide.BUY, 1, 100))))
    }

    @Test
    fun `kill switch가 켜져 있으면 차단한다`() {
        val acc = account(1_000_000)
        assertEquals(RejectReason.KILL_SWITCH, rejectReason(riskManager.check(ctx(acc, risk(acc, kill = true), OrderSide.BUY, 1, 100))))
    }

    @Test
    fun `동일 종목 동일 방향 미체결 주문이 있으면 차단한다`() {
        val acc = account(1_000_000)
        val d = riskManager.check(ctx(acc, risk(acc), OrderSide.BUY, 1, 100, hasOpenOrderSameSide = true))
        assertEquals(RejectReason.DUPLICATE_ORDER, rejectReason(d))
    }

    @Test
    fun `일일 손실 한도에 도달하면 신규 매수를 차단한다`() {
        val acc = account(1_000_000)
        // 오늘 실현손실 -1,000,000, 한도 1,000,000 → 도달.
        val d = riskManager.check(ctx(acc, risk(acc, dailyLoss = 1_000_000), OrderSide.BUY, 1, 100, todayRealizedPnl = -1_000_000))
        assertEquals(RejectReason.DAILY_LOSS_LIMIT, rejectReason(d))
    }

    @Test
    fun `일일 손실 한도 미만이면 매수를 허용한다`() {
        val acc = account(1_000_000)
        val d = riskManager.check(ctx(acc, risk(acc, dailyLoss = 1_000_000), OrderSide.BUY, 1, 100, todayRealizedPnl = -999_999))
        assertTrue(d.isApproved)
    }

    @Test
    fun `매수 잔고가 부족하면 차단한다`() {
        val acc = account(500)
        assertEquals(RejectReason.INSUFFICIENT_BALANCE, rejectReason(riskManager.check(ctx(acc, risk(acc), OrderSide.BUY, 10, 100))))
    }

    @Test
    fun `종목당 비중 한도를 넘으면 차단한다`() {
        val acc = account(1_000_000)
        // maxPct=50, price=100, qty=6000 → 60% > 50%
        assertEquals(RejectReason.POSITION_LIMIT_EXCEEDED, rejectReason(riskManager.check(ctx(acc, risk(acc, maxPct = 50), OrderSide.BUY, 6000, 100))))
    }

    @Test
    fun `비중 한도 이내의 매수는 통과한다`() {
        val acc = account(1_000_000)
        assertTrue(riskManager.check(ctx(acc, risk(acc, maxPct = 50), OrderSide.BUY, 4000, 100)).isApproved)
    }

    @Test
    fun `보유 수량보다 많이 매도하면 차단한다`() {
        val acc = account(1_000_000)
        val position = Position(account = acc, stock = stock, quantity = 5, avgPrice = 100)
        assertEquals(RejectReason.INSUFFICIENT_POSITION, rejectReason(riskManager.check(ctx(acc, risk(acc), OrderSide.SELL, 10, 100, position))))
    }

    @Test
    fun `보유 수량 이내의 매도는 통과한다 (잔고와 무관)`() {
        val acc = account(0)
        val position = Position(account = acc, stock = stock, quantity = 10, avgPrice = 100)
        assertTrue(riskManager.check(ctx(acc, risk(acc), OrderSide.SELL, 5, 100, position)).isApproved)
    }
}
