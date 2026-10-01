package com.example.minimo.domain

import java.math.BigDecimal

/** Pure grade maths. All arithmetic uses [BigDecimal]; the UI never computes. */
object GradeCalculator {
    /**
     * Analyses [evaluations] against [passMark] (T) and [goal] (G).
     *
     * Weights that do not sum to 100 are used as given; [CourseAnalysis.Computed.weightsSumTo100] tells the
     * UI to warn about it.
     */
    fun analyze(
        evaluations: List<Evaluation>,
        passMark: BigDecimal,
        goal: BigDecimal,
    ): CourseAnalysis {
        if (evaluations.isEmpty()) return CourseAnalysis.NoEvaluations

        val a = accumulated(evaluations)
        val p = pendingFraction(evaluations)
        val maxPossible = a + MAX_GRADE * p

        return CourseAnalysis.Computed(
            accumulated = RoundingPolicy.displayedGrade(a),
            pendingWeight = p.movePointRight(2),
            totalWeight = evaluations.sumOf { it.weight },
            maxPossible = RoundingPolicy.displayedGrade(maxPossible),
            toPass = outcome(a, p, maxPossible, passMark),
            toGoal = outcome(a, p, maxPossible, goal),
        )
    }

    /** Final grade F = A + s * P when the pending evaluations average [pendingAverage] (s). */
    fun project(
        evaluations: List<Evaluation>,
        pendingAverage: BigDecimal,
        passMark: BigDecimal,
        goal: BigDecimal,
    ): Projection {
        val exact = accumulated(evaluations) + pendingAverage * pendingFraction(evaluations)
        return Projection(
            pendingAverage = pendingAverage,
            finalGrade = RoundingPolicy.displayedGrade(exact),
            passes = RoundingPolicy.reaches(exact, passMark),
            reachesGoal = RoundingPolicy.reaches(exact, goal),
        )
    }

    /** A: points earned out of 10, from the exact grade and weight of each graded evaluation. */
    private fun accumulated(evaluations: List<Evaluation>): BigDecimal =
        evaluations.fold(BigDecimal.ZERO) { sum, e ->
            when (val status = e.status) {
                is EvaluationStatus.Graded -> sum + (status.grade * e.weight).movePointLeft(2)
                EvaluationStatus.Pending -> sum
            }
        }

    /** P: fraction (0..1) of the course still pending. */
    private fun pendingFraction(evaluations: List<Evaluation>): BigDecimal =
        evaluations
            .filter { it.status is EvaluationStatus.Pending }
            .sumOf { it.weight }
            .movePointLeft(2)

    private fun outcome(a: BigDecimal, p: BigDecimal, maxPossible: BigDecimal, target: BigDecimal): TargetOutcome {
        if (p.signum() == 0) {
            return TargetOutcome.NoPending(
                target = target,
                finalGrade = RoundingPolicy.displayedGrade(a),
                reached = RoundingPolicy.reaches(a, target),
            )
        }
        if (RoundingPolicy.reaches(a, target)) return TargetOutcome.Secured(target)

        val required = RoundingPolicy.requiredGrade(target - a, p)
        return if (RoundingPolicy.reaches(maxPossible, target)) {
            TargetOutcome.Reachable(target, required)
        } else {
            TargetOutcome.Impossible(target, required, RoundingPolicy.displayedGrade(maxPossible))
        }
    }
}
