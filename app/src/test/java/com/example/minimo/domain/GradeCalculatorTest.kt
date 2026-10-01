package com.example.minimo.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeCalculatorTest {
    private val pass = BigDecimal("6.0")

    private fun d(value: String) = BigDecimal(value)

    private var nextId = 0

    /** A graded evaluation. "Nota 0" is `graded("40", "0")`. */
    private fun graded(weight: String, grade: String) =
        Evaluation("e${nextId++}", "Eval", d(weight), EvaluationStatus.Graded(d(grade)))

    private fun pending(weight: String) =
        Evaluation("e${nextId++}", "Eval", d(weight), EvaluationStatus.Pending)

    private fun analyze(evaluations: List<Evaluation>, goal: String = "6.0"): CourseAnalysis.Computed =
        GradeCalculator.analyze(evaluations, pass, d(goal)) as CourseAnalysis.Computed

    private fun reachable(outcome: TargetOutcome): BigDecimal =
        (outcome as TargetOutcome.Reachable).required

    private fun impossible(outcome: TargetOutcome): TargetOutcome.Impossible =
        outcome as TargetOutcome.Impossible

    // Vector 1
    @Test
    fun vector1_minimumAndSimulation() {
        val evals = listOf(graded("30", "5.0"), graded("40", "6.25"), pending("30"))
        val result = analyze(evals)
        assertEquals(d("4.00"), result.accumulated)
        assertEquals(d("6.67"), reachable(result.toPass))

        val pass7 = GradeCalculator.project(evals, d("7"), pass, d("6.0"))
        assertEquals(d("6.10"), pass7.finalGrade)
        assertTrue(pass7.passes)

        val fail6 = GradeCalculator.project(evals, d("6"), pass, d("6.0"))
        assertEquals(d("5.80"), fail6.finalGrade)
        assertFalse(fail6.passes)
    }

    // Vector 2
    @Test
    fun vector2_exactQuotient() {
        val result = analyze(listOf(graded("30", "5.0"), graded("30", "6.0"), pending("40")))
        assertEquals(d("3.30"), result.accumulated)
        assertEquals(d("6.75"), reachable(result.toPass))
    }

    // Vector 3
    @Test
    fun vector3_alreadyPassed() {
        val result = analyze(listOf(graded("40", "9.0"), graded("40", "6.5"), pending("20")))
        assertEquals(d("6.20"), result.accumulated)
        assertTrue(result.toPass is TargetOutcome.Secured)
    }

    // Vector 4
    @Test
    fun vector4_impossible() {
        val result = analyze(listOf(graded("50", "1.0"), graded("20", "2.5"), pending("30")))
        assertEquals(d("1.00"), result.accumulated)
        val outcome = impossible(result.toPass)
        assertEquals(d("16.67"), outcome.required)
        assertEquals(d("4.00"), outcome.maxPossible)
    }

    // Vector 5
    @Test
    fun vector5_borderCountsAsPassed() {
        val result = analyze(listOf(graded("30", "6.0"), graded("30", "6.0"), graded("40", "6.0")))
        val outcome = result.toPass as TargetOutcome.NoPending
        assertEquals(d("6.00"), outcome.finalGrade)
        assertTrue(outcome.reached)
    }

    // Vector 6
    @Test
    fun vector6_possibleAtExactlyTen() {
        val result = analyze(listOf(graded("30", "10"), graded("40", "0"), pending("30")))
        assertEquals(d("3.00"), result.accumulated)
        assertEquals(d("10.00"), reachable(result.toPass))
    }

    // Vector 7
    @Test
    fun vector7_goals() {
        val evals = listOf(graded("30", "5.0"), graded("40", "6.25"), pending("30"))
        assertEquals(d("6.67"), reachable(analyze(evals, "6").toGoal))
        assertEquals(d("10.00"), reachable(analyze(evals, "7").toGoal))
        val outcome = impossible(analyze(evals, "8").toGoal)
        assertEquals(d("13.34"), outcome.required)
        assertEquals(d("7.00"), outcome.maxPossible)
    }

    // Vector 8
    @Test
    fun vector8_goals() {
        val evals = listOf(graded("30", "5.0"), graded("30", "6.0"), pending("40"))
        assertEquals(d("9.25"), reachable(analyze(evals, "7").toGoal))
        val outcome = impossible(analyze(evals, "8").toGoal)
        assertEquals(d("11.75"), outcome.required)
        assertEquals(d("7.30"), outcome.maxPossible)
    }

    // Vector 9
    @Test
    fun vector9_goals() {
        val evals = listOf(graded("40", "9.0"), graded("40", "6.5"), pending("20"))
        assertTrue(analyze(evals, "6").toGoal is TargetOutcome.Secured)
        assertEquals(d("4.00"), reachable(analyze(evals, "7").toGoal))
        assertEquals(d("9.00"), reachable(analyze(evals, "8").toGoal))
        val outcome = impossible(analyze(evals, "8.5").toGoal)
        assertEquals(d("11.50"), outcome.required)
        assertEquals(d("8.20"), outcome.maxPossible)
    }

    // Vector 10
    @Test
    fun vector10_nothingPendingGoals() {
        val evals = listOf(graded("50", "7.5"), graded("50", "7.5"))
        val met = analyze(evals, "7").toGoal as TargetOutcome.NoPending
        assertEquals(d("7.50"), met.finalGrade)
        assertTrue(met.reached)
        val notMet = analyze(evals, "8").toGoal as TargetOutcome.NoPending
        assertFalse(notMet.reached)
    }

    @Test
    fun weightsNotSummingTo100AreUsedAsGivenAndFlagged() {
        val result = analyze(listOf(graded("30", "5.0"), graded("30", "6.0"), pending("35")))
        assertEquals(d("95"), result.totalWeight)
        assertFalse(result.weightsSumTo100)
        assertEquals(d("35.00"), result.pendingWeight.setScale(2))
        // (6 - 3.30) / 0.35 = 7.714... -> rounded up
        assertEquals(d("7.72"), reachable(result.toPass))
    }

    @Test
    fun weightsSummingTo100AreNotFlagged() {
        val result = analyze(listOf(graded("70", "5.0"), pending("30")))
        assertTrue(result.weightsSumTo100)
    }

    @Test
    fun courseWithoutEvaluationsHasNothingToCompute() {
        assertEquals(CourseAnalysis.NoEvaluations, GradeCalculator.analyze(emptyList(), pass, pass))
    }

    @Test
    fun requiredGradeNeverUnderstatesAndMaxPossibleNeverOverstates() {
        // A = 3.0, P = 0.30 -> goal 6.1 needs 10.33333..., which is impossible; shown rounded up.
        val result = analyze(listOf(graded("30", "10"), graded("40", "0"), pending("30")), "6.1")
        assertEquals(d("10.34"), impossible(result.toGoal).required)
        // Truncated display: A = 4.0025 shows as 4.00, not 4.01.
        val truncated = analyze(listOf(graded("50", "8.005"), pending("50")))
        assertEquals(d("4.00"), truncated.accumulated)
    }

    @Test
    fun verdictUsesExactValueNotRoundedDisplay() {
        // Final = 5.99 exactly: displays as 5.99 and does not pass under the strict policy.
        val result = analyze(listOf(graded("100", "5.99")))
        assertFalse((result.toPass as TargetOutcome.NoPending).reached)
    }

    @Test
    fun projectionIsAccumulatedPlusAverageTimesPending() {
        val evals = listOf(graded("30", "10"), graded("40", "0"), pending("30"))
        val projection = GradeCalculator.project(evals, d("10"), pass, d("6.0"))
        assertEquals(d("6.00"), projection.finalGrade)
        assertTrue(projection.passes)
        assertTrue(projection.reachesGoal)
    }

    @Test
    fun rejectsGradesOutsideZeroToTen() {
        assertTrue(Validation.isValidGrade(d("0")))
        assertTrue(Validation.isValidGrade(d("10")))
        assertFalse(Validation.isValidGrade(d("-0.01")))
        assertFalse(Validation.isValidGrade(d("10.01")))
    }

    @Test
    fun goalMustBeBetweenPassMarkAndTenInTenths() {
        assertTrue(Validation.isValidGoal(d("6.0"), pass))
        assertTrue(Validation.isValidGoal(d("8.5"), pass))
        assertTrue(Validation.isValidGoal(d("10"), pass))
        assertFalse(Validation.isValidGoal(d("5.9"), pass))
        assertFalse(Validation.isValidGoal(d("10.1"), pass))
        assertFalse(Validation.isValidGoal(d("7.25"), pass))
    }

    @Test
    fun effectiveGoalFallsBackToDefaultAndNeverBelowPassMark() {
        val settings = GradeSettings(passMark = d("6.0"), defaultGoal = d("7.0"))
        assertEquals(d("7.0"), settings.effectiveGoal(null))
        assertEquals(d("8.5"), settings.effectiveGoal(d("8.5")))
        assertEquals(d("6.0"), settings.effectiveGoal(d("5.0")))
    }

    // Portal rounding (Settings switch, off by default).

    private fun rounded(evaluations: List<Evaluation>, goal: String = "6.0"): CourseAnalysis.Computed =
        GradeCalculator.analyze(evaluations, pass, d(goal), roundLikePortal = true) as CourseAnalysis.Computed

    @Test
    fun portalGradeIsOneDecimalHalfUp() {
        assertEquals(d("2.0"), analyze(listOf(graded("50", "3.94"), pending("50"))).accumulatedOnPortal) // 1.97
        assertEquals(d("6.3"), RoundingPolicy.portalGrade(d("6.3225")))
        assertEquals(d("6.0"), RoundingPolicy.portalGrade(d("5.95")))
        assertEquals(d("5.9"), RoundingPolicy.portalGrade(d("5.9499")))
    }

    @Test
    fun roundingLowersTheMinimumToWhatRoundsUpToTheTarget() {
        val evals = listOf(graded("30", "5.0"), graded("40", "6.25"), pending("30"))
        // (5.95 - 4.00) / 0.30 = 6.50, instead of 6.67 with the exact rule.
        assertEquals(d("6.50"), reachable(rounded(evals).toPass))
        // Goal 7: (6.95 - 4.00) / 0.30 = 9.8333... -> 9.84; goal 8: 13.17, impossible.
        assertEquals(d("9.84"), reachable(rounded(evals, "7").toGoal))
        assertEquals(d("13.17"), impossible(rounded(evals, "8").toGoal).required)

        val strict = GradeCalculator.project(evals, d("6.5"), pass, pass)
        val withRounding = GradeCalculator.project(evals, d("6.5"), pass, pass, roundLikePortal = true)
        assertEquals(d("5.95"), strict.finalGrade)
        assertEquals(d("6.0"), withRounding.finalGradeOnPortal)
        assertFalse(strict.passes)
        assertTrue(withRounding.passes)
    }

    @Test
    fun roundingDecidesTheBorderCases() {
        // Final 5.95: fails with the exact rule, passes when it counts as the portal's 6.0.
        val final595 = listOf(graded("50", "5.9"), graded("50", "6.0"))
        assertFalse((analyze(final595).toPass as TargetOutcome.NoPending).reached)
        assertTrue((rounded(final595).toPass as TargetOutcome.NoPending).reached)
        assertFalse((rounded(listOf(graded("100", "5.94"))).toPass as TargetOutcome.NoPending).reached)

        // A = 5.95 with 30% pending: already secured only when rounding.
        val secured = listOf(graded("70", "8.5"), pending("30"))
        assertEquals(d("0.17"), reachable(analyze(secured).toPass))
        assertTrue(rounded(secured).toPass is TargetOutcome.Secured)

        // A = 2.95, P = 0.30, F_max = 5.95: impossible with the exact rule, needs exactly 10 when rounding.
        val border = listOf(graded("50", "5.9"), graded("20", "0"), pending("30"))
        assertEquals(d("10.17"), impossible(analyze(border).toPass).required)
        assertEquals(d("10.00"), reachable(rounded(border).toPass))
    }
}
