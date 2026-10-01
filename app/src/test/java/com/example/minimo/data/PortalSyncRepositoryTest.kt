package com.example.minimo.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.minimo.data.portal.Fixtures
import com.example.minimo.data.portal.GradesParser
import com.example.minimo.data.portal.PortalCourseData
import com.example.minimo.data.portal.PortalModule
import com.example.minimo.data.portal.PortalSnapshot
import com.example.minimo.domain.Course
import com.example.minimo.domain.CourseSource
import com.example.minimo.domain.EvaluationStatus
import java.io.File
import java.math.BigDecimal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PortalSyncRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.repository() =
        CourseRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "t.preferences_pb") })

    private val activities = checkNotNull(GradesParser.parseDetail(Fixtures.detalleDmd104)).activities
    private val dmd = PortalCourseData(PortalModule("DMD104", "Datawarehouse y Minería de Datos", false), activities)
    private val esa = PortalCourseData(PortalModule("ESA501", "Estadística Aplicada", false), activities)
    private val manual = Course("m1", null, "Materia manual", CourseSource.MANUAL, null, emptyList())

    @Test
    fun syncReplacesOnlyPortalCoursesAndKeepsGoals() = runTest {
        val repository = repository()
        repository.saveCourse(manual)
        assertTrue(repository.applyPortalSync(PortalSnapshot("02 2026", listOf(dmd, esa)), syncedAtMillis = 1_000))
        repository.setCourseGoal("portal-DMD104", BigDecimal("8.5"))

        assertTrue(repository.applyPortalSync(PortalSnapshot("02 2026", listOf(dmd)), syncedAtMillis = 2_000))

        val data = repository.appData.first()
        assertEquals(listOf("m1", "portal-DMD104"), data.courses.map { it.id })
        assertEquals(manual, data.courses.first())
        assertEquals("8.5", data.courses[1].goal?.toPlainString())
        assertEquals(SyncInfo("02 2026", 2_000), data.lastSync)
    }

    @Test
    fun realZeroChoiceIsSavedAndSurvivesTheNextSync() = runTest {
        val repository = repository()
        repository.applyPortalSync(PortalSnapshot("02 2026", listOf(dmd)), 1_000)
        val guideId = repository.appData.first().courses.single().evaluations
            .single { it.name == "Guía de ejercicios prácticos" }.id

        repository.setRealZero("portal-DMD104", guideId, realZero = true)
        fun statusIn(data: AppData) = data.courses.single().evaluations.single { it.id == guideId }.status
        assertEquals(EvaluationStatus.Graded(BigDecimal.ZERO), statusIn(repository.appData.first()))

        repository.applyPortalSync(PortalSnapshot("02 2026", listOf(dmd)), 2_000)
        assertEquals(EvaluationStatus.Graded(BigDecimal("0.00")), statusIn(repository.appData.first()))

        repository.setRealZero("portal-DMD104", guideId, realZero = false)
        repository.applyPortalSync(PortalSnapshot("02 2026", listOf(dmd)), 3_000)
        assertEquals(EvaluationStatus.Pending, statusIn(repository.appData.first()))
    }

    @Test
    fun realZeroDoesNotApplyToGradedActivitiesOrManualCourses() = runTest {
        val repository = repository()
        repository.saveCourse(manual)
        repository.applyPortalSync(PortalSnapshot("02 2026", listOf(dmd)), 1_000)
        val before = repository.appData.first()
        val gradedId = before.courses[1].evaluations.first().id

        repository.setRealZero("portal-DMD104", gradedId, realZero = true)
        repository.setRealZero("m1", "whatever", realZero = true)

        assertEquals(before, repository.appData.first())
    }
}
