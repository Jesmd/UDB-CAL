package com.example.minimo.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import com.example.minimo.data.CourseRepository
import com.example.minimo.domain.Course
import com.example.minimo.domain.CourseAnalysis
import com.example.minimo.domain.CourseSource
import com.example.minimo.domain.Evaluation
import com.example.minimo.domain.EvaluationStatus
import com.example.minimo.domain.TargetOutcome
import com.example.minimo.ui.detail.COURSE_ID_ARG
import com.example.minimo.ui.detail.CourseDetailUiState
import com.example.minimo.ui.detail.CourseDetailViewModel
import com.example.minimo.ui.detail.SimulationState
import java.io.File
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class CourseDetailViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun graded(id: String, weight: String, grade: String) =
        Evaluation(id, id, BigDecimal(weight), EvaluationStatus.Graded(BigDecimal(grade)))

    private fun pending(id: String, weight: String) =
        Evaluation(id, id, BigDecimal(weight), EvaluationStatus.Pending)

    /** Vector 1: 30% 5.0, 40% 6.25, 30% pending. */
    private val course = Course(
        "c1", null, "Cálculo III", CourseSource.MANUAL, null,
        listOf(graded("e1", "30", "5.0"), graded("e2", "40", "6.25"), pending("e3", "30")),
    )

    private suspend fun kotlinx.coroutines.test.TestScope.viewModel(): CourseDetailViewModel {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "t.preferences_pb") }
        val repository = CourseRepository(store)
        repository.saveCourse(course)
        val viewModel = CourseDetailViewModel(repository, SavedStateHandle(mapOf(COURSE_ID_ARG to "c1")))
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel
    }

    private suspend fun CourseDetailViewModel.content(): CourseDetailUiState.Content =
        state.first { it is CourseDetailUiState.Content } as CourseDetailUiState.Content

    @Test
    fun computesMinimumForPassAndGoal() = runTest {
        val content = viewModel().content()
        val analysis = content.analysis as CourseAnalysis.Computed
        assertEquals(BigDecimal("6.67"), (analysis.toPass as TargetOutcome.Reachable).required)
        assertEquals(BigDecimal("6.0"), content.goal)
        assertTrue(content.canSimulate)
    }

    @Test
    fun simulatorIsEphemeralAndValidated() = runTest {
        val viewModel = viewModel()
        assertEquals(SimulationState.Idle, viewModel.content().simulation)

        viewModel.onSimulatorTextChange("7,0")
        val result = viewModel.content().simulation as SimulationState.Result
        assertEquals(BigDecimal("6.10"), result.projection.finalGrade)
        assertTrue(result.projection.passes)

        viewModel.onSimulatorTextChange("11")
        assertEquals(SimulationState.Invalid, viewModel.content().simulation)
    }

    @Test
    fun unknownCourseIsReportedAsNotFound() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "t2.preferences_pb") }
        val viewModel = CourseDetailViewModel(CourseRepository(store), SavedStateHandle(mapOf(COURSE_ID_ARG to "nope")))
        backgroundScope.launch { viewModel.state.collect {} }
        assertEquals(CourseDetailUiState.NotFound, viewModel.state.first { it != CourseDetailUiState.Loading })
    }
}
