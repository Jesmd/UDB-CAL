package com.example.minimo.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.minimo.data.portal.PortalCourses
import com.example.minimo.data.portal.PortalSnapshot
import com.example.minimo.domain.Course
import com.example.minimo.domain.CourseSource
import com.example.minimo.domain.EvaluationStatus
import com.example.minimo.domain.Evaluation
import com.example.minimo.domain.GradeSettings
import java.io.IOException
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException

/** Local storage of courses, evaluations, goals and settings. Everything is kept as one JSON snapshot. */
class CourseRepository(private val store: DataStore<Preferences>) {
    /**
     * The stored data. Throws when the stored snapshot cannot be read; collectors must handle that
     * (nothing is overwritten in that case).
     */
    val appData: Flow<AppData> = store.data.map { read(it) }

    private val _saveFailures = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits each time a change could not be saved, so the UI can say so. */
    val saveFailures: Flow<Unit> = _saveFailures

    /** Adds the course, or replaces the one with the same id. */
    suspend fun saveCourse(course: Course) = update { data ->
        val index = data.courses.indexOfFirst { it.id == course.id }
        data.copy(
            courses = if (index < 0) data.courses + course else data.courses.toMutableList().also { it[index] = course },
        )
    }

    suspend fun deleteCourse(courseId: String) = update { data ->
        data.copy(courses = data.courses.filterNot { it.id == courseId })
    }

    /** Adds the evaluation to the course, or replaces the one with the same id. */
    suspend fun saveEvaluation(courseId: String, evaluation: Evaluation) = updateCourse(courseId) { course ->
        val index = course.evaluations.indexOfFirst { it.id == evaluation.id }
        course.copy(
            evaluations = if (index < 0) {
                course.evaluations + evaluation
            } else {
                course.evaluations.toMutableList().also { it[index] = evaluation }
            },
        )
    }

    suspend fun deleteEvaluation(courseId: String, evaluationId: String) = updateCourse(courseId) { course ->
        course.copy(evaluations = course.evaluations.filterNot { it.id == evaluationId })
    }

    /** Sets the course's own goal; `null` goes back to the default goal. */
    suspend fun setCourseGoal(courseId: String, goal: BigDecimal?) = updateCourse(courseId) { it.copy(goal = goal) }

    suspend fun saveSettings(settings: GradeSettings) = update { it.copy(settings = settings) }

    suspend fun setDiagnosticMode(enabled: Boolean) = update { it.copy(diagnosticMode = enabled) }

    /**
     * Replaces every portal course with [snapshot] in one step; manual courses are never touched.
     * Goals chosen for portal courses and "real zero" choices are kept. Returns whether it was saved.
     */
    suspend fun applyPortalSync(snapshot: PortalSnapshot, syncedAtMillis: Long): Boolean = update { data ->
        val previousGoals = data.courses.filter { it.source == CourseSource.PORTAL }.associate { it.id to it.goal }
        val portalCourses = snapshot.courses.map { course ->
            PortalCourses.toCourse(course, data.zeroConfirmations).let { it.copy(goal = previousGoals[it.id]) }
        }
        data.copy(
            courses = data.courses.filter { it.source == CourseSource.MANUAL } + portalCourses,
            lastSync = SyncInfo(snapshot.cycle, syncedAtMillis),
        )
    }

    /**
     * Marks a portal activity shown as 0.00 as a real zero ([realZero] = true) or back as pending.
     * The choice is remembered across syncs by course code + activity name.
     */
    suspend fun setRealZero(courseId: String, evaluationId: String, realZero: Boolean) = update { data ->
        val course = data.courses.firstOrNull { it.id == courseId && it.source == CourseSource.PORTAL } ?: return@update data
        val code = course.code ?: return@update data
        val evaluation = course.evaluations.firstOrNull { it.id == evaluationId } ?: return@update data
        val status = evaluation.status
        val isPortalZero = status == EvaluationStatus.Pending ||
            (status is EvaluationStatus.Graded && status.grade.signum() == 0)
        if (!isPortalZero) return@update data

        val key = PortalCourses.zeroKey(code, evaluation.name)
        val newStatus = if (realZero) EvaluationStatus.Graded(BigDecimal.ZERO) else EvaluationStatus.Pending
        data.copy(
            zeroConfirmations = if (realZero) data.zeroConfirmations + key else data.zeroConfirmations - key,
            courses = data.courses.map { c ->
                if (c.id != courseId) c else c.copy(
                    evaluations = c.evaluations.map { if (it.id == evaluationId) it.copy(status = newStatus) else it },
                )
            },
        )
    }

    private suspend fun updateCourse(courseId: String, change: (Course) -> Course) = update { data ->
        data.copy(courses = data.courses.map { if (it.id == courseId) change(it) else it })
    }

    /** Applies [change] to the stored data. Returns whether it was saved; failures are also reported. */
    private suspend fun update(change: (AppData) -> AppData): Boolean {
        try {
            store.edit { prefs -> prefs[KEY] = AppDataCodec.encode(change(read(prefs))) }
            return true
        } catch (_: IOException) {
        } catch (_: SerializationException) {
        } catch (_: IllegalArgumentException) {
        }
        _saveFailures.tryEmit(Unit)
        return false
    }

    private fun read(prefs: Preferences): AppData =
        prefs[KEY]?.let(AppDataCodec::decode) ?: AppData()

    private companion object {
        val KEY = stringPreferencesKey("app_data")
    }
}
