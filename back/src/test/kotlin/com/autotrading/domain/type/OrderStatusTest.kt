package com.autotrading.domain.type

import com.autotrading.domain.type.OrderStatus.CANCELLED
import com.autotrading.domain.type.OrderStatus.FILLED
import com.autotrading.domain.type.OrderStatus.PARTIAL
import com.autotrading.domain.type.OrderStatus.PENDING
import com.autotrading.domain.type.OrderStatus.REJECTED
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrderStatusTest {

    @Test
    fun `PENDING can transition to any non-pending status`() {
        assertTrue(PENDING.canTransitionTo(PARTIAL))
        assertTrue(PENDING.canTransitionTo(FILLED))
        assertTrue(PENDING.canTransitionTo(CANCELLED))
        assertTrue(PENDING.canTransitionTo(REJECTED))
    }

    @Test
    fun `PARTIAL can only go to FILLED or CANCELLED`() {
        assertTrue(PARTIAL.canTransitionTo(FILLED))
        assertTrue(PARTIAL.canTransitionTo(CANCELLED))
        assertFalse(PARTIAL.canTransitionTo(REJECTED))
        assertFalse(PARTIAL.canTransitionTo(PENDING))
    }

    @Test
    fun `terminal states allow no transitions`() {
        for (terminal in listOf(FILLED, CANCELLED, REJECTED)) {
            assertTrue(terminal.isTerminal)
            for (target in OrderStatus.entries) {
                assertFalse(terminal.canTransitionTo(target), "$terminal -> $target should be illegal")
            }
        }
    }

    @Test
    fun `non-terminal states report not terminal`() {
        assertFalse(PENDING.isTerminal)
        assertFalse(PARTIAL.isTerminal)
    }
}