package com.nexus.core.common

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs

sealed interface CalcResult {
    data class Value(val display: String, val value: Double) : CalcResult
    data class Error(val message: String) : CalcResult
}

/**
 * Small recursive-descent calculator used by the Nexus calculator tool and by the
 * global search live results. It accepts `+ - * / % ^`, parentheses, decimal points
 * and the Italian decimal comma.
 */
object Calculator {
    private const val MAX_INPUT_LENGTH = 256

    fun evaluate(expression: String): CalcResult {
        val normalized = expression.trim().replace(',', '.')
        if (normalized.isEmpty()) return CalcResult.Error("Inserisci un'espressione")
        if (normalized.length > MAX_INPUT_LENGTH) return CalcResult.Error("Espressione troppo lunga")
        if (normalized.any { it.isLetter() }) return CalcResult.Error("Caratteri non supportati")
        return try {
            val value = Parser(normalized).parse()
            when {
                !value.isFinite() -> CalcResult.Error("Risultato non valido")
                value.isNaN() -> CalcResult.Error("Risultato non valido")
                else -> CalcResult.Value(format(value), value)
            }
        } catch (failure: CalcException) {
            CalcResult.Error(failure.message ?: "Espressione non valida")
        } catch (failure: ArithmeticException) {
            CalcResult.Error("Operazione non valida")
        }
    }

    fun evaluateOrNull(expression: String): Double? = (evaluate(expression) as? CalcResult.Value)?.value

    fun format(value: Double): String {
        if (!value.isFinite()) return "Errore"
        if (value == 0.0) return "0"
        if (abs(value) >= 1e15 || abs(value) < 1e-9) {
            return BigDecimal.valueOf(value).round(MathContext(10)).stripTrailingZeros().toString()
        }
        return BigDecimal.valueOf(value)
            .setScale(10, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }

    private class CalcException(message: String) : Exception(message)

    private class Parser(private val source: String) {
        private var position = 0

        fun parse(): Double {
            val value = expression()
            skipSpaces()
            if (position < source.length) fail()
            return value
        }

        private fun expression(): Double {
            var value = term()
            while (true) {
                skipSpaces()
                value = when {
                    match('+') -> value + term()
                    match('-') -> value - term()
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = unary()
            while (true) {
                skipSpaces()
                when {
                    match('*') -> value *= unary()
                    match('/') -> {
                        val divisor = unary()
                        if (divisor == 0.0) fail("Divisione per zero")
                        value /= divisor
                    }
                    match('%') -> {
                        val divisor = unary()
                        if (divisor == 0.0) fail("Divisione per zero")
                        value %= divisor
                    }
                    else -> return value
                }
            }
        }

        private fun unary(): Double {
            skipSpaces()
            return when {
                match('+') -> unary()
                match('-') -> -unary()
                else -> power()
            }
        }

        private fun power(): Double {
            val base = primary()
            skipSpaces()
            return if (match('^')) {
                val exponent = unary()
                Math.pow(base, exponent)
            } else {
                base
            }
        }

        private fun primary(): Double {
            skipSpaces()
            if (match('(')) {
                val value = expression()
                skipSpaces()
                if (!match(')')) fail()
                return value
            }
            val start = position
            var hasDot = false
            while (position < source.length) {
                val char = source[position]
                when {
                    char.isDigit() -> position++
                    char == '.' && !hasDot -> {
                        hasDot = true
                        position++
                    }
                    else -> break
                }
            }
            if (start == position) fail()
            return source.substring(start, position).toDoubleOrNull() ?: fail()
        }

        private fun skipSpaces() {
            while (position < source.length && source[position].isWhitespace()) position++
        }

        private fun match(expected: Char): Boolean {
            skipSpaces()
            if (position < source.length && source[position] == expected) {
                position++
                return true
            }
            return false
        }

        private fun fail(message: String = "Espressione non valida"): Nothing = throw CalcException(message)
    }
}
