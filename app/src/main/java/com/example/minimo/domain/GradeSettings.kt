package com.example.minimo.domain

import java.math.BigDecimal

val MAX_GRADE: BigDecimal = BigDecimal("10")
val DEFAULT_PASS_MARK: BigDecimal = BigDecimal("6.0")

/**
 * User-configurable thresholds.
 *
 * @property passMark minimum grade (T) to pass a course.
 * @property defaultGoal goal (G) applied to courses that have none of their own.
 */
data class GradeSettings(
    val passMark: BigDecimal = DEFAULT_PASS_MARK,
    val defaultGoal: BigDecimal = DEFAULT_PASS_MARK,
) {
    /** The goal that applies to a course: its own, or the default, never below the pass mark. */
    fun effectiveGoal(courseGoal: BigDecimal?): BigDecimal =
        (courseGoal ?: defaultGoal).max(passMark)
}
