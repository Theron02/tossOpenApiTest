package com.autotrading.entity

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class PaperAccountTest {

    @Test
    fun `withdraw decrements balance`() {
        val a = Fixtures.account(cashBalance = 1_000_000)
        a.withdraw(300_000)
        assertEquals(700_000, a.cashBalance)
    }

    @Test
    fun `withdraw rejects amount over balance`() {
        val a = Fixtures.account(cashBalance = 100)
        assertThrows<IllegalArgumentException> { a.withdraw(101) }
    }

    @Test
    fun `withdraw rejects non-positive amount`() {
        val a = Fixtures.account(cashBalance = 100)
        assertThrows<IllegalArgumentException> { a.withdraw(0) }
    }

    @Test
    fun `deposit increments balance`() {
        val a = Fixtures.account(cashBalance = 100)
        a.deposit(50)
        assertEquals(150, a.cashBalance)
    }

    @Test
    fun `deposit rejects non-positive amount`() {
        val a = Fixtures.account(cashBalance = 100)
        assertThrows<IllegalArgumentException> { a.deposit(-1) }
    }
}