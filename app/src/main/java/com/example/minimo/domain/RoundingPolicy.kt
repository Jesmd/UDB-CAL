package com.example.minimo.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The single place that decides how numbers are rounded and when a grade counts as reaching a threshold.
 *
 * The portal shows final grades with one decimal, rounded (1.97 shows as 2.0). By default the app is strict:
 * a grade reaches a threshold only if its exact value is >= the threshold. With `roundLikePortal` (a switch in
 * Settings) the grade counts as the portal shows it, assuming half-up rounding (5.95 -> 6.0).
 */
object RoundingPolicy {
    private val HALF_TENTH = BigDecimal("0.05")

    /** Whether [finalGrade] (exact) reaches [threshold]. */
    fun reaches(finalGrade: BigDecimal, threshold: BigDecimal, roundLikePortal: Boolean = false): Boolean =
        if (roundLikePortal) portalGrade(finalGrade) >= threshold else finalGrade >= threshold

    /**
     * The lowest exact grade that [reaches] [threshold]. Thresholds have at most one decimal (see Validation),
     * so with portal rounding it is `threshold - 0.05`.
     */
    fun lowestReaching(threshold: BigDecimal, roundLikePortal: Boolean = false): BigDecimal =
        if (roundLikePortal) threshold - HALF_TENTH else threshold

    /** The grade as the portal shows it: one decimal, half up. */
    fun portalGrade(exact: BigDecimal): BigDecimal =
        exact.setScale(1, RoundingMode.HALF_UP)

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
