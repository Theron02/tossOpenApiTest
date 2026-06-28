package com.autotrading.entity

import com.autotrading.domain.type.OrderSide
import com.autotrading.domain.type.OrderType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExecutionAndRiskTest {

    private fun order() = TradeOrder(
        account = Fixtures.account(),
        stock = Fixtures.stock(),
        side = OrderSide.BUY,
        orderType = OrderType.MARKET,
        quantity = 10,
        idempotencyKey = "k-${System.nanoTime()}",
    )

    @Test
    fun `grossAmount excludes fee`() {
        val e = Execution(order(), filledQty = 5, filledPrice = 70_000, fee = 105, executedAt = Instant.now())
        assertEquals(350_000, e.grossAmount)
    }

    @Test
    fun `execution rejects invalid values`() {
        assertThrows<IllegalArgumentException> {
            Execution(order(), filledQty = 0, filledPrice = 70_000, fee = 0, executedAt = Instant.now())
        }
        assertThrows<IllegalArgumentException> {
            Execution(order(), filledQty = 1, filledPrice = 0, fee = 0, executedAt = Instant.now())
        }
        assertThrows<IllegalArgumentException> {
            Execution(order(), filledQty = 1, filledPrice = 1, fee = -1, executedAt = Instant.now())
        }
    }

    @Test
    fun `risk setting validates bounds`() {
        assertThrows<IllegalArgumentException> {
            RiskSetting(Fixtures.account(), dailyLossLimit = 0, maxPositionPct = 0)
        }
        assertThrows<IllegalArgumentException> {
            RiskSetting(Fixtures.account(), dailyLossLimit = 0, maxPositionPct = 101)
        }
        assertThrows<IllegalArgumentException> {
            RiskSetting(Fixtures.account(), dailyLossLimit = -1, maxPositionPct = 50)
        }
    }

    @Test
    fun `kill switch toggles`() {
        val r = RiskSetting(Fixtures.account(), dailyLossLimit = 500_000, maxPositionPct = 20)
        assertFalse(r.killSwitch)
        r.activateKillSwitch()
        assertTrue(r.killSwitch)
        r.deactivateKillSwitch()
        assertFalse(r.killSwitch)
    }
}