package com.example.minimo.data

import com.example.minimo.domain.Course
import com.example.minimo.domain.CourseSource
import com.example.minimo.domain.Evaluation
import com.example.minimo.domain.EvaluationStatus
import com.example.minimo.domain.GradeSettings
import java.math.BigDecimal
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StorageTest {
    private val sample = AppData(
        settings = GradeSettings(passMark = BigDecimal("6.0"), defaultGoal = BigDecimal("7.5"), roundLikePortal = true),
        courses = listOf(
            Course(
                id = "c1",
                code = "MAT101",
                name = "Cálculo III",
                source = CourseSource.MANUAL,
                goal = BigDecimal("8.5"),
                evaluations = listOf(
                    Evaluation("e1", "Parcial 1", BigDecimal("30"), EvaluationStatus.Graded(BigDecimal("5.25"))),
                    Evaluation("e2", "Parcial 2", BigDecimal("30.5"), EvaluationStatus.Graded(BigDecimal("0"))),
                    Evaluation("e3", "Final", BigDecimal("39.5"), EvaluationStatus.Pending),
                ),
            ),
            Course("c2", null, "Sin evaluaciones", CourseSource.MANUAL, null, emptyList()),
        ),
    )

    @Test
    fun roundTripKeepsEverythingIncludingPendingAndZeroGrades() {
        assertEquals(sample, AppDataCodec.decode(AppDataCodec.encode(sample)))
    }

    @Test
    fun roundTripKeepsDecimalScale() {
        val decoded = AppDataCodec.decode(AppDataCodec.encode(sample))
        assertEquals("7.5", decoded.settings.defaultGoal.toPlainString())
        assertEquals("5.25", (decoded.courses[0].evaluations[0].status as EvaluationStatus.Graded).grade.toPlainString())
    }

    @Test
    fun emptyObjectDecodesToDefaults() {
        assertEquals(AppData(), AppDataCodec.decode("{}"))
    }

    @Test
    fun unknownKeysAreIgnored() {
        assertEquals(AppData(), AppDataCodec.decode("""{"somethingNew": 1}"""))
    }

    @Test
    fun corruptJsonThrows() {
        assertThrows(SerializationException::class.java) { AppDataCodec.decode("{not json") }
    }

    @Test
    fun invalidNumberThrows() {
        assertThrows(IllegalArgumentException::class.java) { AppDataCodec.decode("""{"passMark": "abc"}""") }
    }
}
