package com.example.minimo.domain

import java.math.BigDecimal

/** Where a course came from. Syncing replaces only [PORTAL] courses and never touches [MANUAL] ones. */
enum class CourseSource { MANUAL, PORTAL }

/**
 * @property goal the student's desired final grade for this course; `null` means "use the default goal".
 */
data class Course(
    val id: String,
    val code: String?,
    val name: String,
    val source: CourseSource,
    val goal: BigDecimal?,
    val evaluations: List<Evaluation>,
)

/**
 * A graded activity or exam.
 *
 * @property weight percentage of the final grade (0..100).
 */
data class Evaluation(
    val id: String,
    val name: String,
    val weight: BigDecimal,
    val status: EvaluationStatus,
)

sealed interface EvaluationStatus {
    /** Not graded yet; it still counts towards what the student can earn. */
    data object Pending : EvaluationStatus

    /** Graded on the 0..10 scale. */
    data class Graded(val grade: BigDecimal) : EvaluationStatus
}
