package com.example.minimo.ui

import java.math.BigDecimal

/** A number as the user would write it: no trailing zeros ("30", "6.25"). */
fun BigDecimal.asNumber(): String = stripTrailingZeros().toPlainString()

/** A weight, e.g. "30%". */
fun BigDecimal.asPercent(): String = asNumber() + "%"

/** A pass mark or goal, always with at least one decimal ("6.0", "7.5"). */
fun BigDecimal.asThreshold(): String {
    val stripped = stripTrailingZeros()
    return (if (stripped.scale() < 1) stripped.setScale(1) else stripped).toPlainString()
}
