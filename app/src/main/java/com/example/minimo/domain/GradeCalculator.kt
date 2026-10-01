package com.example.minimo.domain

import java.math.BigDecimal

/** Pure grade maths. All arithmetic uses [BigDecimal]; the UI never computes. */
object GradeCalculator {
    /**
     * Analyses [evaluations] against [passMark] (T) and [goal] (G). With [roundLikePortal] a final grade counts
     * as the portal shows it (one decimal, half up), see [RoundingPolicy].
     *
     * Weights that do not sum to 100 are used as given; [CourseAnalysis.Computed.weightsSumTo100] tells the
     * UI to warn about it.
     */
    fun analyze(
        evaluations: List<Evaluation>,
        passMark: BigDecimal,
        goal: BigDecimal,
        roundLikePortal: Boolean = false,
    ): CourseAnalysis {
        if (evaluations.isEmpty()) return CourseAnalysis.NoEvaluations

        val a = accumulated(evaluations)
        val p = pendingFraction(evaluations)
        val maxPossible = a + MAX_GRADE * p

        return CourseAnalysis.Computed(
            accumulated = RoundingPolicy.displayedGrade(a),
            accumulatedOnPortal = RoundingPolicy.portalGrade(a),
            pendingWeight = p.movePointRight(2),
            totalWeight = evaluations.sumOf { it.weight },
            maxPossible = RoundingPolicy.displayedGrade(maxPossible),
            toPass = outcome(a, p, maxPossible, passMark, roundLikePortal),
            toGoal = outcome(a, p, maxPossible, goal, roundLikePortal),
        )
    }

    /** Final grade F = A + s * P when the pending evaluations average [pendingAverage] (s). */
    fun project(
        evaluations: List<Evaluation>,
        pendingAverage: BigDecimal,
        passMark: BigDecimal,
        goal: BigDecimal,
        roundLikePortal: Boolean = false,
    ): Projection {
        val exact = accumulated(evaluations) + pendingAverage * pendingFraction(evaluations)
        return Projection(
            pendingAverage = pendingAverage,
            finalGrade = RoundingPolicy.displayedGrade(exact),
            finalGradeOnPortal = RoundingPolicy.portalGrade(exact),
            passes = RoundingPolicy.reaches(exact, passMark, roundLikePortal),
            reachesGoal = RoundingPolicy.reaches(exact, goal, roundLikePortal),
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

    private fun outcome(
        a: BigDecimal,
        p: BigDecimal,
        maxPossible: BigDecimal,
        target: BigDecimal,
        roundLikePortal: Boolean,
    ): TargetOutcome {
        if (p.signum() == 0) {
            return TargetOutcome.NoPending(
                target = target,
                finalGrade = RoundingPolicy.displayedGrade(a),
                reached = RoundingPolicy.reaches(a, target, roundLikePortal),
            )
        }
        if (RoundingPolicy.reaches(a, target, roundLikePortal)) return TargetOutcome.Secured(target)

        // The final grade must reach the lowest exact value that counts as the target (the target itself, or
        // target - 0.05 when rounding like the portal).
        val required = RoundingPolicy.requiredGrade(RoundingPolicy.lowestReaching(target, roundLikePortal) - a, p)
        return if (RoundingPolicy.reaches(maxPossible, target, roundLikePortal)) {
            TargetOutcome.Reachable(target, required)
        } else {
            TargetOutcome.Impossible(target, required, RoundingPolicy.displayedGrade(maxPossible))
        }
    }
}
