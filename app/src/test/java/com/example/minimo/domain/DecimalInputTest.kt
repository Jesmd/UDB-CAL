package com.example.minimo.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class DecimalInputTest {
    private fun parsed(text: String) = DecimalInput.parse(text)

    @Test
    fun acceptsPointAndComma() {
        assertEquals(0, BigDecimal("6.25").compareTo(parsed("6.25")))
        assertEquals(0, BigDecimal("6.25").compareTo(parsed("6,25")))
    }

    @Test
    fun acceptsIntegersAndLeadingOrTrailingSeparator() {
        assertEquals(0, BigDecimal("7").compareTo(parsed("7")))
        assertEquals(0, BigDecimal("7").compareTo(parsed("7.")))
        assertEquals(0, BigDecimal("0.5").compareTo(parsed(".5")))
        assertEquals(0, BigDecimal("0.5").compareTo(parsed(",5")))
    }

    @Test
    fun trimsSpaces() {
        assertEquals(0, BigDecimal("8.5").compareTo(parsed("  8,5 ")))
    }

    @Test
    fun rejectsEverythingElse() {
        listOf("", " ", "abc", "-1", "+1", "1e3", "1.2.3", "1,2,3", "1,000.5", ".", ",", "6.5%", "1 000").forEach {
            assertNull("'$it' should be rejected", parsed(it))
        }
    }

    @Test
    fun passMarkMustBeAboveZeroUpToTenWithOneDecimal() {
        assertTrue(Validation.isValidPassMark(BigDecimal("6.0")))
        assertTrue(Validation.isValidPassMark(BigDecimal("0.1")))
        assertTrue(Validation.isValidPassMark(BigDecimal("10")))
        assertFalse(Validation.isValidPassMark(BigDecimal("0")))
        assertFalse(Validation.isValidPassMark(BigDecimal("10.1")))
        assertFalse(Validation.isValidPassMark(BigDecimal("5.75")))
    }
}
