package com.example.minimo.data

import com.example.minimo.domain.Course
import com.example.minimo.domain.CourseSource
import com.example.minimo.domain.Evaluation
import com.example.minimo.domain.EvaluationStatus
import com.example.minimo.domain.GradeSettings
import java.math.BigDecimal
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * JSON snapshot of AppData. Numbers are stored as strings so no precision is lost, and every field has a
 * default so later phases can add fields without breaking snapshots written by this version.
 */

@Serializable
internal data class AppDataDto(
    val schemaVersion: Int = 1,
    val passMark: String = "6.0",
    val defaultGoal: String = "6.0",
    val courses: List<CourseDto> = emptyList(),
    val diagnosticMode: Boolean = false,
)

@Serializable
internal data class CourseDto(
    val id: String,
    val code: String? = null,
    val name: String,
    val source: CourseSource = CourseSource.MANUAL,
    val goal: String? = null,
    val evaluations: List<EvaluationDto> = emptyList(),
)

@Serializable
internal data class EvaluationDto(
    val id: String,
    val name: String,
    val weight: String,
    /** `null` means pending. */
    val grade: String? = null,
)

/** Reads and writes [AppData] as JSON. Decoding throws on corrupt data; callers must not overwrite it. */
internal object AppDataCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(data: AppData): String = json.encodeToString(AppDataDto.serializer(), data.toDto())

    /** @throws kotlinx.serialization.SerializationException or IllegalArgumentException when [text] is not valid. */
    fun decode(text: String): AppData = json.decodeFromString(AppDataDto.serializer(), text).toDomain()

    private fun AppData.toDto() = AppDataDto(
        passMark = settings.passMark.toPlainString(),
        defaultGoal = settings.defaultGoal.toPlainString(),
        courses = courses.map { it.toDto() },
        diagnosticMode = diagnosticMode,
    )

    private fun Course.toDto() = CourseDto(
        id = id,
        code = code,
        name = name,
        source = source,
        goal = goal?.toPlainString(),
        evaluations = evaluations.map { e ->
            EvaluationDto(
                id = e.id,
                name = e.name,
                weight = e.weight.toPlainString(),
                grade = (e.status as? EvaluationStatus.Graded)?.grade?.toPlainString(),
            )
        },
    )

    private fun AppDataDto.toDomain() = AppData(
        settings = GradeSettings(BigDecimal(passMark), BigDecimal(defaultGoal)),
        courses = courses.map { it.toDomain() },
        diagnosticMode = diagnosticMode,
    )

    private fun CourseDto.toDomain() = Course(
        id = id,
        code = code,
        name = name,
        source = source,
        goal = goal?.let(::BigDecimal),
        evaluations = evaluations.map { e ->
            Evaluation(
                id = e.id,
                name = e.name,
                weight = BigDecimal(e.weight),
                status = e.grade?.let { EvaluationStatus.Graded(BigDecimal(it)) } ?: EvaluationStatus.Pending,
            )
        },
    )
}
