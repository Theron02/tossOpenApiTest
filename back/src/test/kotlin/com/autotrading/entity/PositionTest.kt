package com.autotrading.entity

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PositionTest {

    private fun position(quantity: Int, avgPrice: Long) =
        Position(account = Fixtures.account(), stock = Fixtures.stock(), quantity = quantity, avgPrice = avgPrice)

    @Test
    fun `addBuy updates avg price as weighted average`() {
        val p = position(quantity = 10, avgPrice = 70_000)
        p.addBuy(filledQty = 10, filledPrice = 72_000)
        // (70000*10 + 72000*10) / 20 = 71000
        assertEquals(20, p.quantity)
        assertEquals(71_000, p.avgPrice)
    }

    @Test
    fun `addBuy uses integer division (truncates)`() {
        val p = position(quantity = 3, avgPrice = 1_000)
        p.addBuy(filledQty = 2, filledPrice = 1_001)
        // (1000*3 + 1001*2) / 5 = 5002 / 5 = 1000 (truncated)
        assertEquals(5, p.quantity)
        assertEquals(1_000, p.avgPrice)
    }

    @Test
    fun `reduceSell decrements quantity and keeps avg price`() {
        val p = position(quantity = 10, avgPrice = 70_000)
        p.reduceSell(filledQty = 4)
        assertEquals(6, p.quantity)
        assertEquals(70_000, p.avgPrice)
    }

    @Test
    fun `reduceSell rejects selling more than held`() {
        val p = position(quantity = 5, avgPrice = 70_000)
        assertThrows<IllegalArgumentException> { p.reduceSell(filledQty = 6) }
    }

    @Test
    fun `isEmpty reflects zero quantity`() {
        val p = position(quantity = 5, avgPrice = 70_000)
        assertFalse(p.isEmpty)
        p.reduceSell(5)
        assertTrue(p.isEmpty)
    }

    @Test
    fun `addBuy rejects non-positive inputs`() {
        val p = position(quantity = 1, avgPrice = 1_000)
        assertThrows<IllegalArgumentException> { p.addBuy(0, 1_000) }
        assertThrows<IllegalArgumentException> { p.addBuy(1, 0) }
    }
}