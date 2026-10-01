package com.example.minimo.data.portal

import com.example.minimo.domain.CourseAnalysis
import com.example.minimo.domain.CourseSource
import com.example.minimo.domain.EvaluationStatus
import com.example.minimo.domain.GradeCalculator
import com.example.minimo.domain.TargetOutcome
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalCoursesTest {
    private val module = PortalModule("DMD104", "Datawarehouse y Minería de Datos", withdrawn = false)
    private val activities = checkNotNull(GradesParser.parseDetail(Fixtures.detalleDmd104)).activities
    private val data = PortalCourseData(module, activities)

    private fun analyze(confirmations: Set<String>): CourseAnalysis.Computed {
        val course = PortalCourses.toCourse(data, confirmations)
        return GradeCalculator.analyze(course.evaluations, BigDecimal("6.0"), BigDecimal("6.0")) as CourseAnalysis.Computed
    }

    @Test
    fun zeroPointZeroIsPendingAndTheRestIsGraded() {
        val course = PortalCourses.toCourse(data, emptySet())
        assertEquals("portal-DMD104", course.id)
        assertEquals("DMD104", course.code)
        assertEquals(CourseSource.PORTAL, course.source)
        assertEquals(
            listOf("Guía de ejercicios prácticos", "Desafío práctico 3", "Defensa final de proyecto"),
            course.evaluations.filter { it.status == EvaluationStatus.Pending }.map { it.name },
        )
    }

    @Test
    fun matchesTheHandCalculationForDmd104() {
        // Graded: 10×9 + 9.4×10 + 9.75×7 + 10×10 + 10×13 + 10×15 = 632.25 → A = 6.3225 (portal shows 6.3).
        val result = analyze(emptySet())
        assertEquals(BigDecimal("6.32"), result.accumulated)
        assertEquals(0, BigDecimal("36").compareTo(result.pendingWeight))
        assertTrue(result.toPass is TargetOutcome.Secured)
        assertEquals(BigDecimal("9.92"), result.maxPossible)
    }

    @Test
    fun aConfirmedRealZeroCountsAsGradedZero() {
        val key = PortalCourses.zeroKey("DMD104", "Guía de ejercicios prácticos")
        val course = PortalCourses.toCourse(data, setOf(key))
        val guide = course.evaluations.single { it.name == "Guía de ejercicios prácticos" }
        assertEquals(EvaluationStatus.Graded(BigDecimal("0.00")), guide.status)
        assertEquals(0, BigDecimal("28").compareTo(analyze(setOf(key)).pendingWeight))
    }

    @Test
    fun aRenamedActivityGoesBackToPending() {
        val key = PortalCourses.zeroKey("DMD104", "Guía de ejercicios prácticos")
        val renamed = data.copy(
            activities = activities.map {
                if (it.name == "Guía de ejercicios prácticos") it.copy(name = "Guía de ejercicios prácticos 1") else it
            },
        )
        val course = PortalCourses.toCourse(renamed, setOf(key))
        assertEquals(EvaluationStatus.Pending, course.evaluations.single { it.name.startsWith("Guía") }.status)
    }

    @Test
    fun aConfirmationForAnotherCourseDoesNotApply() {
        val key = PortalCourses.zeroKey("ESA501", "Guía de ejercicios prácticos")
        val course = PortalCourses.toCourse(data, setOf(key))
        assertEquals(3, course.evaluations.count { it.status == EvaluationStatus.Pending })
    }
}
