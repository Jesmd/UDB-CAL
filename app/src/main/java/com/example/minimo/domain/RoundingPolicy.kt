package com.example.minimo.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The single place that decides how numbers are rounded and when a grade counts as reaching a threshold.
 *
 * The portal shows final grades with one decimal, but it is not confirmed whether the university rounds
 * (e.g. 5.95 -> 6.0). Until confirmed, the policy is strict: a grade reaches a threshold only if its exact
 * value is >= the threshold. To change this, edit [reaches] only.
 */
object RoundingPolicy {
    /** Whether the exact [finalGrade] reaches [threshold]. */
    fun reaches(finalGrade: BigDecimal, threshold: BigDecimal): Boolean =
        finalGrade >= threshold

    /**
     * The grade needed on what is pending, `needed / pending`, rounded UP to 2 decimals so the app never
     * says a lower grade is enough than the real one. The exact quotient is rounded in a single step.
     */
    fun requiredGrade(needed: BigDecimal, pending: BigDecimal): BigDecimal =
        needed.divide(pending, 2, RoundingMode.CEILING)

    /** Obtained grades are shown truncated, so the app never overstates what the student has. */
    fun displayedGrade(exact: BigDecimal): BigDecimal =
        exact.setScale(2, RoundingMode.DOWN)
}
