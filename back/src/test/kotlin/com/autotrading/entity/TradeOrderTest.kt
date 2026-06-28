package com.autotrading.entity

import com.autotrading.domain.type.OrderSide
import com.autotrading.domain.type.OrderStatus
import com.autotrading.domain.type.OrderType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class TradeOrderTest {

    private fun marketOrder(quantity: Int = 10) = TradeOrder(
        account = Fixtures.account(),
        stock = Fixtures.stock(),
        side = OrderSide.BUY,
        orderType = OrderType.MARKET,
        quantity = quantity,
        idempotencyKey = "key-${System.nanoTime()}",
    )

    @Test
    fun `new order starts PENDING with full remaining`() {
        val o = marketOrder(quantity = 10)
        assertEquals(OrderStatus.PENDING, o.status)
        assertEquals(0, o.filledQuantity)
        assertEquals(10, o.remainingQuantity)
    }

    @Test
    fun `partial fill transitions to PARTIAL`() {
        val o = marketOrder(quantity = 10)
        o.applyFill(4)
        assertEquals(OrderStatus.PARTIAL, o.status)
        assertEquals(4, o.filledQuantity)
        assertEquals(6, o.remainingQuantity)
    }

    @Test
    fun `multiple partial fills stay PARTIAL then complete to FILLED`() {
        val o = marketOrder(quantity = 10)
        o.applyFill(4)
        o.applyFill(3)
        assertEquals(OrderStatus.PARTIAL, o.status)
        assertEquals(7, o.filledQuantity)
        o.applyFill(3)
        assertEquals(OrderStatus.FILLED, o.status)
        assertEquals(0, o.remainingQuantity)
    }

    @Test
    fun `full fill in one shot transitions to FILLED`() {
        val o = marketOrder(quantity = 10)
        o.applyFill(10)
        assertEquals(OrderStatus.FILLED, o.status)
    }

    @Test
    fun `fill beyond order quantity is rejected`() {
        val o = marketOrder(quantity = 10)
        o.applyFill(8)
        assertThrows<IllegalArgumentException> { o.applyFill(3) }
    }

    @Test
    fun `illegal transition from terminal state throws`() {
        val o = marketOrder(quantity = 10)
        o.transitionTo(OrderStatus.CANCELLED)
        assertThrows<IllegalArgumentException> { o.transitionTo(OrderStatus.FILLED) }
    }

    @Test
    fun `limit order requires price`() {
        assertThrows<IllegalArgumentException> {
            TradeOrder(
                account = Fixtures.account(),
                stock = Fixtures.stock(),
                side = OrderSide.BUY,
                orderType = OrderType.LIMIT,
                quantity = 10,
                price = null,
                idempotencyKey = "k",
            )
        }
    }

    @Test
    fun `order quantity must be positive`() {
        assertThrows<IllegalArgumentException> { marketOrder(quantity = 0) }
    }
}