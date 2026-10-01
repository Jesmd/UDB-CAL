package com.example.minimo.domain

import java.math.BigDecimal

/** Parses numbers typed by the user. */
object DecimalInput {
    private val PATTERN = Regex("""\d+([.,]\d*)?|[.,]\d+""")

    /** Accepts a comma or a point as decimal separator. Returns `null` for anything else (signs, exponents, text). */
    fun parse(text: String): BigDecimal? {
        val trimmed = text.trim()
        if (!PATTERN.matches(trimmed)) return null
        return trimmed.replace(',', '.').let { if (it.endsWith('.')) it.dropLast(1) else it }
            .let { if (it.startsWith('.')) "0$it" else it }
            .toBigDecimalOrNull()
    }
}
