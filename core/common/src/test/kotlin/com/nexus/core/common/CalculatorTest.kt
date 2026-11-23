package com.nexus.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorTest {
    private fun value(expression: String): String =
        (Calculator.evaluate(expression) as CalcResult.Value).display

    @Test
    fun `respects operator precedence`() {
        assertEquals("7", value("1 + 2 * 3"))
        assertEquals("9", value("(1 + 2) * 3"))
        assertEquals("2", value("10 / 5"))
        assertEquals("1", value("10 % 3"))
    }

    @Test
    fun `supports decimals and italian comma`() {
        assertEquals("0.3", value("0,1 + 0,2"))
        assertEquals("1.5", value("0.5 * 3"))
        assertEquals("6.25", value("2.5 ^ 2"))
    }

    @Test
    fun `supports unary operators and right associative powers`() {
        assertEquals("-4", value("-2 ^ 2"))
        assertEquals("512", value("2 ^ 3 ^ 2"))
        assertEquals("0.25", value("2 ^ -2"))
    }

    @Test
    fun `reports division by zero`() {
        val result = Calculator.evaluate("4 / 0")
        assertTrue(result is CalcResult.Error)
        assertEquals("Divisione per zero", (result as CalcResult.Error).message)
    }

    @Test
    fun `rejects malformed input`() {
        assertTrue(Calculator.evaluate("1 +") is CalcResult.Error)
        assertTrue(Calculator.evaluate("abc") is CalcResult.Error)
        assertTrue(Calculator.evaluate("()") is CalcResult.Error)
        assertTrue(Calculator.evaluate("") is CalcResult.Error)
    }

    @Test
    fun `live expression detection works`() {
        assertEquals(4.0, Calculator.evaluateOrNull("2 + 2"))
        assertEquals(null, Calculator.evaluateOrNull("meteo"))
    }
}
