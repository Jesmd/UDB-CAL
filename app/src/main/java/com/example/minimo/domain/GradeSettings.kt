package com.example.minimo.domain

import java.math.BigDecimal

val MAX_GRADE: BigDecimal = BigDecimal("10")
val DEFAULT_PASS_MARK: BigDecimal = BigDecimal("6.0")

/**
 * User-configurable thresholds.
 *
 * @property passMark minimum grade (T) to pass a course.
 * @property defaultGoal goal (G) applied to courses that have none of their own.
 * @property roundLikePortal whether a final grade counts as the portal shows it (one decimal, rounded),
 *   so 5.95 counts as 6.0. Off by default: the exact grade decides.
 */
data class GradeSettings(
    val passMark: BigDecimal = DEFAULT_PASS_MARK,
    val defaultGoal: BigDecimal = DEFAULT_PASS_MARK,
    val roundLikePortal: Boolean = false,
) {
    /** The goal that applies to a course: its own, or the default, never below the pass mark. */
    fun effectiveGoal(courseGoal: BigDecimal?): BigDecimal =
        (courseGoal ?: defaultGoal).max(passMark)
}
