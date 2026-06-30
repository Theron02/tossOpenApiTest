package com.autotrading.domain.risk

import com.autotrading.domain.type.Market
import com.autotrading.domain.type.OrderSide
import com.autotrading.entity.PaperAccount
import com.autotrading.entity.Position
import com.autotrading.entity.RiskSetting
import com.autotrading.entity.Stock
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 리스크 가드 단위 테스트. 외부 의존 없이 엔티티를 직접 구성해 결정론적으로 검증한다.
 * (금전·리스크 로직은 반드시 단위 테스트 — backend/CLAUDE.md 8)
 */
class RiskManagerTest {

    private val riskManager = RiskManager()

    private val stock = Stock(code = "005930", name = "삼성전자", market = Market.KOSPI)

    private fun account(balance: Long) =
        PaperAccount(name = "test", cashBalance = balance, initialSeed = balance)

    private fun risk(acc: PaperAccount, maxPct: Int = 100, kill: Boolean = false) =
        RiskSetting(account = acc, dailyLossLimit = 1_000_000, maxPositionPct = maxPct).apply {
            if (kill) activateKillSwitch()
        }

    private fun ctx(
        acc: PaperAccount,
        risk: RiskSetting?,
        side: OrderSide,
        qty: Int,
        price: Long,
        position: Position? = null,
    ) = RiskCheckContext(acc, risk, side, qty, price, position)

    @Test
    fun `리스크 설정이 없으면 차단한다`() {
        val acc = account(1_000_000)
        val ex = assertFailsWith<RiskRejectedException> {
            riskManager.check(ctx(acc, null, OrderSide.BUY, 1, 100))
        }
        assertEquals(RejectReason.MISSING_RISK_SETTING, ex.reason)
    }

    @Test
    fun `kill switch가 켜져 있으면 차단한다`() {
        val acc = account(1_000_000)
        val ex = assertFailsWith<RiskRejectedException> {
            riskManager.check(ctx(acc, risk(acc, kill = true), OrderSide.BUY, 1, 100))
        }
        assertEquals(RejectReason.KILL_SWITCH, ex.reason)
    }

    @Test
    fun `매수 잔고가 부족하면 차단한다`() {
        val acc = account(500)
        val ex = assertFailsWith<RiskRejectedException> {
            riskManager.check(ctx(acc, risk(acc), OrderSide.BUY, 10, 100)) // cost=1000 > 500
        }
        assertEquals(RejectReason.INSUFFICIENT_BALANCE, ex.reason)
    }

    @Test
    fun `종목당 비중 한도를 넘으면 차단한다`() {
        val acc = account(1_000_000)
        // maxPct=50, price=100, qty=6000 → postNotional=600,000, equity=1,000,000 → 60% > 50%
        val ex = assertFailsWith<RiskRejectedException> {
            riskManager.check(ctx(acc, risk(acc, maxPct = 50), OrderSide.BUY, 6000, 100))
        }
        assertEquals(RejectReason.POSITION_LIMIT_EXCEEDED, ex.reason)
    }

    @Test
    fun `비중 한도 이내의 매수는 통과한다`() {
        val acc = account(1_000_000)
        // qty=4000 → postNotional=400,000 → 40% <= 50%
        riskManager.check(ctx(acc, risk(acc, maxPct = 50), OrderSide.BUY, 4000, 100))
    }

    @Test
    fun `보유 수량보다 많이 매도하면 차단한다`() {
        val acc = account(1_000_000)
        val position = Position(account = acc, stock = stock, quantity = 5, avgPrice = 100)
        val ex = assertFailsWith<RiskRejectedException> {
            riskManager.check(ctx(acc, risk(acc), OrderSide.SELL, 10, 100, position))
        }
        assertEquals(RejectReason.INSUFFICIENT_POSITION, ex.reason)
    }

    @Test
    fun `보유 수량 이내의 매도는 통과한다`() {
        val acc = account(1_000_000)
        val position = Position(account = acc, stock = stock, quantity = 10, avgPrice = 100)
        riskManager.check(ctx(acc, risk(acc), OrderSide.SELL, 10, 100, position))
    }

    @Test
    fun `매도는 잔고와 무관하게 통과한다`() {
        // 잔고 0이어도 보유 수량이 충분하면 매도 가능 (잔고 가드는 매수에만).
        val acc = account(0)
        val position = Position(account = acc, stock = stock, quantity = 10, avgPrice = 100)
        riskManager.check(ctx(acc, risk(acc), OrderSide.SELL, 5, 100, position))
    }
}
