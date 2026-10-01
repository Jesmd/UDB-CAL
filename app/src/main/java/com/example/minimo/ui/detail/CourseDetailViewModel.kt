package com.example.minimo.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.minimo.AppContainer
import com.example.minimo.data.CourseRepository
import com.example.minimo.domain.Course
import com.example.minimo.domain.CourseAnalysis
import com.example.minimo.domain.DecimalInput
import com.example.minimo.domain.Evaluation
import com.example.minimo.domain.EvaluationStatus
import com.example.minimo.domain.GradeCalculator
import com.example.minimo.domain.GradeSettings
import com.example.minimo.domain.Projection
import com.example.minimo.domain.Validation
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val COURSE_ID_ARG = "courseId"

/** State of the "what if I get X?" simulator. It is never stored. */
sealed interface SimulationState {
    /** Nothing typed yet. */
    data object Idle : SimulationState

    /** The typed text is not a number between 0 and 10. */
    data object Invalid : SimulationState

    data class Result(val projection: Projection) : SimulationState
}

sealed interface CourseDetailUiState {
    data object Loading : CourseDetailUiState
    data object NotFound : CourseDetailUiState
    data object Error : CourseDetailUiState

    /**
     * @property goal the goal in effect for this course.
     * @property hasOwnGoal whether the goal was chosen for this course (as opposed to the default one).
     * @property minimumShown whether "Calcular mínimo" was pressed.
     * @property canSimulate whether anything is pending, so a simulation makes sense.
     */
    data class Content(
        val course: Course,
        val settings: GradeSettings,
        val goal: BigDecimal,
        val hasOwnGoal: Boolean,
        val analysis: CourseAnalysis,
        val minimumShown: Boolean,
        val canSimulate: Boolean,
        val simulatorText: String,
        val simulation: SimulationState,
    ) : CourseDetailUiState
}

class CourseDetailViewModel(
    private val repository: CourseRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val courseId: String = checkNotNull(savedStateHandle[COURSE_ID_ARG])

    private val minimumShown = MutableStateFlow(false)
    private val simulatorText = MutableStateFlow("")

    val state: StateFlow<CourseDetailUiState> = combine(
        repository.appData,
        minimumShown,
        simulatorText,
    ) { data, shown, text ->
        val course = data.courses.firstOrNull { it.id == courseId }
        if (course == null) {
            CourseDetailUiState.NotFound
        } else {
            val goal = data.settings.effectiveGoal(course.goal)
            val analysis = GradeCalculator.analyze(course.evaluations, data.settings.passMark, goal)
            val canSimulate = analysis is CourseAnalysis.Computed && analysis.pendingWeight.signum() > 0
            CourseDetailUiState.Content(
                course = course,
                settings = data.settings,
                goal = goal,
                hasOwnGoal = course.goal != null,
                analysis = analysis,
                minimumShown = shown,
                canSimulate = canSimulate,
                simulatorText = text,
                simulation = simulate(course, data.settings, goal, text),
            )
        }
    }
        .catch<CourseDetailUiState> { emit(CourseDetailUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CourseDetailUiState.Loading)

    fun onCalculateMinimum() {
        minimumShown.value = true
    }

    fun onSimulatorTextChange(text: String) {
        simulatorText.update { text }
    }

    /** Sets this course's goal, in tenths (e.g. 75 is 7.5). */
    fun setGoalTenths(tenths: Int) {
        viewModelScope.launch { repository.setCourseGoal(courseId, BigDecimal.valueOf(tenths.toLong(), 1)) }
    }

    fun useDefaultGoal() {
        viewModelScope.launch { repository.setCourseGoal(courseId, null) }
    }

    /** Adds an evaluation, or edits the one with [evaluationId]. A `null` [grade] means pending. */
    fun saveEvaluation(evaluationId: String?, name: String, weight: BigDecimal, grade: BigDecimal?) {
        val evaluation = Evaluation(
            id = evaluationId ?: UUID.randomUUID().toString(),
            name = name.trim(),
            weight = weight,
            status = grade?.let { EvaluationStatus.Graded(it) } ?: EvaluationStatus.Pending,
        )
        viewModelScope.launch { repository.saveEvaluation(courseId, evaluation) }
    }

    fun deleteEvaluation(evaluationId: String) {
        viewModelScope.launch { repository.deleteEvaluation(courseId, evaluationId) }
    }

    fun editCourse(name: String, code: String) {
        val current = (state.value as? CourseDetailUiState.Content)?.course ?: return
        viewModelScope.launch {
            repository.saveCourse(current.copy(name = name.trim(), code = code.trim().ifEmpty { null }))
        }
    }

    fun deleteCourse(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteCourse(courseId)
            onDeleted()
        }
    }

    private fun simulate(course: Course, settings: GradeSettings, goal: BigDecimal, text: String): SimulationState {
        if (text.isBlank()) return SimulationState.Idle
        val average = DecimalInput.parse(text)
        if (average == null || !Validation.isValidGrade(average)) return SimulationState.Invalid
        return SimulationState.Result(GradeCalculator.project(course.evaluations, average, settings.passMark, goal))
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { CourseDetailViewModel(container.courseRepository, createSavedStateHandle()) }
        }
    }
}
