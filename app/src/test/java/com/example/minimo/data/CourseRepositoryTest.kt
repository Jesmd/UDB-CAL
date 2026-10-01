package com.example.minimo.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.minimo.domain.Course
import com.example.minimo.domain.CourseSource
import com.example.minimo.domain.Evaluation
import com.example.minimo.domain.EvaluationStatus
import com.example.minimo.domain.GradeSettings
import java.io.File
import java.math.BigDecimal
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CourseRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.newStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "test.preferences_pb") }

    private fun course(id: String, name: String = "Materia $id") =
        Course(id, null, name, CourseSource.MANUAL, null, emptyList())

    private fun evaluation(id: String, weight: String = "30", grade: String? = null) = Evaluation(
        id, "Eval $id", BigDecimal(weight),
        grade?.let { EvaluationStatus.Graded(BigDecimal(it)) } ?: EvaluationStatus.Pending,
    )

    @Test
    fun startsEmptyWithDefaultSettings() = runTest {
        assertEquals(AppData(), CourseRepository(newStore()).appData.first())
    }

    @Test
    fun savedDataSurvivesANewRepositoryOnTheSameFile() = runTest {
        val store = newStore()
        val first = CourseRepository(store)
        first.saveCourse(course("c1"))
        first.saveEvaluation("c1", evaluation("e1", "40", "6.5"))
        first.saveSettings(GradeSettings(BigDecimal("6.0"), BigDecimal("7.0")))

        val reopened = CourseRepository(store).appData.first()
        assertEquals(listOf("c1"), reopened.courses.map { it.id })
        assertEquals(listOf("e1"), reopened.courses[0].evaluations.map { it.id })
        assertEquals("7.0", reopened.settings.defaultGoal.toPlainString())
    }

    @Test
    fun saveCourseReplacesExistingOneAndKeepsOrder() = runTest {
        val repository = CourseRepository(newStore())
        repository.saveCourse(course("a"))
        repository.saveCourse(course("b"))
        repository.saveCourse(course("a", name = "Renombrada"))

        val courses = repository.appData.first().courses
        assertEquals(listOf("a", "b"), courses.map { it.id })
        assertEquals("Renombrada", courses[0].name)
    }

    @Test
    fun deleteCourseRemovesOnlyThatCourse() = runTest {
        val repository = CourseRepository(newStore())
        repository.saveCourse(course("a"))
        repository.saveCourse(course("b"))
        repository.deleteCourse("a")
        assertEquals(listOf("b"), repository.appData.first().courses.map { it.id })
    }

    @Test
    fun evaluationsAreAddedEditedAndDeleted() = runTest {
        val repository = CourseRepository(newStore())
        repository.saveCourse(course("c"))
        repository.saveEvaluation("c", evaluation("e1"))
        repository.saveEvaluation("c", evaluation("e2", "20"))
        repository.saveEvaluation("c", evaluation("e1", "30", "9.0"))
        repository.deleteEvaluation("c", "e2")

        val evaluations = repository.appData.first().courses.single().evaluations
        assertEquals(listOf("e1"), evaluations.map { it.id })
        assertEquals(EvaluationStatus.Graded(BigDecimal("9.0")), evaluations[0].status)
    }

    @Test
    fun goalIsStoredPerCourseAndCanBeCleared() = runTest {
        val repository = CourseRepository(newStore())
        repository.saveCourse(course("a"))
        repository.saveCourse(course("b"))
        repository.setCourseGoal("a", BigDecimal("8.5"))
        assertEquals("8.5", repository.appData.first().courses[0].goal?.toPlainString())
        assertEquals(null, repository.appData.first().courses[1].goal)

        repository.setCourseGoal("a", null)
        assertEquals(null, repository.appData.first().courses[0].goal)
    }

    @Test
    fun corruptSnapshotIsReportedAndNeverOverwritten() = runTest {
        val store = newStore()
        store.edit { it[stringPreferencesKey("app_data")] = "{not json" }
        val repository = CourseRepository(store)

        val readFailure = runCatching { repository.appData.first() }.exceptionOrNull()
        assertTrue("reading corrupt data must fail", readFailure != null)

        val failure = async(start = CoroutineStart.UNDISPATCHED) { repository.saveFailures.first() }
        repository.saveCourse(course("c"))
        failure.await()

        assertEquals("{not json", store.data.first()[stringPreferencesKey("app_data")])
        assertTrue(failure.isCompleted)
    }
}
