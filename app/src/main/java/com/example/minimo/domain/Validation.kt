package com.example.minimo.domain

import java.math.BigDecimal

/** Rules for values typed by hand. Invalid input is rejected at capture time. */
object Validation {
    /** A grade is valid when it lies in 0..10. */
    fun isValidGrade(grade: BigDecimal): Boolean =
        grade.signum() >= 0 && grade <= MAX_GRADE

    /** A weight is valid when it lies in 0..100. */
    fun isValidWeight(weight: BigDecimal): Boolean =
        weight.signum() >= 0 && weight <= BigDecimal("100")

    /** The pass mark is valid when it lies in (0, 10] with at most one decimal. */
    fun isValidPassMark(passMark: BigDecimal): Boolean =
        passMark.signum() > 0 && passMark <= MAX_GRADE && passMark.stripTrailingZeros().scale() <= 1

    /** A goal is valid when it lies between the pass mark and 10, in steps of 0.1. */
    fun isValidGoal(goal: BigDecimal, passMark: BigDecimal): Boolean =
        goal >= passMark && goal <= MAX_GRADE && goal.stripTrailingZeros().scale() <= 1
}
