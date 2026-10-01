package com.example.minimo.domain

import java.math.BigDecimal

/**
 * Result of analysing a course. Every [BigDecimal] here is ready to display (already rounded by
 * [RoundingPolicy]); the verdicts were decided with exact values before rounding.
 */
sealed interface CourseAnalysis {
    /** The course has no evaluations, so there is nothing to compute. */
    data object NoEvaluations : CourseAnalysis

    /**
     * @property accumulated points already earned out of 10 (A).
     * @property accumulatedOnPortal A as the portal shows it (one decimal, rounded), e.g. 1.97 -> 2.0.
     * @property pendingWeight percentage of the course still pending (P * 100).
     * @property totalWeight sum of all weights, in percent; should be 100.
     * @property maxPossible best possible final grade (F_max).
     * @property toPass what is needed to reach the pass mark.
     * @property toGoal what is needed to reach the goal.
     */
    data class Computed(
        val accumulated: BigDecimal,
        val accumulatedOnPortal: BigDecimal,
        val pendingWeight: BigDecimal,
        val totalWeight: BigDecimal,
        val maxPossible: BigDecimal,
        val toPass: TargetOutcome,
        val toGoal: TargetOutcome,
    ) : CourseAnalysis {
        val weightsSumTo100: Boolean get() = totalWeight.compareTo(BigDecimal("100")) == 0
    }
}

/** What it takes to reach one [target] grade (the pass mark or the goal). */
sealed interface TargetOutcome {
    val target: BigDecimal

    /** Nothing is pending: the course is decided. [reached] says whether [finalGrade] meets [target]. */
    data class NoPending(
        override val target: BigDecimal,
        val finalGrade: BigDecimal,
        val reached: Boolean,
    ) : TargetOutcome

    /** Already guaranteed, even scoring 0 on everything pending. */
    data class Secured(override val target: BigDecimal) : TargetOutcome

    /** Reachable by averaging [required] (0..10, rounded up) on what is pending. */
    data class Reachable(override val target: BigDecimal, val required: BigDecimal) : TargetOutcome

    /** Unreachable: even 10 on everything pending falls short. [required] is above 10. */
    data class Impossible(
        override val target: BigDecimal,
        val required: BigDecimal,
        val maxPossible: BigDecimal,
    ) : TargetOutcome
}

/** Outcome of "what if I average [pendingAverage] on what is pending?". */
data class Projection(
    val pendingAverage: BigDecimal,
    val finalGrade: BigDecimal,
    /** The projected final grade as the portal would show it (one decimal, rounded). */
    val finalGradeOnPortal: BigDecimal,
    val passes: Boolean,
    val reachesGoal: Boolean,
)
